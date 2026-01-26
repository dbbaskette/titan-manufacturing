package com.titan.inventory.model;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.math.BigDecimal;

/**
 * Reorder calculation result
 */
public record ReorderCalculation(
    @JsonPropertyDescription("Stock Keeping Unit identifier")
    String sku,

    @JsonPropertyDescription("Product name")
    String productName,

    @JsonPropertyDescription("Facility identifier")
    String facilityId,

    @JsonPropertyDescription("Facility name")
    String facilityName,

    @JsonPropertyDescription("Current stock quantity")
    int currentStock,

    @JsonPropertyDescription("Configured reorder point")
    int reorderPoint,

    @JsonPropertyDescription("Calculated safety stock")
    int safetyStock,

    @JsonPropertyDescription("Economic Order Quantity")
    int economicOrderQty,

    @JsonPropertyDescription("Recommended order quantity")
    int recommendedOrder,

    @JsonPropertyDescription("Recommended order date")
    String recommendedOrderDate,

    @JsonPropertyDescription("Primary supplier for this product")
    String primarySupplier,

    @JsonPropertyDescription("Supplier lead time in days")
    int supplierLeadTimeDays,

    @JsonPropertyDescription("Estimated order cost")
    BigDecimal estimatedCost,

    @JsonPropertyDescription("Whether immediate reorder is needed")
    boolean urgentReorder,

    @JsonPropertyDescription("Human-readable summary")
    String summary
) {}
