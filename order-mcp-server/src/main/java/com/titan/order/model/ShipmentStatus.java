package com.titan.order.model;

/**
 * Status of a shipment for an order.
 */
public record ShipmentStatus(
    String shipmentId,
    String carrier,
    String trackingNumber,
    String status,
    String shipDate,
    String deliveryDate,
    String trackingUrl
) {}
