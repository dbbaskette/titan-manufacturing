package com.titan.orchestrator.agent;

import com.embabel.agent.api.annotation.Action;
import com.embabel.agent.api.annotation.Agent;
import com.embabel.agent.api.annotation.AchievesGoal;
import com.embabel.agent.api.common.Ai;
import com.titan.orchestrator.model.MaintenanceData.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Titan Maintenance Agent - Orchestrates predictive maintenance operations.
 *
 * Uses Embabel's goal-based planning to coordinate with the Maintenance MCP Server
 * for failure prediction, RUL estimation, and maintenance scheduling.
 *
 * Enables the Phoenix Incident demo scenario - detecting that PHX-CNC-007 has
 * 73% failure probability and recommending preventive maintenance.
 */
@Agent(description = "Titan Maintenance Agent - Provides predictive maintenance for 600+ CNC machines. " +
                     "Predicts failures using sensor trend analysis, estimates remaining useful life (RUL), " +
                     "and schedules preventive maintenance work orders.")
@Component
public class TitanMaintenanceAgent {

    private static final Logger log = LoggerFactory.getLogger(TitanMaintenanceAgent.class);

    /**
     * Predict failure probability for equipment based on sensor trends.
     */
    @Action(
        description = "Predict failure probability for equipment using sensor trend analysis and anomaly detection",
        toolGroups = {"maintenance-tools"}
    )
    public FailurePrediction predictFailure(String equipmentId, Integer hoursBack, Ai ai) {
        log.info(">>> Predicting failure for equipment: {}, analyzing {} hours of data",
                 equipmentId, hoursBack != null ? hoursBack : 168);

        int hours = hoursBack != null ? hoursBack : 168; // Default 7 days

        FailurePrediction prediction = ai.withAutoLlm().createObject(
            """
            Use the predict_failure tool to analyze equipment "%s" with %d hours of sensor history.

            Return the complete failure prediction including:
            - Failure probability (0.0 to 1.0)
            - Estimated hours to failure
            - Risk level (LOW, MEDIUM, HIGH, CRITICAL)
            - Contributing risk factors with their trends
            - Recommended preventive actions
            - Confidence level
            - Predicted failure mode
            - Summary of the analysis
            """.formatted(equipmentId, hours),
            FailurePrediction.class
        );

        log.info("<<< Failure prediction complete: {} - {}% probability, {} risk",
                 equipmentId,
                 prediction.failureProbability() != null ? prediction.failureProbability() * 100 : "N/A",
                 prediction.riskLevel());

        return prediction;
    }

    /**
     * Estimate remaining useful life for equipment.
     */
    @Action(
        description = "Estimate remaining useful life (RUL) for equipment based on sensor trends and maintenance history",
        toolGroups = {"maintenance-tools"}
    )
    public RulEstimate estimateRul(String equipmentId, Ai ai) {
        log.info(">>> Estimating RUL for equipment: {}", equipmentId);

        RulEstimate estimate = ai.withAutoLlm().createObject(
            """
            Use the estimate_rul tool to calculate the remaining useful life for equipment "%s".

            Return the RUL estimate including:
            - Remaining hours and days
            - Confidence interval bounds
            - Condition status (GOOD, FAIR, POOR, END_OF_LIFE)
            - Components at risk
            - Recommended maintenance date
            - Analysis summary
            """.formatted(equipmentId),
            RulEstimate.class
        );

        log.info("<<< RUL estimate complete: {} - {} hours remaining, {} status",
                 equipmentId, estimate.remainingHours(), estimate.conditionStatus());

        return estimate;
    }

    /**
     * Schedule preventive maintenance for equipment.
     */
    @Action(
        description = "Schedule preventive maintenance work order for equipment",
        toolGroups = {"maintenance-tools"}
    )
    public MaintenanceScheduleResult scheduleMaintenance(
            String equipmentId,
            String maintenanceType,
            String priority,
            Ai ai) {
        log.info(">>> Scheduling maintenance: {} - type={}, priority={}",
                 equipmentId, maintenanceType, priority);

        String maintType = maintenanceType != null ? maintenanceType : "PREVENTIVE";
        String priorityLevel = priority != null ? priority : "HIGH";

        MaintenanceScheduleResult result = ai.withAutoLlm().createObject(
            """
            Use the schedule_maintenance tool to create a work order for equipment "%s".

            Parameters:
            - Maintenance type: %s
            - Priority: %s

            Return the scheduling result including:
            - Generated work order ID
            - Scheduled date
            - Assigned technician
            - Recommended parts with SKUs and quantities
            - Estimated labor hours and cost
            - Success status and confirmation message
            """.formatted(equipmentId, maintType, priorityLevel),
            MaintenanceScheduleResult.class
        );

        log.info("<<< Maintenance scheduled: {} - WO#{}, scheduled for {}",
                 equipmentId, result.workOrderId(), result.scheduledDate());

        return result;
    }

