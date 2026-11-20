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
    private final CurrentActorProvider actors;
    private final NotificationPublisher notifications;

    public BillingService(BillingRepository repository, VnpayGateway gateway, CurrentActorProvider actors,
                          NotificationPublisher notifications) {
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
        for (PaymentRow open : repository.openPendingPayments(invoiceId)) {
            if ("SUCCESS".equals(confirm(open))) {
                throw conflict("status", "This invoice is not awaiting payment.");
            }
        }
        if (!repository.openPendingPayments(invoiceId).isEmpty()) {
            throw conflict("status", "A VNPay payment for this invoice is still open. Finish it, or wait until it expires.");
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
        InvoiceRow invoice = repository.invoice(payment.invoiceId()).orElseThrow(BillingService::invoiceNotFound);
        if (invoice.ownerUserId() != actor.userId() && !actor.roles().contains(RoleCode.CLUB_MANAGER)) {
            throw invoiceNotFound();
        }
        String outcome = confirm(payment);
        return result(payment.invoiceId(), fields.get("vnp_ResponseCode"), outcome);
    }

    /** "Kiểm tra lại": re-queries every PENDING payment of the invoice (owner of the invoice or Club Manager). */
    @Transactional
    public PaymentResult checkPayments(long invoiceId) {
        ActorContext actor = actor();
        InvoiceRow invoice = actor.roles().contains(RoleCode.CLUB_MANAGER)
                ? repository.invoice(invoiceId).orElseThrow(BillingService::invoiceNotFound)
                : ownInvoice(invoiceId, actor);
        String outcome = "NO_PENDING_PAYMENT";
        for (PaymentRow payment : repository.pendingPayments(invoice.summary().id())) {
            outcome = confirm(payment);
            if (outcome.equals("SUCCESS")) {
                break;
            }
        }
        return result(invoiceId, null, outcome);
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
        PaymentRow payment = found.get();
        long amount;
        try {
            amount = Long.parseLong(fields.getOrDefault("vnp_Amount", ""));
        } catch (NumberFormatException error) {
            return rsp("04", "Invalid Amount");
        }
        if (amount != payment.amount() * 100) {
            return rsp("04", "Invalid Amount");
        }
        if (!"PENDING".equals(payment.status())) {
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

    /** querydr confirmation. SUCCESS / FAILED are written; anything unconfirmed stays PENDING. */
    private String confirm(PaymentRow payment) {
        if (!"PENDING".equals(payment.status())) {
            return payment.status();
        }
        boolean expired = payment.expiresAt() != null && Instant.now().isAfter(payment.expiresAt().plusSeconds(300));
        Optional<QueryResult> queried = gateway.query(payment.txnRef(), payment.providerCreateDate());
        if (queried.isEmpty()) {
            return "PENDING";
        }
        QueryResult result = queried.get();
        if ("00".equals(result.responseCode())) {
            if ("00".equals(result.transactionStatus())) {
                if (result.amountTimes100() != payment.amount() * 100) {
                    return "PENDING";
                }
                applySuccess(payment, "QUERYDR", result.transactionNo(), result.bankCode(), null,
                        result.responseCode(), result.transactionStatus(), result.payDate());
                return "SUCCESS";
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
        String providerNo = transactionNo == null || transactionNo.isBlank() ? payment.txnRef() : transactionNo;
        InvoiceRow invoice = repository.invoice(payment.invoiceId()).orElseThrow(BillingService::invoiceNotFound);
        // Money VNPay took for an invoice that is already paid or was cancelled is never counted twice: it is flagged
        // for a refund from the VNPay merchant portal and the Club Manager who issued the invoice is told.
        if (!"ISSUED".equals(invoice.summary().status()) || repository.invoiceHasSuccess(payment.invoiceId())) {
            if (repository.markPaymentRefundRequired(payment.id(), via, providerNo, bankCode, cardType, responseCode,
                    transactionStatus, payDate) == 1) {
                notifications.persistAndPublish(invoice.issuedBy().id(), NotificationApi.Category.ALERTS,
                        "SERVICE_PAYMENT_REFUND_REQUIRED", "Cần hoàn tiền VNPay",
                        "VNPay xác nhận thêm " + vnd(payment.amount()) + " cho hóa đơn " + invoice.summary().invoiceCode()
                                + " đã thanh toán hoặc đã hủy. Hãy hoàn tiền giao dịch " + providerNo + " trên cổng VNPay.",
                        "SERVICE_INVOICE", payment.invoiceId(), false);
                repository.auditSystem("SERVICE_PAYMENT_REFUND_REQUIRED", payment.invoiceId(),
                        "VNPay " + via + " confirmed " + payment.txnRef() + " for an invoice that is not awaiting payment.");
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
                    "VNPay đã xác nhận thanh toán " + vnd(payment.amount()) + " cho hóa đơn " + code + ".",
                    "SERVICE_INVOICE", payment.invoiceId(), false);
            notifications.persistAndPublish(invoice.issuedBy().id(), NotificationApi.Category.ALERTS,
                    "SERVICE_INVOICE_PAID", "Chủ ngựa đã thanh toán",
                    invoice.summary().owner().fullName() + " đã thanh toán hóa đơn " + code + " ("
                            + vnd(payment.amount()) + ").", "SERVICE_INVOICE", payment.invoiceId(), false);
        }
        repository.auditSystem("SERVICE_INVOICE_PAID", payment.invoiceId(),
                "VNPay " + via + " confirmed " + payment.txnRef() + " (" + payment.amount() + " VND).");
    }

    private PaymentResult result(long invoiceId, String responseCode, String outcome) {
        InvoiceRow invoice = repository.invoice(invoiceId).orElseThrow(BillingService::invoiceNotFound);
        String message = switch (outcome) {
            case "SUCCESS" -> "VNPay đã xác nhận thanh toán. Hóa đơn đã được ghi nhận là đã thanh toán.";
            case "FAILED" -> "Giao dịch không thành công hoặc đã bị hủy. Hóa đơn vẫn chưa thanh toán.";
            case "NO_PENDING_PAYMENT" -> "Không có giao dịch nào đang chờ xác nhận.";
            default -> "Chưa nhận được xác nhận từ VNPay. Hóa đơn chưa được ghi nhận thanh toán; hãy bấm Kiểm tra lại sau ít phút.";
        };
        return new PaymentResult(invoiceId, invoice.summary().invoiceCode(), invoice.summary().status(), outcome,
                responseCode, message);
    }

    private InvoiceDetail detail(InvoiceRow row) {
        long id = row.summary().id();
        return new InvoiceDetail(row.summary(), repository.lines(id), repository.payments(id), row.issuedBy(),
                row.cancelledAt(), row.cancellationReason(), gateway.properties().configured());
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
