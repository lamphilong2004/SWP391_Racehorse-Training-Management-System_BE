package com.racehorse.billing;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * VNPay sandbox merchant settings (DEC-016). The terminal code and hash secret come only from the environment
 * (VNPAY_TMN_CODE, VNPAY_HASH_SECRET); they are never committed, logged or returned by the API.
 */
@ConfigurationProperties(prefix = "app.payment.vnpay")
public record VnpayProperties(
        String tmnCode,
        String hashSecret,
        String payUrl,
        String apiUrl,
        String returnUrl,
        Integer expireMinutes) {

    public VnpayProperties {
        tmnCode = tmnCode == null ? "" : tmnCode.trim();
        hashSecret = hashSecret == null ? "" : hashSecret.trim();
        payUrl = blank(payUrl) ? "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html" : payUrl.trim();
        apiUrl = blank(apiUrl) ? "https://sandbox.vnpayment.vn/merchant_webapi/api/transaction" : apiUrl.trim();
        returnUrl = blank(returnUrl) ? "http://localhost:5173/thanh-toan/vnpay" : returnUrl.trim();
        expireMinutes = expireMinutes == null || expireMinutes < 5 || expireMinutes > 60 ? 15 : expireMinutes;
    }

    public boolean configured() {
        return !tmnCode.isEmpty() && !hashSecret.isEmpty();
    }

    @Override
    public String toString() {
        return "VnpayProperties[tmnCode=" + (tmnCode.isEmpty() ? "" : "***") + ", payUrl=" + payUrl + "]";
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
