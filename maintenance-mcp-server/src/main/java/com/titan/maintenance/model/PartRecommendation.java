package com.titan.maintenance.model;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/**
 * Recommended replacement part for maintenance.
 */
public record PartRecommendation(
    @JsonPropertyDescription("Part SKU")
    String sku,

    @JsonPropertyDescription("Part name/description")
    String name,

    @JsonPropertyDescription("Quantity needed")
    Integer quantity,

    @JsonPropertyDescription("Estimated unit price")
    Double unitPrice,

    @JsonPropertyDescription("Reason for recommendation")
    String reason
) {}
