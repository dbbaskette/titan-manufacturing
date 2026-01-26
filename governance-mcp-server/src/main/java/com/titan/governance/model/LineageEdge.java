package com.titan.governance.model;

/**
 * An edge in the data lineage graph.
 */
public record LineageEdge(
    String fromId,
    String toId,
    String relationship  // DERIVED_FROM, FEEDS_INTO, REFERENCES
) {}
