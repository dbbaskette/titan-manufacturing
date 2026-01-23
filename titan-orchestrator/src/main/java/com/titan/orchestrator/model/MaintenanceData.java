package com.titan.orchestrator.model;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;

/**
 * Domain models for predictive maintenance data from the Maintenance MCP Agent.
 */
public class MaintenanceData {

    @JsonClassDescription("Failure prediction result for manufacturing equipment")
    public record FailurePrediction(
        @JsonPropertyDescription("Equipment identifier")
        String equipmentId,

        @JsonPropertyDescription("Probability of failure (0.0 to 1.0)")
        Double failureProbability,

        @JsonPropertyDescription("Estimated hours until failure")
        Double hoursToFailure,

        @JsonPropertyDescription("Risk level: LOW, MEDIUM, HIGH, CRITICAL")
        String riskLevel,

        @JsonPropertyDescription("Contributing risk factors")
        List<RiskFactor> riskFactors,

        @JsonPropertyDescription("Recommended actions to prevent failure")
        List<String> recommendedActions,

        @JsonPropertyDescription("Confidence level of the prediction (0.0 to 1.0)")
        Double confidenceLevel,

        @JsonPropertyDescription("Predicted failure mode")
        String predictedFailureMode,

        @JsonPropertyDescription("Analysis summary")
        String summary
    ) {}

    @JsonClassDescription("Risk factor contributing to failure prediction")
    public record RiskFactor(
        @JsonPropertyDescription("Factor name")
        String factor,

        @JsonPropertyDescription("Contribution weight (0.0 to 1.0)")
        Double contribution,

        @JsonPropertyDescription("Current value")
        String currentValue,

        @JsonPropertyDescription("Threshold value")
        String threshold,

        @JsonPropertyDescription("Trend direction: INCREASING, STABLE, DECREASING")
        String trend
    ) {}

    @JsonClassDescription("Remaining Useful Life estimate for equipment")
    public record RulEstimate(
        @JsonPropertyDescription("Equipment identifier")
        String equipmentId,

        @JsonPropertyDescription("Estimated remaining useful life in hours")
        Double remainingHours,

        @JsonPropertyDescription("Remaining days")
        Double remainingDays,

        @JsonPropertyDescription("Confidence interval lower bound (hours)")
        Double confidenceLowerBound,

        @JsonPropertyDescription("Confidence interval upper bound (hours)")
        Double confidenceUpperBound,

        @JsonPropertyDescription("Condition-based RUL status: GOOD, FAIR, POOR, END_OF_LIFE")
        String conditionStatus,

        @JsonPropertyDescription("Components that may need replacement")
        List<String> componentsAtRisk,

        @JsonPropertyDescription("Recommended maintenance date")
        String recommendedMaintenanceDate,

        @JsonPropertyDescription("Analysis summary")
        String summary
    ) {}

    @JsonClassDescription("Historical maintenance record")
    public record MaintenanceRecord(
        @JsonPropertyDescription("Work order ID")
        String workOrderId,

        @JsonPropertyDescription("Equipment identifier")
        String equipmentId,

        @JsonPropertyDescription("Type of maintenance performed")
        String maintenanceType,

        @JsonPropertyDescription("Maintenance completion date")
        String completedDate,

        @JsonPropertyDescription("Technician who performed the work")
        String technicianName,

        @JsonPropertyDescription("Labor hours spent")
        Double laborHours,

        @JsonPropertyDescription("Total cost of maintenance")
        Double totalCost,

        @JsonPropertyDescription("Parts replaced during maintenance")
        List<String> partsReplaced,

        @JsonPropertyDescription("Work description/notes")
        String notes
    ) {}

    @JsonClassDescription("Result of scheduling maintenance work order")
    public record MaintenanceScheduleResult(
        @JsonPropertyDescription("Generated work order ID")
        String workOrderId,

        @JsonPropertyDescription("Equipment identifier")
        String equipmentId,

        @JsonPropertyDescription("Type of maintenance scheduled")
        String maintenanceType,

        @JsonPropertyDescription("Scheduled date and time")
        String scheduledDate,

        @JsonPropertyDescription("Assigned technician name")
        String technicianName,

        @JsonPropertyDescription("Recommended parts for the work order")
        List<PartRecommendation> recommendedParts,

        @JsonPropertyDescription("Estimated labor hours")
        Double estimatedLaborHours,

        @JsonPropertyDescription("Estimated total cost")
        Double estimatedCost,

        @JsonPropertyDescription("Priority level: ROUTINE, HIGH, URGENT, EMERGENCY")
        String priority,

        @JsonPropertyDescription("Whether scheduling was successful")
        boolean success,

        @JsonPropertyDescription("Confirmation or error message")
        String message
    ) {}

    @JsonClassDescription("Recommended replacement part for maintenance")
    public record PartRecommendation(
        @JsonPropertyDescription("Part SKU")
        String sku,

        @JsonPropertyDescription("Part name/description")
        String name,

        @JsonPropertyDescription("Quantity needed")
        Integer quantity,

        @JsonPropertyDescription("Estimated unit price")
        Double unitPrice,

        @JsonPropertyDescription("Reason for recommendation")
        String reason
    ) {}

    // Result wrapper records for LLM responses
    public record MaintenanceHistoryResult(List<MaintenanceRecord> records) {}
}
