package com.titan.governance.model;

import java.util.List;

/**
 * Metadata about a database table.
 */
public record TableMetadata(
    String tableName,
    String schema,
    String description,
    String owner,
    String domain,
    List<ColumnInfo> columns,
    int rowCount,
    String lastUpdated,
    List<String> tags,
    String summary
) {}
