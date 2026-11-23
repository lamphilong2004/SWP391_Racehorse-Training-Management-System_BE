package com.racehorse.billing;

import com.racehorse.TestJwtProperties;
import com.racehorse.auth.RoleCode;
import com.racehorse.testsupport.TestAccounts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** DEC-016 billing: price list, monthly invoice from real usage, owner scoping, VNPay payment and IPN. */
@SpringBootTest
@ActiveProfiles("test")
class BillingApiTest {

    private static final String SECRET = "TEST-ONLY-VNPAY-SECRET-0001";
    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");

    @Autowired private WebApplicationContext context;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PasswordEncoder encoder;
    @Autowired private JsonMapper json;
    private MockMvc mvc;
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean private VnpayGateway gatewaySpy;
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean private BillingRepository repositorySpy;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        TestJwtProperties.register(registry);
        registry.add("app.payment.vnpay.tmn-code", () -> "TESTTMN1");
        registry.add("app.payment.vnpay.hash-secret", () -> SECRET);
        // querydr is unreachable in tests (closed local port), like a local machine whose query VNPay cannot answer.
        registry.add("app.payment.vnpay.api-url", () -> "http://127.0.0.1:9/merchant_webapi/api/transaction");
    }

    @org.junit.jupiter.api.AfterEach
    void cleanAfter() {
        clean();
    }

    @BeforeEach
    void clean() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        jdbc.update("DELETE FROM dbo.payment_transaction");
        jdbc.update("DELETE FROM dbo.service_invoice_line");
        jdbc.update("DELETE FROM dbo.service_invoice");
        jdbc.update("DELETE FROM dbo.service_price");
        jdbc.update("DELETE FROM dbo.club_payment_account");
        jdbc.update("DELETE FROM dbo.notification WHERE recipient_user_id BETWEEN 7700 AND 7799");
        jdbc.update("DELETE FROM dbo.training_session WHERE id BETWEEN 7700 AND 7799");
        jdbc.update("DELETE FROM dbo.horse_program_phase WHERE id BETWEEN 7700 AND 7799");
        jdbc.update("DELETE FROM dbo.horse_training_program WHERE id BETWEEN 7700 AND 7799");
        jdbc.update("DELETE FROM dbo.horse_stable_assignment WHERE horse_id BETWEEN 7700 AND 7799");
        jdbc.update("DELETE FROM dbo.horse_ownership WHERE horse_id BETWEEN 7700 AND 7799");
        jdbc.update("DELETE FROM dbo.stable_box WHERE box_code LIKE 'BILL-%'");
        jdbc.update("DELETE FROM dbo.horse WHERE id BETWEEN 7700 AND 7799");
        jdbc.update("DELETE FROM dbo.audit_log WHERE actor_user_id BETWEEN 7700 AND 7799 OR target_type = 'SERVICE_INVOICE'");
        jdbc.update("DELETE FROM dbo.user_role WHERE user_id BETWEEN 7700 AND 7799");
        jdbc.update("DELETE FROM dbo.app_user WHERE id BETWEEN 7700 AND 7799");
    }

    @Test
    void managerIssuesAMonthlyInvoiceFromRealUsageAndOnlyItsOwnerSeesIt() throws Exception {
        String cm = TestAccounts.create(mvc, jdbc, encoder, json, 7701, RoleCode.CLUB_MANAGER).token();
        String owner = TestAccounts.create(mvc, jdbc, encoder, json, 7702, RoleCode.HORSE_OWNER).token();
        String stranger = TestAccounts.create(mvc, jdbc, encoder, json, 7703, RoleCode.HORSE_OWNER).token();
        String month = YearMonth.now(VN).toString();

        mvc.perform(post("/api/billing/invoices/generate").header("Authorization", "Bearer " + cm)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"billingMonth\":\"" + month + "\"}"))
                .andExpect(status().isConflict());
        mvc.perform(put("/api/billing/prices").header("Authorization", "Bearer " + cm)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"boardingCareDaily\":500}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/billing/prices").header("Authorization", "Bearer " + owner)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"boardingCareDaily\":500000}"))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/billing/prices").header("Authorization", "Bearer " + cm)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"boardingCareDaily\":250000,\"trainingSession\":400000}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].unitPrice").value(250000))
                .andExpect(jsonPath("$.data[1].unitPrice").value(400000));

        long invoiceId = seedHorseAndGenerate(cm, month, 7702);
        int days = LocalDate.now(VN).getDayOfMonth();
        long expected = days * 250_000L + 2 * 400_000L;

        mvc.perform(post("/api/billing/invoices/generate").header("Authorization", "Bearer " + cm)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"billingMonth\":\"" + month + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.skipped[?(@.horseId == 7750)].reason").value("ALREADY_INVOICED"));
        mvc.perform(post("/api/billing/invoices/generate").header("Authorization", "Bearer " + cm)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"billingMonth\":\"" + YearMonth.now(VN).plusMonths(1) + "\"}"))
                .andExpect(status().isBadRequest());

        mvc.perform(get("/api/owner/invoices/" + invoiceId).header("Authorization", "Bearer " + owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.invoice.totalAmount").value(expected))
                .andExpect(jsonPath("$.data.invoice.status").value("ISSUED"))
                .andExpect(jsonPath("$.data.lines[0].quantity").value(days))
                .andExpect(jsonPath("$.data.lines[1].quantity").value(2))
                .andExpect(jsonPath("$.data.paymentConfigured").value(true));
        mvc.perform(get("/api/owner/invoices").header("Authorization", "Bearer " + stranger))
                .andExpect(jsonPath("$.data.length()").value(0));
        mvc.perform(get("/api/owner/invoices/" + invoiceId).header("Authorization", "Bearer " + stranger))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/billing/invoices").header("Authorization", "Bearer " + owner))
                .andExpect(status().isForbidden());
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM dbo.notification WHERE recipient_user_id = 7702 AND notification_type = 'SERVICE_INVOICE_ISSUED'",
                Integer.class));
    }

    @Test
    void ownerPaysWithVnpayAndOnlyASignedIpnMarksTheInvoicePaid() throws Exception {
        String cm = TestAccounts.create(mvc, jdbc, encoder, json, 7711, RoleCode.CLUB_MANAGER).token();
        String owner = TestAccounts.create(mvc, jdbc, encoder, json, 7712, RoleCode.HORSE_OWNER).token();
        mvc.perform(put("/api/billing/prices").header("Authorization", "Bearer " + cm)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"boardingCareDaily\":100000,\"trainingSession\":300000}"))
                .andExpect(status().isOk());
        long invoiceId = seedHorseAndGenerate(cm, YearMonth.now(VN).toString(), 7712);

        String created = mvc.perform(post("/api/owner/invoices/" + invoiceId + "/vnpay-payment")
                        .header("Authorization", "Bearer " + owner)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"bankCode\":\"VNPAYQR\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.paymentUrl").value(containsString("https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?")))
                .andExpect(jsonPath("$.data.paymentUrl").value(containsString("vnp_BankCode=VNPAYQR")))
                .andExpect(jsonPath("$.data.paymentUrl").value(containsString("vnp_SecureHash=")))
                .andReturn().getResponse().getContentAsString();
        JsonNode link = json.readTree(created).path("data");
        String txnRef = link.path("txnRef").stringValue();
        long amount = link.path("amount").longValue();

        mvc.perform(post("/api/billing/invoices/" + invoiceId + "/cancel").header("Authorization", "Bearer " + cm)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Sai số\"}"))
                .andExpect(status().isConflict());

        Map<String, String> ipn = new LinkedHashMap<>();
        ipn.put("vnp_TmnCode", "TESTTMN1");
        ipn.put("vnp_Amount", String.valueOf(amount * 100));
        ipn.put("vnp_BankCode", "NCB");
        ipn.put("vnp_CardType", "QRCODE");
        ipn.put("vnp_OrderInfo", "Thanh toan hoa don");
        ipn.put("vnp_PayDate", "20261120101010");
        ipn.put("vnp_ResponseCode", "00");
        ipn.put("vnp_TransactionNo", "14226112");
        ipn.put("vnp_TransactionStatus", "00");
        ipn.put("vnp_TxnRef", txnRef);

        Map<String, String> forged = new LinkedHashMap<>(ipn);
        forged.put("vnp_SecureHash", VnpaySigner.sign("wrong-secret", ipn));
        mvc.perform(ipnRequest(forged)).andExpect(jsonPath("$.RspCode").value("97"));

        Map<String, String> wrongAmount = new LinkedHashMap<>(ipn);
        wrongAmount.put("vnp_Amount", "100");
        wrongAmount.put("vnp_SecureHash", VnpaySigner.sign(SECRET, wrongAmount));
        mvc.perform(ipnRequest(wrongAmount)).andExpect(jsonPath("$.RspCode").value("04"));

        mvc.perform(get("/api/owner/invoices/" + invoiceId).header("Authorization", "Bearer " + owner))
                .andExpect(jsonPath("$.data.invoice.status").value("ISSUED"));

        ipn.put("vnp_SecureHash", VnpaySigner.sign(SECRET, ipn));
        mvc.perform(ipnRequest(ipn)).andExpect(status().isOk()).andExpect(jsonPath("$.RspCode").value("00"));
        mvc.perform(ipnRequest(ipn)).andExpect(jsonPath("$.RspCode").value("02"));

        mvc.perform(get("/api/owner/invoices/" + invoiceId).header("Authorization", "Bearer " + owner))
                .andExpect(jsonPath("$.data.invoice.status").value("PAID"))
                .andExpect(jsonPath("$.data.payments[0].status").value("SUCCESS"))
                .andExpect(jsonPath("$.data.payments[0].confirmedVia").value("IPN"))
                .andExpect(jsonPath("$.data.payments[0].providerTransactionNo").value("14226112"));
        mvc.perform(post("/api/owner/invoices/" + invoiceId + "/vnpay-payment").header("Authorization", "Bearer " + owner)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/billing/invoices/" + invoiceId + "/cancel").header("Authorization", "Bearer " + cm)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Sai số\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void ownerScansTheClubVietQrAndPressesPayToSettleTheInvoiceWithHistory() throws Exception {
        String cm = TestAccounts.create(mvc, jdbc, encoder, json, 7781, RoleCode.CLUB_MANAGER).token();
        String owner = TestAccounts.create(mvc, jdbc, encoder, json, 7782, RoleCode.HORSE_OWNER).token();
        String stranger = TestAccounts.create(mvc, jdbc, encoder, json, 7783, RoleCode.HORSE_OWNER).token();
        mvc.perform(put("/api/billing/prices").header("Authorization", "Bearer " + cm)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"boardingCareDaily\":100000,\"trainingSession\":300000}"))
                .andExpect(status().isOk());
        long invoiceId = seedHorseAndGenerate(cm, YearMonth.now(VN).toString(), 7782);

        mvc.perform(get("/api/owner/invoices/" + invoiceId + "/qr-transfer").header("Authorization", "Bearer " + owner))
                .andExpect(status().isConflict());
        mvc.perform(put("/api/billing/payment-account").header("Authorization", "Bearer " + cm)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bankBin\":\"ABC\",\"bankName\":\"\",\"accountNo\":\"12\",\"accountName\":\"\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/billing/payment-account").header("Authorization", "Bearer " + owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bankBin\":\"970436\",\"bankName\":\"Vietcombank\",\"accountNo\":\"0123456789\",\"accountName\":\"X\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/billing/payment-account").header("Authorization", "Bearer " + cm)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bankBin\":\"970436\",\"bankName\":\"Vietcombank\",\"accountNo\":\"0123 456 789\",\"accountName\":\"CLB DUA NGUA G2\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accountNo").value("0123456789"));

        String code = json.readTree(mvc.perform(get("/api/owner/invoices/" + invoiceId).header("Authorization", "Bearer " + owner))
                .andReturn().getResponse().getContentAsString()).path("data").path("invoice").path("invoiceCode").stringValue();
        long total = jdbc.queryForObject("SELECT CAST(total_amount AS BIGINT) FROM dbo.service_invoice WHERE id = ?", Long.class, invoiceId);
        mvc.perform(get("/api/owner/invoices/" + invoiceId + "/qr-transfer").header("Authorization", "Bearer " + owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bankBin").value("970436"))
                .andExpect(jsonPath("$.data.accountNo").value("0123456789"))
                .andExpect(jsonPath("$.data.accountName").value("CLB DUA NGUA G2"))
                .andExpect(jsonPath("$.data.amount").value(total))
                .andExpect(jsonPath("$.data.content").value(code.replace("-", "")));
        mvc.perform(post("/api/owner/invoices/" + invoiceId + "/qr-transfer").header("Authorization", "Bearer " + stranger))
                .andExpect(status().isNotFound());

        mvc.perform(post("/api/owner/invoices/" + invoiceId + "/qr-transfer").header("Authorization", "Bearer " + owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.invoiceStatus").value("PAID"))
                .andExpect(jsonPath("$.data.paymentStatus").value("SUCCESS"));
        mvc.perform(post("/api/owner/invoices/" + invoiceId + "/qr-transfer").header("Authorization", "Bearer " + owner))
                .andExpect(status().isConflict());
        mvc.perform(get("/api/billing/invoices/" + invoiceId).header("Authorization", "Bearer " + cm))
                .andExpect(jsonPath("$.data.invoice.status").value("PAID"))
                .andExpect(jsonPath("$.data.payments[0].provider").value("VIETQR"))
                .andExpect(jsonPath("$.data.payments[0].confirmedVia").value("OWNER"))
                .andExpect(jsonPath("$.data.payments[0].amount").value(total))
                .andExpect(jsonPath("$.data.payments[0].bankCode").value("970436"));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM dbo.notification WHERE recipient_user_id = 7781"
                + " AND notification_type = 'SERVICE_INVOICE_PAID'", Integer.class));
    }

    @Test
    void anUnsignedBrowserReturnNeverPaysAnInvoice() throws Exception {
        String owner = TestAccounts.create(mvc, jdbc, encoder, json, 7722, RoleCode.HORSE_OWNER).token();
        mvc.perform(post("/api/payments/vnpay/return").header("Authorization", "Bearer " + owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"params\":{\"vnp_TxnRef\":\"HD1T1\",\"vnp_ResponseCode\":\"00\",\"vnp_SecureHash\":\"abc\"}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void whenQuerydrIsUnavailableOnlyAGenuineSignedReturnWithMatchingAmountPays() throws Exception {
        String cm = TestAccounts.create(mvc, jdbc, encoder, json, 7761, RoleCode.CLUB_MANAGER).token();
        String owner = TestAccounts.create(mvc, jdbc, encoder, json, 7762, RoleCode.HORSE_OWNER).token();
        mvc.perform(put("/api/billing/prices").header("Authorization", "Bearer " + cm)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"boardingCareDaily\":100000,\"trainingSession\":300000}"))
                .andExpect(status().isOk());
        long invoiceId = seedHorseAndGenerate(cm, YearMonth.now(VN).toString(), 7762);
        JsonNode link = json.readTree(mvc.perform(post("/api/owner/invoices/" + invoiceId + "/vnpay-payment")
                        .header("Authorization", "Bearer " + owner).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).path("data");
        String txnRef = link.path("txnRef").stringValue();
        long amount = link.path("amount").longValue();

        Map<String, String> tampered = new LinkedHashMap<>(signedIpn(txnRef, amount, "555"));
        tampered.remove("vnp_SecureHash");
        tampered.put("vnp_Amount", "100");
        tampered.put("vnp_SecureHash", VnpaySigner.sign(SECRET, tampered));
        mvc.perform(post("/api/payments/vnpay/return").header("Authorization", "Bearer " + owner)
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("params", tampered))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.paymentStatus").value("PENDING"))
                .andExpect(jsonPath("$.data.invoiceStatus").value("ISSUED"));

        mvc.perform(post("/api/payments/vnpay/return").header("Authorization", "Bearer " + owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("params", signedIpn(txnRef, amount, "777")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.paymentStatus").value("SUCCESS"))
                .andExpect(jsonPath("$.data.invoiceStatus").value("PAID"));
        mvc.perform(get("/api/owner/invoices/" + invoiceId).header("Authorization", "Bearer " + owner))
                .andExpect(jsonPath("$.data.payments[0].confirmedVia").value("RETURN"))
                .andExpect(jsonPath("$.data.payments[0].providerTransactionNo").value("777"));
    }

    @Test
    void aSignedReturnOfACancelledPaymentMarksItFailedAndAllowsANewOne() throws Exception {
        String cm = TestAccounts.create(mvc, jdbc, encoder, json, 7771, RoleCode.CLUB_MANAGER).token();
        String owner = TestAccounts.create(mvc, jdbc, encoder, json, 7772, RoleCode.HORSE_OWNER).token();
        mvc.perform(put("/api/billing/prices").header("Authorization", "Bearer " + cm)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"boardingCareDaily\":100000,\"trainingSession\":300000}"))
                .andExpect(status().isOk());
        long invoiceId = seedHorseAndGenerate(cm, YearMonth.now(VN).toString(), 7772);
        JsonNode link = json.readTree(mvc.perform(post("/api/owner/invoices/" + invoiceId + "/vnpay-payment")
                        .header("Authorization", "Bearer " + owner).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).path("data");
        Map<String, String> cancelled = new LinkedHashMap<>(signedIpn(link.path("txnRef").stringValue(), link.path("amount").longValue(), "0"));
        cancelled.remove("vnp_SecureHash");
        cancelled.put("vnp_ResponseCode", "24");
        cancelled.put("vnp_TransactionStatus", "02");
        cancelled.put("vnp_SecureHash", VnpaySigner.sign(SECRET, cancelled));
        mvc.perform(post("/api/payments/vnpay/return").header("Authorization", "Bearer " + owner)
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("params", cancelled))))
                .andExpect(jsonPath("$.data.paymentStatus").value("FAILED"))
                .andExpect(jsonPath("$.data.invoiceStatus").value("ISSUED"));
        mvc.perform(post("/api/owner/invoices/" + invoiceId + "/vnpay-payment")
                        .header("Authorization", "Bearer " + owner).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isCreated());
    }

    @Test
    void managerCancelsAnUnpaidInvoiceWithAReasonAndCanIssueItAgain() throws Exception {
        String cm = TestAccounts.create(mvc, jdbc, encoder, json, 7731, RoleCode.CLUB_MANAGER).token();
        TestAccounts.create(mvc, jdbc, encoder, json, 7732, RoleCode.HORSE_OWNER);
        mvc.perform(put("/api/billing/prices").header("Authorization", "Bearer " + cm)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"boardingCareDaily\":100000,\"trainingSession\":300000}"))
                .andExpect(status().isOk());
        String month = YearMonth.now(VN).toString();
        long invoiceId = seedHorseAndGenerate(cm, month, 7732);
        mvc.perform(post("/api/billing/invoices/" + invoiceId + "/cancel").header("Authorization", "Bearer " + cm)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\" \"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/billing/invoices/" + invoiceId + "/cancel").header("Authorization", "Bearer " + cm)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Nhập sai giá\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.invoice.status").value("CANCELLED"))
                .andExpect(jsonPath("$.data.cancellationReason").value("Nhập sai giá"));
        mvc.perform(post("/api/billing/invoices/generate").header("Authorization", "Bearer " + cm)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"billingMonth\":\"" + month + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.created[?(@.horse.id == 7750)].status").value("ISSUED"));
    }

    @Test
    void theRestOfAMonthBilledUpToADayIsBilledLaterWithoutCountingADayTwice() throws Exception {
        String cm = TestAccounts.create(mvc, jdbc, encoder, json, 7741, RoleCode.CLUB_MANAGER).token();
        TestAccounts.create(mvc, jdbc, encoder, json, 7742, RoleCode.HORSE_OWNER);
        mvc.perform(put("/api/billing/prices").header("Authorization", "Bearer " + cm)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"boardingCareDaily\":100000,\"trainingSession\":300000}"))
                .andExpect(status().isOk());
        String month = YearMonth.now(VN).toString();
        long first = seedHorseAndGenerate(cm, month, 7742);
        LocalDate today = LocalDate.now(VN);
        if (today.getDayOfMonth() == 1) {
            return; // the first part already covers the only billable day
        }
        // Simulate that the first invoice was issued yesterday: it covers the month up to yesterday only.
        jdbc.update("UPDATE dbo.service_invoice_line SET quantity = quantity - 1, amount = (quantity - 1) * unit_price"
                + " WHERE invoice_id = ? AND service_code = 'BOARDING_CARE_DAILY'", first);
        jdbc.update("UPDATE dbo.service_invoice SET period_end = ?, total_amount = total_amount - 100000 WHERE id = ?",
                today.minusDays(1), first);
        jdbc.update("UPDATE dbo.training_session SET actual_end = ?, actual_start = ? WHERE id = 7751",
                today.minusDays(1).atTime(2, 0), today.minusDays(1).atTime(1, 0));

        String body = mvc.perform(post("/api/billing/invoices/generate").header("Authorization", "Bearer " + cm)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"billingMonth\":\"" + month + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode second = null;
        for (JsonNode invoice : json.readTree(body).path("data").path("created")) {
            if (invoice.path("horse").path("id").longValue() == 7750) second = invoice;
        }
        org.junit.jupiter.api.Assertions.assertNotNull(second, body);
        assertEquals(today.toString(), second.path("periodStart").stringValue());
        // Only today: 1 boarding day; the session completed today is billed here, the one of yesterday stayed in the first part.
        assertEquals(100_000L + 300_000L, second.path("totalAmount").longValue());
    }

    @Test
    void aSecondPaymentConfirmedForAPaidInvoiceIsFlaggedForRefundNotCountedTwice() throws Exception {
        String cm = TestAccounts.create(mvc, jdbc, encoder, json, 7751, RoleCode.CLUB_MANAGER).token();
        String owner = TestAccounts.create(mvc, jdbc, encoder, json, 7752, RoleCode.HORSE_OWNER).token();
        mvc.perform(put("/api/billing/prices").header("Authorization", "Bearer " + cm)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"boardingCareDaily\":100000,\"trainingSession\":300000}"))
                .andExpect(status().isOk());
        long invoiceId = seedHorseAndGenerate(cm, YearMonth.now(VN).toString(), 7752);
        String created = mvc.perform(post("/api/owner/invoices/" + invoiceId + "/vnpay-payment")
                        .header("Authorization", "Bearer " + owner).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        JsonNode link = json.readTree(created).path("data");
        mvc.perform(post("/api/owner/invoices/" + invoiceId + "/vnpay-payment")
                        .header("Authorization", "Bearer " + owner).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isConflict());

        long amount = link.path("amount").longValue();
        jdbc.update("""
                INSERT INTO dbo.payment_transaction (invoice_id, provider, txn_ref, amount, status, requested_by_user_id,
                    provider_create_date, expires_at) VALUES (?, 'VNPAY', 'HDOLDLINK1', ?, 'PENDING', 7752, '20261120090000', ?)
                """, invoiceId, amount, LocalDateTime.now(ZoneOffset.UTC).plusMinutes(10));
        mvc.perform(ipnRequest(signedIpn(link.path("txnRef").stringValue(), amount, "111"))).andExpect(jsonPath("$.RspCode").value("00"));
        mvc.perform(ipnRequest(signedIpn("HDOLDLINK1", amount, "222"))).andExpect(jsonPath("$.RspCode").value("00"));

        mvc.perform(get("/api/billing/invoices/" + invoiceId).header("Authorization", "Bearer " + cm))
                .andExpect(jsonPath("$.data.invoice.status").value("PAID"))
                .andExpect(jsonPath("$.data.payments[?(@.txnRef == 'HDOLDLINK1')].status").value("REFUND_REQUIRED"));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM dbo.notification WHERE recipient_user_id = 7751"
                + " AND notification_type = 'SERVICE_PAYMENT_REFUND_REQUIRED'", Integer.class));
    }

    @Test
    void theVietQrPayButtonIsRefusedWhileAVnpayLinkOfTheInvoiceCanStillBePaid() throws Exception {
        String cm = TestAccounts.create(mvc, jdbc, encoder, json, 7791, RoleCode.CLUB_MANAGER).token();
        String owner = TestAccounts.create(mvc, jdbc, encoder, json, 7792, RoleCode.HORSE_OWNER).token();
        mvc.perform(put("/api/billing/prices").header("Authorization", "Bearer " + cm)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"boardingCareDaily\":100000,\"trainingSession\":300000}"))
                .andExpect(status().isOk());
        mvc.perform(put("/api/billing/payment-account").header("Authorization", "Bearer " + cm)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bankBin\":\"970436\",\"bankName\":\"Vietcombank\",\"accountNo\":\"0123456789\",\"accountName\":\"CLB\"}"))
                .andExpect(status().isOk());
        long invoiceId = seedHorseAndGenerate(cm, YearMonth.now(VN).toString(), 7792);
        mvc.perform(post("/api/owner/invoices/" + invoiceId + "/vnpay-payment")
                        .header("Authorization", "Bearer " + owner).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isCreated());

        mvc.perform(post("/api/owner/invoices/" + invoiceId + "/qr-transfer").header("Authorization", "Bearer " + owner))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.message", containsString("still open")));
        assertEquals("ISSUED", jdbc.queryForObject("SELECT status FROM dbo.service_invoice WHERE id = ?", String.class, invoiceId));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM dbo.payment_transaction WHERE invoice_id = ? AND provider = 'VIETQR'",
                Integer.class, invoiceId));

        // Once the VNPay link can no longer be paid (expired), the owner may settle by VietQR.
        jdbc.update("UPDATE dbo.payment_transaction SET expires_at = ? WHERE invoice_id = ?",
                LocalDateTime.now(ZoneOffset.UTC).minusMinutes(1), invoiceId);
        mvc.perform(post("/api/owner/invoices/" + invoiceId + "/qr-transfer").header("Authorization", "Bearer " + owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.invoiceStatus").value("PAID"));
    }

    @Test
    void twoPaymentStartsAtTheSameMomentOpenOnlyOneVnpayLink() throws Exception {
        String cm = TestAccounts.create(mvc, jdbc, encoder, json, 7795, RoleCode.CLUB_MANAGER).token();
        String owner = TestAccounts.create(mvc, jdbc, encoder, json, 7796, RoleCode.HORSE_OWNER).token();
        mvc.perform(put("/api/billing/prices").header("Authorization", "Bearer " + cm)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"boardingCareDaily\":100000,\"trainingSession\":300000}"))
                .andExpect(status().isOk());
        long invoiceId = seedHorseAndGenerate(cm, YearMonth.now(VN).toString(), 7796);
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            for (int round = 0; round < 8; round++) {
                java.util.concurrent.CountDownLatch go = new java.util.concurrent.CountDownLatch(1);
                java.util.concurrent.Callable<Integer> start = () -> {
                    go.await();
                    return mvc.perform(post("/api/owner/invoices/" + invoiceId + "/vnpay-payment")
                                    .header("Authorization", "Bearer " + owner).contentType(MediaType.APPLICATION_JSON).content("{}"))
                            .andReturn().getResponse().getStatus();
                };
                var first = pool.submit(start);
                var second = pool.submit(start);
                go.countDown();
                java.util.List<Integer> statuses = new java.util.ArrayList<>(java.util.List.of(
                        first.get(30, java.util.concurrent.TimeUnit.SECONDS), second.get(30, java.util.concurrent.TimeUnit.SECONDS)));
                java.util.Collections.sort(statuses);
                assertEquals(java.util.List.of(201, 409), statuses, "round " + round);
                assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM dbo.payment_transaction WHERE invoice_id = ?"
                        + " AND status = 'PENDING'", Integer.class, invoiceId), "round " + round);
                // The owner cancelled at VNPay: the next round starts with no open link.
                jdbc.update("UPDATE dbo.payment_transaction SET status = 'FAILED' WHERE invoice_id = ? AND status = 'PENDING'", invoiceId);
            }
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void ownerCancelsAnOpenVnpayLinkAndCanPayAgainAtOnce() throws Exception {
        String cm = TestAccounts.create(mvc, jdbc, encoder, json, 7724, RoleCode.CLUB_MANAGER).token();
        String owner = TestAccounts.create(mvc, jdbc, encoder, json, 7725, RoleCode.HORSE_OWNER).token();
        String stranger = TestAccounts.create(mvc, jdbc, encoder, json, 7726, RoleCode.HORSE_OWNER).token();
        long invoiceId = invoiceWithPricesAndAccount(cm, 7725);
        String cancel = "/api/owner/invoices/" + invoiceId + "/vnpay-payment/cancel";

        mvc.perform(post(cancel).header("Authorization", "Bearer " + owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.paymentStatus").value("NO_PENDING_PAYMENT"));
        String first = startVnpay(owner, invoiceId).path("txnRef").stringValue();
        mvc.perform(post(cancel).header("Authorization", "Bearer " + stranger)).andExpect(status().isNotFound());
        mvc.perform(post(cancel).header("Authorization", "Bearer " + cm)).andExpect(status().isForbidden());

        mvc.perform(post(cancel).header("Authorization", "Bearer " + owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.paymentStatus").value("CANCELLED"))
                .andExpect(jsonPath("$.data.invoiceStatus").value("ISSUED"));
        assertEquals("FAILED/24/OWNER", jdbc.queryForObject("SELECT status || '/' || response_code || '/' || confirmed_via"
                + " FROM dbo.payment_transaction WHERE txn_ref = ?", String.class, first));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM dbo.audit_log WHERE action = 'SERVICE_INVOICE_PAYMENT_CANCELLED'"
                + " AND target_id = ?", Integer.class, invoiceId));

        // Paying again works at once: a new VNPay link, and after cancelling that one, VietQR.
        startVnpay(owner, invoiceId);
        mvc.perform(post(cancel).header("Authorization", "Bearer " + owner))
                .andExpect(jsonPath("$.data.paymentStatus").value("CANCELLED"));
        mvc.perform(post("/api/owner/invoices/" + invoiceId + "/qr-transfer").header("Authorization", "Bearer " + owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.invoiceStatus").value("PAID"));
    }

    @Test
    void aCancelledLinkThatVnpayStillReportsPaidIsRecordedNotLost() throws Exception {
        String cm = TestAccounts.create(mvc, jdbc, encoder, json, 7734, RoleCode.CLUB_MANAGER).token();
        String owner = TestAccounts.create(mvc, jdbc, encoder, json, 7735, RoleCode.HORSE_OWNER).token();
        long invoiceId = invoiceWithPricesAndAccount(cm, 7735);
        JsonNode link = startVnpay(owner, invoiceId);
        String txnRef = link.path("txnRef").stringValue();
        long amount = link.path("amount").longValue();
        mvc.perform(post("/api/owner/invoices/" + invoiceId + "/vnpay-payment/cancel").header("Authorization", "Bearer " + owner))
                .andExpect(jsonPath("$.data.paymentStatus").value("CANCELLED"));

        // The owner had already paid in the VNPay tab: the signed return still records it and the invoice is paid.
        mvc.perform(post("/api/payments/vnpay/return").header("Authorization", "Bearer " + owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("params", signedIpn(txnRef, amount, "888")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.paymentStatus").value("SUCCESS"))
                .andExpect(jsonPath("$.data.invoiceStatus").value("PAID"));
        assertEquals("SUCCESS", jdbc.queryForObject("SELECT status FROM dbo.payment_transaction WHERE txn_ref = ?",
                String.class, txnRef));
        // A repeated IPN for the same payment is answered as already confirmed and changes nothing.
        mvc.perform(ipnRequest(signedIpn(txnRef, amount, "888"))).andExpect(jsonPath("$.RspCode").value("02"));
    }

    @Test
    void aCancelledLinkPaidAfterTheOwnerSettledByVietQrIsFlaggedForRefund() throws Exception {
        String cm = TestAccounts.create(mvc, jdbc, encoder, json, 7744, RoleCode.CLUB_MANAGER).token();
        String owner = TestAccounts.create(mvc, jdbc, encoder, json, 7745, RoleCode.HORSE_OWNER).token();
        long invoiceId = invoiceWithPricesAndAccount(cm, 7745);
        JsonNode link = startVnpay(owner, invoiceId);
        mvc.perform(post("/api/owner/invoices/" + invoiceId + "/vnpay-payment/cancel").header("Authorization", "Bearer " + owner))
                .andExpect(jsonPath("$.data.paymentStatus").value("CANCELLED"));
        mvc.perform(post("/api/owner/invoices/" + invoiceId + "/qr-transfer").header("Authorization", "Bearer " + owner))
                .andExpect(jsonPath("$.data.invoiceStatus").value("PAID"));

        mvc.perform(ipnRequest(signedIpn(link.path("txnRef").stringValue(), link.path("amount").longValue(), "999")))
                .andExpect(jsonPath("$.RspCode").value("00"));
        assertEquals("REFUND_REQUIRED", jdbc.queryForObject("SELECT status FROM dbo.payment_transaction WHERE txn_ref = ?",
                String.class, link.path("txnRef").stringValue()));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM dbo.payment_transaction WHERE invoice_id = ?"
                + " AND status = 'SUCCESS'", Integer.class, invoiceId));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM dbo.notification WHERE recipient_user_id = 7744"
                + " AND notification_type = 'SERVICE_PAYMENT_REFUND_REQUIRED'", Integer.class));
    }

    @Test
    void paymentCheckFindsACancelledLinkThatVnpayReportsPaid() throws Exception {
        String cm = TestAccounts.create(mvc, jdbc, encoder, json, 7764, RoleCode.CLUB_MANAGER).token();
        String owner = TestAccounts.create(mvc, jdbc, encoder, json, 7765, RoleCode.HORSE_OWNER).token();
        long invoiceId = invoiceWithPricesAndAccount(cm, 7765);
        JsonNode link = startVnpay(owner, invoiceId);
        String txnRef = link.path("txnRef").stringValue();
        long amount = link.path("amount").longValue();
        mvc.perform(post("/api/owner/invoices/" + invoiceId + "/vnpay-payment/cancel").header("Authorization", "Bearer " + owner))
                .andExpect(jsonPath("$.data.paymentStatus").value("CANCELLED"));

        // No IPN reaches a local machine and the VNPay tab was closed: only querydr can tell the link was paid.
        org.mockito.Mockito.doReturn(java.util.Optional.of(new VnpayGateway.QueryResult("00", "00", amount * 100, "123456", "NCB",
                        "20261122120000", "OK")))
                .when(gatewaySpy).query(org.mockito.ArgumentMatchers.eq(txnRef), org.mockito.ArgumentMatchers.any());
        mvc.perform(post("/api/owner/invoices/" + invoiceId + "/payment-check").header("Authorization", "Bearer " + owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.paymentStatus").value("SUCCESS"))
                .andExpect(jsonPath("$.data.invoiceStatus").value("PAID"));
        assertEquals("SUCCESS/QUERYDR", jdbc.queryForObject("SELECT status || '/' || confirmed_via FROM dbo.payment_transaction"
                + " WHERE txn_ref = ?", String.class, txnRef));
    }

    @Test
    void paymentCheckThatCannotReachVnpaySaysHowThePaymentIsRecordedInsteadOfAskingToRetry() throws Exception {
        String cm = TestAccounts.create(mvc, jdbc, encoder, json, 7784, RoleCode.CLUB_MANAGER).token();
        String owner = TestAccounts.create(mvc, jdbc, encoder, json, 7785, RoleCode.HORSE_OWNER).token();
        long invoiceId = invoiceWithPricesAndAccount(cm, 7785);
        String txnRef = startVnpay(owner, invoiceId).path("txnRef").stringValue();

        // The sandbox querydr answers 99 with an unverifiable signature: the gateway returns no result.
        org.mockito.Mockito.doReturn(java.util.Optional.empty())
                .when(gatewaySpy).query(org.mockito.ArgumentMatchers.eq(txnRef), org.mockito.ArgumentMatchers.any());
        mvc.perform(post("/api/owner/invoices/" + invoiceId + "/payment-check").header("Authorization", "Bearer " + owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.paymentStatus").value("PENDING"))
                .andExpect(jsonPath("$.data.invoiceStatus").value("ISSUED"))
                .andExpect(jsonPath("$.data.message").value(BillingService.QUERY_UNAVAILABLE_MESSAGE));
        assertEquals("PENDING", jdbc.queryForObject("SELECT status FROM dbo.payment_transaction WHERE txn_ref = ?",
                String.class, txnRef));

        // VNPay answers but the payment is still in progress: the usual "check again later" message stays.
        org.mockito.Mockito.doReturn(java.util.Optional.of(new VnpayGateway.QueryResult("00", "01", 0, "", "", "", "Pending")))
                .when(gatewaySpy).query(org.mockito.ArgumentMatchers.eq(txnRef), org.mockito.ArgumentMatchers.any());
        mvc.perform(post("/api/owner/invoices/" + invoiceId + "/payment-check").header("Authorization", "Bearer " + owner))
                .andExpect(jsonPath("$.data.paymentStatus").value("PENDING"))
                .andExpect(jsonPath("$.data.message").value(org.hamcrest.Matchers.startsWith("Chưa nhận được xác nhận từ VNPay.")));
    }

    @Test
    void aCancelledLinkAndItsReplacementConfirmedAtOnceCountOnePaymentAndFlagTheOther() throws Exception {
        String cm = TestAccounts.create(mvc, jdbc, encoder, json, 7774, RoleCode.CLUB_MANAGER).token();
        String owner = TestAccounts.create(mvc, jdbc, encoder, json, 7775, RoleCode.HORSE_OWNER).token();
        long invoiceId = invoiceWithPricesAndAccount(cm, 7775);
        JsonNode first = startVnpay(owner, invoiceId);
        mvc.perform(post("/api/owner/invoices/" + invoiceId + "/vnpay-payment/cancel").header("Authorization", "Bearer " + owner))
                .andExpect(jsonPath("$.data.paymentStatus").value("CANCELLED"));
        JsonNode second = startVnpay(owner, invoiceId);

        // Line both callbacks up right after their "already paid?" read. Without a shared lock both see "not paid".
        java.util.concurrent.CyclicBarrier barrier = new java.util.concurrent.CyclicBarrier(2);
        org.mockito.Mockito.doAnswer(call -> {
            Object real = call.callRealMethod();
            try {
                barrier.await(300, java.util.concurrent.TimeUnit.MILLISECONDS);
            } catch (java.util.concurrent.TimeoutException | java.util.concurrent.BrokenBarrierException waited) {
                // The other callback is waiting for the invoice lock: nothing to line up with.
            }
            return real;
        }).when(repositorySpy).invoiceHasSuccess(invoiceId);
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            java.util.concurrent.CountDownLatch go = new java.util.concurrent.CountDownLatch(1);
            java.util.List<java.util.concurrent.Future<String>> results = new java.util.ArrayList<>();
            for (JsonNode link : java.util.List.of(first, second)) {
                results.add(pool.submit(() -> {
                    go.await();
                    String body = mvc.perform(ipnRequest(signedIpn(link.path("txnRef").stringValue(),
                            link.path("amount").longValue(), "T" + link.path("txnRef").stringValue().hashCode())))
                            .andReturn().getResponse().getContentAsString();
                    return json.readTree(body).path("RspCode").stringValue();
                }));
            }
            go.countDown();
            for (var result : results) {
                assertEquals("00", result.get(30, java.util.concurrent.TimeUnit.SECONDS));
            }
        } finally {
            pool.shutdownNow();
        }
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM dbo.payment_transaction WHERE invoice_id = ? AND status = 'SUCCESS'",
                Integer.class, invoiceId));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM dbo.payment_transaction WHERE invoice_id = ?"
                + " AND status = 'REFUND_REQUIRED'", Integer.class, invoiceId));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM dbo.notification WHERE recipient_user_id = 7774"
                + " AND notification_type = 'SERVICE_PAYMENT_REFUND_REQUIRED'", Integer.class));
    }

    private long invoiceWithPricesAndAccount(String cm, long ownerId) throws Exception {
        mvc.perform(put("/api/billing/prices").header("Authorization", "Bearer " + cm)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"boardingCareDaily\":100000,\"trainingSession\":300000}"))
                .andExpect(status().isOk());
        mvc.perform(put("/api/billing/payment-account").header("Authorization", "Bearer " + cm)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bankBin\":\"970436\",\"bankName\":\"Vietcombank\",\"accountNo\":\"0123456789\",\"accountName\":\"CLB\"}"))
                .andExpect(status().isOk());
        return seedHorseAndGenerate(cm, YearMonth.now(VN).toString(), ownerId);
    }

    private JsonNode startVnpay(String owner, long invoiceId) throws Exception {
        return json.readTree(mvc.perform(post("/api/owner/invoices/" + invoiceId + "/vnpay-payment")
                        .header("Authorization", "Bearer " + owner).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bankCode\":\"VNBANK\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).path("data");
    }

    private static Map<String, String> signedIpn(String txnRef, long amount, String transactionNo) {
        Map<String, String> ipn = new LinkedHashMap<>();
        ipn.put("vnp_TmnCode", "TESTTMN1");
        ipn.put("vnp_Amount", String.valueOf(amount * 100));
        ipn.put("vnp_BankCode", "NCB");
        ipn.put("vnp_ResponseCode", "00");
        ipn.put("vnp_TransactionNo", transactionNo);
        ipn.put("vnp_TransactionStatus", "00");
        ipn.put("vnp_TxnRef", txnRef);
        ipn.put("vnp_SecureHash", VnpaySigner.sign(SECRET, ipn));
        return ipn;
    }

    /** Horse 7750 stabled since the 1st of the month with 2 COMPLETED sessions (+1 SCHEDULED, not billed). */
    private long seedHorseAndGenerate(String cm, String month, long ownerId) throws Exception {
        LocalDate start = YearMonth.parse(month).atDay(1);
        jdbc.update("INSERT INTO dbo.horse (id, horse_code, name, lifecycle) VALUES (7750, 'RH-7750', 'Bill Horse', 'ACTIVE')");
        jdbc.update("INSERT INTO dbo.stable_box (box_code, yard) VALUES ('BILL-01', 'Test')");
        long box = jdbc.queryForObject("SELECT id FROM dbo.stable_box WHERE box_code = 'BILL-01'", Long.class);
        jdbc.update("""
                INSERT INTO dbo.horse_stable_assignment (horse_id, stable_box_id, assigned_by_user_id, effective_from)
                VALUES (7750, ?, ?, ?)""", box, ownerId, start.minusMonths(2));
        jdbc.update("""
                INSERT INTO dbo.horse_ownership (horse_id, owner_user_id, share_percentage, effective_from, is_managing_owner)
                VALUES (7750, ?, 100, ?, TRUE)""", ownerId, start.minusYears(1));
        jdbc.update("""
                INSERT INTO dbo.horse_training_program (id, horse_id, template_version_id, created_by_user_id, name, status,
                    period_start, period_end, activated_at) VALUES (7750, 7750, 1, ?, 'P', 'ACTIVE', ?, ?, ?)""",
                ownerId, start, start.plusMonths(2), LocalDateTime.now());
        jdbc.update("""
                INSERT INTO dbo.horse_program_phase (id, horse_training_program_id, name, phase_order, start_date, end_date)
                VALUES (7750, 7750, 'Phase', 1, ?, ?)""", start, start.plusMonths(2));
        // Keep completed sessions on today's Vietnam billing date, including runs just after midnight.
        // Subtracting 30 minutes from the wall clock can otherwise put both sessions in yesterday's invoice.
        LocalDateTime doneAt = LocalDate.now(VN).atStartOfDay(VN)
                .withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
        for (int i = 1; i <= 3; i++) {
            jdbc.update("""
                    INSERT INTO dbo.training_session (id, session_code, horse_program_phase_id, created_by_user_id,
                        work_rider_user_id, scheduled_start, actual_start, actual_end, status)
                    VALUES (?, ?, 7750, ?, ?, ?, ?, ?, ?)""", 7750 + i, "S-77" + i, ownerId, ownerId,
                    doneAt.minusHours(1), i < 3 ? doneAt.minusMinutes(40) : null, i < 3 ? doneAt : null,
                    i < 3 ? "COMPLETED" : "SCHEDULED");
        }
        String body = mvc.perform(post("/api/billing/invoices/generate").header("Authorization", "Bearer " + cm)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"billingMonth\":\"" + month + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        for (JsonNode invoice : json.readTree(body).path("data").path("created")) {
            if (invoice.path("horse").path("id").longValue() == 7750) {
                return invoice.path("id").longValue();
            }
        }
        throw new AssertionError("Invoice for horse 7750 was not created: " + body);
    }

    private static org.springframework.test.web.servlet.RequestBuilder ipnRequest(Map<String, String> params) {
        var request = get("/api/payments/vnpay/ipn");
        params.forEach(request::param);
        return request;
    }
    @Test
    void reviewQrMustNotSettleAnInvoiceWhileItsVnpayLinkIsStillPayable() throws Exception {
        String cm = TestAccounts.create(mvc, jdbc, encoder, json, 7781, RoleCode.CLUB_MANAGER).token();
        String owner = TestAccounts.create(mvc, jdbc, encoder, json, 7782, RoleCode.HORSE_OWNER).token();
        mvc.perform(put("/api/billing/prices").header("Authorization", "Bearer " + cm)
                .contentType(MediaType.APPLICATION_JSON).content("{\"boardingCareDaily\":100000,\"trainingSession\":300000}"))
                .andExpect(status().isOk());
        mvc.perform(put("/api/billing/payment-account").header("Authorization", "Bearer " + cm)
                .contentType(MediaType.APPLICATION_JSON).content("{\"bankBin\":\"970436\",\"bankName\":\"Vietcombank\",\"accountNo\":\"0123456789\",\"accountName\":\"TEST CLUB\"}"))
                .andExpect(status().isOk());
        long invoiceId = seedHorseAndGenerate(cm, YearMonth.now(VN).toString(), 7782);
        mvc.perform(post("/api/owner/invoices/" + invoiceId + "/vnpay-payment").header("Authorization", "Bearer " + owner)
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/owner/invoices/" + invoiceId + "/qr-transfer").header("Authorization", "Bearer " + owner))
                .andExpect(status().isConflict());
    }
}
