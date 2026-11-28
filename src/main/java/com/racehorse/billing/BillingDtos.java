package com.racehorse.billing;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** DEC-016 service billing contract. Amounts are whole VND. */
public final class BillingDtos {

    private BillingDtos() {
    }

    public static final String BOARDING = "BOARDING_CARE_DAILY";
    public static final String TRAINING = "TRAINING_SESSION";

    @Schema(name = "ServicePrice")
    public record Price(String serviceCode, String label, String unit, Long unitPrice, Instant updatedAt, String updatedBy) {
    }

    @Schema(name = "UpdateServicePricesRequest")
    public record UpdatePricesRequest(Long boardingCareDaily, Long trainingSession) {
    }

    @Schema(name = "GenerateInvoicesRequest", description = "billingMonth as YYYY-MM; not after the current month.")
    public record GenerateRequest(String billingMonth) {
    }

    @Schema(name = "GenerateInvoicesResult")
    public record GenerateResult(String billingMonth, LocalDate periodStart, LocalDate periodEnd,
                                 List<InvoiceSummary> created, List<Skipped> skipped) {
    }

    @Schema(name = "SkippedHorse")
    public record Skipped(long horseId, String horseCode, String horseName, String reason) {
    }

    @Schema(name = "CancelInvoiceRequest")
    public record CancelRequest(String reason) {
    }

    @Schema(name = "InvoiceSummary")
    public record InvoiceSummary(long id, String invoiceCode, String billingMonth, LocalDate periodStart, LocalDate periodEnd,
                                 Horse horse, Party owner, long totalAmount, String status, LocalDate dueDate,
                                 Instant issuedAt, Instant paidAt) {
    }

    @Schema(name = "InvoiceDetail")
    public record InvoiceDetail(InvoiceSummary invoice, List<Line> lines, List<Payment> payments, Party issuedBy,
                                Instant cancelledAt, String cancellationReason, boolean paymentConfigured, boolean momoConfigured) {
    }

    @Schema(name = "InvoiceHorse")
    public record Horse(long id, String horseCode, String name) {
    }

    @Schema(name = "InvoiceParty")
    public record Party(long id, String fullName, String email) {
    }

    @Schema(name = "InvoiceLine")
    public record Line(int lineNo, String serviceCode, String description, int quantity, long unitPrice, long amount) {
    }

    @Schema(name = "PaymentTransaction")
    public record Payment(long id, String provider, String txnRef, long amount, String status, String bankCode,
                          String cardType, String providerTransactionNo, String responseCode, String confirmedVia,
                          Instant createdAt, Instant confirmedAt, Instant expiresAt) {
    }

    @Schema(name = "CreateVnpayPaymentRequest", description = "bankCode optional: VNPAYQR, VNBANK or INTCARD.")
    public record CreatePaymentRequest(String bankCode) {
    }

    @Schema(name = "VnpayPaymentLink")
    public record PaymentLink(String paymentUrl, String txnRef, long amount, Instant expiresAt) {
    }

    @Schema(name = "MomoPaymentLink")
    public record MomoPaymentLink(String payUrl, String txnRef, long amount, Instant expiresAt) {
    }

    @Schema(name = "VnpayReturnRequest", description = "All query parameters VNPay appended to the return URL.")
    public record ReturnRequest(Map<String, String> params) {
    }

    @Schema(name = "ClubPaymentAccount", description = "Receiving bank account shown in the VietQR code (BIN per NAPAS).")
    public record PaymentAccount(String bankBin, String bankName, String accountNo, String accountName, Instant updatedAt,
                                 String updatedBy) {
    }

    @Schema(name = "UpdateClubPaymentAccountRequest")
    public record PaymentAccountRequest(String bankBin, String bankName, String accountNo, String accountName) {
    }

    @Schema(name = "VietQrTransfer", description = "What the VietQR code encodes: receiving account, invoice total, transfer content.")
    public record QrTransfer(String bankBin, String bankName, String accountNo, String accountName, long amount, String content) {
    }

    @Schema(name = "PaymentResult")
    public record PaymentResult(long invoiceId, String invoiceCode, String invoiceStatus, String paymentStatus,
                                String responseCode, String message) {
    }

    /** Running cost of one own horse this month, not yet invoiced (owner price transparency, DEC-016 update). */
    @Schema(name = "OwnerHorseServiceEstimate")
    public record HorseEstimate(long horseId, String horseCode, String horseName, LocalDate from, LocalDate to,
                                LocalDate invoicedUntil, int boardingDays, int completedSessions,
                                Long boardingAmount, Long trainingAmount, Long total) {
    }

    /** Price list plus the running (not yet invoiced) cost of the owner's horses for the current month. */
    @Schema(name = "OwnerServiceEstimate")
    public record OwnerEstimate(String billingMonth, LocalDate asOf, List<Price> prices, List<HorseEstimate> horses,
                                Long total) {
    }
}
