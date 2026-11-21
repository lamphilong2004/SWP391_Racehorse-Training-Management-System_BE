package com.racehorse.billing;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VnpaySignerTest {

    private static final String SECRET = "TESTSECRETKEY0123456789";

    @Test
    void hashDataSortsEncodesAndSkipsEmptyAndHashFields() {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("vnp_TxnRef", "HD7T20261120");
        fields.put("vnp_Amount", "150000000");
        fields.put("vnp_OrderInfo", "Thanh toan hoa don HD 202611 0001");
        fields.put("vnp_ReturnUrl", "http://localhost:5173/thanh-toan/vnpay");
        fields.put("vnp_BankCode", "");
        fields.put("vnp_SecureHash", "ignored");
        fields.put("vnp_SecureHashType", "HmacSHA512");
        fields.put("other", "ignored");

        assertEquals("vnp_Amount=150000000"
                        + "&vnp_OrderInfo=Thanh+toan+hoa+don+HD+202611+0001"
                        + "&vnp_ReturnUrl=http%3A%2F%2Flocalhost%3A5173%2Fthanh-toan%2Fvnpay"
                        + "&vnp_TxnRef=HD7T20261120",
                VnpaySigner.hashData(fields));
    }

    @Test
    void hmacSha512MatchesKnownVector() {
        // RFC 4231 test case 2: key "Jefe", data "what do ya want for nothing?".
        assertEquals("164b7a7bfcf819e2e395fbe73b56e0a387bd64222e831fd610270cd7ea2505549758bf75c05a994a6d034f65f8f0e6fdcaeab1a34d4a6b4b636e070a38bce737",
                VnpaySigner.hmacSha512("Jefe", "what do ya want for nothing?"));
    }

    @Test
    void paymentUrlCarriesTheSignatureOfItsOwnQuery() {
        Map<String, String> fields = Map.of("vnp_Amount", "1000000", "vnp_Command", "pay", "vnp_TxnRef", "A1");
        String url = VnpaySigner.paymentUrl("https://sandbox.vnpayment.vn/paymentv2/vpcpay.html", SECRET, fields);
        String query = "vnp_Amount=1000000&vnp_Command=pay&vnp_TxnRef=A1";
        assertEquals("https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?" + query + "&vnp_SecureHash="
                + VnpaySigner.hmacSha512(SECRET, query), url);
    }

    @Test
    void verifyAcceptsAGenuineReturnAndRejectsTampering() {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("vnp_Amount", "150000000");
        fields.put("vnp_ResponseCode", "00");
        fields.put("vnp_TransactionStatus", "00");
        fields.put("vnp_TxnRef", "HD7T20261120");
        fields.put("vnp_SecureHash", VnpaySigner.sign(SECRET, fields).toUpperCase());
        assertTrue(VnpaySigner.verify(SECRET, fields));

        for (String tampered : List.of("vnp_Amount", "vnp_ResponseCode")) {
            Map<String, String> copy = new LinkedHashMap<>(fields);
            copy.put(tampered, "99");
            assertFalse(VnpaySigner.verify(SECRET, copy), tampered);
        }
        Map<String, String> unsigned = new LinkedHashMap<>(fields);
        unsigned.remove("vnp_SecureHash");
        assertFalse(VnpaySigner.verify(SECRET, unsigned));
        assertFalse(VnpaySigner.verify("another-secret", fields));
    }
}
