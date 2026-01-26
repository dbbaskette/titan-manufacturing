package com.titan.order.model;

import java.math.BigDecimal;
import java.util.List;

/**
 * Result of order validation including inventory, credit, and contract checks.
 */
public record ValidationResult(
    String orderId,
    String customerId,
    String customerName,
    boolean isValid,
    boolean inventoryAvailable,
    boolean creditApproved,
    boolean contractValid,
    List<ValidationIssue> issues,
    BigDecimal orderTotal,
    BigDecimal availableCredit,
    String contractType,
    int priorityLevel,
    String summary
) {}
