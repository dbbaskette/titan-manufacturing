package com.titan.maintenance.model;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;

/**
 * Result of scheduling a maintenance work order.
 */
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
