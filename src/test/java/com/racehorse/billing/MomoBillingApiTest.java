package com.racehorse.billing;

import com.racehorse.TestJwtProperties;
import com.racehorse.auth.RoleCode;
import com.racehorse.testsupport.TestAccounts;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
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
import tools.jackson.databind.json.JsonMapper;
import java.net.InetSocketAddress;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** H2 + an explicit local HTTP fixture; no merchant credentials, real gateway payment or demo database involved. */
@SpringBootTest
@ActiveProfiles("test")
class MomoBillingApiTest {
    static final String KEY="test-only-access", SECRET="test-only-secret", PARTNER="MOMO_TEST";
    static volatile String mode="pending", createMode="ok";
    static final AtomicInteger createCalls = new AtomicInteger();
    static final HttpServer server = startFixture();
    @Autowired WebApplicationContext context;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder encoder;
    @Autowired JsonMapper json;
    MockMvc mvc;
    String owner, stranger, cm;
    long invoice;
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r) {
        TestJwtProperties.register(r);
        r.add("app.payment.momo.partner-code",()->PARTNER);
        r.add("app.payment.momo.access-key",()->KEY);
        r.add("app.payment.momo.secret-key",()->SECRET);
        r.add("app.payment.momo.endpoint",()->"http://127.0.0.1:"+server.getAddress().getPort());
        r.add("app.payment.vnpay.tmn-code",()->"TESTTMN1");
        r.add("app.payment.vnpay.hash-secret",()->"test-only-vnpay");
        r.add("app.payment.vnpay.api-url",()->"http://127.0.0.1:9/query");
    }
    @BeforeEach void setup() throws Exception {
        mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build(); clean();
        mode="pending"; createMode="ok"; createCalls.set(0);
        cm=TestAccounts.create(mvc,jdbc,encoder,json,7801,RoleCode.CLUB_MANAGER).token();
        owner=TestAccounts.create(mvc,jdbc,encoder,json,7802,RoleCode.HORSE_OWNER).token();
        stranger=TestAccounts.create(mvc,jdbc,encoder,json,7803,RoleCode.HORSE_OWNER).token();
        jdbc.update("INSERT INTO dbo.horse(id,horse_code,name,lifecycle) VALUES(7800,'RH-MOMO-TEST','MoMo fixture','ACTIVE')");
        LocalDate d=LocalDate.now();
        jdbc.update("""
            INSERT INTO dbo.service_invoice(invoice_code,horse_id,owner_user_id,billing_month,period_start,period_end,
              total_amount,status,due_date,issued_by_user_id) VALUES('HD-MOMO-TEST',7800,7802,?,?,?,50000,'ISSUED',?,7801)
            """, d.withDayOfMonth(1),d.withDayOfMonth(1),d,d.plusDays(7));
        invoice=jdbc.queryForObject("SELECT id FROM dbo.service_invoice WHERE invoice_code='HD-MOMO-TEST'",Long.class);
    }
    @AfterEach void clean() {
        jdbc.update("DELETE FROM dbo.payment_transaction");jdbc.update("DELETE FROM dbo.service_invoice_line");
        jdbc.update("DELETE FROM dbo.service_invoice");
        jdbc.update("DELETE FROM dbo.notification WHERE recipient_user_id BETWEEN 7800 AND 7809");
        jdbc.update("DELETE FROM dbo.audit_log WHERE actor_user_id BETWEEN 7800 AND 7809 OR target_type='SERVICE_INVOICE'");
        jdbc.update("DELETE FROM dbo.horse WHERE id=7800");
        jdbc.update("DELETE FROM dbo.user_role WHERE user_id BETWEEN 7800 AND 7809");
        jdbc.update("DELETE FROM dbo.app_user WHERE id BETWEEN 7800 AND 7809");
    }
    String create() throws Exception {
        String body=mvc.perform(post("/api/owner/invoices/"+invoice+"/momo-payment").header("Authorization","Bearer "+owner))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.data.payUrl").value(org.hamcrest.Matchers.startsWith("https://test-payment.momo.vn/")))
            .andReturn().getResponse().getContentAsString();
        return json.readTree(body).path("data").path("txnRef").asString();
    }
    Map<String,String> callback(String order) {
        Map<String,String> f=new LinkedHashMap<>();
        f.put("partnerCode",PARTNER);f.put("orderId",order);f.put("requestId",order);f.put("amount","50000");
        f.put("orderInfo","Demo");f.put("orderType","momo_wallet");f.put("transId","123456789");
        f.put("resultCode","0");f.put("message","Successful.");f.put("payType","napas");
        f.put("responseTime","1795230000000");f.put("extraData",""); sign(f);return f;
    }
    void sign(Map<String,String> f){f.put("signature",MomoSigner.notification(KEY,SECRET,f));}
    void invoiceStillUnpaid(){assertEquals("ISSUED",jdbc.queryForObject("SELECT status FROM dbo.service_invoice WHERE id=?",String.class,invoice));}
    void setReceivingAccount() throws Exception {
        mvc.perform(put("/api/billing/payment-account").header("Authorization","Bearer "+cm)
            .contentType(MediaType.APPLICATION_JSON).content("{\"bankBin\":\"970436\",\"bankName\":\"Test Bank\",\"accountNo\":\"000000\",\"accountName\":\"TEST CLUB\"}"))
            .andExpect(status().isOk());
    }
    @Test void signedReturnNeedsIndependentQueryAndQueryBindsAllEchoedIdentifiers() throws Exception {
        String order=create(); Map<String,String> f=callback(order);
        for(String result:List.of("pending","amount","order","request","partner","missing-code","missing-trans")) {
            mode=result;
            mvc.perform(post("/api/payments/momo/return").header("Authorization","Bearer "+owner)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("params",f))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.paymentStatus").value("PENDING"));
            invoiceStillUnpaid();
        }
        mode="success";
        mvc.perform(post("/api/payments/momo/return").header("Authorization","Bearer "+owner)
            .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("params",f))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.invoiceStatus").value("PAID"));
        assertEquals("MOMO_QUERY",jdbc.queryForObject("SELECT confirmed_via FROM dbo.payment_transaction WHERE txn_ref=?",String.class,order));
        mvc.perform(post("/api/owner/invoices/"+invoice+"/momo-payment").header("Authorization","Bearer "+owner)).andExpect(status().isConflict());
    }
    @Test void tamperingAndOtherOwnersCannotSettleOrPay() throws Exception {
        String order=create();mode="success";
        for(String field:List.of("signature","amount","partnerCode","requestId")) {
            Map<String,String> f=callback(order);f.put(field,"tampered");
            if(!field.equals("signature"))sign(f);
            mvc.perform(post("/api/payments/momo/return").header("Authorization","Bearer "+owner)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("params",f))))
                .andExpect(status().isBadRequest());invoiceStillUnpaid();
        }
        mvc.perform(post("/api/owner/invoices/"+invoice+"/momo-payment").header("Authorization","Bearer "+stranger)).andExpect(status().isNotFound());
        mvc.perform(post("/api/owner/invoices/"+invoice+"/momo-payment").header("Authorization","Bearer "+cm)).andExpect(status().isForbidden());
        mvc.perform(post("/api/payments/momo/return").header("Authorization","Bearer "+stranger)
            .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("params",callback(order)))))
            .andExpect(status().isNotFound());invoiceStillUnpaid();
    }
    @Test void signedPublicIpnIsIdempotentAndInvalidSignatureCannotPay() throws Exception {
        String order=create();Map<String,String> f=callback(order);f.put("signature","0".repeat(64));
        mvc.perform(post("/api/payments/momo/ipn").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(f)))
            .andExpect(status().isBadRequest());invoiceStillUnpaid();sign(f);
        for(int i=0;i<2;i++) mvc.perform(post("/api/payments/momo/ipn").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(f)))
            .andExpect(status().isNoContent());
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM dbo.payment_transaction WHERE status='SUCCESS'",Integer.class));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM dbo.notification WHERE recipient_user_id=7802 AND notification_type='SERVICE_INVOICE_PAID'",Integer.class));
    }
    @Test void uncertainCreateRetainsPendingAndBlocksEveryOtherMethodEvenAfterLocalTimerExpires() throws Exception {
        createMode="broken";
        mvc.perform(post("/api/owner/invoices/"+invoice+"/momo-payment").header("Authorization","Bearer "+owner))
            .andExpect(status().isConflict());
        invoiceStillUnpaid();
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM dbo.payment_transaction WHERE status='PENDING'",Integer.class));
        jdbc.update("UPDATE dbo.payment_transaction SET expires_at=DATEADD('DAY',-1,CURRENT_TIMESTAMP)");
        for(String method:List.of("momo-payment","vnpay-payment","qr-transfer"))
            mvc.perform(post("/api/owner/invoices/"+invoice+"/"+method).header("Authorization","Bearer "+owner))
                .andExpect(status().isConflict());
    }
    @Test void concurrentRequestsCreateOnlyOneRemoteOrder() throws Exception {
        try(var pool=Executors.newVirtualThreadPerTaskExecutor()){
            CountDownLatch start=new CountDownLatch(1);
            Callable<Integer> request=()->{start.await();return mvc.perform(post("/api/owner/invoices/"+invoice+"/momo-payment")
                .header("Authorization","Bearer "+owner)).andReturn().getResponse().getStatus();};
            var a=pool.submit(request);var b=pool.submit(request);start.countDown();
            var codes=new ArrayList<>(List.of(a.get(20,TimeUnit.SECONDS),b.get(20,TimeUnit.SECONDS)));Collections.sort(codes);
            assertEquals(List.of(201,409),codes);
        }
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM dbo.payment_transaction",Integer.class));
    }
    @Test void concurrentVnpayAndMomoCannotOpenTwoPayableTransactions() throws Exception {
        try (var pool=Executors.newVirtualThreadPerTaskExecutor()) {
            CountDownLatch start=new CountDownLatch(1);
            var momo=pool.submit(()->{start.await();return mvc.perform(post("/api/owner/invoices/"+invoice+"/momo-payment")
                .header("Authorization","Bearer "+owner)).andReturn().getResponse().getStatus();});
            var vnpay=pool.submit(()->{start.await();return mvc.perform(post("/api/owner/invoices/"+invoice+"/vnpay-payment")
                .header("Authorization","Bearer "+owner)).andReturn().getResponse().getStatus();});
            start.countDown();
            var statuses=new ArrayList<>(List.of(momo.get(20,TimeUnit.SECONDS),vnpay.get(20,TimeUnit.SECONDS)));
            Collections.sort(statuses);assertEquals(List.of(201,409),statuses);
        }
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM dbo.payment_transaction WHERE status='PENDING'",Integer.class));
    }
    @Test void openVnpayAndPaidVietQrRejectMomoWithoutCallingTheGateway() throws Exception {
        mvc.perform(post("/api/owner/invoices/"+invoice+"/vnpay-payment").header("Authorization","Bearer "+owner))
            .andExpect(status().isCreated());
        mvc.perform(post("/api/owner/invoices/"+invoice+"/momo-payment").header("Authorization","Bearer "+owner))
            .andExpect(status().isConflict());
        invoiceStillUnpaid();assertEquals(0,createCalls.get());
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM dbo.payment_transaction WHERE provider='MOMO'",Integer.class));
        // Fixture-only expiry: the existing contract permits VietQR after the VNPay link expires.
        jdbc.update("UPDATE dbo.payment_transaction SET expires_at=DATEADD('DAY',-1,CURRENT_TIMESTAMP)");
        setReceivingAccount();
        mvc.perform(post("/api/owner/invoices/"+invoice+"/qr-transfer").header("Authorization","Bearer "+owner))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.invoiceStatus").value("PAID"));
        mvc.perform(post("/api/owner/invoices/"+invoice+"/momo-payment").header("Authorization","Bearer "+owner))
            .andExpect(status().isConflict());
        assertEquals(0,createCalls.get());
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM dbo.payment_transaction WHERE provider='MOMO'",Integer.class));
    }
    @Test void concurrentVietQrAndMomoSettleOrOpenOnlyOneTransaction() throws Exception {
        setReceivingAccount();
        try(var pool=Executors.newVirtualThreadPerTaskExecutor()) {
            CountDownLatch start=new CountDownLatch(1);
            var momo=pool.submit(()->{start.await();return mvc.perform(post("/api/owner/invoices/"+invoice+"/momo-payment")
                .header("Authorization","Bearer "+owner)).andReturn().getResponse().getStatus();});
            var qr=pool.submit(()->{start.await();return mvc.perform(post("/api/owner/invoices/"+invoice+"/qr-transfer")
                .header("Authorization","Bearer "+owner)).andReturn().getResponse().getStatus();});
            start.countDown();
            var codes=new ArrayList<>(List.of(momo.get(20,TimeUnit.SECONDS),qr.get(20,TimeUnit.SECONDS)));
            Collections.sort(codes);
            assertTrue(codes.equals(List.of(200,409)) || codes.equals(List.of(201,409)),codes.toString());
            assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM dbo.payment_transaction",Integer.class));
            if(codes.getFirst()==200) {
                assertEquals("PAID",jdbc.queryForObject("SELECT status FROM dbo.service_invoice WHERE id=?",String.class,invoice));
                assertEquals("VIETQR",jdbc.queryForObject("SELECT provider FROM dbo.payment_transaction",String.class));
                assertEquals(0,createCalls.get());
            } else {
                invoiceStillUnpaid();
                assertEquals("MOMO",jdbc.queryForObject("SELECT provider FROM dbo.payment_transaction",String.class));
                assertEquals(1,createCalls.get());
            }
        }
    }
    @Test void vnpayCancellationCannotCancelOrUnlockAnOutstandingMomoOrder() throws Exception {
        create();
        mvc.perform(post("/api/owner/invoices/"+invoice+"/vnpay-payment/cancel").header("Authorization","Bearer "+owner))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.paymentStatus").value("NO_PENDING_PAYMENT"));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM dbo.payment_transaction WHERE provider='MOMO' AND status='PENDING'",Integer.class));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM dbo.audit_log WHERE action='SERVICE_INVOICE_PAYMENT_CANCELLED' AND target_id=?",Integer.class,invoice));
        mvc.perform(post("/api/owner/invoices/"+invoice+"/vnpay-payment").header("Authorization","Bearer "+owner))
            .andExpect(status().isConflict());
        invoiceStillUnpaid();
    }
    @Test void failedMomoHistoryDoesNotClaimConfirmationOfALaterVietQrPayment() throws Exception {
        String order=create();Map<String,String> f=callback(order);f.put("resultCode","1006");sign(f);
        mvc.perform(post("/api/payments/momo/ipn").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(f)))
            .andExpect(status().isNoContent());
        mvc.perform(put("/api/billing/payment-account").header("Authorization","Bearer "+cm)
            .contentType(MediaType.APPLICATION_JSON).content("{\"bankBin\":\"970436\",\"bankName\":\"Test Bank\",\"accountNo\":\"000000\",\"accountName\":\"TEST CLUB\"}"))
            .andExpect(status().isOk());
        mvc.perform(post("/api/owner/invoices/"+invoice+"/qr-transfer").header("Authorization","Bearer "+owner))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.invoiceStatus").value("PAID"));
        mvc.perform(post("/api/owner/invoices/"+invoice+"/payment-check").header("Authorization","Bearer "+owner))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.invoiceStatus").value("PAID"))
            .andExpect(jsonPath("$.data.paymentStatus").value("NO_PENDING_PAYMENT"))
            .andExpect(jsonPath("$.data.message").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("MoMo"))));
    }
    @Test void verifiedCancellationAllowsRetryButPendingCodesNeverSettle() throws Exception {
        String order=create();Map<String,String> f=callback(order);
        for(String code:List.of("1000","7000","7002","9000")){
            f.put("resultCode",code);sign(f);
            mvc.perform(post("/api/payments/momo/ipn").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(f)))
                .andExpect(status().isNoContent());invoiceStillUnpaid();
        }
        f.put("resultCode","1006");sign(f);
        mvc.perform(post("/api/payments/momo/ipn").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(f)))
            .andExpect(status().isNoContent());invoiceStillUnpaid();create();
    }
    String paymentField(String column, String order) {
        return jdbc.queryForObject("SELECT " + column + " FROM dbo.payment_transaction WHERE txn_ref=?", String.class, order);
    }
    String onlyMomoOrder() {
        return jdbc.queryForObject("SELECT txn_ref FROM dbo.payment_transaction WHERE provider='MOMO'", String.class);
    }
    void cancelMomo(String expectedStatus) throws Exception {
        mvc.perform(post("/api/owner/invoices/"+invoice+"/momo-payment/cancel").header("Authorization","Bearer "+owner))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.paymentStatus").value(expectedStatus));
    }
    @Test void aRefusedCreateIsRecordedFailedAndLeavesTheInvoicePayableByAnyMethod() throws Exception {
        for (String refusal : List.of("forbidden", "refused")) {
            jdbc.update("DELETE FROM dbo.payment_transaction");
            createMode = refusal;
            mvc.perform(post("/api/owner/invoices/"+invoice+"/momo-payment").header("Authorization","Bearer "+owner))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.message").value("MOMO_CREATE_REJECTED"));
            String order = onlyMomoOrder();
            assertEquals("FAILED", paymentField("status", order), refusal);
            assertEquals(refusal.equals("forbidden") ? "403" : "13", paymentField("response_code", order));
            invoiceStillUnpaid();
            // Nothing is open: VNPay can start at once (a definite refusal never locks the invoice).
            mvc.perform(post("/api/owner/invoices/"+invoice+"/vnpay-payment").header("Authorization","Bearer "+owner))
                .andExpect(status().isCreated());
        }
    }
    @Test void ownerCancelsAnUncertainMomoOrderAndCanPayAnotherWayAtOnce() throws Exception {
        createMode = "broken";
        mvc.perform(post("/api/owner/invoices/"+invoice+"/momo-payment").header("Authorization","Bearer "+owner))
            .andExpect(status().isConflict());
        String order = onlyMomoOrder();
        assertEquals("PENDING", paymentField("status", order));
        mode = "pending";
        cancelMomo("CANCELLED");
        assertEquals("FAILED/OWNER", paymentField("status || '/' || confirmed_via", order));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM dbo.audit_log WHERE action='SERVICE_INVOICE_PAYMENT_CANCELLED' AND target_id=?",
            Integer.class, invoice));
        invoiceStillUnpaid();
        mvc.perform(post("/api/owner/invoices/"+invoice+"/vnpay-payment").header("Authorization","Bearer "+owner))
            .andExpect(status().isCreated());
        // The VNPay cancel action never touches MoMo orders, and the MoMo one never touches VNPay links.
        cancelMomo("NO_PENDING_PAYMENT");
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM dbo.payment_transaction WHERE provider='VNPAY' AND status='PENDING'", Integer.class));
    }
    @Test void cancellingFindsTheMomoOrderAlreadyPaidAndRecordsIt() throws Exception {
        String order = create();
        mode = "success";
        cancelMomo("SUCCESS");
        assertEquals("SUCCESS/MOMO_QUERY", paymentField("status || '/' || confirmed_via", order));
        assertEquals("PAID", jdbc.queryForObject("SELECT status FROM dbo.service_invoice WHERE id=?", String.class, invoice));
    }
    @Test void aCancelledMomoOrderThatMomoLaterReportsPaidIsFoundByPaymentCheck() throws Exception {
        String order = create();
        mode = "pending";
        cancelMomo("CANCELLED");
        mode = "success";
        mvc.perform(post("/api/owner/invoices/"+invoice+"/payment-check").header("Authorization","Bearer "+owner))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.paymentStatus").value("SUCCESS"))
            .andExpect(jsonPath("$.data.invoiceStatus").value("PAID"));
        assertEquals("SUCCESS/MOMO_QUERY", paymentField("status || '/' || confirmed_via", order));
    }
    @Test void aCancelledMomoOrderPaidAfterTheOwnerSettledByVietQrIsFlaggedForRefund() throws Exception {
        String order = create();
        mode = "pending";
        cancelMomo("CANCELLED");
        setReceivingAccount();
        mvc.perform(post("/api/owner/invoices/"+invoice+"/qr-transfer").header("Authorization","Bearer "+owner))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.invoiceStatus").value("PAID"));
        mvc.perform(post("/api/payments/momo/ipn").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(callback(order))))
            .andExpect(status().isNoContent());
        assertEquals("REFUND_REQUIRED", paymentField("status", order));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM dbo.payment_transaction WHERE invoice_id=? AND status='SUCCESS'", Integer.class, invoice));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM dbo.notification WHERE recipient_user_id=7801"
            + " AND notification_type='SERVICE_PAYMENT_REFUND_REQUIRED'", Integer.class));
    }
    @Test void aCancelledMomoOrderThatMomoReportsFailedStaysCancelledOnReturn() throws Exception {
        String order = create();
        mode = "pending";
        cancelMomo("CANCELLED");
        Map<String,String> f = callback(order); f.put("resultCode","1006"); sign(f);
        mvc.perform(post("/api/payments/momo/return").header("Authorization","Bearer "+owner)
            .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("params",f))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.paymentStatus").value("FAILED"));
        assertEquals("FAILED/OWNER", paymentField("status || '/' || confirmed_via", order));
        invoiceStillUnpaid();
    }
    @Test void aRefusalAboutAnotherOrderKeepsTheReservationPending() throws Exception {
        createMode = "refused-unrelated";
        mvc.perform(post("/api/owner/invoices/"+invoice+"/momo-payment").header("Authorization","Bearer "+owner))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.error.message").value("MOMO_CREATE_UNCONFIRMED"));
        assertEquals("PENDING", paymentField("status", onlyMomoOrder()));
        mvc.perform(post("/api/owner/invoices/"+invoice+"/vnpay-payment").header("Authorization","Bearer "+owner))
            .andExpect(status().isConflict());
    }
    @Test void anUncertainOrderThatMomoDoesNotKnowIsFailedByPaymentCheck() throws Exception {
        createMode = "broken";
        mvc.perform(post("/api/owner/invoices/"+invoice+"/momo-payment").header("Authorization","Bearer "+owner))
            .andExpect(status().isConflict());
        String order = onlyMomoOrder();
        // A 42 about another order proves nothing: still pending.
        mode = "notfound-unrelated";
        mvc.perform(post("/api/owner/invoices/"+invoice+"/payment-check").header("Authorization","Bearer "+owner))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.paymentStatus").value("PENDING"));
        assertEquals("PENDING", paymentField("status", order));
        // A correlated 42 (no such order at MoMo, amount omitted): the order can never be paid, so it fails and unlocks.
        mode = "notfound";
        mvc.perform(post("/api/owner/invoices/"+invoice+"/payment-check").header("Authorization","Bearer "+owner))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.paymentStatus").value("FAILED"));
        assertEquals("FAILED/42", paymentField("status || '/' || response_code", order));
        invoiceStillUnpaid();
        mvc.perform(post("/api/owner/invoices/"+invoice+"/vnpay-payment").header("Authorization","Bearer "+owner))
            .andExpect(status().isCreated());
    }
    @Test void aLateQueryAfterAVietQrSettlementReportsTheRefundNotAnOrdinarySuccess() throws Exception {
        String order = create();
        mode = "pending";
        cancelMomo("CANCELLED");
        setReceivingAccount();
        mvc.perform(post("/api/owner/invoices/"+invoice+"/qr-transfer").header("Authorization","Bearer "+owner))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.invoiceStatus").value("PAID"));
        mode = "success";
        mvc.perform(post("/api/owner/invoices/"+invoice+"/payment-check").header("Authorization","Bearer "+owner))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.paymentStatus").value("REFUND_REQUIRED"))
            .andExpect(jsonPath("$.data.message").value(org.hamcrest.Matchers.containsString("hoàn tiền")));
        assertEquals("REFUND_REQUIRED/MOMO_QUERY", paymentField("status || '/' || confirmed_via", order));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM dbo.notification WHERE recipient_user_id=7801"
            + " AND notification_type='SERVICE_PAYMENT_REFUND_REQUIRED'", Integer.class));
    }
    static HttpServer startFixture() {
        try {
            HttpServer s=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
            s.createContext("/v2/gateway/api/",e->{
                JsonMapper j=JsonMapper.builder().build();var n=j.readTree(e.getRequestBody());
                Map<String,Object> body=new LinkedHashMap<>();
                for(String k:List.of("partnerCode","orderId","requestId","amount"))body.put(k,n.path(k).asString());
                boolean create=e.getRequestURI().getPath().endsWith("create");
                if(create){
                    createCalls.incrementAndGet();
                    body.put("resultCode",0);body.put("payUrl","https://test-payment.momo.vn/v2/gateway/pay?t=fixture");
                    if(createMode.equals("broken")){e.sendResponseHeaders(500,-1);e.close();return;}
                    if(createMode.equals("forbidden")){e.sendResponseHeaders(403,-1);e.close();return;}
                    if(createMode.equals("refused")){body.put("resultCode",13);body.remove("payUrl");}
                    if(createMode.equals("refused-unrelated")){body.put("resultCode",13);body.remove("payUrl");body.put("orderId","UNRELATED-ORDER");}
                } else {
                    body.put("amount",50000);body.put("resultCode",mode.equals("pending")?1000:0);
                    body.put("transId",123456789);body.put("payType","napas");
                    switch(mode){case "amount"->body.put("amount",1);case "order"->body.put("orderId","WRONG");
                        case "request"->body.put("requestId","WRONG");case "partner"->body.put("partnerCode","WRONG");
                        case "missing-code"->body.remove("resultCode");case "missing-trans"->body.remove("transId");
                        case "notfound"->{body.put("resultCode",42);body.remove("amount");body.remove("transId");}
                        case "notfound-unrelated"->{body.put("resultCode",42);body.put("orderId","UNRELATED-ORDER");}default->{}}
                }
                byte[] data=j.writeValueAsBytes(body);e.getResponseHeaders().add("Content-Type","application/json");
                e.sendResponseHeaders(200,data.length);e.getResponseBody().write(data);e.close();
            });s.start();return s;
        }catch(Exception e){throw new IllegalStateException(e);}
    }
}
