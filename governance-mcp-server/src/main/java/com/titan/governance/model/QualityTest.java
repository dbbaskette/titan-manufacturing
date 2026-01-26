package com.titan.governance.model;

/**
 * A single data quality test result.
 */
public record QualityTest(
    String testName,
    String testType,  // NULL_CHECK, UNIQUENESS, REFERENTIAL_INTEGRITY, RANGE_CHECK
    String status,    // PASSED, FAILED, WARNING
    String column,
    String details
) {}
