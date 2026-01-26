package com.titan.order.model;

/**
 * An event in the order lifecycle.
 */
public record OrderEvent(
    String eventType,
    String timestamp,
    String createdBy,
    String notes
) {}
