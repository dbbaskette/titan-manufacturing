package com.titan.orchestrator.listener;

import com.embabel.agent.api.invocation.AgentInvocation;
import com.embabel.agent.core.AgentPlatform;
import com.titan.orchestrator.model.AnomalyEvent;
import com.titan.orchestrator.model.AnomalyEvent.CriticalAnomalyInput;
import com.titan.orchestrator.model.AnomalyEvent.HighAnomalyInput;
import com.titan.orchestrator.model.AnomalyResponse.CriticalAnomalyResponse;
import com.titan.orchestrator.model.AnomalyResponse.HighAnomalyResponse;
import com.titan.orchestrator.service.AutomatedActionService;
import com.titan.orchestrator.service.NotificationService;
import com.titan.orchestrator.service.RecommendationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Listens for anomaly events from RabbitMQ and triggers Embabel agent responses.
 *
 * CRITICAL events → Full automated response (schedule maintenance, notify)
 * HIGH events → Create recommendation for human approval
 */
@Component
public class AnomalyEventListener {

    private static final Logger log = LoggerFactory.getLogger(AnomalyEventListener.class);

    private final AgentPlatform agentPlatform;
    private final RecommendationService recommendationService;
    private final AutomatedActionService automatedActionService;
    private final NotificationService notificationService;

    public AnomalyEventListener(
            AgentPlatform agentPlatform,
            RecommendationService recommendationService,
            AutomatedActionService automatedActionService,
            NotificationService notificationService) {
        this.agentPlatform = agentPlatform;
        this.recommendationService = recommendationService;
        this.automatedActionService = automatedActionService;
        this.notificationService = notificationService;
    }

    /**
     * Handle CRITICAL anomaly - full automated response.
     * Invokes Embabel GOAP to determine and execute the appropriate response.
     */
    @RabbitListener(queues = "${anomaly.critical-queue:orchestrator.critical}")
    public void handleCritical(AnomalyEvent event) {
        log.info("╔══════════════════════════════════════════════════════════════╗");
        log.info("║ CRITICAL ANOMALY RECEIVED                                    ║");
        log.info("╠══════════════════════════════════════════════════════════════╣");
        log.info("║ Equipment: {} at {}                              ", event.equipmentId(), event.facilityId());
        log.info("║ Failure Probability: {}%                         ", Math.round(event.prediction().failureProbability() * 100));
        log.info("║ Probable Cause: {}                               ", event.prediction().probableCause());
        log.info("╚══════════════════════════════════════════════════════════════╝");

        try {
            // Cancel any pending HIGH recommendation (superseded by CRITICAL)
            int superseded = recommendationService.cancelPending(
                    event.equipmentId(),
                    "Superseded by CRITICAL alert - auto-response triggered"
            );
            if (superseded > 0) {
                log.info("Superseded {} pending HIGH recommendation(s) for {}", superseded, event.equipmentId());
            }

            // Invoke Embabel GOAP - the planner decides which MCP tools to call
            log.info(">>> Invoking Embabel agent for CRITICAL response goal...");
            var invocation = AgentInvocation.create(agentPlatform, CriticalAnomalyResponse.class);
            CriticalAnomalyResponse result = invocation.invoke(new CriticalAnomalyInput(event));

            // Record the automated action for audit trail
            String actionId = automatedActionService.record(event, result);

            // Send notification deterministically (don't rely on LLM calling sendNotification)
            notificationService.sendMaintenanceAlert(
                    event.equipmentId(),
                    event.facilityId(),
                    event.prediction().probableCause(),
                    event.prediction().failureProbability(),
                    result.workOrderId()
            );

            log.info("╔══════════════════════════════════════════════════════════════╗");
            log.info("║ CRITICAL RESPONSE COMPLETE                                   ║");
            log.info("╠══════════════════════════════════════════════════════════════╣");
            log.info("║ Action ID: {}                                  ", actionId);
            log.info("║ Work Order: {}                                 ", result.workOrderId());
            log.info("║ Parts Reserved: {}                             ", result.partsReserved() != null ? result.partsReserved().size() : 0);
            log.info("║ Notification Sent: deterministic                             ║");
            log.info("╚══════════════════════════════════════════════════════════════╝");

        } catch (Exception e) {
            log.error("Failed to process CRITICAL anomaly for {}: {}", event.equipmentId(), e.getMessage(), e);
            // Could add dead-letter queue handling here
        }
    }

    /**
     * Handle HIGH anomaly - create recommendation for human approval.
     * Reserves parts proactively but does NOT schedule maintenance.
     */
    @RabbitListener(queues = "${anomaly.high-queue:orchestrator.high}")
    public void handleHigh(AnomalyEvent event) {
        log.info("╔══════════════════════════════════════════════════════════════╗");
        log.info("║ HIGH ANOMALY RECEIVED                                        ║");
        log.info("╠══════════════════════════════════════════════════════════════╣");
        log.info("║ Equipment: {} at {}                              ", event.equipmentId(), event.facilityId());
        log.info("║ Failure Probability: {}%                         ", Math.round(event.prediction().failureProbability() * 100));
        log.info("║ Probable Cause: {}                               ", event.prediction().probableCause());
        log.info("╚══════════════════════════════════════════════════════════════╝");

        try {
            // Check if there's already a pending recommendation for this equipment
            if (recommendationService.hasPendingRecommendation(event.equipmentId())) {
                log.info("Skipping - pending recommendation already exists for {}", event.equipmentId());
                return;
            }

            // Invoke Embabel GOAP - the planner reserves parts and creates recommendation
            log.info(">>> Invoking Embabel agent for HIGH response goal...");
            var invocation = AgentInvocation.create(agentPlatform, HighAnomalyResponse.class);
            HighAnomalyResponse result = invocation.invoke(new HighAnomalyInput(event));

            // Create recommendation record for dashboard
            String recommendationId = recommendationService.create(event, result);

            log.info("╔══════════════════════════════════════════════════════════════╗");
            log.info("║ HIGH RESPONSE COMPLETE                                       ║");
            log.info("╠══════════════════════════════════════════════════════════════╣");
            log.info("║ Recommendation ID: {}                          ", recommendationId);
            log.info("║ Parts Reserved: {}                             ", result.partsReserved() != null ? result.partsReserved().size() : 0);
            log.info("║ Recommended Action: {}                         ", result.recommendedAction());
            log.info("║ Status: PENDING (awaiting human approval)                    ║");
            log.info("╚══════════════════════════════════════════════════════════════╝");

        } catch (Exception e) {
            log.error("Failed to process HIGH anomaly for {}: {}", event.equipmentId(), e.getMessage(), e);
        }
    }
}
