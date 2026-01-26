package com.titan.logistics.model;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.math.BigDecimal;

/**
 * Shipment information
 */
public record Shipment(
    @JsonPropertyDescription("Shipment identifier")
    String shipmentId,

    @JsonPropertyDescription("Associated order ID")
    String orderId,

    @JsonPropertyDescription("Carrier ID")
    String carrierId,

    @JsonPropertyDescription("Carrier name")
    String carrierName,

    @JsonPropertyDescription("Tracking number")
    String trackingNumber,

    @JsonPropertyDescription("Tracking URL")
    String trackingUrl,

    @JsonPropertyDescription("Status: PENDING, PICKED_UP, IN_TRANSIT, OUT_FOR_DELIVERY, DELIVERED, EXCEPTION")
    String status,

    @JsonPropertyDescription("Origin facility ID")
    String originFacility,

    @JsonPropertyDescription("Origin facility name")
    String originFacilityName,

    @JsonPropertyDescription("Destination address")
    String destinationAddress,

    @JsonPropertyDescription("Destination city")
    String destinationCity,

    @JsonPropertyDescription("Destination country")
    String destinationCountry,

    @JsonPropertyDescription("Ship date")
    String shipDate,

    @JsonPropertyDescription("Estimated delivery date")
    String estimatedDelivery,

    @JsonPropertyDescription("Actual delivery date (if delivered)")
    String actualDelivery,

    @JsonPropertyDescription("Total weight in kg")
    BigDecimal weightKg,

    @JsonPropertyDescription("Number of packages")
    int packageCount,

    @JsonPropertyDescription("Shipping cost")
    BigDecimal shippingCost,

    @JsonPropertyDescription("Notes")
    String notes
) {}
