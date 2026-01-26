package com.titan.order.model;

/**
 * Detail of inventory allocation for an order line.
 */
public record AllocationDetail(
    String sku,
    String productName,
    int quantityOrdered,
    int quantityAllocated,
    String allocatedFromFacility,
    String batchId,
    String allocationStatus  // ALLOCATED, PARTIAL, BACKORDERED
) {}
