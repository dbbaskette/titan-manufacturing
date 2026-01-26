package com.titan.governance.model;

/**
 * A single compliance check item in a report.
 */
public record ComplianceItem(
    String category,
    String requirement,
    String status,  // COMPLIANT, NON_COMPLIANT, NEEDS_REVIEW
    String evidence,
    String notes
) {}
