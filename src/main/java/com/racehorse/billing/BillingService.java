package com.racehorse.billing;

import com.racehorse.auth.ActorContext;
import com.racehorse.auth.CurrentActorProvider;
import com.racehorse.auth.RoleCode;
import com.racehorse.billing.BillingDtos.CancelRequest;
import com.racehorse.billing.BillingDtos.CreatePaymentRequest;
import com.racehorse.billing.BillingDtos.GenerateRequest;
import com.racehorse.billing.BillingDtos.GenerateResult;
import com.racehorse.billing.BillingDtos.InvoiceDetail;
import com.racehorse.billing.BillingDtos.InvoiceSummary;
import com.racehorse.billing.BillingDtos.PaymentLink;
import com.racehorse.billing.BillingDtos.PaymentResult;
import com.racehorse.billing.BillingDtos.Price;
import com.racehorse.billing.BillingDtos.Skipped;
import com.racehorse.billing.BillingDtos.UpdatePricesRequest;
import com.racehorse.billing.BillingRepository.HorseRow;
import com.racehorse.billing.BillingRepository.InvoiceRow;
import com.racehorse.billing.BillingRepository.OwnerRow;
import com.racehorse.billing.BillingRepository.PaymentRow;
import com.racehorse.billing.BillingRepository.PriceRow;
import com.racehorse.billing.BillingRepository.Stay;
import com.racehorse.billing.VnpayGateway.QueryResult;
import com.racehorse.common.api.ErrorDetail;
import com.racehorse.common.error.BusinessException;
import com.racehorse.common.error.ErrorCode;
import com.racehorse.notification.NotificationApi;
import com.racehorse.notification.NotificationPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.text.NumberFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import static com.racehorse.billing.BillingDtos.BOARDING;
import static com.racehorse.billing.BillingDtos.TRAINING;
import static com.racehorse.billing.VnpayGateway.VN;
import static com.racehorse.billing.VnpayGateway.VNP_TIME;

/**
 * DEC-016 service billing. The Club Manager keeps a price list (boarding + care per day, training per COMPLETED session),
 * issues one monthly invoice per horse computed from real stable assignments and sessions, and the horse's managing
 * owner pays it through VNPay sandbox. An invoice becomes PAID only after VNPay confirms the transaction server to
 * server (IPN, or querydr when the IPN cannot reach a local machine); the browser return alone never marks it paid.
 */
@Service
public class BillingService {

    public static final long MIN_PRICE = 5_000L;
    public static final long MAX_PRICE = 10_000_000L;
    /** Upper bound of one online payment; a larger invoice is settled with the club directly. */
    public static final long MAX_ONLINE_PAYMENT = 999_999_999L;
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final BillingRepository repository;
    private final VnpayGateway gateway;
    private final MomoGateway momo;
    private final CurrentActorProvider actors;
    private final NotificationPublisher notifications;

    public BillingService(BillingRepository repository, VnpayGateway gateway, CurrentActorProvider actors,
                          NotificationPublisher notifications, MomoGateway momo) {
        this.momo = momo;
        this.repository = repository;
        this.gateway = gateway;
        this.actors = actors;
        this.notifications = notifications;
    }

    /* ======================= Club Manager: prices ======================= */

    @Transactional(readOnly = true)
    public List<Price> prices() {
        return List.of(price(BOARDING), price(TRAINING));
    }

