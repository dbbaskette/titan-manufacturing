package com.titan.orchestrator.model;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.math.BigDecimal;
import java.util.List;

/**
 * Data models for Logistics Agent responses.
 */
public class LogisticsData {

    /**
     * Shipping carrier information.
     */
    public record Carrier(
        @JsonPropertyDescription("Carrier ID")
        String carrierId,
        @JsonPropertyDescription("Carrier name")
        String name,
        @JsonPropertyDescription("Service type: EXPRESS, GROUND, FREIGHT")
        String serviceType,
        @JsonPropertyDescription("Whether carrier is active")
        boolean isActive
    ) {}

    /**
     * Shipment information.
     */
    public record Shipment(
        @JsonPropertyDescription("Shipment ID")
        String shipmentId,
        @JsonPropertyDescription("Order ID")
        String orderId,
        @JsonPropertyDescription("Carrier name")
        String carrierName,
        @JsonPropertyDescription("Tracking number")
        String trackingNumber,
        @JsonPropertyDescription("Status: PENDING, IN_TRANSIT, DELIVERED, etc.")
        String status,
        @JsonPropertyDescription("Origin facility")
        String originFacility,
        @JsonPropertyDescription("Destination")
        String destination,
        @JsonPropertyDescription("Estimated delivery date")
        String estimatedDelivery,
        @JsonPropertyDescription("Shipping cost")
        BigDecimal shippingCost
    ) {}

    /**
     * Result of creating a shipment.
     */
    public record ShipmentCreateResult(
        @JsonPropertyDescription("Whether creation succeeded")
        boolean success,
        @JsonPropertyDescription("Shipment ID")
        String shipmentId,
        @JsonPropertyDescription("Tracking number")
        String trackingNumber,
        @JsonPropertyDescription("Tracking URL")
        String trackingUrl,
        @JsonPropertyDescription("Carrier name")
        String carrierName,
        @JsonPropertyDescription("Estimated delivery")
        String estimatedDelivery,
        @JsonPropertyDescription("Shipping cost")
        BigDecimal shippingCost,
        @JsonPropertyDescription("Result message")
        String message
    ) {}

    /**
     * Tracking result for a shipment.
     */
    public record TrackingResult(
        @JsonPropertyDescription("Whether shipment was found")
        boolean found,
        @JsonPropertyDescription("Shipment ID")
        String shipmentId,
        @JsonPropertyDescription("Tracking number")
        String trackingNumber,
        @JsonPropertyDescription("Carrier name")
        String carrierName,
        @JsonPropertyDescription("Current status")
        String status,
        @JsonPropertyDescription("Status description")
        String statusDescription,
        @JsonPropertyDescription("Origin")
        String origin,
        @JsonPropertyDescription("Destination")
        String destination,
        @JsonPropertyDescription("Estimated delivery")
        String estimatedDelivery,
        @JsonPropertyDescription("Summary")
        String summary
    ) {}

    /**
     * Shipping cost estimate.
     */
    public record ShippingEstimate(
        @JsonPropertyDescription("Carrier ID")
        String carrierId,
        @JsonPropertyDescription("Carrier name")
        String carrierName,
        @JsonPropertyDescription("Service level: STANDARD, EXPRESS, PRIORITY")
        String serviceLevel,
        @JsonPropertyDescription("Estimated cost")
        BigDecimal estimatedCost,
        @JsonPropertyDescription("Transit days minimum")
        int transitDaysMin,
        @JsonPropertyDescription("Transit days maximum")
        int transitDaysMax,
        @JsonPropertyDescription("Earliest delivery date")
        String estimatedDeliveryEarliest,
        @JsonPropertyDescription("Whether this is recommended")
        boolean recommended,
        @JsonPropertyDescription("Notes")
        String notes
    ) {}

    /**
     * Response to logistics query.
     */
    public record LogisticsQueryResponse(
        @JsonPropertyDescription("Original query")
        String query,
        @JsonPropertyDescription("Response text")
        String response
    ) {}
}
