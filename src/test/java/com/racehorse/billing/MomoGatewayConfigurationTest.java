package com.racehorse.billing;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import tools.jackson.databind.json.JsonMapper;
import java.util.Map;
import java.util.LinkedHashMap;
import static org.junit.jupiter.api.Assertions.*;

/** Prevent an explicit HTTP test fixture from becoming a runtime payment provider. No network is needed. */
class MomoGatewayConfigurationTest {
    private Map<String, String> signedCallback() {
        Map<String, String> fields = new LinkedHashMap<>(Map.of("orderId", "fixture-order", "amount", "10000", "resultCode", "0"));
        fields.put("signature", MomoSigner.notification("fixture-access", "fixture-secret", fields));
        return fields;
    }

    private MomoGateway gateway(String endpoint, String... profiles) {
        var environment = new MockEnvironment();
        environment.setActiveProfiles(profiles);
        return new MomoGateway(new MomoProperties("fixture-partner", "fixture-access", "fixture-secret",
                endpoint, null, null), JsonMapper.builder().build(), environment);
    }

    @Test void httpFixtureIsUnavailableInNormalOrMixedRuntimeProfiles() {
        for (String[] profiles : new String[][]{{}, {"dev"}, {"production"}, {"test", "production"}, {"dev", "test"}}) {
            var gateway = gateway("http://127.0.0.1:1", profiles);
            assertFalse(gateway.configured());
            assertTrue(gateway.create("unreachable-fixture", 10000, "test").isEmpty());
            assertTrue(gateway.query("unreachable-fixture", 10000).isEmpty());
            assertFalse(gateway.verify(signedCallback()));
        }
    }

    @Test void isolatedTestContextCanStillUseTheExplicitLocalHttpFixture() {
        var gateway = gateway("http://127.0.0.1:12345", "test");
        assertTrue(gateway.configured());
        assertTrue(gateway.verify(signedCallback()));
    }

    @Test void officialHttpsSandboxWorksInNormalAndTestProfiles() {
        for (String[] profiles : new String[][]{{}, {"dev"}, {"production"}, {"test"}}) {
            assertTrue(gateway("https://test-payment.momo.vn", profiles).configured());
            assertTrue(gateway("https://test-payment.momo.vn:443/", profiles).configured());
        }
    }

    @Test void invalidOrUnsupportedEndpointsStayUnavailableInsteadOfThrowing() {
        for (String endpoint : new String[]{"https:test-payment.momo.vn", "https://payment.momo.vn",
                "https://test-payment.momo.vn:444", "http://127.0.0.1:0", "http://127.0.0.1:65536",
                "https://test-payment.momo.vn/other", "https://test-payment.momo.vn?x=1", "not a URI"}) {
            assertFalse(gateway(endpoint, "test").configured(), endpoint);
        }
    }
}
