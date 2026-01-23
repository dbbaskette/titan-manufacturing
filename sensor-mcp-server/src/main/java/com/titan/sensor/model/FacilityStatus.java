package com.titan.sensor.model;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;

/**
 * Overview of equipment health across a facility.
 */
public record FacilityStatus(
    @JsonPropertyDescription("Facility identifier")
    String facilityId,

    @JsonPropertyDescription("Facility name")
    String facilityName,

    @JsonPropertyDescription("Total number of equipment")
    int totalEquipment,

    @JsonPropertyDescription("Equipment currently operational")
    int operationalCount,

    @JsonPropertyDescription("Equipment in warning state")
    int warningCount,

    @JsonPropertyDescription("Equipment in critical state")
    int criticalCount,

    @JsonPropertyDescription("Equipment under maintenance")
    int maintenanceCount,

    @JsonPropertyDescription("Overall facility health percentage")
    double healthPercentage,

    @JsonPropertyDescription("List of equipment with active anomalies")
    List<String> equipmentWithAnomalies,

    @JsonPropertyDescription("Summary of facility status")
    String summary
) {}
