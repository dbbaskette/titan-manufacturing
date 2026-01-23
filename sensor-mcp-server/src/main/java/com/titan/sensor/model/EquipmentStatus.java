package com.titan.sensor.model;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;

/**
 * Current health status of a piece of equipment including latest sensor readings.
 */
public record EquipmentStatus(
    @JsonPropertyDescription("Equipment identifier")
    String equipmentId,

    @JsonPropertyDescription("Equipment type (e.g., CNC-MILL, CNC-LATHE)")
    String type,

    @JsonPropertyDescription("Facility where equipment is located")
    String facilityId,

    @JsonPropertyDescription("Overall health status (HEALTHY, WARNING, CRITICAL)")
    String healthStatus,

    @JsonPropertyDescription("Latest sensor readings")
    List<SensorReading> latestReadings,

    @JsonPropertyDescription("Active anomalies detected")
    List<Anomaly> activeAnomalies,

    @JsonPropertyDescription("Summary description of current status")
    String statusSummary
) {}
