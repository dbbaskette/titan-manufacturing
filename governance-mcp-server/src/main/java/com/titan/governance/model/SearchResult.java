package com.titan.governance.model;

/**
 * A data asset search result.
 */
public record SearchResult(
    String name,
    String type,      // TABLE, VIEW, PIPELINE, DASHBOARD
    String domain,
    String description,
    String owner,
    double relevanceScore
) {}
