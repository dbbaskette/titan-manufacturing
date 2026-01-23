package com.titan.sensor.model;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/**
 * Detected anomaly in sensor readings.
 */
public record Anomaly(
    @JsonPropertyDescription("Anomaly identifier")
    String anomalyId,

    @JsonPropertyDescription("Equipment with detected anomaly")
    String equipmentId,

    @JsonPropertyDescription("Type of anomaly detected")
    String anomalyType,

    @JsonPropertyDescription("Affected sensor type")
    String sensorType,

    @JsonPropertyDescription("Severity level (LOW, MEDIUM, HIGH, CRITICAL)")
    String severity,

    @JsonPropertyDescription("When anomaly was first detected")
    String detectedAt,

    @JsonPropertyDescription("Human-readable description of the anomaly")
    String description,

    @JsonPropertyDescription("Predicted failure date if applicable")
    String predictedFailureDate,

    @JsonPropertyDescription("Confidence score 0.0 to 1.0")
    Double confidenceScore
) {}
