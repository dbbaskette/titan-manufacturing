package com.titan.maintenance.model;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/**
 * A contributing factor to equipment failure risk.
 */
public record RiskFactor(
    @JsonPropertyDescription("Sensor type contributing to risk (e.g., vibration, temperature)")
    String sensorType,

    @JsonPropertyDescription("Current sensor value")
    Double currentValue,

    @JsonPropertyDescription("Unit of measurement")
    String unit,

    @JsonPropertyDescription("Warning threshold for this sensor")
    Double warningThreshold,

    @JsonPropertyDescription("Critical threshold for this sensor")
    Double criticalThreshold,

    @JsonPropertyDescription("Trend direction: INCREASING, DECREASING, STABLE")
    String trend,

    @JsonPropertyDescription("Rate of change per hour")
    Double trendRate,

    @JsonPropertyDescription("Contribution to overall risk (0.0 to 1.0)")
    Double riskContribution
) {}
