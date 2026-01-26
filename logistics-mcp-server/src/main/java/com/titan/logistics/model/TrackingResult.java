package com.titan.logistics.model;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/**
 * Shipment tracking result
 */
public record TrackingResult(
    @JsonPropertyDescription("Whether tracking was found")
    boolean found,

    @JsonPropertyDescription("Shipment ID")
    String shipmentId,

    @JsonPropertyDescription("Tracking number")
    String trackingNumber,

    @JsonPropertyDescription("Carrier name")
    String carrierName,

    @JsonPropertyDescription("Tracking URL")
    String trackingUrl,

    @JsonPropertyDescription("Current status: PENDING, PICKED_UP, IN_TRANSIT, OUT_FOR_DELIVERY, DELIVERED, EXCEPTION")
    String status,

    @JsonPropertyDescription("Status description")
    String statusDescription,

    @JsonPropertyDescription("Origin facility")
    String origin,

    @JsonPropertyDescription("Destination")
    String destination,

    @JsonPropertyDescription("Ship date")
    String shipDate,

    @JsonPropertyDescription("Estimated delivery")
    String estimatedDelivery,

    @JsonPropertyDescription("Actual delivery (if delivered)")
    String actualDelivery,

    @JsonPropertyDescription("Days in transit")
    int daysInTransit,

    @JsonPropertyDescription("Human-readable summary")
    String summary
) {}