    @Transactional
    public List<Price> updatePrices(UpdatePricesRequest request) {
        ActorContext actor = actor();
        if (request == null || (request.boardingCareDaily() == null && request.trainingSession() == null)) {
            throw validation("boardingCareDaily", "Enter at least one price.");
        }
        List<ErrorDetail> errors = new ArrayList<>();
        checkPrice(errors, "boardingCareDaily", request.boardingCareDaily());
        checkPrice(errors, "trainingSession", request.trainingSession());
        if (!errors.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, errors.getFirst().message(), errors);
        }
        if (request.boardingCareDaily() != null) {
            repository.insertPrice(BOARDING, request.boardingCareDaily(), actor.userId());
        }
        if (request.trainingSession() != null) {
            repository.insertPrice(TRAINING, request.trainingSession(), actor.userId());
        }
        return prices();
    }

    /* ======================= Club Manager: receiving bank account (VietQR) ======================= */

    private static final java.util.regex.Pattern BIN = java.util.regex.Pattern.compile("[0-9]{6,8}");
    private static final java.util.regex.Pattern ACCOUNT_NO = java.util.regex.Pattern.compile("[0-9A-Za-z]{4,19}");

    @Transactional(readOnly = true)
    public BillingDtos.PaymentAccount paymentAccount() {
        return repository.currentPaymentAccount().orElse(null);
    }

    @Transactional
    public BillingDtos.PaymentAccount updatePaymentAccount(BillingDtos.PaymentAccountRequest request) {
        ActorContext actor = actor();
        String bin = request == null || request.bankBin() == null ? "" : request.bankBin().trim();
        String bankName = request == null || request.bankName() == null ? "" : request.bankName().trim();
        String accountNo = request == null || request.accountNo() == null ? "" : request.accountNo().replace(" ", "").trim();
        String accountName = request == null || request.accountName() == null ? "" : request.accountName().trim();
        List<ErrorDetail> errors = new ArrayList<>();
        if (!BIN.matcher(bin).matches()) {
            errors.add(new ErrorDetail("bankBin", "Choose a bank."));
        }
        if (bankName.isEmpty() || bankName.length() > 100) {
            errors.add(new ErrorDetail("bankName", "Choose a bank."));
        }
        if (!ACCOUNT_NO.matcher(accountNo).matches()) {
            errors.add(new ErrorDetail("accountNo", "Account number must be 4-19 letters or digits."));
        }
        if (accountName.isEmpty() || accountName.length() > 100) {
            errors.add(new ErrorDetail("accountName", "Account holder name is required (at most 100 characters)."));
        }
        if (!errors.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, errors.getFirst().message(), errors);
        }
        repository.insertPaymentAccount(bin, bankName, accountNo, accountName, actor.userId());
        return paymentAccount();
    }

    /* ======================= Club Manager: invoices ======================= */

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public GenerateResult generate(GenerateRequest request) {
        ActorContext actor = actor();
        YearMonth month = parseMonth(request == null ? null : request.billingMonth());
        LocalDate today = LocalDate.now(VN);
        if (month.isAfter(YearMonth.from(today))) {
            throw validation("billingMonth", "Cannot bill a future month.");
        }
        LocalDate start = month.atDay(1);
        LocalDate end = month.atEndOfMonth().isAfter(today) ? today : month.atEndOfMonth();
        PriceRow boarding = repository.currentPrice(BOARDING).orElse(null);
        PriceRow training = repository.currentPrice(TRAINING).orElse(null);
        if (boarding == null || training == null) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "Set both service prices before issuing invoices.",
                    List.of(new ErrorDetail("prices", "Set both service prices before issuing invoices.")));
        }
        long boardingPrice = boarding.unitPrice().longValueExact();
        long trainingPrice = training.unitPrice().longValueExact();
        LocalDateTime fromUtc = start.atStartOfDay(VN).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
        LocalDateTime toUtc = end.plusDays(1).atStartOfDay(VN).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();

        List<InvoiceSummary> created = new ArrayList<>();
        List<Skipped> skipped = new ArrayList<>();
        int sequence = repository.invoiceCountForMonth(start);
        for (HorseRow horse : repository.horsesStabledBetween(start, end)) {
            // A month can be billed in parts: the current month up to today, then the rest after it has passed.
            // A new part starts the day after the last live invoice of the month, so no day or session is billed twice.
            LocalDate from = repository.billedUntil(horse.id(), start).map(day -> day.plusDays(1)).orElse(start);
            if (from.isAfter(end)) {
                skipped.add(new Skipped(horse.id(), horse.horseCode(), horse.name(), "ALREADY_INVOICED"));
                continue;
            }
            Optional<Long> owner = billedOwner(repository.ownersOn(horse.id(), end));
            if (owner.isEmpty()) {
                skipped.add(new Skipped(horse.id(), horse.horseCode(), horse.name(), "NO_MANAGING_OWNER"));
                continue;
            }
            LocalDateTime partFromUtc = from.equals(start) ? fromUtc
                    : from.atStartOfDay(VN).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
            int days = boardingDays(repository.stays(horse.id(), from, end), from, end);
            int sessions = repository.completedSessions(horse.id(), partFromUtc, toUtc);
            if (days == 0 && sessions == 0) {
                skipped.add(new Skipped(horse.id(), horse.horseCode(), horse.name(), "NO_BILLABLE_SERVICE"));
                continue;
            }
            long total = Math.addExact(Math.multiplyExact((long) days, boardingPrice),
                    Math.multiplyExact((long) sessions, trainingPrice));
            sequence++;
            String code = "HD-" + month.toString().replace("-", "") + "-" + String.format("%04d", sequence);
            long invoiceId = repository.insertInvoice(code, horse.id(), owner.get(), start, from, end, total,
                    today.plusDays(7), actor.userId());
            int lineNo = 0;
            if (days > 0) {
                repository.insertLine(invoiceId, ++lineNo, BOARDING, "Lưu chuồng & chăm sóc: " + days
                        + " ngày ngựa ở chuồng trong kỳ " + DAY.format(from) + " – " + DAY.format(end), days, boardingPrice);
            }
            if (sessions > 0) {
                repository.insertLine(invoiceId, ++lineNo, TRAINING, "Huấn luyện: " + sessions + " buổi đã hoàn thành",
                        sessions, trainingPrice);
            }
            notifications.persistAndPublish(owner.get(), NotificationApi.Category.ALERTS, "SERVICE_INVOICE_ISSUED",
                    "Hóa đơn dịch vụ mới", "Hóa đơn " + code + " cho ngựa " + horse.name() + " (" + horse.horseCode()
                            + "): " + vnd(total) + ". Hạn thanh toán " + DAY.format(today.plusDays(7)) + ".",
                    "SERVICE_INVOICE", invoiceId, false);
            repository.audit(actor.userId(), RoleCode.CLUB_MANAGER.name(), "SERVICE_INVOICE_ISSUED", invoiceId,
                    "Invoice " + code + " issued for " + horse.horseCode() + ": " + days + " boarding days, "
                            + sessions + " sessions, total " + total + " VND.");
            created.add(repository.invoice(invoiceId).orElseThrow().summary());
        }
        return new GenerateResult(month.toString(), start, end, created, skipped);
    }

    @Transactional(readOnly = true)
    public List<InvoiceSummary> invoices(String billingMonth, String status) {
        LocalDate month = billingMonth == null || billingMonth.isBlank() ? null : parseMonth(billingMonth).atDay(1);
        return repository.invoices(month, parseStatus(status), null).stream().map(InvoiceRow::summary).toList();
    }

    @Transactional(readOnly = true)
    public InvoiceDetail invoice(long invoiceId) {
        return detail(repository.invoice(invoiceId).orElseThrow(BillingService::invoiceNotFound));
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public InvoiceDetail cancel(long invoiceId, CancelRequest request) {
        repository.lockInvoice(invoiceId);
        ActorContext actor = actor();
        String reason = request == null || request.reason() == null ? "" : request.reason().trim();
        if (reason.isEmpty() || reason.length() > 500) {
            throw validation("reason", "A cancellation reason is required (at most 500 characters).");
        }
        InvoiceRow invoice = repository.invoice(invoiceId).orElseThrow(BillingService::invoiceNotFound);
        if (!"ISSUED".equals(invoice.summary().status())) {
            throw conflict("status", "Only an unpaid invoice can be cancelled.");
        }
        if (repository.hasOpenPending(invoiceId)) {
            throw conflict("status", "The owner is paying this invoice right now. Try again after the payment expires.");
        }
        if (repository.cancelInvoice(invoiceId, actor.userId(), reason) != 1) {
            throw conflict("status", "The invoice changed while cancelling. Reload it.");
        }
        notifications.persistAndPublish(invoice.ownerUserId(), NotificationApi.Category.ALERTS,
                "SERVICE_INVOICE_CANCELLED", "Hóa đơn đã hủy",
                "Hóa đơn " + invoice.summary().invoiceCode() + " đã được Club Manager hủy: " + reason,
                "SERVICE_INVOICE", invoiceId, false);
        repository.audit(actor.userId(), RoleCode.CLUB_MANAGER.name(), "SERVICE_INVOICE_CANCELLED", invoiceId,
                "Invoice " + invoice.summary().invoiceCode() + " cancelled: " + reason);
        return invoice(invoiceId);
    }

    /* ======================= Horse Owner ======================= */

    @Transactional(readOnly = true)
    public List<InvoiceSummary> myInvoices() {
        return repository.invoices(null, null, actor().userId()).stream().map(InvoiceRow::summary).toList();
    }

    @Transactional(readOnly = true)
    public InvoiceDetail myInvoice(long invoiceId) {
        return detail(ownInvoice(invoiceId, actor()));
    }

    @Transactional
    public PaymentLink createPayment(long invoiceId, CreatePaymentRequest request, String clientIp) {
        ActorContext actor = actor();
        // Lock before reading: the "no open payment" check and the insert below must not interleave with another request.
        repository.lockInvoice(invoiceId);
        InvoiceRow invoice = ownInvoice(invoiceId, actor);
        if (!"ISSUED".equals(invoice.summary().status())) {
            throw conflict("status", "This invoice is not awaiting payment.");
        }
        if (!gateway.properties().configured()) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "VNPay sandbox is not configured on the server (VNPAY_TMN_CODE, VNPAY_HASH_SECRET).");
        }
        String bankCode = request == null || request.bankCode() == null ? "" : request.bankCode().trim().toUpperCase();
        if (!bankCode.isEmpty() && !List.of("VNPAYQR", "VNBANK", "INTCARD").contains(bankCode)) {
            throw validation("bankCode", "bankCode must be VNPAYQR, VNBANK or INTCARD.");
        }
        if (invoice.summary().totalAmount() > MAX_ONLINE_PAYMENT) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "This invoice is above the online payment limit. Settle it with the club.");
        }
        // One payment at a time: a still-valid VNPay link could be paid after a second one, charging the owner twice.
        // Confirmation is an explicit check operation. Do not confirm then throw a conflict in this transaction:
        // throwing would roll back a genuine settlement discovered by that query.
        if (!repository.openPendingPayments(invoiceId).isEmpty()) {
            throw conflict("status", "A payment for this invoice is still open. Check it before using another method.");
        }
        LocalDateTime createdVn = LocalDateTime.now(VN).truncatedTo(ChronoUnit.SECONDS);
        LocalDateTime expiresVn = createdVn.plusMinutes(gateway.properties().expireMinutes());
        String txnRef = "HD" + invoiceId + "T" + VNP_TIME.format(createdVn) + gateway.randomId(3);
        long amount = invoice.summary().totalAmount();
        String orderInfo = ("Thanh toan hoa don " + invoice.summary().invoiceCode()).replaceAll("[^A-Za-z0-9 ]", " ");
        repository.insertPayment(invoiceId, txnRef, amount, actor.userId(), VNP_TIME.format(createdVn),
                expiresVn.atZone(VN).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime());
        String url = gateway.paymentUrl(txnRef, amount, orderInfo, bankCode, ip(clientIp), createdVn, expiresVn);
        repository.audit(actor.userId(), RoleCode.HORSE_OWNER.name(), "SERVICE_INVOICE_PAYMENT_STARTED", invoiceId,
                "VNPay payment " + txnRef + " started for " + amount + " VND.");
        return new PaymentLink(url, txnRef, amount, expiresVn.atZone(VN).toInstant());
    }

    /**
     * Reserve before the remote call. An uncertain response retains PENDING so another click cannot double-charge; the
     * owner can still give it up ("Hủy giao dịch MoMo"). A refusal from MoMo has no checkout address, so the reservation
     * is recorded FAILED and the invoice stays payable by any method.
     */
    @Transactional(noRollbackFor = {MomoCreationUncertain.class, MomoCreationRejected.class})
    public BillingDtos.MomoPaymentLink createMomoPayment(long invoiceId) {
        repository.lockInvoice(invoiceId);
        ActorContext actor = actor();
        InvoiceRow invoice = ownInvoice(invoiceId, actor);
        if (!"ISSUED".equals(invoice.summary().status())) throw conflict("status", "This invoice is not awaiting payment.");
        if (!momo.configured()) throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "MOMO_NOT_CONFIGURED");
        long amount = invoice.summary().totalAmount();
        if (amount < 10_000 || amount > 50_000_000) throw validation("amount", "MOMO_ATM_AMOUNT_LIMIT");
        if (repository.hasOpenPending(invoiceId)) throw conflict("status", "MOMO_PAYMENT_STILL_OPEN");
        LocalDateTime now = LocalDateTime.now(VN).truncatedTo(ChronoUnit.SECONDS);
        String order = "MM" + java.util.UUID.randomUUID().toString().replace("-", "");
        // Local timer is advisory: elapsed time is not proof that MoMo cancelled an order.
        Instant expires = Instant.now().plusSeconds(900);
        repository.insertMomoPayment(invoiceId, order, amount, actor.userId(), VNP_TIME.format(now),
                LocalDateTime.ofInstant(expires, ZoneOffset.UTC));
        MomoGateway.CreateResult created = momo.create(order, amount, "Thanh toan hoa don " + invoice.summary().invoiceCode());
        if (created instanceof MomoGateway.Rejected rejected) {
            PaymentRow reserved = repository.paymentByTxnRef(order).orElseThrow();
            String code = rejected.code().length() > 5 ? rejected.code().substring(0, 5) : rejected.code();
            repository.markPaymentFailed(reserved.id(), code, code);
            throw new MomoCreationRejected();
        }
        if (!(created instanceof MomoGateway.Created opened)) throw new MomoCreationUncertain();
        repository.audit(actor.userId(), RoleCode.HORSE_OWNER.name(), "SERVICE_INVOICE_PAYMENT_STARTED", invoiceId,
                "MoMo ATM sandbox payment " + order + " started for " + amount + " VND.");
        return new BillingDtos.MomoPaymentLink(opened.payUrl(), order, amount, expires);
    }

    public static final class MomoCreationUncertain extends BusinessException {
        MomoCreationUncertain() { super(ErrorCode.BUSINESS_RULE_VIOLATION, "MOMO_CREATE_UNCONFIRMED"); }
    }

    public static final class MomoCreationRejected extends BusinessException {
        MomoCreationRejected() { super(ErrorCode.BUSINESS_RULE_VIOLATION, "MOMO_CREATE_REJECTED"); }
    }

    /** A signed browser return still needs a direct gateway query before settlement. */
    @Transactional
    public PaymentResult handleMomoReturn(Map<String, String> params) {
        PaymentRow payment = verifiedMomoNotification(params);
        repository.lockInvoice(payment.invoiceId());
        ActorContext actor = actor();
        InvoiceRow invoice = repository.invoice(payment.invoiceId()).orElseThrow(BillingService::invoiceNotFound);
        if (invoice.ownerUserId() != actor.userId() && !actor.roles().contains(RoleCode.CLUB_MANAGER)) throw invoiceNotFound();
        PaymentRow current = repository.paymentByTxnRef(payment.txnRef()).orElseThrow();
        String outcome = confirm(current);
        if ("UNAVAILABLE".equals(outcome)) outcome = current.cancelledByOwner() ? "CANCELLED" : "PENDING";
        return momoResult(payment.invoiceId(), outcome);
    }

    /** Signed server callback. Valid repeats are acknowledged without another payment/notification. */
    @Transactional
    public void handleMomoIpn(Map<String, String> params) {
        PaymentRow payment = verifiedMomoNotification(params);
        repository.lockInvoice(payment.invoiceId());
        PaymentRow current = repository.paymentByTxnRef(payment.txnRef()).orElseThrow();
        // An owner-cancelled order may still be paid in a MoMo tab: that success is recorded or flagged for a refund.
        if (!"PENDING".equals(current.status()) && !current.cancelledByOwner()) return;
        applyMomoOutcome(current, Integer.parseInt(params.get("resultCode")), params.get("transId"), params.get("payType"), "MOMO_IPN");
    }

    private PaymentRow verifiedMomoNotification(Map<String, String> params) {
        if (params == null || !momo.verify(params)) throw validation("signature", "MOMO_INVALID_RESPONSE");
        PaymentRow payment = repository.paymentByTxnRef(params.getOrDefault("orderId", "")).orElseThrow(BillingService::invoiceNotFound);
        if (!"MOMO".equals(payment.provider()) || !momo.properties().partnerCode().equals(params.get("partnerCode"))
                || !payment.txnRef().equals(params.get("requestId"))
                || !String.valueOf(payment.amount()).equals(params.get("amount"))) throw validation("amount", "MOMO_INVALID_RESPONSE");
        try {
            int code = Integer.parseInt(params.getOrDefault("resultCode", ""));
            if (code == 0 && !MomoGateway.positiveTransaction(params.get("transId"))) throw new NumberFormatException();
        } catch (NumberFormatException e) { throw validation("resultCode", "MOMO_INVALID_RESPONSE"); }
        return payment;
    }

    private String confirmMomo(PaymentRow payment) {
        if (!"PENDING".equals(payment.status())) return payment.status();
        var outcome = momo.query(payment.txnRef(), payment.amount());
        if (outcome.isEmpty()) return "PENDING";
        var r = outcome.get();
        return applyMomoOutcome(payment, r.resultCode(), r.transactionNo(), r.payType(), "MOMO_QUERY");
    }

    /** An owner-cancelled MoMo order: only resultCode 0 changes it (recorded, or flagged for a refund). */
    private String queryCancelledMomo(PaymentRow payment) {
        var outcome = momo.query(payment.txnRef(), payment.amount());
        if (outcome.isEmpty()) return "UNAVAILABLE";
        if (outcome.get().resultCode() != 0) return "FAILED";
        applySuccess(payment, "MOMO_QUERY", outcome.get().transactionNo(), null, outcome.get().payType(), "0", "0", null);
        return persistedStatus(payment);
    }

    /** Status actually written by applySuccess: SUCCESS, or REFUND_REQUIRED for an invoice settled another way. */
    private String persistedStatus(PaymentRow payment) {
        return repository.paymentByTxnRef(payment.txnRef()).map(PaymentRow::status).orElse("PENDING");
    }

    private String applyMomoOutcome(PaymentRow payment, int code, String transId, String payType, String via) {
        if (code == 0) {
            applySuccess(payment, via, transId, null, payType, "0", "0", null);
        } else if (List.of(42, 1001, 1002, 1003, 1004, 1005, 1006, 1007, 1017).contains(code)) {
            // 42: MoMo has no such order (official result-code table), so nothing can be paid on it.
            repository.markPaymentFailed(payment.id(), String.valueOf(code), String.valueOf(code));
        }
        // 1000/7000/7002/9000 and unknown errors stay pending. Only resultCode=0 settles this one-step ATM flow.
        return repository.paymentByTxnRef(payment.txnRef()).map(PaymentRow::status).orElse("PENDING");
    }

    private PaymentResult momoResult(long invoiceId, String status) {
        InvoiceRow invoice = repository.invoice(invoiceId).orElseThrow(BillingService::invoiceNotFound);
        String text = switch (status) {
            case "SUCCESS" -> "MoMo đã xác nhận thanh toán. Hóa đơn đã thanh toán.";
            case "FAILED" -> "Giao dịch MoMo không thành công. Hóa đơn chưa thanh toán.";
            case "REFUND_REQUIRED" -> "MoMo đã thu tiền trùng. Liên hệ Club Manager để hoàn tiền.";
            case "CANCELLED" -> "Đã hủy giao dịch MoMo đang mở. Bạn có thể thanh toán lại bằng VNPay, MoMo hoặc mã QR.";
            case "NO_PENDING_PAYMENT" -> "Không có giao dịch nào đang chờ xác nhận.";
            default -> "Đang chờ MoMo xác nhận. Nếu chưa thanh toán, hãy hủy giao dịch MoMo rồi thanh toán lại; không thanh toán thêm khi chưa hủy.";
        };
        return new PaymentResult(invoiceId, invoice.summary().invoiceCode(), invoice.summary().status(), status, null, text);
    }

    /** What the owner's VietQR code encodes: the club's receiving account, the invoice total, the invoice code. */
    @Transactional(readOnly = true)
    public BillingDtos.QrTransfer qrTransfer(long invoiceId) {
        InvoiceRow invoice = ownInvoice(invoiceId, actor());
        if (!"ISSUED".equals(invoice.summary().status())) {
            throw conflict("status", "This invoice is not awaiting payment.");
        }
        BillingDtos.PaymentAccount account = requireAccount();
        return new BillingDtos.QrTransfer(account.bankBin(), account.bankName(), account.accountNo(), account.accountName(),
                invoice.summary().totalAmount(), transferContent(invoice.summary().invoiceCode()));
    }

    /**
     * The owner pressed "Thanh toán" after scanning the VietQR code. Recorded at once as a successful payment declared by
     * the owner (provider VIETQR, confirmed_via OWNER) and the invoice becomes PAID; the record never claims a bank check.
     * Refused while a VNPay link of the invoice can still be paid: settling now would let that link charge the owner again.
     */
    @Transactional
    public PaymentResult confirmQrTransfer(long invoiceId) {
        ActorContext actor = actor();
        repository.lockInvoice(invoiceId);
        InvoiceRow invoice = ownInvoice(invoiceId, actor);
        if (!"ISSUED".equals(invoice.summary().status()) || repository.invoiceHasSuccess(invoiceId)) {
            throw conflict("status", "This invoice is not awaiting payment.");
        }
        if (repository.hasOpenPending(invoiceId)) {
            throw conflict("status", "A payment for this invoice is still open. Check it before using another method.");
        }
        BillingDtos.PaymentAccount account = requireAccount();
        LocalDateTime nowVn = LocalDateTime.now(VN).truncatedTo(ChronoUnit.SECONDS);
        String txnRef = "QR" + invoiceId + "T" + VNP_TIME.format(nowVn).substring(2) + gateway.randomId(2);
        long amount = invoice.summary().totalAmount();
        repository.insertOwnerConfirmedTransfer(invoiceId, txnRef, amount, actor.userId(), account.bankBin(),
                VNP_TIME.format(nowVn));
        if (repository.markInvoicePaid(invoiceId) != 1) {
            throw conflict("status", "This invoice is not awaiting payment.");
        }
        String code = invoice.summary().invoiceCode();
        notifications.persistAndPublish(invoice.ownerUserId(), NotificationApi.Category.ALERTS, "SERVICE_INVOICE_PAID",
                "Đã thanh toán hóa đơn", "Đã ghi nhận chuyển khoản QR " + vnd(amount) + " cho hóa đơn " + code + ".",
                "SERVICE_INVOICE", invoiceId, false);
        notifications.persistAndPublish(invoice.issuedBy().id(), NotificationApi.Category.ALERTS, "SERVICE_INVOICE_PAID",
                "Chủ ngựa đã thanh toán", invoice.summary().owner().fullName() + " xác nhận chuyển khoản QR "
                        + vnd(amount) + " cho hóa đơn " + code + " vào tài khoản " + account.bankName() + " "
                        + account.accountNo() + ".", "SERVICE_INVOICE", invoiceId, false);
        repository.audit(actor.userId(), RoleCode.HORSE_OWNER.name(), "SERVICE_INVOICE_PAID", invoiceId,
                "Owner declared a VietQR transfer " + txnRef + " of " + amount + " VND to " + account.bankName() + " "
                        + account.accountNo() + " (content " + transferContent(code) + ").");
        return new PaymentResult(invoiceId, code, "PAID", "SUCCESS", null,
                "Đã ghi nhận thanh toán chuyển khoản QR. Hóa đơn đã được thanh toán.");
    }

    private BillingDtos.PaymentAccount requireAccount() {
        return repository.currentPaymentAccount().orElseThrow(() -> new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                "The club has not set a receiving bank account yet."));
    }

    /** Transfer content: the invoice code without separators (VietQR allows letters and digits, at most 50). */
    static String transferContent(String invoiceCode) {
        return invoiceCode.replaceAll("[^A-Za-z0-9]", "");
    }

    /**
     * Browser return from VNPay. The signature is checked, then the result is confirmed with querydr before anything is
     * written; an unconfirmed result keeps the payment PENDING.
     */
    @Transactional
    public PaymentResult handleReturn(Map<String, String> params) {
        ActorContext actor = actor();
        Map<String, String> fields = params == null ? Map.of() : new LinkedHashMap<>(params);
        if (!gateway.verify(fields)) {
            throw validation("vnp_SecureHash", "The VNPay response signature is invalid.");
        }
        PaymentRow payment = repository.paymentByTxnRef(fields.getOrDefault("vnp_TxnRef", ""))
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Payment not found."));
        if (!"VNPAY".equals(payment.provider())) throw invoiceNotFound();
        InvoiceRow invoice = repository.invoice(payment.invoiceId()).orElseThrow(BillingService::invoiceNotFound);
        if (invoice.ownerUserId() != actor.userId() && !actor.roles().contains(RoleCode.CLUB_MANAGER)) {
            throw invoiceNotFound();
        }
        repository.lockInvoice(payment.invoiceId());
        payment = repository.paymentByTxnRef(payment.txnRef()).orElseThrow();
        String outcome = confirm(payment);
        if (outcome.equals("UNAVAILABLE")) {
            outcome = confirmFromSignedReturn(payment, fields);
        }
        return result(payment.invoiceId(), fields.get("vnp_ResponseCode"), outcome);
    }

    /**
     * Fallback when neither the IPN nor querydr can confirm (local machine without a public URL; the sandbox querydr
     * answered 99). The return parameters are signed by VNPay with the merchant secret (already verified by the caller);
     * the terminal, the amount and both status codes must match before anything is recorded.
     */
    private String confirmFromSignedReturn(PaymentRow payment, Map<String, String> fields) {
        if (!gateway.properties().tmnCode().equals(fields.get("vnp_TmnCode"))
                || !String.valueOf(payment.amount() * 100).equals(fields.get("vnp_Amount"))
                || !payment.txnRef().equals(fields.get("vnp_TxnRef"))) {
            return "PENDING";
        }
        String responseCode = fields.get("vnp_ResponseCode");
        String transactionStatus = fields.get("vnp_TransactionStatus");
        if ("00".equals(responseCode) && "00".equals(transactionStatus)) {
            applySuccess(payment, "RETURN", fields.get("vnp_TransactionNo"), fields.get("vnp_BankCode"),
                    fields.get("vnp_CardType"), responseCode, transactionStatus, fields.get("vnp_PayDate"));
            return repository.paymentByTxnRef(payment.txnRef()).map(PaymentRow::status).orElse("PENDING");
        }
        if (responseCode != null && !responseCode.isBlank() && !"00".equals(responseCode)) {
            repository.markPaymentFailed(payment.id(), responseCode, transactionStatus);
            return "FAILED";
        }
        return "PENDING";
    }

    /**
     * The owner gives up the open VNPay link(s) or MoMo order(s) of the invoice, so it can be paid again at once instead
     * of waiting. The gateway is asked first: a payment already made is recorded, not cancelled. One that the gateway
     * still reports paid after the cancel is recorded then, or flagged for a refund if the invoice was paid another way
     * meanwhile (see {@link #applySuccess}).
     */
    @Transactional
    public PaymentResult cancelOpenPayment(long invoiceId, String provider) {
        ActorContext actor = actor();
        repository.lockInvoice(invoiceId);
        ownInvoice(invoiceId, actor);
        boolean momoProvider = "MOMO".equals(provider);
        String label = momoProvider ? "MoMo" : "VNPay";
        List<PaymentRow> pending = repository.pendingPayments(invoiceId).stream().filter(p -> provider.equals(p.provider())).toList();
        if (pending.isEmpty()) {
            return momoProvider ? momoResult(invoiceId, "NO_PENDING_PAYMENT") : result(invoiceId, null, "NO_PENDING_PAYMENT");
        }
        for (PaymentRow payment : pending) {
            String found = confirm(payment);
            if (moneyFound(found)) {
                return momoProvider ? momoResult(invoiceId, found) : result(invoiceId, null, found);
            }
        }
        for (PaymentRow payment : repository.pendingPayments(invoiceId).stream().filter(p -> provider.equals(p.provider())).toList()) {
            if (repository.markPaymentCancelledByOwner(payment.id()) == 1) {
                repository.audit(actor.userId(), RoleCode.HORSE_OWNER.name(), "SERVICE_INVOICE_PAYMENT_CANCELLED",
                        invoiceId, "Owner cancelled " + label + " payment " + payment.txnRef() + " before it was paid.");
            }
        }
        return momoProvider ? momoResult(invoiceId, "CANCELLED") : result(invoiceId, "24", "CANCELLED");
    }

    /** "Kiểm tra lại": re-queries every PENDING payment of the invoice (owner of the invoice or Club Manager). */
    @Transactional
    public PaymentResult checkPayments(long invoiceId) {
        ActorContext actor = actor();
        repository.lockInvoice(invoiceId);
        InvoiceRow invoice = actor.roles().contains(RoleCode.CLUB_MANAGER)
                ? repository.invoice(invoiceId).orElseThrow(BillingService::invoiceNotFound)
                : ownInvoice(invoiceId, actor);
        String outcome = "NO_PENDING_PAYMENT";
        String checkedProvider = null;
        boolean unreachable = false;
        for (PaymentRow payment : repository.pendingPayments(invoice.summary().id())) {
            checkedProvider = payment.provider();
            outcome = confirm(payment);
            if (outcome.equals("UNAVAILABLE")) {
                // querydr gave no verifiable answer (the sandbox answers 99 to every query): asking again will not help.
                unreachable = true;
                outcome = "PENDING";
            }
            if (moneyFound(outcome)) {
                break;
            }
        }
        if (!moneyFound(outcome)) {
            for (PaymentRow payment : repository.ownerCancelledPayments(invoiceId)) {
                // The persisted result is reported: SUCCESS, or REFUND_REQUIRED when the invoice was settled another way.
                String late = confirm(payment);
                if (moneyFound(late)) {
                    checkedProvider = payment.provider();
                    outcome = late;
                    break;
                }
            }
        }
        // A failed historical MoMo attempt must not make a later VietQR/VNPay payment look confirmed by MoMo.
        if ("NO_PENDING_PAYMENT".equals(outcome) && "PAID".equals(invoice.summary().status())) {
            boolean momoSettled = repository.payments(invoiceId).stream()
                    .anyMatch(p -> "MOMO".equals(p.provider()) && "SUCCESS".equals(p.status()));
            if (momoSettled) { checkedProvider = "MOMO"; outcome = "SUCCESS"; }
        }
        if ("MOMO".equals(checkedProvider)) {
            return momoResult(invoiceId, outcome);
        }
        if (outcome.equals("PENDING") && unreachable) {
            return result(invoiceId, null, outcome, QUERY_UNAVAILABLE_MESSAGE);
        }
        return result(invoiceId, null, outcome);
    }

    /** The gateway took the money: recorded as the payment (SUCCESS) or flagged for a refund (REFUND_REQUIRED). */
    private static boolean moneyFound(String outcome) {
        return "SUCCESS".equals(outcome) || "REFUND_REQUIRED".equals(outcome);
    }

    /** VNPay IPN (server to server, public URL with SSL). Returns VNPay's RspCode / Message. */
    @Transactional
    public Map<String, String> handleIpn(Map<String, String> params) {
        Map<String, String> fields = params == null ? Map.of() : new LinkedHashMap<>(params);
        if (!gateway.verify(fields)) {
            return rsp("97", "Invalid Checksum");
        }
        if (!gateway.properties().tmnCode().equals(fields.get("vnp_TmnCode"))) {
            return rsp("97", "Invalid Checksum");
        }
        Optional<PaymentRow> found = repository.paymentByTxnRef(fields.getOrDefault("vnp_TxnRef", ""));
        if (found.isEmpty()) {
            return rsp("01", "Order not Found");
        }
        // Settle one payment of an invoice at a time, in the same lock order as create / cancel / VietQR, and only
        // then read the payment and invoice state the decision depends on.
        if (!"VNPAY".equals(found.get().provider())) return rsp("01", "Order not Found");
        repository.lockInvoice(found.get().invoiceId());
        PaymentRow payment = repository.paymentByTxnRef(found.get().txnRef()).orElseThrow();
        long amount;
        try {
            amount = Long.parseLong(fields.getOrDefault("vnp_Amount", ""));
        } catch (NumberFormatException error) {
            return rsp("04", "Invalid Amount");
        }
        if (amount != payment.amount() * 100) {
            return rsp("04", "Invalid Amount");
        }
        if (!"PENDING".equals(payment.status()) && !payment.cancelledByOwner()) {
            return rsp("02", "Order already confirmed");
        }
        if ("00".equals(fields.get("vnp_ResponseCode")) && "00".equals(fields.get("vnp_TransactionStatus"))) {
            applySuccess(payment, "IPN", fields.get("vnp_TransactionNo"), fields.get("vnp_BankCode"),
                    fields.get("vnp_CardType"), fields.get("vnp_ResponseCode"), fields.get("vnp_TransactionStatus"),
                    fields.get("vnp_PayDate"));
        } else {
            repository.markPaymentFailed(payment.id(), fields.get("vnp_ResponseCode"), fields.get("vnp_TransactionStatus"));
        }
        return rsp("00", "Confirm Success");
    }

    /* ======================= internals ======================= */

    /**
     * querydr confirmation. SUCCESS / FAILED are written; anything unconfirmed stays PENDING. UNAVAILABLE means VNPay
     * could not be asked (network error, error answer or unverifiable signature): nothing was written.
     */
    private String confirm(PaymentRow payment) {
        if (payment.cancelledByOwner()) {
            // Only a late success changes a cancelled link: the money is then recorded (or flagged for a refund).
            String outcome = "MOMO".equals(payment.provider()) ? queryCancelledMomo(payment) : query(payment);
            return moneyFound(outcome) || "UNAVAILABLE".equals(outcome) ? outcome : "FAILED";
        }
        if (!"PENDING".equals(payment.status())) {
            return payment.status();
        }
        if ("MOMO".equals(payment.provider())) return confirmMomo(payment);
        return query(payment);
    }

    private String query(PaymentRow payment) {
        boolean expired = payment.expiresAt() != null && Instant.now().isAfter(payment.expiresAt().plusSeconds(300));
        Optional<QueryResult> queried = gateway.query(payment.txnRef(), payment.providerCreateDate());
        if (queried.isEmpty()) {
            return "UNAVAILABLE";
        }
        QueryResult result = queried.get();
        if ("00".equals(result.responseCode())) {
            if ("00".equals(result.transactionStatus())) {
                if (result.amountTimes100() != payment.amount() * 100) {
                    return "PENDING";
                }
                applySuccess(payment, "QUERYDR", result.transactionNo(), result.bankCode(), null,
                        result.responseCode(), result.transactionStatus(), result.payDate());
                return persistedStatus(payment);
            }
            if ("01".equals(result.transactionStatus()) && !expired) {
                return "PENDING";
            }
            repository.markPaymentFailed(payment.id(), result.responseCode(), result.transactionStatus());
            return "FAILED";
        }
        if ("91".equals(result.responseCode()) && expired) {
            repository.markPaymentFailed(payment.id(), result.responseCode(), null);
            return "FAILED";
        }
        return "PENDING";
    }

    private void applySuccess(PaymentRow payment, String via, String transactionNo, String bankCode, String cardType,
                              String responseCode, String transactionStatus, String payDate) {
        String providerLabel = "MOMO".equals(payment.provider()) ? "MoMo" : "VNPay";
        // provider_transaction_no holds at most 20 characters; VNPay's own number is used, never our longer reference.
        String providerNo = transactionNo == null || transactionNo.isBlank() ? "UNKNOWN" : transactionNo;
        repository.lockInvoice(payment.invoiceId());
        InvoiceRow invoice = repository.invoice(payment.invoiceId()).orElseThrow(BillingService::invoiceNotFound);
        // Money VNPay took for an invoice that is already paid or was cancelled is never counted twice: it is flagged
        // for a refund from the VNPay merchant portal and the Club Manager who issued the invoice is told.
        if (!"ISSUED".equals(invoice.summary().status()) || repository.invoiceHasSuccess(payment.invoiceId())) {
            if (repository.markPaymentRefundRequired(payment.id(), via, providerNo, bankCode, cardType, responseCode,
                    transactionStatus, payDate) == 1) {
                notifications.persistAndPublish(invoice.issuedBy().id(), NotificationApi.Category.ALERTS,
                        "SERVICE_PAYMENT_REFUND_REQUIRED", "Cần hoàn tiền " + providerLabel,
                        providerLabel + " xác nhận thêm " + vnd(payment.amount()) + " cho hóa đơn " + invoice.summary().invoiceCode()
                                + " đã thanh toán hoặc đã hủy. Hãy hoàn tiền giao dịch " + providerNo + " trên cổng " + providerLabel + ".",
                        "SERVICE_INVOICE", payment.invoiceId(), false);
                repository.auditSystem("SERVICE_PAYMENT_REFUND_REQUIRED", payment.invoiceId(),
                        providerLabel + " " + via + " confirmed " + payment.txnRef() + " for an invoice that is not awaiting payment.");
            }
            return;
        }
        if (repository.markPaymentSuccess(payment.id(), via, providerNo, bankCode, cardType, responseCode,
                transactionStatus, payDate) != 1) {
            return;
        }
        if (repository.markInvoicePaid(payment.invoiceId()) == 1) {
            String code = invoice.summary().invoiceCode();
            notifications.persistAndPublish(invoice.ownerUserId(), NotificationApi.Category.ALERTS,
                    "SERVICE_INVOICE_PAID", "Đã thanh toán hóa đơn",
                    providerLabel + " đã xác nhận thanh toán " + vnd(payment.amount()) + " cho hóa đơn " + code + ".",
                    "SERVICE_INVOICE", payment.invoiceId(), false);
            notifications.persistAndPublish(invoice.issuedBy().id(), NotificationApi.Category.ALERTS,
                    "SERVICE_INVOICE_PAID", "Chủ ngựa đã thanh toán",
                    invoice.summary().owner().fullName() + " đã thanh toán hóa đơn " + code + " ("
                            + vnd(payment.amount()) + ").", "SERVICE_INVOICE", payment.invoiceId(), false);
        }
        repository.auditSystem("SERVICE_INVOICE_PAID", payment.invoiceId(),
                providerLabel + " " + via + " confirmed " + payment.txnRef() + " (" + payment.amount() + " VND).");
    }

    /**
     * "Kiểm tra lại" could not ask VNPay. The payment stays PENDING (never assumed paid); the owner is told what records
     * it instead of being asked to retry: the signed return page after paying, or cancelling the link if not paid.
     */
    static final String QUERY_UNAVAILABLE_MESSAGE = "Chưa tra cứu được giao dịch với VNPay. Nếu bạn đã thanh toán xong,"
            + " hóa đơn được ghi nhận khi VNPay chuyển về trang kết quả. Nếu chưa thanh toán, hãy hủy giao dịch VNPay"
            + " đang mở rồi thanh toán lại.";

    private PaymentResult result(long invoiceId, String responseCode, String outcome, String message) {
        InvoiceRow invoice = repository.invoice(invoiceId).orElseThrow(BillingService::invoiceNotFound);
        return new PaymentResult(invoiceId, invoice.summary().invoiceCode(), invoice.summary().status(), outcome,
                responseCode, message);
    }

    private PaymentResult result(long invoiceId, String responseCode, String outcome) {
        InvoiceRow invoice = repository.invoice(invoiceId).orElseThrow(BillingService::invoiceNotFound);
        String message = switch (outcome) {
            case "SUCCESS" -> "VNPay đã xác nhận thanh toán. Hóa đơn đã được ghi nhận là đã thanh toán.";
            case "FAILED" -> "Giao dịch không thành công hoặc đã bị hủy. Hóa đơn vẫn chưa thanh toán.";
            case "NO_PENDING_PAYMENT" -> "Không có giao dịch nào đang chờ xác nhận.";
            case "CANCELLED" -> "Đã hủy giao dịch VNPay đang mở. Bạn có thể thanh toán lại bằng VNPay hoặc mã QR.";
            case "REFUND_REQUIRED" -> "VNPay đã thu tiền nhưng hóa đơn đã được thanh toán hoặc đã hủy. Club Manager sẽ hoàn tiền giao dịch này.";
            default -> "Chưa nhận được xác nhận từ VNPay. Hóa đơn chưa được ghi nhận thanh toán; hãy bấm Kiểm tra lại sau ít phút.";
        };
        return new PaymentResult(invoiceId, invoice.summary().invoiceCode(), invoice.summary().status(), outcome,
                responseCode, message);
    }

    private InvoiceDetail detail(InvoiceRow row) {
        long id = row.summary().id();
        return new InvoiceDetail(row.summary(), repository.lines(id), repository.payments(id), row.issuedBy(),
                row.cancelledAt(), row.cancellationReason(), gateway.properties().configured(), momo.configured());
    }

    private InvoiceRow ownInvoice(long invoiceId, ActorContext actor) {
        InvoiceRow invoice = repository.invoice(invoiceId).orElseThrow(BillingService::invoiceNotFound);
        if (invoice.ownerUserId() != actor.userId()) {
            throw invoiceNotFound();
        }
        return invoice;
    }

    private Price price(String code) {
        Optional<PriceRow> row = repository.currentPrice(code);
        boolean boarding = code.equals(BOARDING);
        return new Price(code, boarding ? "Lưu chuồng & chăm sóc" : "Huấn luyện",
                boarding ? "ngày" : "buổi hoàn thành",
                row.map(p -> p.unitPrice().longValueExact()).orElse(null),
                row.map(PriceRow::createdAt).orElse(null), row.map(PriceRow::createdBy).orElse(null));
    }

    static int boardingDays(List<Stay> stays, LocalDate start, LocalDate end) {
        long days = 0;
        for (Stay stay : stays) {
            LocalDate from = stay.from().isBefore(start) ? start : stay.from();
            LocalDate to = stay.to() == null || stay.to().isAfter(end) ? end : stay.to();
            if (!to.isBefore(from)) {
                days += ChronoUnit.DAYS.between(from, to) + 1;
            }
        }
        return Math.toIntExact(days);
    }

    static Optional<Long> billedOwner(List<OwnerRow> owners) {
        List<OwnerRow> managing = owners.stream().filter(OwnerRow::managing).toList();
        if (managing.size() == 1) {
            return Optional.of(managing.getFirst().userId());
        }
        if (managing.isEmpty() && owners.size() == 1) {
            return Optional.of(owners.getFirst().userId());
        }
        return Optional.empty();
    }

    private static YearMonth parseMonth(String value) {
        try {
            if (value == null || !value.matches("\\d{4}-\\d{2}")) {
                throw new DateTimeParseException("format", String.valueOf(value), 0);
            }
            YearMonth month = YearMonth.parse(value);
            if (month.getYear() < 2020) {
                throw new DateTimeParseException("range", value, 0);
            }
            return month;
        } catch (DateTimeParseException error) {
            throw validation("billingMonth", "billingMonth must be YYYY-MM.");
        }
    }

    private static String parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        String value = status.trim().toUpperCase();
        if (!List.of("ISSUED", "PAID", "CANCELLED").contains(value)) {
            throw validation("status", "status must be ISSUED, PAID or CANCELLED.");
        }
        return value;
    }

    private static void checkPrice(List<ErrorDetail> errors, String field, Long value) {
        if (value != null && (value < MIN_PRICE || value > MAX_PRICE)) {
            errors.add(new ErrorDetail(field, "Price must be between 5,000 and 10,000,000 VND."));
        }
    }

    private static String ip(String clientIp) {
        if (clientIp == null || clientIp.isBlank() || clientIp.contains(":")) {
            return "127.0.0.1";
        }
        return clientIp;
    }

    private static String vnd(long amount) {
        return NumberFormat.getIntegerInstance(Locale.forLanguageTag("vi-VN")).format(amount) + " ₫";
    }

    private static Map<String, String> rsp(String code, String message) {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("RspCode", code);
        body.put("Message", message);
        return body;
    }

    private ActorContext actor() {
        return actors.currentActor()
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED, "Authentication required."));
    }

    private static BusinessException invoiceNotFound() {
        return new BusinessException(ErrorCode.NOT_FOUND, "Invoice not found.");
    }

    private static BusinessException conflict(String field, String message) {
        return new BusinessException(ErrorCode.CONFLICT, message, List.of(new ErrorDetail(field, message)));
    }

    private static BusinessException validation(String field, String message) {
        return new BusinessException(ErrorCode.VALIDATION_ERROR, message, List.of(new ErrorDetail(field, message)));
    }
}
