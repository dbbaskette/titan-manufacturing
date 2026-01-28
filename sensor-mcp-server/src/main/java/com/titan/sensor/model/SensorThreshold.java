package com.titan.sensor.model;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/**
 * Sensor threshold configuration for anomaly detection.
 */
public record SensorThreshold(
    @JsonPropertyDescription("Threshold ID")
    Integer thresholdId,

    @JsonPropertyDescription("Equipment ID (null for global default)")
    String equipmentId,

    @JsonPropertyDescription("Sensor type")
    String sensorType,

    @JsonPropertyDescription("Warning threshold value")
    Double warningThreshold,

    @JsonPropertyDescription("Critical threshold value")
    Double criticalThreshold,

    @JsonPropertyDescription("Last updated timestamp")
    String updatedAt,

    @JsonPropertyDescription("User who last updated")
    String updatedBy
) {}
