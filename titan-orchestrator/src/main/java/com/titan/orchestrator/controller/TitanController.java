package com.titan.orchestrator.controller;

import com.embabel.agent.api.invocation.AgentInvocation;
import com.embabel.agent.core.AgentPlatform;
import com.titan.orchestrator.agent.TitanSensorAgent.ChatQueryResponse;
import com.titan.orchestrator.agent.TitanSensorAgent.HealthAnalysisReport;
import com.titan.orchestrator.model.SensorData.FacilityStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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

    public TitanController(AgentPlatform agentPlatform) {
        this.agentPlatform = agentPlatform;
    }

    /**
     * Natural language chat interface for Titan Manufacturing queries.
     */
    @PostMapping("/chat")
    public ResponseEntity<ChatResponse> chat(@RequestBody ChatRequest request) {
        log.info("Chat request: {}", request.message());

        try {
            // Create invocation targeting ChatQueryResponse goal
            var invocation = AgentInvocation.create(agentPlatform, ChatQueryResponse.class);
            ChatQueryResponse result = invocation.invoke(request.message());

            return ResponseEntity.ok(new ChatResponse(
                true,
                result.response(),
                null
            ));
        } catch (Exception e) {
            log.error("Chat error: {}", e.getMessage(), e);
            return ResponseEntity.ok(new ChatResponse(
                false,
                null,
                e.getMessage()
            ));
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
