package com.titan.maintenance.model;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.math.BigDecimal;

/**
 * Maintenance work order record.
 */
public record MaintenanceRecord(
    @JsonPropertyDescription("Unique record identifier")
    Integer recordId,

    @JsonPropertyDescription("Equipment identifier")
    String equipmentId,

    @JsonPropertyDescription("Type of maintenance: PREVENTIVE, CORRECTIVE, EMERGENCY, PREDICTIVE")
    String maintenanceType,

    @JsonPropertyDescription("Scheduled date for maintenance")
    String scheduledDate,

    @JsonPropertyDescription("Completion date if completed")
    String completedDate,

    @JsonPropertyDescription("Assigned technician ID")
    String technicianId,

    @JsonPropertyDescription("Assigned technician name")
    String technicianName,

    @JsonPropertyDescription("Work order identifier")
    String workOrderId,

    @JsonPropertyDescription("Status: SCHEDULED, IN_PROGRESS, COMPLETED, CANCELLED")
    String status,

    @JsonPropertyDescription("Parts used or required")
    String partsUsed,

    @JsonPropertyDescription("Labor hours spent or estimated")
    BigDecimal laborHours,

    @JsonPropertyDescription("Total cost")
    BigDecimal cost,

    @JsonPropertyDescription("Additional notes")
    String notes
) {}
