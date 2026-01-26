package com.titan.logistics.model;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/**
 * Shipping carrier information
 */
public record Carrier(
    @JsonPropertyDescription("Carrier identifier (e.g., FEDEX-EXPRESS, UPS-GROUND)")
    String carrierId,

    @JsonPropertyDescription("Carrier name")
    String name,

    @JsonPropertyDescription("Service type: EXPRESS, GROUND, FREIGHT, AIR")
    String serviceType,

    @JsonPropertyDescription("URL template for tracking (use {tracking} placeholder)")
    String trackingUrlTemplate,

    @JsonPropertyDescription("Contact email")
    String contactEmail,

    @JsonPropertyDescription("Contact phone")
    String contactPhone,

    @JsonPropertyDescription("Whether carrier is currently active")
    boolean isActive
) {}
