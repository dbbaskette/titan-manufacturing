package com.titan.orchestrator.controller;

import com.embabel.agent.api.invocation.AgentInvocation;
import com.embabel.agent.core.AgentPlatform;
import com.titan.orchestrator.agent.TitanSensorAgent.ChatQueryResponse;
import com.titan.orchestrator.agent.TitanSensorAgent.HealthAnalysisReport;
import com.titan.orchestrator.agent.TitanMaintenanceAgent.MaintenanceQueryResponse;
import com.titan.orchestrator.model.SensorData.FacilityStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * REST API for Titan Manufacturing orchestrator.
 * Provides endpoints for natural language queries and structured operations.
 */
@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class TitanController {

    private static final Logger log = LoggerFactory.getLogger(TitanController.class);

    private final AgentPlatform agentPlatform;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${titan.maintenance.url:http://localhost:8082}")
    private String maintenanceUrl;

    @Value("${titan.generator.url:http://localhost:8090}")
    private String generatorUrl;

    @Value("${titan.order.url:http://localhost:8085}")
    private String orderUrl;

    public TitanController(AgentPlatform agentPlatform) {
        this.agentPlatform = agentPlatform;
    }

    // ── ML Pipeline Endpoints (proxy to maintenance-mcp-server) ─────────────

    @GetMapping("/ml/model")
    public ResponseEntity<String> getMlModel() {
        return proxyGet("/ml/model");
    }

    @GetMapping("/ml/predictions")
    public ResponseEntity<String> getMlPredictions() {
        return proxyGet("/ml/predictions");
    }

    @GetMapping("/ml/gemfire/status")
    public ResponseEntity<String> getMlGemFireStatus() {
        return proxyGet("/ml/gemfire/status");
    }

    @GetMapping("/ml/pmml")
    public ResponseEntity<String> getMlPmml() {
        return proxyGet("/ml/pmml");
    }

    @PostMapping("/ml/retrain")
    public ResponseEntity<String> mlRetrain() {
        return proxyPost("/ml/retrain");
    }

    @PostMapping("/ml/deploy")
    public ResponseEntity<String> mlDeploy() {
        return proxyPost("/ml/deploy");
    }

    @PostMapping("/ml/predictions/reset")
    public ResponseEntity<String> mlPredictionsReset() {
        return proxyPost("/ml/predictions/reset");
    }

    @PostMapping("/ml/training/generate")
    public ResponseEntity<String> mlTrainingGenerate(
            @RequestParam(defaultValue = "500") int normalCount,
            @RequestParam(defaultValue = "100") int failureCountPerPattern
    ) {
        return proxyPost("/ml/training/generate?normalCount=" + normalCount + "&failureCountPerPattern=" + failureCountPerPattern);
    }

    @GetMapping("/ml/training/stats")
    public ResponseEntity<String> mlTrainingStats() {
        return proxyGet("/ml/training/stats");
    }

    private ResponseEntity<String> proxyGet(String path) {
        try {
            String body = restTemplate.getForObject(maintenanceUrl + path, String.class);
            return ResponseEntity.ok().header("Content-Type", "application/json").body(body);
        } catch (Exception e) {
            log.error("ML proxy GET {} failed: {}", path, e.getMessage());
            return ResponseEntity.internalServerError().body("{\"error\":\"" + e.getMessage() + "\"}");
        }
    }

    private ResponseEntity<String> proxyPost(String path) {
        try {
            String body = restTemplate.postForObject(maintenanceUrl + path, null, String.class);
            return ResponseEntity.ok().header("Content-Type", "application/json").body(body);
        } catch (Exception e) {
            log.error("ML proxy POST {} failed: {}", path, e.getMessage());
            return ResponseEntity.internalServerError().body("{\"error\":\"" + e.getMessage() + "\"}");
        }
    }

    // ── Generator Endpoints (proxy to sensor-data-generator) ───────────────

    @GetMapping("/generator/equipment")
    public ResponseEntity<String> getGeneratorEquipment() {
        try {
            String body = restTemplate.getForObject(generatorUrl + "/api/generator/equipment", String.class);
            return ResponseEntity.ok().header("Content-Type", "application/json").body(body);
        } catch (Exception e) {
            log.error("Generator proxy GET /equipment failed: {}", e.getMessage());
            return ResponseEntity.internalServerError().body("{\"error\":\"" + e.getMessage() + "\"}");
        }
    }

    // ── Order Endpoints (proxy to order-mcp-server) ─────────────────────────

    @GetMapping("/orders")
    public ResponseEntity<String> getOrders() {
        return proxyOrderGet("/orders");
    }

    @GetMapping("/orders/counts")
    public ResponseEntity<String> getOrderCounts() {
        return proxyOrderGet("/orders/counts");
    }

    @GetMapping("/orders/{orderId}")
    public ResponseEntity<String> getOrderDetails(@PathVariable String orderId) {
        return proxyOrderGet("/orders/" + orderId);
    }

    @GetMapping("/orders/{orderId}/events")
    public ResponseEntity<String> getOrderEvents(@PathVariable String orderId) {
        return proxyOrderGet("/orders/" + orderId + "/events");
    }

    @PatchMapping("/orders/{orderId}/status")
    public ResponseEntity<String> updateOrderStatus(@PathVariable String orderId, @RequestBody String body) {
        try {
            org.springframework.http.HttpEntity<String> entity = new org.springframework.http.HttpEntity<>(
                body, createJsonHeaders());
            ResponseEntity<String> response = restTemplate.exchange(
                orderUrl + "/orders/" + orderId + "/status",
                org.springframework.http.HttpMethod.PATCH,
                entity,
                String.class);
            return ResponseEntity.ok().header("Content-Type", "application/json").body(response.getBody());
        } catch (Exception e) {
            log.error("Order proxy PATCH /orders/{}/status failed: {}", orderId, e.getMessage());
            return ResponseEntity.internalServerError().body("{\"error\":\"" + e.getMessage() + "\"}");
        }
    }

    private ResponseEntity<String> proxyOrderGet(String path) {
        try {
            String body = restTemplate.getForObject(orderUrl + path, String.class);
            return ResponseEntity.ok().header("Content-Type", "application/json").body(body);
        } catch (Exception e) {
            log.error("Order proxy GET {} failed: {}", path, e.getMessage());
            return ResponseEntity.internalServerError().body("{\"error\":\"" + e.getMessage() + "\"}");
        }
    }

    private org.springframework.http.HttpHeaders createJsonHeaders() {
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        return headers;
    }

    /**
     * Natural language chat interface for Titan Manufacturing queries.
     */
    @PostMapping("/chat")
    public ResponseEntity<ChatResponse> chat(@RequestBody ChatRequest request) {
        log.info("Chat request: {}", request.message());

        try {
            String msg = request.message().toLowerCase();
            String response;

            // Route to the appropriate agent based on query content
            if (msg.contains("failure") || msg.contains("predict") || msg.contains("maintenance")
                    || msg.contains("rul") || msg.contains("remaining useful life")
                    || msg.contains("work order") || msg.contains("schedule maintenance")) {
                log.info("Routing to maintenance agent");
                var invocation = AgentInvocation.create(agentPlatform, MaintenanceQueryResponse.class);
                MaintenanceQueryResponse result = invocation.invoke(request.message());
                response = result.response();
            } else {
                log.info("Routing to sensor agent");
                var invocation = AgentInvocation.create(agentPlatform, ChatQueryResponse.class);
                ChatQueryResponse result = invocation.invoke(request.message());
                response = result.response();
            }

            return ResponseEntity.ok(new ChatResponse(true, response, null));
        } catch (Exception e) {
            log.error("Chat error: {}", e.getMessage(), e);
            return ResponseEntity.ok(new ChatResponse(false, null, e.getMessage()));
        }
    }

    /**
     * Analyze specific equipment health.
     */
    @GetMapping("/equipment/{equipmentId}/health")
    public ResponseEntity<HealthAnalysisReport> analyzeEquipmentHealth(@PathVariable String equipmentId) {
        log.info("Health analysis request for: {}", equipmentId);

        try {
            var invocation = AgentInvocation.create(agentPlatform, HealthAnalysisReport.class);
            HealthAnalysisReport result = invocation.invoke(equipmentId);

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Health analysis error: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Get facility status overview.
     */
    @GetMapping("/facilities/{facilityId}/status")
    public ResponseEntity<FacilityStatus> getFacilityStatus(@PathVariable String facilityId) {
        log.info("Facility status request for: {}", facilityId);

        try {
            var invocation = AgentInvocation.create(agentPlatform, FacilityStatus.class);
            FacilityStatus result = invocation.invoke(facilityId);

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Facility status error: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * List connected MCP agents.
     */
    @GetMapping("/agents")
    public ResponseEntity<Map<String, Object>> listAgents() {
        return ResponseEntity.ok(Map.of(
            "agents", Map.of(
                "sensor", Map.of("url", "http://sensor-mcp-server:8081", "status", "configured"),
                "maintenance", Map.of("url", "http://maintenance-mcp-server:8082", "status", "pending"),
                "inventory", Map.of("url", "http://inventory-mcp-server:8083", "status", "pending"),
                "logistics", Map.of("url", "http://logistics-mcp-server:8084", "status", "pending"),
                "order", Map.of("url", "http://order-mcp-server:8085", "status", "pending"),
                "communications", Map.of("url", "http://communications-mcp-server:8086", "status", "pending"),
                "governance", Map.of("url", "http://governance-mcp-server:8087", "status", "pending")
            ),
            "orchestrator", "Embabel Agent Framework",
            "version", "1.0.0"
        ));
    }

    /**
     * List Titan facilities.
     */
    @GetMapping("/facilities")
    public ResponseEntity<Map<String, Object>> listFacilities() {
        return ResponseEntity.ok(Map.of(
            "facilities", java.util.List.of(
                Map.of("id", "PHX", "name", "Phoenix Precision", "city", "Phoenix", "country", "USA"),
                Map.of("id", "DET", "name", "Detroit Dynamics", "city", "Detroit", "country", "USA"),
                Map.of("id", "ATL", "name", "Atlanta Aerospace", "city", "Atlanta", "country", "USA"),
                Map.of("id", "DAL", "name", "Dallas Defense", "city", "Dallas", "country", "USA"),
                Map.of("id", "MUC", "name", "Munich Motors", "city", "Munich", "country", "Germany"),
                Map.of("id", "MAN", "name", "Manchester Manufacturing", "city", "Manchester", "country", "UK"),
                Map.of("id", "LYN", "name", "Lyon Precision", "city", "Lyon", "country", "France"),
                Map.of("id", "SHA", "name", "Shanghai Heavy Industries", "city", "Shanghai", "country", "China"),
                Map.of("id", "TYO", "name", "Tokyo Tech", "city", "Tokyo", "country", "Japan"),
                Map.of("id", "SEO", "name", "Seoul Systems", "city", "Seoul", "country", "South Korea"),
                Map.of("id", "SYD", "name", "Sydney Aerospace", "city", "Sydney", "country", "Australia"),
                Map.of("id", "MEX", "name", "Mexico City Manufacturing", "city", "Mexico City", "country", "Mexico")
            )
        ));
    }

    /**
     * Health check endpoint.
     */
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("OK");
    }

    // Request/Response records

    public record ChatRequest(String message) {}
    public record ChatResponse(boolean success, String response, String error) {}
}
