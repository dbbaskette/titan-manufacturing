package com.titan.order.model;

import java.math.BigDecimal;

/**
 * A planned shipment for an order.
 */
public record PlannedShipment(
    String shipmentId,
    String fromFacility,
    String carrier,
    String serviceLevel,
    String estimatedShipDate,
    String estimatedDeliveryDate,
    int itemCount,
    BigDecimal estimatedCost
) {}
