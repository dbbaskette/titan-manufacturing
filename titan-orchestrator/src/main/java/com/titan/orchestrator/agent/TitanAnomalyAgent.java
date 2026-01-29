package com.titan.orchestrator.agent;

import com.embabel.agent.api.annotation.Action;
import com.embabel.agent.api.annotation.AchievesGoal;
import com.embabel.agent.api.annotation.Agent;
import com.embabel.agent.api.common.Ai;
import com.titan.orchestrator.model.AnomalyEvent;
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
    public CriticalAnomalyResponse handleCriticalAnomaly(AnomalyEvent event, Ai ai) {
        log.info(">>> Handling CRITICAL anomaly for {} ({}% failure probability)",
                 event.equipmentId(),
                 Math.round(event.prediction().failureProbability() * 100));

        CriticalAnomalyResponse response = ai.withAutoLlm().createObject(
            """
            CRITICAL ALERT: Equipment %s at %s facility has %.0f%% failure probability.
            Probable cause: %s

            You must prevent this equipment failure. Use the available tools to:

            1. INVENTORY: Based on the probable cause "%s", determine what parts are needed.
               - For bearing issues: need SKU INDL-BRG-7420 (Spindle Bearing)
               - For motor issues: need SKU INDL-MOT-5500 (CNC Motor Assembly)
               - For coolant issues: need SKU INDL-PMP-2200 (Coolant Pump)
               - For electrical issues: need SKU INDL-CTR-1100 (Motor Controller)
               Check stock availability and reserve the required parts.

            2. MAINTENANCE: Schedule emergency maintenance for equipment %s.
               Use maintenance type: EMERGENCY
               Include the probable cause in the notes.

            3. COMMUNICATIONS: Notify the plant manager at facility %s about the scheduled
               emergency maintenance.

            Take all necessary actions now and report what was done.
            """.formatted(
                event.equipmentId(),
                event.facilityId(),
                event.prediction().failureProbability() * 100,
                event.prediction().probableCause(),
                event.prediction().probableCause(),
                event.equipmentId(),
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
    public HighAnomalyResponse handleHighAnomaly(AnomalyEvent event, Ai ai) {
        log.info(">>> Handling HIGH anomaly for {} ({}% failure probability)",
                 event.equipmentId(),
                 Math.round(event.prediction().failureProbability() * 100));

        HighAnomalyResponse response = ai.withAutoLlm().createObject(
            """
            HIGH RISK ALERT: Equipment %s at %s facility has %.0f%% failure probability.
            Probable cause: %s

            Prepare for potential maintenance but DO NOT schedule it yet (requires human approval).

            Use the available tools to:

            1. INVENTORY: Based on the probable cause "%s", determine what parts are needed.
               - For bearing issues: need SKU INDL-BRG-7420 (Spindle Bearing)
               - For motor issues: need SKU INDL-MOT-5500 (CNC Motor Assembly)
               - For coolant issues: need SKU INDL-PMP-2200 (Coolant Pump)
               - For electrical issues: need SKU INDL-CTR-1100 (Motor Controller)
               Check stock availability and RESERVE the required parts with a 48-hour hold.

            2. MAINTENANCE: Use the predict_failure tool to get detailed failure prediction
               information that will help inform the maintenance recommendation.

            IMPORTANT: Do NOT schedule maintenance. Only reserve parts and gather information.

            Report what parts were reserved and recommend the appropriate maintenance action
            for human review.
            """.formatted(
                event.equipmentId(),
                event.facilityId(),
                event.prediction().failureProbability() * 100,
                event.prediction().probableCause(),
                event.prediction().probableCause()
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
