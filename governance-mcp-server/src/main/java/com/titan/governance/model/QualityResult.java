package com.titan.governance.model;

import java.util.List;

/**
 * Data quality test results for a table.
 */
public record QualityResult(
    String tableName,
    String overallStatus,  // PASSED, WARNING, FAILED
    double qualityScore,
    List<QualityTest> tests,
    String lastTestRun,
    String summary
) {}
