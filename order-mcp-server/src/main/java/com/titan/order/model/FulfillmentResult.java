package com.titan.order.model;

import java.math.BigDecimal;
import java.util.List;

/**
 * Result of initiating order fulfillment including allocation and shipment plan.
 */
public record FulfillmentResult(
    String orderId,
    String customerId,
    boolean success,
    String fulfillmentStatus,
    List<AllocationDetail> allocations,
    List<PlannedShipment> plannedShipments,
    boolean isExpedited,
    String estimatedDeliveryDate,
    BigDecimal totalCost,
    String summary
) {}
