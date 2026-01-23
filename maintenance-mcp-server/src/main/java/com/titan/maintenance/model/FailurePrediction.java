package com.titan.maintenance.model;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;

/**
 * Failure probability prediction for equipment based on sensor trend analysis.
 */
public record FailurePrediction(
    @JsonPropertyDescription("Equipment identifier")
    String equipmentId,

    @JsonPropertyDescription("Overall failure probability (0.0 to 1.0)")
    Double failureProbability,

    @JsonPropertyDescription("Failure probability as percentage (0 to 100)")
    Integer failureProbabilityPercent,

    @JsonPropertyDescription("Predicted time to failure in hours")
    Integer predictedHoursToFailure,

    @JsonPropertyDescription("Risk level: LOW, MEDIUM, HIGH, CRITICAL")
    String riskLevel,

    @JsonPropertyDescription("Primary contributing factors to failure risk")
    List<RiskFactor> riskFactors,

    @JsonPropertyDescription("Recommended maintenance actions")
    List<String> recommendations,

    @JsonPropertyDescription("Confidence level of the prediction (0.0 to 1.0)")
    Double confidenceScore,

    @JsonPropertyDescription("Human-readable analysis summary")
    String analysisSummary
) {}
