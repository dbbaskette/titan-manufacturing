package com.titan.governance.model;

import java.util.List;

/**
 * A compliance report for regulatory audits.
 */
public record ComplianceReport(
    String reportType,
    String generatedAt,
    String dateRangeStart,
    String dateRangeEnd,
    String overallStatus,
    List<ComplianceItem> items,
    List<String> recommendations,
    String summary
) {}
