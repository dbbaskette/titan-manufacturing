package com.titan.inventory.model;

import java.math.BigDecimal;

public record CompatiblePart(
    String sku,
    String name,
    String partRole,
    boolean isPrimary,
    String notes,
    String category,
    BigDecimal unitPrice,
    int stockAtFacility,
    int totalStock,
    String stockStatus
) {}
