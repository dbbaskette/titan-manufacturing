package com.titan.maintenance.model;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/**
 * Remaining Useful Life (RUL) estimate for equipment.
 */
public record RulEstimate(
    @JsonPropertyDescription("Equipment identifier")
    String equipmentId,

    @JsonPropertyDescription("Estimated remaining useful life in hours")
    Integer remainingHours,

    @JsonPropertyDescription("Estimated remaining useful life in days")
    Integer remainingDays,

    @JsonPropertyDescription("Lower bound estimate in hours (conservative)")
    Integer lowerBoundHours,

    @JsonPropertyDescription("Upper bound estimate in hours (optimistic)")
    Integer upperBoundHours,

    @JsonPropertyDescription("Equipment age in days since installation")
    Integer equipmentAgeDays,

    @JsonPropertyDescription("Days since last maintenance")
    Integer daysSinceLastMaintenance,

    @JsonPropertyDescription("Recommended maintenance interval in days")
    Integer recommendedIntervalDays,

    @JsonPropertyDescription("Confidence level of estimate (0.0 to 1.0)")
    Double confidenceScore,

    @JsonPropertyDescription("Methodology used for RUL estimation")
    String methodology,

    @JsonPropertyDescription("Human-readable RUL analysis summary")
    String summary
) {}
