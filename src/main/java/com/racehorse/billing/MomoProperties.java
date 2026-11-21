package com.racehorse.billing;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.net.URI;

/** Sandbox only; merchant credentials are environment settings and never appear in logs or responses. */
@ConfigurationProperties(prefix = "app.payment.momo")
public record MomoProperties(String partnerCode, String accessKey, String secretKey,
                             String endpoint, String returnUrl, String ipnUrl) {
    public MomoProperties {
        partnerCode = clean(partnerCode); accessKey = clean(accessKey); secretKey = clean(secretKey);
        endpoint = clean(endpoint).isEmpty() ? "https://test-payment.momo.vn" : clean(endpoint);
        returnUrl = clean(returnUrl).isEmpty() ? "http://127.0.0.1:5173/thanh-toan/momo" : clean(returnUrl);
        ipnUrl = clean(ipnUrl).isEmpty() ? "http://127.0.0.1:8080/api/payments/momo/ipn" : clean(ipnUrl);
    }
    public boolean configured() {
        return !partnerCode.isEmpty() && !accessKey.isEmpty() && !secretKey.isEmpty() && sandboxEndpoint();
    }
    private boolean sandboxEndpoint() {
        try {
            URI uri = URI.create(endpoint);
            return !uri.isOpaque() && uri.getHost() != null
                && uri.getUserInfo() == null && uri.getQuery() == null && uri.getFragment() == null
                && (uri.getPath().isEmpty() || uri.getPath().equals("/"))
                && (("https".equals(uri.getScheme()) && "test-payment.momo.vn".equals(uri.getHost())
                        && (uri.getPort() == -1 || uri.getPort() == 443))
                    || ("http".equals(uri.getScheme()) && "127.0.0.1".equals(uri.getHost())
                        && (uri.getPort() == -1 || uri.getPort() > 0 && uri.getPort() <= 65535)));
        } catch (IllegalArgumentException e) { return false; }
    }
    @Override public String toString() { return "MomoProperties[sandbox, credentials=REDACTED]"; }
    private static String clean(String s) { return s == null ? "" : s.trim(); }
}
