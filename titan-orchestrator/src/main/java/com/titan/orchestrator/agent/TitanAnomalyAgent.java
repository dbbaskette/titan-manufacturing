package com.titan.orchestrator.agent;

import com.embabel.agent.api.annotation.Action;
import com.embabel.agent.api.annotation.AchievesGoal;
import com.embabel.agent.api.annotation.Agent;
import com.embabel.agent.api.common.Ai;
import com.titan.orchestrator.model.AnomalyEvent;
import com.titan.orchestrator.model.AnomalyEvent.CriticalAnomalyInput;
import com.titan.orchestrator.model.AnomalyEvent.HighAnomalyInput;
import com.titan.orchestrator.model.AnomalyResponse.CriticalAnomalyResponse;
import com.titan.orchestrator.model.AnomalyResponse.HighAnomalyResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Titan Anomaly Response Agent - Responds to equipment anomalies detected by ML scoring.
 *
 * Uses Embabel's GOAP (Goal Oriented Action Planning) to dynamically determine
 * which tools to invoke based on the goal and available context.
 *
 * For CRITICAL alerts: Full automated response (reserve parts, schedule maintenance, notify)
 * For HIGH alerts: Reserve parts and create recommendation for human approval
 */
@Agent(description = "Titan Anomaly Response Agent - Responds to equipment anomalies by coordinating " +
                     "maintenance scheduling, parts reservation, and personnel notification. " +
                     "Uses predictive maintenance data to prevent equipment failures.")
@Component
public class TitanAnomalyAgent {

    private static final Logger log = LoggerFactory.getLogger(TitanAnomalyAgent.class);

    /**
     * Handle CRITICAL anomaly - full automated response.
     *
     * Embabel's GOAP planner will determine which tools to call:
     * - Inventory tools to check stock and reserve parts
     * - Maintenance tools to schedule emergency maintenance
     * - Communications tools to notify plant manager
     */
    @AchievesGoal(description = "Prevent imminent equipment failure by reserving parts, " +
                                "scheduling emergency maintenance, and notifying personnel")
    @Action(
        description = "Respond to critical equipment anomaly to prevent failure",
        toolGroups = {"maintenance-tools", "inventory-tools", "communications-tools"}
    )
    public CriticalAnomalyResponse handleCriticalAnomaly(CriticalAnomalyInput input, Ai ai) {
        AnomalyEvent event = input.event();
        log.info(">>> Handling CRITICAL anomaly for {} ({}% failure probability)",
                 event.equipmentId(),
                 Math.round(event.prediction().failureProbability() * 100));

        CriticalAnomalyResponse response = ai.withAutoLlm().createObject(
            """
            CRITICAL ALERT: Equipment %s at %s facility has %.0f%% failure probability.
            Probable cause: %s

            You must prevent this equipment failure. Complete ALL of the following steps:

            1. FIND COMPATIBLE PARTS: Use getCompatibleParts with equipmentId="%s" and
               faultType based on the probable cause:
               - "Bearing degradation" → faultType="BEARING"
               - "Motor burnout" → faultType="MOTOR"
               - "Spindle wear" → faultType="SPINDLE"
               - "Coolant system failure" → faultType="COOLANT"
               - "Electrical fault" → faultType="ELECTRICAL"
               This returns parts compatible with this specific machine model with stock levels.

            2. SCHEDULE MAINTENANCE: Use scheduleMaintenance for equipment %s.
               Type: EMERGENCY. Include the probable cause and compatible parts found in the notes.

            3. NOTIFY PERSONNEL: Use sendNotification with:
               - recipientId: "%s" (the facility ID)
               - templateType: "MAINTENANCE_ALERT"
               - variablesJson: {"equipment_id": "%s", "probable_cause": "%s", "facility_id": "%s"}

            You MUST execute all 3 steps. Report what was done.
            """.formatted(
                event.equipmentId(),
                event.facilityId(),
                event.prediction().failureProbability() * 100,
                event.prediction().probableCause(),
                event.equipmentId(),
                event.equipmentId(),
                event.facilityId(),
                event.equipmentId(),
                event.prediction().probableCause(),
                event.facilityId()
            ),
            CriticalAnomalyResponse.class
        );

        log.info("<<< CRITICAL response complete for {}: WO={}, parts={}",
                 event.equipmentId(),
                 response.workOrderId(),
                 response.partsReserved() != null ? response.partsReserved().size() : 0);

        return response;
    }

    /**
     * Handle HIGH anomaly - reserve parts and create recommendation.
     *
     * Embabel's GOAP planner will determine which tools to call:
     * - Inventory tools to check stock and reserve parts (48h hold)
     * - Does NOT schedule maintenance (requires human approval)
     */
    @AchievesGoal(description = "Prepare for potential equipment failure by reserving parts " +
                                "and creating a maintenance recommendation for human approval")
    @Action(
        description = "Respond to high-risk equipment anomaly with recommendation",
        toolGroups = {"maintenance-tools", "inventory-tools"}
    )
    public HighAnomalyResponse handleHighAnomaly(HighAnomalyInput input, Ai ai) {
        AnomalyEvent event = input.event();
        log.info(">>> Handling HIGH anomaly for {} ({}% failure probability)",
                 event.equipmentId(),
                 Math.round(event.prediction().failureProbability() * 100));

        HighAnomalyResponse response = ai.withAutoLlm().createObject(
            """
            HIGH RISK ALERT: Equipment %s at %s facility has %.0f%% failure probability.
            Probable cause: %s

            Prepare for potential maintenance but DO NOT schedule it yet (requires human approval).

            1. FIND COMPATIBLE PARTS: Use getCompatibleParts with equipmentId="%s" and
               faultType based on the probable cause:
               - "Bearing degradation" → faultType="BEARING"
               - "Motor burnout" → faultType="MOTOR"
               - "Spindle wear" → faultType="SPINDLE"
               - "Coolant system failure" → faultType="COOLANT"
               - "Electrical fault" → faultType="ELECTRICAL"
               This returns parts compatible with this specific machine model with stock levels.

            2. GATHER PREDICTION DATA: Use predictFailure to get detailed failure analysis
               for equipment %s.

            IMPORTANT: Do NOT schedule maintenance. Only find compatible parts and gather information.

            Report what parts were found (include SKUs and stock status) and recommend
            the appropriate maintenance action for human review.
            """.formatted(
                event.equipmentId(),
                event.facilityId(),
                event.prediction().failureProbability() * 100,
                event.prediction().probableCause(),
                event.equipmentId(),
                event.equipmentId()
            ),
            HighAnomalyResponse.class
        );

        log.info("<<< HIGH response complete for {}: recommendation={}, parts={}",
                 event.equipmentId(),
                 response.recommendationId(),
                 response.partsReserved() != null ? response.partsReserved().size() : 0);

        return response;
    }
}
