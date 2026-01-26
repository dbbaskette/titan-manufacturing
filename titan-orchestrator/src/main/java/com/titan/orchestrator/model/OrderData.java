package com.titan.orchestrator.model;

import java.math.BigDecimal;
import java.util.List;

/**
 * Data models for Order Agent operations.
 */
public class OrderData {

    public record ValidationResult(
        String orderId,
        String customerId,
        String customerName,
        boolean isValid,
        boolean inventoryAvailable,
        boolean creditApproved,
        boolean contractValid,
        List<String> issues,
        BigDecimal orderTotal,
        String summary
    ) {}

    public record ContractTerms(
        String contractId,
        String customerId,
        String customerName,
        String contractType,
        int priorityLevel,
        BigDecimal discountPercent,
        int paymentTermsDays,
        BigDecimal creditLimit,
        BigDecimal availableCredit,
        boolean isActive,
        String summary
    ) {}

    public record FulfillmentResult(
        String orderId,
        boolean success,
        String fulfillmentStatus,
        List<String> allocations,
        List<String> plannedShipments,
        boolean isExpedited,
        String estimatedDeliveryDate,
        String summary
    ) {}

    public record OrderStatusResult(
        String orderId,
        String customerId,
        String customerName,
        String currentStatus,
        BigDecimal orderTotal,
        int lineCount,
        List<String> events,
        List<String> shipments,
        String summary
    ) {}

    public record OrderQueryResponse(
        String query,
        String response
    ) {}
}
