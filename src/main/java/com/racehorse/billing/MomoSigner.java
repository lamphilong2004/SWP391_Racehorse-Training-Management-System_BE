package com.racehorse.billing;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.StringJoiner;

/** Exact raw field order from MoMo ATM onetime / query docs; do not URL-encode the signing input. */
public final class MomoSigner {
    private MomoSigner() {}
    public static String data(String accessKey, Map<String, String> fields, String... names) {
        StringJoiner raw = new StringJoiner("&"); raw.add("accessKey=" + accessKey);
        for (String name : names) raw.add(name + "=" + fields.getOrDefault(name, ""));
        return raw.toString();
    }
    public static String create(String accessKey, String secret, Map<String, String> f) {
        return hmac(secret, data(accessKey, f, "amount", "extraData", "ipnUrl", "orderId", "orderInfo",
                "partnerCode", "redirectUrl", "requestId", "requestType"));
    }
    public static String query(String accessKey, String secret, Map<String, String> f) {
        return hmac(secret, data(accessKey, f, "orderId", "partnerCode", "requestId"));
    }
    public static String notification(String accessKey, String secret, Map<String, String> f) {
        return hmac(secret, data(accessKey, f, "amount", "extraData", "message", "orderId", "orderInfo", "orderType",
                "partnerCode", "payType", "requestId", "responseTime", "resultCode", "transId"));
    }
    public static boolean verify(String accessKey, String secret, Map<String, String> f) {
        String signature = f.get("signature");
        if (signature == null || !signature.matches("[a-fA-F0-9]{64}")) return false;
        return MessageDigest.isEqual(notification(accessKey, secret, f).getBytes(StandardCharsets.US_ASCII),
                signature.toLowerCase(java.util.Locale.ROOT).getBytes(StandardCharsets.US_ASCII));
    }
    public static String hmac(String secret, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) { throw new IllegalStateException("HMAC-SHA256 unavailable", e); }
    }
}
