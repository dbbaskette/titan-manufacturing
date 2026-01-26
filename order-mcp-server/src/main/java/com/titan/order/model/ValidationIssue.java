package com.titan.order.model;

/**
 * An issue found during order validation.
 */
public record ValidationIssue(
    String issueType,  // INVENTORY, CREDIT, CONTRACT, OTHER
    String severity,   // ERROR, WARNING, INFO
    String sku,        // Optional: affected SKU
    String message
) {}
