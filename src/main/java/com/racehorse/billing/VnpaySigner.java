package com.racehorse.billing;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.StringJoiner;
import java.util.TreeMap;

/**
 * VNPay 2.1.0 signing, following the official PHP/Java samples (sandbox.vnpayment.vn/apis/docs/thanh-toan-pay/pay.html):
 * fields sorted by name, empty values skipped, values URL-encoded (US-ASCII, space as '+'), joined with '&',
 * HMAC-SHA512 with the merchant secret, lowercase hex.
 */
public final class VnpaySigner {

    private VnpaySigner() {
    }

    /** The exact string that is signed: sorted vnp_* fields except vnp_SecureHash / vnp_SecureHashType. */
    public static String hashData(Map<String, String> fields) {
        StringJoiner joiner = new StringJoiner("&");
        for (Map.Entry<String, String> entry : new TreeMap<>(fields).entrySet()) {
            String name = entry.getKey();
            String value = entry.getValue();
            if (value == null || value.isEmpty() || !name.startsWith("vnp_")
                    || name.equals("vnp_SecureHash") || name.equals("vnp_SecureHashType")) {
                continue;
            }
            joiner.add(encode(name) + "=" + encode(value));
        }
        return joiner.toString();
    }

    public static String sign(String secret, Map<String, String> fields) {
        return hmacSha512(secret, hashData(fields));
    }

    /** Payment URL: the same encoded query that was signed, plus vnp_SecureHash. */
    public static String paymentUrl(String payUrl, String secret, Map<String, String> fields) {
        String query = hashData(fields);
        return payUrl + "?" + query + "&vnp_SecureHash=" + hmacSha512(secret, query);
    }

    /** Constant-time check of the vnp_SecureHash that VNPay sent on the return URL or the IPN. */
    public static boolean verify(String secret, Map<String, String> fields) {
        String received = fields.get("vnp_SecureHash");
        if (received == null || received.isBlank()) {
            return false;
        }
        return constantTimeEquals(sign(secret, fields), received.trim().toLowerCase());
    }

    public static String hmacSha512(String secret, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA512");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException error) {
            throw new IllegalStateException("HMAC-SHA512 is not available.", error);
        }
    }

    public static boolean constantTimeEquals(String expected, String actual) {
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII),
                actual.toLowerCase().getBytes(StandardCharsets.US_ASCII));
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.US_ASCII);
    }
}
