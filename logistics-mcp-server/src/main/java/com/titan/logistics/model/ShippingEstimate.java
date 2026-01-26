package com.titan.logistics.model;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.math.BigDecimal;

/**
 * Shipping cost and time estimate
 */
public record ShippingEstimate(
    @JsonPropertyDescription("Carrier ID")
    String carrierId,

    @JsonPropertyDescription("Carrier name")
    String carrierName,

    @JsonPropertyDescription("Service level: STANDARD, EXPRESS, PRIORITY")
    String serviceLevel,

    @JsonPropertyDescription("Service type: EXPRESS, GROUND, FREIGHT")
    String serviceType,

    @JsonPropertyDescription("Estimated cost")
    BigDecimal estimatedCost,

    @JsonPropertyDescription("Minimum transit days")
    int transitDaysMin,

    @JsonPropertyDescription("Maximum transit days")
    int transitDaysMax,

    @JsonPropertyDescription("Estimated delivery date (earliest)")
    String estimatedDeliveryEarliest,

    @JsonPropertyDescription("Estimated delivery date (latest)")
    String estimatedDeliveryLatest,

    @JsonPropertyDescription("Whether this is recommended option")
    boolean recommended,

    @JsonPropertyDescription("Reason for recommendation or notes")
    String notes
) {}