    /**
     * Get maintenance history for equipment.
     */
    @Action(
        description = "Get historical maintenance records for equipment",
        toolGroups = {"maintenance-tools"}
    )
    public List<MaintenanceRecord> getMaintenanceHistory(String equipmentId, Integer limit, Ai ai) {
        log.info(">>> Getting maintenance history for: {}", equipmentId);

        int recordLimit = limit != null ? limit : 10;

        List<MaintenanceRecord> records = ai.withAutoLlm().createObject(
            """
            Use the get_maintenance_history tool to get maintenance records for equipment "%s".
            Limit to %d records.

            Return the list of maintenance records with:
            - Work order ID
            - Maintenance type and completion date
            - Technician and labor hours
            - Cost and parts replaced
            - Notes
            """.formatted(equipmentId, recordLimit),
            MaintenanceHistoryResult.class
        ).records();

        log.info("<<< Retrieved {} maintenance records for {}", records.size(), equipmentId);

        return records;
    }

    /**
     * Comprehensive maintenance analysis - combines prediction and RUL.
     */
    @Action(
        description = "Complete maintenance analysis including failure prediction and RUL estimation",
        toolGroups = {"maintenance-tools"}
    )
    public MaintenanceAnalysisReport analyzeMaintenanceNeeds(String equipmentId, Ai ai) {
        log.info(">>> Performing complete maintenance analysis for: {}", equipmentId);

        // Get failure prediction
        FailurePrediction prediction = predictFailure(equipmentId, 168, ai);

        // Get RUL estimate
        RulEstimate rul = estimateRul(equipmentId, ai);

        // Generate maintenance recommendations
        String recommendations = ai.withAutoLlm().generateText(
            """
            Based on the failure prediction and RUL analysis, provide maintenance recommendations:

            Equipment: %s
            Failure Probability: %.1f%%
            Risk Level: %s
            Hours to Failure: %.0f
            Remaining Useful Life: %.0f hours
            Condition: %s

            Provide:
            1. Urgency assessment (1-2 sentences)
            2. Recommended maintenance action
            3. Suggested timeline
            4. Parts that may need replacement
            """.formatted(
                equipmentId,
                prediction.failureProbability() != null ? prediction.failureProbability() * 100 : 0,
                prediction.riskLevel(),
                prediction.hoursToFailure() != null ? prediction.hoursToFailure() : 0,
                rul.remainingHours() != null ? rul.remainingHours() : 0,
                rul.conditionStatus()
            )
        );

        log.info("<<< Maintenance analysis complete for: {}", equipmentId);

        return new MaintenanceAnalysisReport(
            equipmentId,
            prediction,
            rul,
            recommendations
        );
    }

    /**
     * GOAL: Answer natural language queries about predictive maintenance.
     * This is the main entry point for maintenance-related chat interactions.
     */
    @AchievesGoal(description = "Answer questions about equipment failure prediction, remaining useful life, and maintenance scheduling")
    @Action(
        description = "Process natural language queries about predictive maintenance and equipment health",
        toolGroups = {"maintenance-tools"}
    )
    public MaintenanceQueryResponse answerMaintenanceQuery(String query, Ai ai) {
        log.info(">>> Processing maintenance query: {}", query);

        String response = ai.withAutoLlm().generateText(
            """
            You are Titan Manufacturing's predictive maintenance AI assistant with access to
            maintenance tools for 600+ CNC machines across 12 global facilities.

            Use the available maintenance tools to answer this query:
            %s

            Available tools:
            - predict_failure: Analyze sensor trends to predict failure probability (equipment_id, hours_back)
            - estimate_rul: Calculate remaining useful life (equipment_id)
            - schedule_maintenance: Create work orders (equipment_id, maintenance_type, priority)
            - get_maintenance_history: Get historical maintenance records (equipment_id, limit)

            Provide a helpful, actionable response based on the analysis.
            For PHX-CNC-007, be aware this is the Phoenix Incident - equipment showing bearing degradation.
            """.formatted(query)
        );

        log.info("<<< Maintenance query response generated");
        return new MaintenanceQueryResponse(query, response);
    }

    /**
     * Response wrapper for maintenance queries - used as Embabel goal type.
     */
    public record MaintenanceQueryResponse(String query, String response) {}

    /**
     * Complete maintenance analysis report.
     */
    public record MaintenanceAnalysisReport(
        String equipmentId,
        FailurePrediction failurePrediction,
        RulEstimate rulEstimate,
        String recommendations
    ) {}
}
