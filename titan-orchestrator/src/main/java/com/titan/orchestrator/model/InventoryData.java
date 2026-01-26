package com.titan.orchestrator.model;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.math.BigDecimal;
import java.util.List;

/**
 * Data models for Inventory Agent responses.
 */
public class InventoryData {

    /**
     * Stock level at a specific facility.
     */
    public record StockLevel(
        @JsonPropertyDescription("Product SKU")
        String sku,
        @JsonPropertyDescription("Facility ID")
        String facilityId,
        @JsonPropertyDescription("Facility name")
        String facilityName,
        @JsonPropertyDescription("Current quantity")
        int quantity,
        @JsonPropertyDescription("Reorder point threshold")
        int reorderPoint,
        @JsonPropertyDescription("Status: IN_STOCK, LOW_STOCK, OUT_OF_STOCK")
        String status
    ) {}

    /**
     * Result of checking stock levels.
     */
    public record StockCheckResult(
        @JsonPropertyDescription("Product SKU")
        String sku,
        @JsonPropertyDescription("Product name")
        String productName,
        @JsonPropertyDescription("Stock levels by facility")
        List<StockLevel> stockByFacility,
        @JsonPropertyDescription("Total quantity across all facilities")
        int totalQuantity,
        @JsonPropertyDescription("Whether any facility needs reorder")
        boolean needsReorder,
        @JsonPropertyDescription("Summary message")
        String summary
    ) {}

    /**
     * Product search result.
     */
    public record ProductSearchResult(
        @JsonPropertyDescription("Product SKU")
        String sku,
        @JsonPropertyDescription("Product name")
        String name,
        @JsonPropertyDescription("Product description")
        String description,
        @JsonPropertyDescription("Division ID")
        String divisionId,
        @JsonPropertyDescription("Category")
        String category,
        @JsonPropertyDescription("Unit price")
        BigDecimal unitPrice,
        @JsonPropertyDescription("Similarity score for semantic search")
        double similarityScore,
        @JsonPropertyDescription("Total stock across facilities")
        int totalStock
    ) {}

    /**
     * Alternative product or supplier.
     */
    public record AlternativeProduct(
        @JsonPropertyDescription("Alternative SKU")
        String alternativeSku,
        @JsonPropertyDescription("Product name")
        String name,
        @JsonPropertyDescription("Supplier ID")
        String supplierId,
        @JsonPropertyDescription("Supplier name")
        String supplierName,
        @JsonPropertyDescription("Unit cost")
        BigDecimal unitCost,
        @JsonPropertyDescription("Lead time in days")
        int leadTimeDays,
        @JsonPropertyDescription("Available quantity")
        int availableQuantity,
        @JsonPropertyDescription("Reason for suggestion")
        String reason
    ) {}

    /**
     * Reorder calculation result.
     */
    public record ReorderCalculation(
        @JsonPropertyDescription("Product SKU")
        String sku,
        @JsonPropertyDescription("Product name")
        String productName,
        @JsonPropertyDescription("Facility ID")
        String facilityId,
        @JsonPropertyDescription("Current stock")
        int currentStock,
        @JsonPropertyDescription("Reorder point")
        int reorderPoint,
        @JsonPropertyDescription("Recommended order quantity")
        int recommendedOrder,
        @JsonPropertyDescription("Recommended order date")
        String recommendedOrderDate,
        @JsonPropertyDescription("Primary supplier")
        String primarySupplier,
        @JsonPropertyDescription("Estimated cost")
        BigDecimal estimatedCost,
        @JsonPropertyDescription("Whether urgent reorder is needed")
        boolean urgentReorder,
        @JsonPropertyDescription("Summary message")
        String summary
    ) {}

    /**
     * Response to inventory query.
     */
    public record InventoryQueryResponse(
        @JsonPropertyDescription("Original query")
        String query,
        @JsonPropertyDescription("Response text")
        String response
    ) {}
}
