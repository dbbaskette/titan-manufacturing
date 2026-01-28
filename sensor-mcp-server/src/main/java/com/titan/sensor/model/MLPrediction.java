package com.titan.sensor.model;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/**
 * Machine learning prediction result for equipment failure.
 */
public record MLPrediction(
    @JsonPropertyDescription("Equipment identifier")
    String equipmentId,

    @JsonPropertyDescription("Predicted failure probability (0.0 to 1.0)")
    Double failureProbability,

    @JsonPropertyDescription("Risk level (CRITICAL, HIGH, MEDIUM, LOW)")
    String riskLevel,

    @JsonPropertyDescription("Estimated hours until potential failure")
    Integer hoursToFailure,

    @JsonPropertyDescription("Prediction confidence (0.0 to 1.0)")
    Double confidence,

    @JsonPropertyDescription("Vibration contribution to risk score")
    Double vibrationContribution,

    @JsonPropertyDescription("Temperature contribution to risk score")
    Double temperatureContribution,

    @JsonPropertyDescription("Trend contribution to risk score")
    Double trendContribution,

    @JsonPropertyDescription("ML model version used")
    String modelVersion,

    @JsonPropertyDescription("Human-readable summary")
    String summary
) {}
