package com.titan.logistics.model;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.math.BigDecimal;

/**
 * Result of creating a new shipment
 */
public record ShipmentCreateResult(
    @JsonPropertyDescription("Whether shipment was created successfully")
    boolean success,

    @JsonPropertyDescription("Created shipment ID")
    String shipmentId,

    @JsonPropertyDescription("Generated tracking number")
    String trackingNumber,

    @JsonPropertyDescription("Tracking URL")
    String trackingUrl,

    @JsonPropertyDescription("Carrier name")
    String carrierName,

    @JsonPropertyDescription("Estimated delivery date")
    String estimatedDelivery,

    @JsonPropertyDescription("Shipping cost")
    BigDecimal shippingCost,

    @JsonPropertyDescription("Human-readable message")
    String message
) {}
