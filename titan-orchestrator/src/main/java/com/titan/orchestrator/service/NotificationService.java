package com.titan.orchestrator.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.UUID;

/**
 * Sends notifications directly via the communications MCP server,
 * bypassing the LLM agent to ensure reliable delivery.
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${MCP_SERVERS_COMMUNICATIONS:http://localhost:8086}")
    private String commsServerUrl;

    /**
     * Send a maintenance alert notification for an equipment anomaly.
     */
    public void sendMaintenanceAlert(String equipmentId, String facilityId, String probableCause,
                                      double failureProbability, String workOrderId) {
        log.info("Sending maintenance alert for {} at {}", equipmentId, facilityId);

        try {
            String variablesJson = objectMapper.writeValueAsString(Map.of(
                    "equipment_id", equipmentId,
                    "facility_id", facilityId,
                    "probable_cause", probableCause != null ? probableCause : "Unknown",
                    "failure_probability", String.valueOf(Math.round(failureProbability * 100)) + "%",
                    "work_order_id", workOrderId != null ? workOrderId : "N/A"
            ));

            // Call sendNotification via MCP stateless protocol (JSON-RPC over HTTP)
            Map<String, Object> mcpRequest = Map.of(
                    "jsonrpc", "2.0",
                    "id", UUID.randomUUID().toString(),
                    "method", "tools/call",
                    "params", Map.of(
                            "name", "sendNotification",
                            "arguments", Map.of(
                                    "recipientId", facilityId,
                                    "templateType", "MAINTENANCE_ALERT",
                                    "variablesJson", variablesJson
                            )
                    )
            );

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            String body = objectMapper.writeValueAsString(mcpRequest);
            log.debug("MCP request: {}", body);

            HttpEntity<String> entity = new HttpEntity<>(body, headers);
            ResponseEntity<String> response = restTemplate.exchange(
                    commsServerUrl + "/mcp",
                    HttpMethod.POST,
                    entity,
                    String.class
            );

            log.info("Notification response: {} - {}", response.getStatusCode(), response.getBody());

        } catch (Exception e) {
            log.error("Failed to send maintenance alert for {}: {}", equipmentId, e.getMessage(), e);
        }
    }
}
