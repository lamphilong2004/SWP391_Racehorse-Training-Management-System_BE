package com.racehorse.billing;

import com.racehorse.billing.BillingDtos.Horse;
import com.racehorse.billing.BillingDtos.InvoiceSummary;
import com.racehorse.billing.BillingDtos.Line;
import com.racehorse.billing.BillingDtos.Party;
import com.racehorse.billing.BillingDtos.Payment;
import com.racehorse.common.jdbc.GeneratedIdentity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
public class BillingRepository {

    public record PriceRow(String serviceCode, BigDecimal unitPrice, Instant createdAt, String createdBy) {
    }

    public record HorseRow(long id, String horseCode, String name) {
    }

    public record Stay(LocalDate from, LocalDate to) {
    }

    public record OwnerRow(long userId, boolean managing) {
    }

    public record InvoiceRow(InvoiceSummary summary, long ownerUserId, Party issuedBy, Instant cancelledAt,
                             String cancellationReason) {
    }

    public record PaymentRow(long id, long invoiceId, String txnRef, long amount, String status,
                             String providerCreateDate, Instant expiresAt) {
    }

    private static final String INVOICE_SELECT = """
            SELECT i.id, i.invoice_code, i.billing_month, i.period_start, i.period_end, i.total_amount, i.status,
                   i.due_date, i.issued_at, i.paid_at, i.cancelled_at, i.cancellation_reason, i.owner_user_id,
                   h.id AS horse_id, h.horse_code, h.name AS horse_name,
                   o.full_name AS owner_name, o.email AS owner_email,
                   u.id AS issuer_id, u.full_name AS issuer_name, u.email AS issuer_email
            FROM dbo.service_invoice i
            JOIN dbo.horse h ON h.id = i.horse_id
            JOIN dbo.app_user o ON o.id = i.owner_user_id
            JOIN dbo.app_user u ON u.id = i.issued_by_user_id
            """;

    private static final RowMapper<InvoiceRow> INVOICE_ROW = (r, n) -> new InvoiceRow(
            new InvoiceSummary(r.getLong("id"), r.getString("invoice_code"),
                    r.getObject("billing_month", LocalDate.class).toString().substring(0, 7),
                    r.getObject("period_start", LocalDate.class), r.getObject("period_end", LocalDate.class),
                    new Horse(r.getLong("horse_id"), r.getString("horse_code"), r.getString("horse_name")),
                    new Party(r.getLong("owner_user_id"), r.getString("owner_name"), r.getString("owner_email")),
                    r.getBigDecimal("total_amount").longValueExact(), r.getString("status"),
                    r.getObject("due_date", LocalDate.class), instant(r, "issued_at"), instant(r, "paid_at")),
            r.getLong("owner_user_id"),
            new Party(r.getLong("issuer_id"), r.getString("issuer_name"), r.getString("issuer_email")),
            instant(r, "cancelled_at"), r.getString("cancellation_reason"));

    private final JdbcTemplate jdbc;

    public BillingRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /* ---------- prices ---------- */

    public Optional<PriceRow> currentPrice(String serviceCode) {
        return jdbc.query("""
                SELECT TOP 1 p.service_code, p.unit_price, p.created_at, u.full_name
                FROM dbo.service_price p JOIN dbo.app_user u ON u.id = p.created_by_user_id
                WHERE p.service_code = ?
                ORDER BY p.created_at DESC, p.id DESC
                """, (r, n) -> new PriceRow(r.getString("service_code"), r.getBigDecimal("unit_price"),
                instant(r, "created_at"), r.getString("full_name")), serviceCode).stream().findFirst();
    }

    public void insertPrice(String serviceCode, long unitPrice, long userId) {
        jdbc.update("""
                INSERT INTO dbo.service_price (service_code, unit_price, currency_code, created_by_user_id, created_at)
                VALUES (?, ?, 'VND', ?, ?)
                """, serviceCode, BigDecimal.valueOf(unitPrice), userId, now());
    }

