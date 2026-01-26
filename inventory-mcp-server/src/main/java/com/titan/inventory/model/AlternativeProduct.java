package com.titan.inventory.model;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.math.BigDecimal;

/**
 * Alternative product or supplier information
 */
public record AlternativeProduct(
    @JsonPropertyDescription("Alternative product SKU")
    String alternativeSku,

    @JsonPropertyDescription("Product name")
    String name,

    @JsonPropertyDescription("Supplier identifier")
    String supplierId,

    @JsonPropertyDescription("Supplier name")
    String supplierName,

    @JsonPropertyDescription("Supplier country")
    String supplierCountry,

    @JsonPropertyDescription("Unit cost from this supplier")
    BigDecimal unitCost,

    @JsonPropertyDescription("Lead time in days")
    int leadTimeDays,

    @JsonPropertyDescription("Supplier quality rating (0-5)")
    BigDecimal qualityRating,

    @JsonPropertyDescription("Available quantity from this source")
    int availableQuantity,

    @JsonPropertyDescription("Whether this is the primary supplier")
    boolean isPrimary,

    @JsonPropertyDescription("Reason this is suggested as alternative")
    String reason
) {}
