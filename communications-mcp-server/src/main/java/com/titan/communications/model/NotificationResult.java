package com.titan.communications.model;

/**
 * Result of sending a notification to a customer.
 */
public record NotificationResult(
    String notificationId,
    String customerId,
    String customerName,
    String templateType,
    String subject,
    String recipient,
    boolean sent,
    String sentAt,
    String summary
) {}
