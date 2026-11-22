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
    /** Outcome of a create call. Only {@link Uncertain} may leave a remote order behind that nobody was shown. */
    public sealed interface CreateResult permits Created, Rejected, Uncertain {}
    /** MoMo created the order and returned its sandbox checkout address. */
    public record Created(String payUrl) implements CreateResult {}
    /** MoMo refused (HTTP 4xx, or an answer with resultCode != 0): no checkout address exists, nothing can be paid. */
    public record Rejected(String code) implements CreateResult {}
    /** No usable answer (timeout, network, 5xx, unreadable or mismatched success): an order may exist at MoMo. */
    public record Uncertain() implements CreateResult {}
    private record Reply(int status, String body) {}
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
    public CreateResult create(String orderId, long amount, String orderInfo) {
        if (!configured()) return new Rejected("NOCFG");
        Map<String, String> f = new LinkedHashMap<>();
        f.put("partnerCode", properties.partnerCode()); f.put("requestId", orderId); f.put("orderId", orderId);
        f.put("amount", String.valueOf(amount)); f.put("orderInfo", orderInfo);
        f.put("redirectUrl", properties.returnUrl()); f.put("ipnUrl", properties.ipnUrl());
        f.put("requestType", "payWithATM"); f.put("extraData", ""); f.put("lang", "vi");
        f.put("signature", MomoSigner.create(properties.accessKey(), properties.secretKey(), f));
        Optional<Reply> reply = send("create", f);
        if (reply.isEmpty()) return new Uncertain();
        int status = reply.get().status();
        // A 4xx means MoMo refused the request itself (403 for credentials it does not accept): no order was created.
        if (status >= 400 && status < 500) return new Rejected(String.valueOf(status));
        Optional<JsonNode> parsed = status == 200 ? parse(reply.get().body()) : Optional.empty();
        if (parsed.isEmpty()) return new Uncertain();
        JsonNode node = parsed.get();
        String resultCode = text(node, "resultCode");
        // Any answer other than 0 carries no checkout address, so the owner cannot pay this order: it is a refusal.
        if (resultCode.matches("[0-9]{1,5}") && !"0".equals(resultCode)) return new Rejected(resultCode);
        if (!"0".equals(resultCode) || !matches(node, orderId, orderId, amount)) return new Uncertain();
        String url = text(node, "payUrl");
        try {
            URI uri = URI.create(url);
            if (!"https".equals(uri.getScheme()) || !"test-payment.momo.vn".equals(uri.getHost())
                    || uri.getUserInfo() != null || (uri.getPort() != -1 && uri.getPort() != 443)) return new Uncertain();
        } catch (IllegalArgumentException e) { return new Uncertain(); }
        return new Created(url);
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
        return send(operation, fields).filter(reply -> reply.status() == 200).flatMap(reply -> parse(reply.body()));
    }
    private Optional<JsonNode> parse(String body) {
        if (body == null || body.length() > 65536) return Optional.empty();
        try { return Optional.ofNullable(json.readTree(body)); } catch (Exception e) { return Optional.empty(); }
    }
    /** HTTP status and body, or empty when no answer arrived (timeout, network error). */
    private Optional<Reply> send(String operation, Map<String, String> fields) {
        try {
            Map<String, Object> body = new LinkedHashMap<>(fields);
            if (fields.containsKey("amount")) body.put("amount", Long.parseLong(fields.get("amount")));
            String base = properties.endpoint().replaceAll("/$", "");
            HttpRequest request = HttpRequest.newBuilder(URI.create(base + "/v2/gateway/api/" + operation))
                .timeout(Duration.ofSeconds(30)).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            return Optional.of(new Reply(response.statusCode(), response.body()));
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); return Optional.empty(); }
        catch (Exception e) { return Optional.empty(); } // No request, signature, key, or gateway body in logs.
    }
    private static String text(JsonNode n, String key) {
        JsonNode v = n == null ? null : n.get(key); return v == null || v.isNull() ? "" : v.asString();
    }
}
