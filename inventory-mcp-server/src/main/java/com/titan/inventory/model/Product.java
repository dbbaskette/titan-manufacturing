package com.titan.inventory.model;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.math.BigDecimal;

/**
 * Product catalog information
 */
public record Product(
    @JsonPropertyDescription("Stock Keeping Unit identifier")
    String sku,

    @JsonPropertyDescription("Product name")
    String name,

    @JsonPropertyDescription("Detailed product description")
    String description,

    @JsonPropertyDescription("Division: AERO, ENERGY, MOBILITY, or INDUSTRIAL")
    String divisionId,

    @JsonPropertyDescription("Product category")
    String category,

    @JsonPropertyDescription("Product subcategory")
    String subcategory,

    @JsonPropertyDescription("Unit price in USD")
    BigDecimal unitPrice,

    @JsonPropertyDescription("Weight in kilograms")
    BigDecimal weightKg,

    @JsonPropertyDescription("Lead time in days from supplier")
    int leadTimeDays,

    @JsonPropertyDescription("Minimum order quantity")
    int minOrderQty,

    @JsonPropertyDescription("Whether product is active")
    boolean isActive
) {}
