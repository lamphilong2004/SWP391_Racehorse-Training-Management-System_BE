package com.racehorse.staging;

import com.racehorse.TestJwtProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestPropertySource(properties = "springdoc.swagger-ui.enabled=true")
class SwaggerRuntimeSmokeTest {

    @LocalServerPort
    private int port;

    @Autowired
    private JsonMapper jsonMapper;

    @DynamicPropertySource
    static void jwtProperties(DynamicPropertyRegistry registry) {
        TestJwtProperties.register(registry);
    }

    @Test
    void swaggerUiAndImplementedFlowOneRoutesAreAvailableOverHttp() throws Exception {
        HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();
        URI baseUri = URI.create("http://localhost:" + port);
        HttpResponse<String> ui = get(client, baseUri.resolve("/swagger-ui.html"));
        assertEquals(200, ui.statusCode());
        assertTrue(ui.body().contains("swagger-ui"));

        HttpResponse<String> docs = get(client, baseUri.resolve("/v3/api-docs"));
        assertEquals(200, docs.statusCode());
        JsonNode api = jsonMapper.readTree(docs.body());
        assertEquals("updateHorse", api.at("/paths/~1api~1horses~1{id}/put/operationId").asText());
        assertEquals("recordBaselineExamination",
                api.at("/paths/~1api~1horses~1{horseId}~1baseline-examinations/post/operationId").asText());
        assertEquals("recordHorseWeight",
                api.at("/paths/~1api~1horses~1{horseId}~1weights/post/operationId").asText());
        assertEquals("listMyHorses", api.at("/paths/~1api~1owner~1horses/get/operationId").asText());
        assertEquals("getHorseTimeline",
                api.at("/paths/~1api~1horses~1{id}~1timeline/get/operationId").asText());
        assertEquals("getMyHorseTrainingSummary",
                api.at("/paths/~1api~1owner~1horses~1{id}~1training-summary/get/operationId").asText());
        assertOperation(api, "/api/training-programs/{id}/status", "patch", "setProgramStatus");
        assertOperation(api, "/api/training-sessions/{id}/metrics", "post", "submitMetricSample");
        assertOperation(api, "/api/simulator/sessions/{id}/start", "post", "startSimulator");
        assertOperation(api, "/api/simulator/sessions/{id}/stop", "post", "stopSimulator");
        assertOperation(api, "/api/monitoring-alerts/{id}/acknowledge", "post", "acknowledgeAlert");
        assertOperation(api, "/api/training-sessions/{id}/evaluation", "post", "submitEvaluation");
        assertOperation(api, "/api/training-sessions/{id}/result", "get", "getSessionResult");
        assertOperation(api, "/api/horses/{id}/performance", "get", "getHorsePerformance");
        assertOperation(api, "/api/medical/cases", "get", "listMedicalCases");
        assertOperation(api, "/api/medical/cases", "post", "createMedicalCase");
        assertOperation(api, "/api/medical/cases/{caseId}", "get", "getMedicalCase");
        assertOperation(api, "/api/medical/cases/{caseId}/examinations", "post", "createMedicalExamination");
        assertOperation(api, "/api/medical/cases/{caseId}/injuries", "post", "createInjury");
        assertOperation(api, "/api/medical/cases/{caseId}/re-examinations", "post", "createReexamination");
        assertOperation(api, "/api/medical/cases/{caseId}/close", "post", "closeMedicalCase");
        assertOperation(api, "/api/medical/cases/{caseId}/treatment-plan", "get", "getTreatmentPlan");
        assertOperation(api, "/api/medical/cases/{caseId}/treatment-plan", "put", "putTreatmentPlan");
        assertOperation(api, "/api/medical/cases/{caseId}/treatment-tasks", "get", "listTreatmentCaseTasks");
        assertOperation(api, "/api/groom/treatment-tasks", "get", "getMyTreatmentTasks");
        assertOperation(api, "/api/groom/treatment-tasks/{taskId}/complete", "post", "completeTreatmentTask");
        assertOperation(api, "/api/training-programs/{id}/adjustment-requests", "post", "createAdjustmentRequest");
        assertOperation(api, "/api/adjustment-requests", "get", "listAdjustmentRequests");
        assertOperation(api, "/api/adjustment-requests/{id}/approve", "post", "approveAdjustmentRequest");
        assertOperation(api, "/api/adjustment-requests/{id}/reject", "post", "rejectAdjustmentRequest");
    }

    private void assertOperation(JsonNode api, String path, String method, String operationId) {
        assertEquals(operationId,
                api.at("/paths/" + path.replace("/", "~1") + "/" + method + "/operationId").asText(),
                path + " " + method);
    }

    private HttpResponse<String> get(HttpClient client, URI uri) throws Exception {
        return client.send(HttpRequest.newBuilder(uri).GET().build(), HttpResponse.BodyHandlers.ofString());
    }
}
