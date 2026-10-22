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

    @Test
    void all128RequiredContractOperationsAreMappedAndEnforceAuthentication() throws Exception {
        HttpClient client=HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
        URI base=URI.create("http://localhost:"+port);
        java.util.Map<?,?> api=jsonMapper.readValue(get(client,base.resolve("/v3/api-docs")).body(),java.util.Map.class);
        java.util.Map<?,?> paths=(java.util.Map<?,?>)api.get("paths");
        java.util.Set<String> mapped=new java.util.HashSet<>();
        for(var entry:paths.entrySet()) {
            var methods=(java.util.Map<?,?>)entry.getValue();
            for(Object verb:methods.keySet())mapped.add(verb+" "+entry.getKey().toString().replaceAll("\\{[^}]+}","{}"));
        }
        java.util.Set<String> required=new java.util.HashSet<>();
        for(String file:java.util.List.of("flow1-horse.openapi.yaml","flow2-training.openapi.yaml","flow3-medical.openapi.yaml","shared.openapi.yaml","local-demo-extensions.openapi.yaml")) {
            String path=null;
            for(String line:java.nio.file.Files.readAllLines(java.nio.file.Path.of("docs/api",file))) {
                if(line.matches("  /api/[^:]+:"))path=line.strip().replaceAll(":$","");
                if(path==null||!line.matches("    (get|post|put|patch|delete):"))continue;
                String verb=line.strip().replace(":","");
                String key=verb+" "+path.replaceAll("\\{[^}]+}","{}");
                assertTrue(required.add(key),"Duplicate required operation "+key);
                assertTrue(mapped.contains(key),"Missing implementation mapping "+key);
                String concrete=path.replaceAll("\\{[^}]+}","999999999");
                boolean publicAuth=java.util.Set.of("/api/auth/login","/api/auth/register","/api/auth/forgot-password","/api/auth/reset-password").contains(path);
                var body=java.util.Set.of("get","delete").contains(verb)?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString("{}");
                var request=HttpRequest.newBuilder(base.resolve(concrete)).header("Content-Type","application/json")
                        .method(verb.toUpperCase(java.util.Locale.ROOT),body).build();
                var response=client.send(request,HttpResponse.BodyHandlers.ofString());
                assertEquals(publicAuth?400:401,response.statusCode(),key+" authentication boundary");
                assertTrue(!jsonMapper.readTree(response.body()).get("success").asBoolean(),key+" error envelope");
            }
        }
        assertEquals(128,required.size(),"Update the approved inventory explicitly, never silently drop operations.");
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
