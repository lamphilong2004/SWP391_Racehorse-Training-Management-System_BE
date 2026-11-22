package com.racehorse.billing;

import com.racehorse.billing.BillingDtos.CancelRequest;
import com.racehorse.billing.BillingDtos.CreatePaymentRequest;
import com.racehorse.billing.BillingDtos.GenerateRequest;
import com.racehorse.billing.BillingDtos.GenerateResult;
import com.racehorse.billing.BillingDtos.InvoiceDetail;
import com.racehorse.billing.BillingDtos.InvoiceSummary;
import com.racehorse.billing.BillingDtos.PaymentLink;
import com.racehorse.billing.BillingDtos.PaymentResult;
import com.racehorse.billing.BillingDtos.Price;
import com.racehorse.billing.BillingDtos.ReturnRequest;
import com.racehorse.billing.BillingDtos.UpdatePricesRequest;
import com.racehorse.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** DEC-016 service billing: Club Manager price list and invoices; Horse Owner invoices and VNPay sandbox payment. */
@RestController
@Tag(name = "Billing")
public class BillingController {

    private final BillingService service;

    public BillingController(BillingService service) {
        this.service = service;
    }

    /* ---------- Club Manager ---------- */

    @GetMapping("/api/billing/prices")
    @PreAuthorize("hasRole('CLUB_MANAGER')")
    @Operation(operationId = "listServicePrices", summary = "Current service prices (null when not set yet)",
            security = @SecurityRequirement(name = "bearerAuth"))
    public ApiResponse<List<Price>> prices() {
        return ApiResponse.ok(service.prices());
    }

    @PutMapping("/api/billing/prices")
    @PreAuthorize("hasRole('CLUB_MANAGER')")
    @Operation(operationId = "updateServicePrices",
            summary = "Set new prices (1,000-100,000,000 VND). Each change is kept as history; issued invoices keep their prices.",
            security = @SecurityRequirement(name = "bearerAuth"))
    public ApiResponse<List<Price>> updatePrices(@RequestBody UpdatePricesRequest request) {
        return ApiResponse.ok(service.updatePrices(request));
    }

    @GetMapping("/api/billing/payment-account")
    @PreAuthorize("hasRole('CLUB_MANAGER')")
    @Operation(operationId = "getClubPaymentAccount", summary = "Receiving bank account for VietQR (null when not set)",
            security = @SecurityRequirement(name = "bearerAuth"))
    public ApiResponse<BillingDtos.PaymentAccount> paymentAccount() {
        return ApiResponse.ok(service.paymentAccount());
    }

    @PutMapping("/api/billing/payment-account")
    @PreAuthorize("hasRole('CLUB_MANAGER')")
    @Operation(operationId = "updateClubPaymentAccount", summary = "Set the receiving bank account shown in owners' VietQR codes",
            security = @SecurityRequirement(name = "bearerAuth"))
    public ApiResponse<BillingDtos.PaymentAccount> updatePaymentAccount(@RequestBody BillingDtos.PaymentAccountRequest request) {
        return ApiResponse.ok(service.updatePaymentAccount(request));
    }

    @PostMapping("/api/billing/invoices/generate")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('CLUB_MANAGER')")
    @Operation(operationId = "generateServiceInvoices",
            summary = "Issue one invoice per horse for a month from boarding days and COMPLETED training sessions",
            description = "The current month is billed up to today. A horse already invoiced for the month (not cancelled),"
                    + " without a managing owner, or without billable service is listed in skipped.",
            security = @SecurityRequirement(name = "bearerAuth"))
    public ApiResponse<GenerateResult> generate(@RequestBody GenerateRequest request) {
        return ApiResponse.ok(service.generate(request));
    }

    @GetMapping("/api/billing/invoices")
    @PreAuthorize("hasRole('CLUB_MANAGER')")
    @Operation(operationId = "listServiceInvoices", summary = "Invoices, optionally filtered by month (YYYY-MM) and status",
            security = @SecurityRequirement(name = "bearerAuth"))
    public ApiResponse<List<InvoiceSummary>> invoices(@RequestParam(required = false) String month,
                                                      @RequestParam(required = false) String status) {
        return ApiResponse.ok(service.invoices(month, status));
    }

    @GetMapping("/api/billing/invoices/{invoiceId}")
    @PreAuthorize("hasRole('CLUB_MANAGER')")
    @Operation(operationId = "getServiceInvoice", summary = "Invoice with lines and payment attempts",
            security = @SecurityRequirement(name = "bearerAuth"))
    public ApiResponse<InvoiceDetail> invoice(@PathVariable long invoiceId) {
        return ApiResponse.ok(service.invoice(invoiceId));
    }

    @PostMapping("/api/billing/invoices/{invoiceId}/cancel")
    @PreAuthorize("hasRole('CLUB_MANAGER')")
    @Operation(operationId = "cancelServiceInvoice", summary = "Cancel an unpaid invoice with a reason (kept, never deleted)",
            security = @SecurityRequirement(name = "bearerAuth"))
    public ApiResponse<InvoiceDetail> cancel(@PathVariable long invoiceId, @RequestBody CancelRequest request) {
        return ApiResponse.ok(service.cancel(invoiceId, request));
    }

    @PostMapping("/api/billing/invoices/{invoiceId}/payment-check")
    @PreAuthorize("hasRole('CLUB_MANAGER')")
    @Operation(operationId = "checkServiceInvoicePayment", summary = "Ask VNPay (querydr) for the result of pending payments",
            security = @SecurityRequirement(name = "bearerAuth"))
    public ApiResponse<PaymentResult> managerCheck(@PathVariable long invoiceId) {
        return ApiResponse.ok(service.checkPayments(invoiceId));
    }

