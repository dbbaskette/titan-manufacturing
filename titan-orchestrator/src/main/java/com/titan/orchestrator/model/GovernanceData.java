package com.titan.orchestrator.model;

import java.math.BigDecimal;
import java.util.List;

/**
 * Data models for Governance Agent operations.
 */
public class GovernanceData {

    public record TableMetadata(
        String tableName,
        String schema,
        String description,
        String owner,
        String domain,
        List<String> columns,
        int rowCount,
        List<String> tags,
        String summary
    ) {}

    public record LineageResult(
        String tableName,
        String direction,
        List<String> upstreamSources,
        List<String> downstreamConsumers,
        String summary
    ) {}

    public record QualityResult(
        String tableName,
        String overallStatus,
        double qualityScore,
        List<String> tests,
        String summary
    ) {}

    public record SearchResult(
        String name,
        String type,
        String domain,
        String description,
        double relevanceScore
    ) {}

    public record GlossaryTerm(
        String term,
        String definition,
        String domain,
        List<String> relatedTerms,
        String summary
    ) {}

    public record BatchTraceResult(
        String batchId,
        String materialSku,
        String materialName,
        String supplierName,
        String receivedDate,
        BigDecimal quantity,
        String status,
        List<String> certifications,
        List<String> usageHistory,
        String summary
    ) {}

    public record ComplianceReport(
        String reportType,
        String dateRange,
        String overallStatus,
        List<String> items,
        List<String> recommendations,
        String summary
    ) {}

    public record GovernanceQueryResponse(
        String query,
        String response
    ) {}
}
