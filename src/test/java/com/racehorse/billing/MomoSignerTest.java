package com.racehorse.billing;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class MomoSignerTest {
    @Test void fixedCreateAndQueryVectorsUseOfficialAtmFieldOrderAndRawValues() {
        Map<String,String> f=new LinkedHashMap<>();
        f.put("amount","10000");f.put("extraData","");f.put("ipnUrl","http://127.0.0.1:8080/ipn");
        f.put("orderId","TEST1");f.put("orderInfo","Demo ATM");f.put("partnerCode","MOMO_TEST");
        f.put("redirectUrl","http://127.0.0.1:5173/return");f.put("requestId","TEST1");f.put("requestType","payWithATM");
        // Independently computed with Node crypto; whitespace must not become %20/+ and empty extraData stays present.
        assertEquals("771fad0910b48aabea77b5079d30d46e2ec0066f541d60ddc3b6c45a3e99876d",
            MomoSigner.create("test-only-access","test-only-secret",f));
        f.put("requestId","QUERY1");
        assertEquals("de25d862373c1ad036eccbf0336fd4f9bc30aca7d12042fef4ad6b32e5afde24",
            MomoSigner.query("test-only-access","test-only-secret",f));
    }
    @Test void validNotificationSignatureRejectsTamperedAmountAndMalformedHash() {
        Map<String,String> f=new LinkedHashMap<>(Map.of("amount","50000","orderId","TEST1","resultCode","0"));
        f.put("signature",MomoSigner.notification("access","secret",f));
        assertTrue(MomoSigner.verify("access","secret",f));
        f.put("amount","1");assertFalse(MomoSigner.verify("access","secret",f));
        f.put("signature","0");assertFalse(MomoSigner.verify("access","secret",f));
    }
    @Test void liveMoneyEndpointIsNeverEnabledAndKeysAreRedacted() {
        var p=new MomoProperties("partner","access","secret","https://payment.momo.vn",null,null);
        assertFalse(p.configured());assertFalse(p.toString().contains("secret"));
        assertFalse(new MomoProperties("","","",null,null,null).configured());
    }
}
