package com.titan.communications.model;

/**
 * A drafted customer update ready for review/approval.
 */
public record DraftResult(
    String orderId,
    String customerId,
    String customerName,
    String updateType,
    String subject,
    String body,
    String orderStatus,
    String recommendedAction,
    String summary
) {}
