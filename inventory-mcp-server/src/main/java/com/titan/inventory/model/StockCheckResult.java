package com.titan.inventory.model;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;

/**
 * Result of checking stock levels for a product
 */
public record StockCheckResult(
    @JsonPropertyDescription("Stock Keeping Unit identifier")
    String sku,

    @JsonPropertyDescription("Product name")
    String productName,

    @JsonPropertyDescription("Division: AERO, ENERGY, MOBILITY, or INDUSTRIAL")
    String divisionId,

    @JsonPropertyDescription("Stock levels at each facility")
    List<StockLevel> stockByFacility,

    @JsonPropertyDescription("Total quantity across all facilities")
    int totalQuantity,

    @JsonPropertyDescription("Number of facilities with stock")
    int facilitiesWithStock,

    @JsonPropertyDescription("Whether any facility needs reorder")
    boolean needsReorder,

    @JsonPropertyDescription("List of facilities that need reorder")
    List<String> facilitiesNeedingReorder,

    @JsonPropertyDescription("Human-readable summary")
    String summary
) {}