    /* ---------- Horse Owner ---------- */

    @GetMapping("/api/owner/invoices")
    @PreAuthorize("hasRole('HORSE_OWNER')")
    @Operation(operationId = "listMyServiceInvoices", summary = "Invoices addressed to the signed-in Horse Owner",
            security = @SecurityRequirement(name = "bearerAuth"))
    public ApiResponse<List<InvoiceSummary>> myInvoices() {
        return ApiResponse.ok(service.myInvoices());
    }

    @GetMapping("/api/owner/invoices/{invoiceId}")
    @PreAuthorize("hasRole('HORSE_OWNER')")
    @Operation(operationId = "getMyServiceInvoice", summary = "One own invoice; another owner's invoice is 404",
            security = @SecurityRequirement(name = "bearerAuth"))
    public ApiResponse<InvoiceDetail> myInvoice(@PathVariable long invoiceId) {
        return ApiResponse.ok(service.myInvoice(invoiceId));
    }

    @GetMapping("/api/owner/invoices/{invoiceId}/qr-transfer")
    @PreAuthorize("hasRole('HORSE_OWNER')")
    @Operation(operationId = "getQrTransfer", summary = "VietQR transfer data: club account, invoice total, transfer content",
            security = @SecurityRequirement(name = "bearerAuth"))
    public ApiResponse<BillingDtos.QrTransfer> qrTransfer(@PathVariable long invoiceId) {
        return ApiResponse.ok(service.qrTransfer(invoiceId));
    }

    @PostMapping("/api/owner/invoices/{invoiceId}/qr-transfer")
    @PreAuthorize("hasRole('HORSE_OWNER')")
    @Operation(operationId = "confirmQrTransfer",
            summary = "Owner pressed Thanh toán after the VietQR transfer: recorded as paid, declared by the owner",
            security = @SecurityRequirement(name = "bearerAuth"))
    public ApiResponse<PaymentResult> confirmQrTransfer(@PathVariable long invoiceId) {
        return ApiResponse.ok(service.confirmQrTransfer(invoiceId));
    }

    @PostMapping("/api/owner/invoices/{invoiceId}/vnpay-payment")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('HORSE_OWNER')")
    @Operation(operationId = "createVnpayPayment", summary = "Create a signed VNPay sandbox payment URL (QR or card)",
            security = @SecurityRequirement(name = "bearerAuth"))
    public ApiResponse<PaymentLink> pay(@PathVariable long invoiceId, @RequestBody(required = false) CreatePaymentRequest request,
                                        HttpServletRequest http) {
        return ApiResponse.ok(service.createPayment(invoiceId, request, http.getRemoteAddr()));
    }

    @PostMapping("/api/owner/invoices/{invoiceId}/vnpay-payment/cancel")
    @PreAuthorize("hasRole('HORSE_OWNER')")
    @Operation(operationId = "cancelVnpayPayment",
            summary = "Cancel the open VNPay link of the invoice so it can be paid again at once",
            security = @SecurityRequirement(name = "bearerAuth"))
    public ApiResponse<PaymentResult> cancelVnpayPayment(@PathVariable long invoiceId) {
        return ApiResponse.ok(service.cancelOpenPayment(invoiceId));
    }

    @PostMapping("/api/owner/invoices/{invoiceId}/payment-check")
    @PreAuthorize("hasRole('HORSE_OWNER')")
    @Operation(operationId = "checkMyServiceInvoicePayment", summary = "Ask VNPay (querydr) for the result of pending payments",
            security = @SecurityRequirement(name = "bearerAuth"))
    public ApiResponse<PaymentResult> ownerCheck(@PathVariable long invoiceId) {
        return ApiResponse.ok(service.checkPayments(invoiceId));
    }

    @PostMapping("/api/owner/invoices/{invoiceId}/momo-payment")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('HORSE_OWNER')")
    public ApiResponse<BillingDtos.MomoPaymentLink> momoPay(@PathVariable long invoiceId) {
        return ApiResponse.ok(service.createMomoPayment(invoiceId));
    }

    @PostMapping("/api/payments/momo/return")
    @PreAuthorize("hasAnyRole('HORSE_OWNER','CLUB_MANAGER')")
    public ApiResponse<PaymentResult> momoReturn(@RequestBody ReturnRequest request) {
        return ApiResponse.ok(service.handleMomoReturn(request == null ? Map.of() : request.params()));
    }

    @PostMapping("/api/payments/momo/ipn")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void momoIpn(@RequestBody Map<String, String> params) { service.handleMomoIpn(params); }

    /* ---------- VNPay ---------- */

    @PostMapping("/api/payments/vnpay/return")
    @PreAuthorize("hasAnyRole('HORSE_OWNER','CLUB_MANAGER')")
    @Operation(operationId = "confirmVnpayReturn",
            summary = "Browser return: verify the signature, then confirm with VNPay querydr before recording the result",
            security = @SecurityRequirement(name = "bearerAuth"))
    public ApiResponse<PaymentResult> vnpayReturn(@RequestBody ReturnRequest request) {
        return ApiResponse.ok(service.handleReturn(request == null ? Map.of() : request.params()));
    }

    @GetMapping("/api/payments/vnpay/ipn")
    @Operation(operationId = "vnpayIpn", summary = "VNPay IPN (public, signature-verified). Responds with RspCode/Message")
    public Map<String, String> ipn(@RequestParam Map<String, String> params) {
        return service.handleIpn(params);
    }
}
