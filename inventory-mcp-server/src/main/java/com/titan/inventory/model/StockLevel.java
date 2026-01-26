package com.titan.inventory.model;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/**
 * Stock level at a specific facility
 */
public record StockLevel(
    @JsonPropertyDescription("Stock Keeping Unit identifier")
    String sku,

    @JsonPropertyDescription("Facility identifier (e.g., PHX, MUC, SHA)")
    String facilityId,

    @JsonPropertyDescription("Facility name")
    String facilityName,

    @JsonPropertyDescription("Current quantity in stock")
    int quantity,

    @JsonPropertyDescription("Reorder point threshold")
    int reorderPoint,

    @JsonPropertyDescription("Date of last inventory count")
    String lastCountDate,

    @JsonPropertyDescription("Stock status: IN_STOCK, LOW_STOCK, OUT_OF_STOCK")
    String status
) {}
