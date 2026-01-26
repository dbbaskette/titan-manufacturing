package com.titan.governance.model;

/**
 * Information about a table column.
 */
public record ColumnInfo(
    String name,
    String dataType,
    boolean nullable,
    String description,
    boolean isPrimaryKey,
    boolean isForeignKey,
    String referencesTable
) {}
