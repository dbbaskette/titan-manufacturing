package com.titan.governance.model;

/**
 * A node in the data lineage graph.
 */
public record LineageNode(
    String id,
    String name,
    String type,  // TABLE, VIEW, PIPELINE, REPORT
    String description,
    int depth
) {}
