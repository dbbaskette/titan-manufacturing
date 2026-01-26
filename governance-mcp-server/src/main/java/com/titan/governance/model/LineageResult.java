package com.titan.governance.model;

import java.util.List;

/**
 * Data lineage tracing result.
 */
public record LineageResult(
    String tableName,
    String direction,
    List<LineageNode> nodes,
    List<LineageEdge> edges,
    String summary
) {}
