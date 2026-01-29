package com.titan.orchestrator.controller;

import com.embabel.agent.api.invocation.AgentInvocation;
import com.embabel.agent.core.AgentPlatform;
import com.titan.orchestrator.model.AnomalyEvent;
import com.titan.orchestrator.model.AnomalyEvent.CriticalAnomalyInput;
import com.titan.orchestrator.model.AnomalyResponse.CriticalAnomalyResponse;
import com.titan.orchestrator.service.AutomatedActionService;
import com.titan.orchestrator.service.NotificationService;
import com.titan.orchestrator.service.RecommendationService;

import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST API for maintenance recommendations and automated actions.
 * Provides endpoints for the dashboard to display and approve/dismiss recommendations.
 */
@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class RecommendationController {

    private static final Logger log = LoggerFactory.getLogger(RecommendationController.class);

    private final RecommendationService recommendationService;
    private final AutomatedActionService automatedActionService;
    private final AgentPlatform agentPlatform;
    private final NotificationService notificationService;

    public RecommendationController(
            RecommendationService recommendationService,
            AutomatedActionService automatedActionService,
            AgentPlatform agentPlatform,
            NotificationService notificationService) {
        this.recommendationService = recommendationService;
        this.automatedActionService = automatedActionService;
        this.agentPlatform = agentPlatform;
        this.notificationService = notificationService;
    }

    // ── Recommendations Endpoints ────────────────────────────────────────────

    /**
     * Get all pending recommendations awaiting human approval.
     */
    @GetMapping("/recommendations")
    public ResponseEntity<List<Map<String, Object>>> getPendingRecommendations() {
        log.debug("Fetching pending recommendations");
        List<Map<String, Object>> recommendations = recommendationService.getPendingRecommendations();
        return ResponseEntity.ok(recommendations);
    }

    /**
     * Get a specific recommendation by ID.
     */
    @GetMapping("/recommendations/{recommendationId}")
    public ResponseEntity<Map<String, Object>> getRecommendation(
            @PathVariable String recommendationId) {
        try {
            Map<String, Object> recommendation = recommendationService.getRecommendation(recommendationId);
            return ResponseEntity.ok(recommendation);
        } catch (Exception e) {
            log.error("Recommendation not found: {}", recommendationId);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Approve a recommendation - triggers the full maintenance workflow.
     * This invokes the Embabel agent to schedule maintenance using the reserved parts.
     */
    @PostMapping("/recommendations/{recommendationId}/approve")
    public ResponseEntity<ApprovalResponse> approveRecommendation(
            @PathVariable String recommendationId,
            @RequestBody(required = false) ApprovalRequest request) {

        String approvedBy = (request != null && request.approvedBy() != null)
                ? request.approvedBy() : "dashboard-user";

        log.info("Approving recommendation {} by {}", recommendationId, approvedBy);

        try {
            // Get the recommendation details
            Map<String, Object> rec = recommendationService.getRecommendation(recommendationId);

            if (!"PENDING".equals(rec.get("status"))) {
                return ResponseEntity.badRequest()
                        .body(new ApprovalResponse(false, null, "Recommendation is not in PENDING status"));
            }

            // Mark as approved
            recommendationService.approve(recommendationId, approvedBy);

            // Build an AnomalyEvent from the stored recommendation so the GOAP planner
            // can route it through handleCriticalAnomaly (which schedules maintenance + notifies)
            log.info(">>> Invoking Embabel agent to schedule approved maintenance...");

            double failProb = rec.get("failure_probability") != null
                    ? ((Number) rec.get("failure_probability")).doubleValue() : 0.6;
            AnomalyEvent approvalEvent = new AnomalyEvent(
                    "APPROVAL-" + recommendationId,
                    "APPROVAL",
                    Instant.now(),
                    (String) rec.get("equipment_id"),
                    (String) rec.get("facility_id"),
                    new AnomalyEvent.Prediction(
                            failProb,
                            "HIGH",
                            (String) rec.get("probable_cause"),
                            0, 0, 0, 0, 0, 0,
                            Instant.now().toString()
                    )
            );

            var invocation = AgentInvocation.create(agentPlatform, CriticalAnomalyResponse.class);
            CriticalAnomalyResponse result = invocation.invoke(new CriticalAnomalyInput(approvalEvent));

            // Update recommendation with work order ID
            recommendationService.setWorkOrderId(recommendationId, result.workOrderId());

            // Send notification deterministically (don't rely on LLM calling sendNotification)
            notificationService.sendMaintenanceAlert(
                    (String) rec.get("equipment_id"),
                    (String) rec.get("facility_id"),
                    (String) rec.get("probable_cause"),
                    failProb,
                    result.workOrderId()
            );

            log.info("Recommendation {} approved - Work Order: {}", recommendationId, result.workOrderId());

            return ResponseEntity.ok(new ApprovalResponse(
                    true,
                    result.workOrderId(),
                    "Maintenance scheduled successfully"
            ));

        } catch (Exception e) {
            log.error("Failed to approve recommendation {}: {}", recommendationId, e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(new ApprovalResponse(false, null, e.getMessage()));
        }
    }

    /**
     * Dismiss a recommendation - releases reserved parts.
     */
    @PostMapping("/recommendations/{recommendationId}/dismiss")
    public ResponseEntity<DismissResponse> dismissRecommendation(
            @PathVariable String recommendationId,
            @RequestBody(required = false) DismissRequest request) {

        String reason = (request != null && request.reason() != null)
                ? request.reason() : "Dismissed by operator";

        log.info("Dismissing recommendation {}: {}", recommendationId, reason);

        try {
            recommendationService.dismiss(recommendationId, reason);

            // TODO: Release reserved parts via inventory MCP tool

            return ResponseEntity.ok(new DismissResponse(true, "Recommendation dismissed"));

        } catch (Exception e) {
            log.error("Failed to dismiss recommendation {}: {}", recommendationId, e.getMessage());
            return ResponseEntity.internalServerError()
                    .body(new DismissResponse(false, e.getMessage()));
        }
    }

    /**
     * Get resolved recommendations (approved, dismissed, superseded, completed).
     */
    @GetMapping("/recommendations/resolved")
    public ResponseEntity<List<Map<String, Object>>> getResolvedRecommendations(
            @RequestParam(defaultValue = "50") int limit) {
        log.debug("Fetching resolved recommendations, limit={}", limit);
        List<Map<String, Object>> resolved = recommendationService.getResolvedRecommendations(limit);
        return ResponseEntity.ok(resolved);
    }

    // ── Automated Actions Endpoints ──────────────────────────────────────────

    /**
     * Get recent automated actions (CRITICAL auto-responses).
     */
    @GetMapping("/automated-actions")
    public ResponseEntity<List<Map<String, Object>>> getAutomatedActions(
            @RequestParam(defaultValue = "20") int limit) {
        log.debug("Fetching recent automated actions, limit={}", limit);
        List<Map<String, Object>> actions = automatedActionService.getRecentActions(limit);
        return ResponseEntity.ok(actions);
    }

    /**
     * Get automated actions for a specific equipment.
     */
    @GetMapping("/automated-actions/equipment/{equipmentId}")
    public ResponseEntity<List<Map<String, Object>>> getActionsForEquipment(
            @PathVariable String equipmentId) {
        List<Map<String, Object>> actions = automatedActionService.getActionsForEquipment(equipmentId);
        return ResponseEntity.ok(actions);
    }

    /**
     * Get a specific automated action by ID.
     */
    @GetMapping("/automated-actions/{actionId}")
    public ResponseEntity<Map<String, Object>> getAutomatedAction(
            @PathVariable String actionId) {
        try {
            Map<String, Object> action = automatedActionService.getAction(actionId);
            return ResponseEntity.ok(action);
        } catch (Exception e) {
            log.error("Action not found: {}", actionId);
            return ResponseEntity.notFound().build();
        }
    }

    // ── Request/Response Records ─────────────────────────────────────────────

    public record ApprovalRequest(String approvedBy) {}
    public record ApprovalResponse(boolean success, String workOrderId, String message) {}

    public record DismissRequest(String reason) {}
    public record DismissResponse(boolean success, String message) {}
}
