package com.titan.order.model;

import java.math.BigDecimal;
import java.util.List;

/**
 * Complete order status including event timeline.
 */
public record OrderStatusResult(
    String orderId,
    String customerId,
    String customerName,
    String orderDate,
    String requiredDate,
    String currentStatus,
    BigDecimal orderTotal,
    int lineCount,
    List<OrderLineStatus> lines,
    List<OrderEvent> events,
    List<ShipmentStatus> shipments,
    String summary
) {}
