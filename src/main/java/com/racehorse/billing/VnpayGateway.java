package com.racehorse.billing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * VNPay sandbox calls: build the signed payment URL and query a transaction status (querydr) server to server.
 * querydr is how a local run confirms a payment, because VNPay can only call an IPN URL that is public with SSL.
 */
@Component
public class VnpayGateway {

    public static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");
    public static final DateTimeFormatter VNP_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final Logger log = LoggerFactory.getLogger(VnpayGateway.class);
    private static final String VERSION = "2.1.0";
    private static final char[] ALPHANUMERIC = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();

    /** Result of a querydr call whose response signature was verified. */
    public record QueryResult(String responseCode, String transactionStatus, long amountTimes100, String transactionNo,
                              String bankCode, String payDate, String message) {
    }

    private final VnpayProperties properties;
    private final JsonMapper json;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
    private final SecureRandom random = new SecureRandom();

    public VnpayGateway(VnpayProperties properties, JsonMapper json) {
        this.properties = properties;
        this.json = json;
    }

    public VnpayProperties properties() {
        return properties;
    }

    public String paymentUrl(String txnRef, long amountVnd, String orderInfo, String bankCode, String clientIp,
                             LocalDateTime createdVn, LocalDateTime expiresVn) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("vnp_Version", VERSION);
        fields.put("vnp_Command", "pay");
        fields.put("vnp_TmnCode", properties.tmnCode());
        fields.put("vnp_Amount", String.valueOf(Math.multiplyExact(amountVnd, 100L)));
        fields.put("vnp_CurrCode", "VND");
        fields.put("vnp_TxnRef", txnRef);
        fields.put("vnp_OrderInfo", orderInfo);
        fields.put("vnp_OrderType", "other");
        fields.put("vnp_Locale", "vn");
        fields.put("vnp_ReturnUrl", properties.returnUrl());
        fields.put("vnp_IpAddr", clientIp);
        fields.put("vnp_CreateDate", VNP_TIME.format(createdVn));
        fields.put("vnp_ExpireDate", VNP_TIME.format(expiresVn));
        if (bankCode != null && !bankCode.isBlank()) {
            fields.put("vnp_BankCode", bankCode);
        }
        return VnpaySigner.paymentUrl(properties.payUrl(), properties.hashSecret(), fields);
    }

    public boolean verify(Map<String, String> fields) {
        return properties.configured() && VnpaySigner.verify(properties.hashSecret(), fields);
    }

    /**
     * querydr (sandbox.vnpayment.vn/apis/docs/truy-van-hoan-tien/querydr&refund.html). Empty when the call fails or the
     * response signature does not match; the caller then leaves the payment PENDING (never assumes success).
     */
    public Optional<QueryResult> query(String txnRef, String transactionDate) {
        if (!properties.configured()) {
            return Optional.empty();
        }
        String requestId = randomId(20);
        String createDate = VNP_TIME.format(LocalDateTime.now(VN));
        String ip = "127.0.0.1";
        String orderInfo = "Kiem tra giao dich " + txnRef;
        String data = String.join("|", requestId, VERSION, "querydr", properties.tmnCode(), txnRef,
                transactionDate, createDate, ip, orderInfo);
        Map<String, String> body = new LinkedHashMap<>();
        body.put("vnp_RequestId", requestId);
        body.put("vnp_Version", VERSION);
        body.put("vnp_Command", "querydr");
        body.put("vnp_TmnCode", properties.tmnCode());
        body.put("vnp_TxnRef", txnRef);
        body.put("vnp_OrderInfo", orderInfo);
        body.put("vnp_TransactionDate", transactionDate);
        body.put("vnp_CreateDate", createDate);
        body.put("vnp_IpAddr", ip);
        body.put("vnp_SecureHash", VnpaySigner.hmacSha512(properties.hashSecret(), data));
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(properties.apiUrl()))
                    .timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode node = json.readTree(response.body());
            String responseCode = text(node, "vnp_ResponseCode");
            String signed = String.join("|", text(node, "vnp_ResponseId"), text(node, "vnp_Command"), responseCode,
                    text(node, "vnp_Message"), text(node, "vnp_TmnCode"), text(node, "vnp_TxnRef"),
                    text(node, "vnp_Amount"), text(node, "vnp_BankCode"), text(node, "vnp_PayDate"),
                    text(node, "vnp_TransactionNo"), text(node, "vnp_TransactionType"),
                    text(node, "vnp_TransactionStatus"), text(node, "vnp_OrderInfo"),
                    text(node, "vnp_PromotionCode"), text(node, "vnp_PromotionAmount"));
            String expected = VnpaySigner.hmacSha512(properties.hashSecret(), signed);
            String received = text(node, "vnp_SecureHash");
            if (received.isEmpty() || !VnpaySigner.constantTimeEquals(expected, received)) {
                log.warn("VNPay querydr response for {} rejected: responseCode={}, signature mismatch", txnRef, responseCode);
                return Optional.empty();
            }
            String amount = text(node, "vnp_Amount");
            return Optional.of(new QueryResult(responseCode, text(node, "vnp_TransactionStatus"),
                    amount.isEmpty() ? -1 : Long.parseLong(amount), text(node, "vnp_TransactionNo"),
                    text(node, "vnp_BankCode"), text(node, "vnp_PayDate"), text(node, "vnp_Message")));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (Exception error) {
            log.warn("VNPay querydr for {} failed: {}", txnRef, error.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    public String randomId(int length) {
        StringBuilder builder = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            builder.append(ALPHANUMERIC[random.nextInt(ALPHANUMERIC.length)]);
        }
        return builder.toString();
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || value.isNull() ? "" : value.asString();
    }
}
