package com.titan.inventory.model;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.math.BigDecimal;

/**
 * Product search result with similarity score
 */
public record ProductSearchResult(
    @JsonPropertyDescription("Stock Keeping Unit identifier")
    String sku,

    @JsonPropertyDescription("Product name")
    String name,

    @JsonPropertyDescription("Product description")
    String description,

    @JsonPropertyDescription("Division: AERO, ENERGY, MOBILITY, or INDUSTRIAL")
    String divisionId,

    @JsonPropertyDescription("Product category")
    String category,

    @JsonPropertyDescription("Product subcategory")
    String subcategory,

    @JsonPropertyDescription("Unit price in USD")
    BigDecimal unitPrice,

    @JsonPropertyDescription("Semantic similarity score (0.0 to 1.0)")
    double similarityScore,

    @JsonPropertyDescription("Total quantity available across all facilities")
    int totalStock
) {}
