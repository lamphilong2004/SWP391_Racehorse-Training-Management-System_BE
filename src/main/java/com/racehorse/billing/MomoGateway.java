package com.racehorse.billing;

import org.springframework.stereotype.Component;
import org.springframework.core.env.Environment;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.Arrays;

/** MoMo ATM sandbox. Redirects disabled; responses are bound to the exact merchant/order/request/amount. */
@Component
public class MomoGateway {
    public record Created(String payUrl) {}
    public record Outcome(int resultCode, String transactionNo, String payType) {}
    private final MomoProperties properties;
    private final JsonMapper json;
    private final Environment environment;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
    public MomoGateway(MomoProperties properties, JsonMapper json, Environment environment) {
        this.properties = properties; this.json = json; this.environment = environment;
    }
    public MomoProperties properties() { return properties; }
    /** An HTTP fixture can only be used by an isolated test context, never by a normal demo/production run. */
    public boolean configured() {
        if (!properties.configured()) return false;
        return "https".equals(URI.create(properties.endpoint()).getScheme())
                || Arrays.equals(environment.getActiveProfiles(), new String[]{"test"});
    }
    public boolean verify(Map<String, String> fields) {
        return configured() && MomoSigner.verify(properties.accessKey(), properties.secretKey(), fields);
    }
    public Optional<Created> create(String orderId, long amount, String orderInfo) {
        if (!configured()) return Optional.empty();
        Map<String, String> f = new LinkedHashMap<>();
        f.put("partnerCode", properties.partnerCode()); f.put("requestId", orderId); f.put("orderId", orderId);
        f.put("amount", String.valueOf(amount)); f.put("orderInfo", orderInfo);
        f.put("redirectUrl", properties.returnUrl()); f.put("ipnUrl", properties.ipnUrl());
        f.put("requestType", "payWithATM"); f.put("extraData", ""); f.put("lang", "vi");
        f.put("signature", MomoSigner.create(properties.accessKey(), properties.secretKey(), f));
        Optional<JsonNode> response = post("create", f);
        if (response.isEmpty()) return Optional.empty();
        JsonNode node = response.get();
        if (!matches(node, orderId, orderId, amount) || !"0".equals(text(node, "resultCode"))) return Optional.empty();
        String url = text(node, "payUrl");
        try {
            URI uri = URI.create(url);
            if (!"https".equals(uri.getScheme()) || !"test-payment.momo.vn".equals(uri.getHost())
                    || uri.getUserInfo() != null || (uri.getPort() != -1 && uri.getPort() != 443)) return Optional.empty();
        } catch (IllegalArgumentException e) { return Optional.empty(); }
        return Optional.of(new Created(url));
    }
    public Optional<Outcome> query(String orderId, long amount) {
        if (!configured()) return Optional.empty();
        String requestId = UUID.randomUUID().toString();
        Map<String, String> f = new LinkedHashMap<>();
        f.put("partnerCode", properties.partnerCode()); f.put("orderId", orderId); f.put("requestId", requestId); f.put("lang", "vi");
        f.put("signature", MomoSigner.query(properties.accessKey(), properties.secretKey(), f));
        Optional<JsonNode> response = post("query", f);
        if (response.isEmpty() || !matches(response.get(), orderId, requestId, amount)) return Optional.empty();
        JsonNode node = response.get();
        try {
            int code = Integer.parseInt(text(node, "resultCode"));
            String transId = text(node, "transId");
            if (code == 0 && !positiveTransaction(transId)) return Optional.empty();
            // Official query response has no signature field. Trust comes from direct HTTPS to the configured sandbox,
            // plus exact response correlation; never call this with data supplied by the browser.
            return Optional.of(new Outcome(code, transId, text(node, "payType")));
        } catch (NumberFormatException e) { return Optional.empty(); }
    }
    static boolean positiveTransaction(String value) { return value != null && value.matches("[1-9][0-9]{0,18}"); }
    private boolean matches(JsonNode n, String order, String request, long amount) {
        return properties.partnerCode().equals(text(n, "partnerCode")) && order.equals(text(n, "orderId"))
            && request.equals(text(n, "requestId")) && String.valueOf(amount).equals(text(n, "amount"));
    }
    private Optional<JsonNode> post(String operation, Map<String, String> fields) {
        try {
            Map<String, Object> body = new LinkedHashMap<>(fields);
            if (fields.containsKey("amount")) body.put("amount", Long.parseLong(fields.get("amount")));
            String base = properties.endpoint().replaceAll("/$", "");
            HttpRequest request = HttpRequest.newBuilder(URI.create(base + "/v2/gateway/api/" + operation))
                .timeout(Duration.ofSeconds(30)).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200 || response.body().length() > 65536) return Optional.empty();
            return Optional.of(json.readTree(response.body()));
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); return Optional.empty(); }
        catch (Exception e) { return Optional.empty(); } // No request, signature, key, or gateway body in logs.
    }
    private static String text(JsonNode n, String key) {
        JsonNode v = n == null ? null : n.get(key); return v == null || v.isNull() ? "" : v.asString();
    }
}