    /* ---------- usage ---------- */

    public List<HorseRow> horsesStabledBetween(LocalDate start, LocalDate end) {
        return jdbc.query("""
                SELECT DISTINCT h.id, h.horse_code, h.name
                FROM dbo.horse h JOIN dbo.horse_stable_assignment a ON a.horse_id = h.id
                WHERE a.effective_from <= ? AND (a.effective_to IS NULL OR a.effective_to >= ?)
                ORDER BY h.horse_code
                """, (r, n) -> new HorseRow(r.getLong("id"), r.getString("horse_code"), r.getString("name")), end, start);
    }

    public List<Stay> stays(long horseId, LocalDate start, LocalDate end) {
        return jdbc.query("""
                SELECT effective_from, effective_to FROM dbo.horse_stable_assignment
                WHERE horse_id = ? AND effective_from <= ? AND (effective_to IS NULL OR effective_to >= ?)
                """, (r, n) -> new Stay(r.getObject("effective_from", LocalDate.class),
                r.getObject("effective_to", LocalDate.class)), horseId, end, start);
    }

    /** COMPLETED sessions of the horse whose end (or start, if no end) falls in [fromUtc, toUtc). */
    public int completedSessions(long horseId, LocalDateTime fromUtc, LocalDateTime toUtc) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM dbo.training_session ts
                JOIN dbo.horse_program_phase p ON p.id = ts.horse_program_phase_id
                JOIN dbo.horse_training_program hp ON hp.id = p.horse_training_program_id
                WHERE hp.horse_id = ? AND ts.status = 'COMPLETED'
                  AND COALESCE(ts.actual_end, ts.scheduled_start) >= ? AND COALESCE(ts.actual_end, ts.scheduled_start) < ?
                """, Integer.class, horseId, fromUtc, toUtc);
        return count == null ? 0 : count;
    }

    public List<OwnerRow> ownersOn(long horseId, LocalDate day) {
        return jdbc.query("""
                SELECT owner_user_id, is_managing_owner FROM dbo.horse_ownership
                WHERE horse_id = ? AND effective_from <= ? AND (effective_to IS NULL OR effective_to >= ?)
                """, (r, n) -> new OwnerRow(r.getLong("owner_user_id"), r.getBoolean("is_managing_owner")),
                horseId, day, day);
    }

    /* ---------- invoices ---------- */

    public boolean activeInvoiceExists(long horseId, LocalDate billingMonth) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT CASE WHEN EXISTS (SELECT 1 FROM dbo.service_invoice
                    WHERE horse_id = ? AND billing_month = ? AND status <> 'CANCELLED') THEN 1 ELSE 0 END
                """, Boolean.class, horseId, billingMonth));
    }

    public int invoiceCountForMonth(LocalDate billingMonth) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM dbo.service_invoice WHERE billing_month = ?",
                Integer.class, billingMonth);
        return count == null ? 0 : count;
    }

    public long insertInvoice(String code, long horseId, long ownerId, LocalDate month, LocalDate start, LocalDate end,
                              long total, LocalDate dueDate, long issuerId) {
        KeyHolder keys = new GeneratedKeyHolder();
        LocalDateTime now = now();
        jdbc.update(connection -> {
            PreparedStatement s = connection.prepareStatement("""
                    INSERT INTO dbo.service_invoice (invoice_code, horse_id, owner_user_id, billing_month, period_start,
                        period_end, total_amount, currency_code, status, due_date, issued_by_user_id, issued_at, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, 'VND', 'ISSUED', ?, ?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            s.setString(1, code);
            s.setLong(2, horseId);
            s.setLong(3, ownerId);
            s.setObject(4, month);
            s.setObject(5, start);
            s.setObject(6, end);
            s.setBigDecimal(7, BigDecimal.valueOf(total));
            s.setObject(8, dueDate);
            s.setLong(9, issuerId);
            s.setObject(10, now);
            s.setObject(11, now);
            return s;
        }, keys);
        return GeneratedIdentity.requireId(keys);
    }

    public void insertLine(long invoiceId, int lineNo, String serviceCode, String description, int quantity, long unitPrice) {
        jdbc.update("""
                INSERT INTO dbo.service_invoice_line (invoice_id, line_no, service_code, description, quantity, unit_price, amount, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, invoiceId, lineNo, serviceCode, description, quantity, BigDecimal.valueOf(unitPrice),
                BigDecimal.valueOf(Math.multiplyExact((long) quantity, unitPrice)), now());
    }

    public List<InvoiceRow> invoices(LocalDate billingMonth, String status, Long ownerUserId) {
        StringBuilder sql = new StringBuilder(INVOICE_SELECT).append(" WHERE 1 = 1");
        List<Object> args = new ArrayList<>();
        if (billingMonth != null) {
            sql.append(" AND i.billing_month = ?");
            args.add(billingMonth);
        }
        if (status != null) {
            sql.append(" AND i.status = ?");
            args.add(status);
        }
        if (ownerUserId != null) {
            sql.append(" AND i.owner_user_id = ?");
            args.add(ownerUserId);
        }
        sql.append(" ORDER BY i.billing_month DESC, i.invoice_code DESC");
        return jdbc.query(sql.toString(), INVOICE_ROW, args.toArray());
    }

    public Optional<InvoiceRow> invoice(long invoiceId) {
        return jdbc.query(INVOICE_SELECT + " WHERE i.id = ?", INVOICE_ROW, invoiceId).stream().findFirst();
    }

    public List<Line> lines(long invoiceId) {
        return jdbc.query("""
                SELECT line_no, service_code, description, quantity, unit_price, amount
                FROM dbo.service_invoice_line WHERE invoice_id = ? ORDER BY line_no
                """, (r, n) -> new Line(r.getInt("line_no"), r.getString("service_code"), r.getString("description"),
                r.getInt("quantity"), r.getBigDecimal("unit_price").longValueExact(),
                r.getBigDecimal("amount").longValueExact()), invoiceId);
    }

    public int cancelInvoice(long invoiceId, long userId, String reason) {
        LocalDateTime now = now();
        return jdbc.update("""
                UPDATE dbo.service_invoice
                SET status = 'CANCELLED', cancelled_by_user_id = ?, cancelled_at = ?, cancellation_reason = ?, updated_at = ?
                WHERE id = ? AND status = 'ISSUED'
                """, userId, now, reason, now, invoiceId);
    }

    public int markInvoicePaid(long invoiceId) {
        LocalDateTime now = now();
        return jdbc.update("""
                UPDATE dbo.service_invoice SET status = 'PAID', paid_at = ?, updated_at = ?
                WHERE id = ? AND status = 'ISSUED'
                """, now, now, invoiceId);
    }

    /* ---------- payments ---------- */

    public List<Payment> payments(long invoiceId) {
        return jdbc.query("""
                SELECT id, provider, txn_ref, amount, status, bank_code, card_type, provider_transaction_no, response_code,
                       confirmed_via, created_at, confirmed_at
                FROM dbo.payment_transaction WHERE invoice_id = ? ORDER BY created_at DESC, id DESC
                """, (r, n) -> new Payment(r.getLong("id"), r.getString("provider"), r.getString("txn_ref"),
                r.getBigDecimal("amount").longValueExact(), r.getString("status"), r.getString("bank_code"),
                r.getString("card_type"), r.getString("provider_transaction_no"), r.getString("response_code"),
                r.getString("confirmed_via"), instant(r, "created_at"), instant(r, "confirmed_at")), invoiceId);
    }

    public boolean hasOpenPending(long invoiceId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT CASE WHEN EXISTS (SELECT 1 FROM dbo.payment_transaction
                    WHERE invoice_id = ? AND status = 'PENDING' AND expires_at > ?) THEN 1 ELSE 0 END
                """, Boolean.class, invoiceId, now()));
    }

    public void insertPayment(long invoiceId, String txnRef, long amount, long userId, String providerCreateDate,
                              LocalDateTime expiresUtc) {
        LocalDateTime now = now();
        jdbc.update("""
                INSERT INTO dbo.payment_transaction (invoice_id, provider, txn_ref, amount, status, requested_by_user_id,
                    provider_create_date, expires_at, created_at)
                VALUES (?, 'VNPAY', ?, ?, 'PENDING', ?, ?, ?, ?)
                """, invoiceId, txnRef, BigDecimal.valueOf(amount), userId, providerCreateDate, expiresUtc, now);
    }

    private static final RowMapper<PaymentRow> PAYMENT_ROW = (r, n) -> new PaymentRow(r.getLong("id"),
            r.getLong("invoice_id"), r.getString("txn_ref"), r.getBigDecimal("amount").longValueExact(),
            r.getString("status"), r.getString("provider_create_date"), instant(r, "expires_at"));

    public Optional<PaymentRow> paymentByTxnRef(String txnRef) {
        return jdbc.query("""
                SELECT id, invoice_id, txn_ref, amount, status, provider_create_date, expires_at
                FROM dbo.payment_transaction WHERE txn_ref = ?
                """, PAYMENT_ROW, txnRef).stream().findFirst();
    }

    public List<PaymentRow> pendingPayments(long invoiceId) {
        return jdbc.query("""
                SELECT id, invoice_id, txn_ref, amount, status, provider_create_date, expires_at
                FROM dbo.payment_transaction WHERE invoice_id = ? AND status = 'PENDING' ORDER BY created_at DESC, id DESC
                """, PAYMENT_ROW, invoiceId);
    }

    public int markPaymentSuccess(long paymentId, String via, String transactionNo, String bankCode, String cardType,
                                  String responseCode, String transactionStatus, String payDate) {
        LocalDateTime now = now();
        return jdbc.update("""
                UPDATE dbo.payment_transaction
                SET status = 'SUCCESS', confirmed_via = ?, confirmed_at = ?, provider_transaction_no = ?, bank_code = ?,
                    card_type = ?, response_code = ?, transaction_status = ?, provider_pay_date = ?, updated_at = ?
                WHERE id = ? AND status = 'PENDING'
                """, via, now, transactionNo, nullIfBlank(bankCode), nullIfBlank(cardType), responseCode,
                transactionStatus, nullIfBlank(payDate), now, paymentId);
    }

    public int markPaymentFailed(long paymentId, String responseCode, String transactionStatus) {
        return jdbc.update("""
                UPDATE dbo.payment_transaction
                SET status = 'FAILED', response_code = ?, transaction_status = ?, updated_at = ?
                WHERE id = ? AND status = 'PENDING'
                """, nullIfBlank(responseCode), nullIfBlank(transactionStatus), now(), paymentId);
    }

    public void audit(long actorUserId, String actorRole, String action, long invoiceId, String summary) {
        jdbc.update("""
                INSERT INTO dbo.audit_log (actor_user_id, actor_role, action, target_type, target_id, safe_summary, source)
                VALUES (?, ?, ?, 'SERVICE_INVOICE', ?, ?, 'WEB')
                """, actorUserId, actorRole, action, invoiceId, summary);
    }

    public void auditSystem(String action, long invoiceId, String summary) {
        jdbc.update("""
                INSERT INTO dbo.audit_log (actor_user_id, actor_role, action, target_type, target_id, safe_summary, source)
                VALUES (NULL, NULL, ?, 'SERVICE_INVOICE', ?, ?, 'VNPAY')
                """, action, invoiceId, summary);
    }

    private static String nullIfBlank(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static LocalDateTime now() {
        return LocalDateTime.now(Clock.systemUTC());
    }

    private static Instant instant(ResultSet r, String column) throws SQLException {
        LocalDateTime value = r.getObject(column, LocalDateTime.class);
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }
}
