package com.titan.orchestrator.agent;

import com.embabel.agent.api.annotation.Action;
import com.embabel.agent.api.annotation.AchievesGoal;
import com.embabel.agent.api.annotation.Agent;
import com.embabel.agent.api.common.Ai;
import com.titan.orchestrator.model.GovernanceData.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Titan Governance Agent
 *
 * Provides data governance capabilities including metadata management,
 * lineage tracking, data quality monitoring, and compliance reporting.
 * Integrates with OpenMetadata via the Governance MCP Server.
 *
 * Key capabilities:
 * - Table metadata and schema information
 * - Data lineage tracing (upstream/downstream)
 * - Data quality test results
 * - Material batch traceability for FAA/ISO compliance
 * - Compliance report generation
 */
@Agent(description = "Titan Governance Agent - Data governance, lineage, and compliance management. " +
       "Provides metadata access, lineage tracing, quality monitoring, and regulatory compliance reporting.")
@Component
public class TitanGovernanceAgent {

    private static final Logger log = LoggerFactory.getLogger(TitanGovernanceAgent.class);

    /**
     * Get metadata for a table.
     */
    @Action(
        description = "Get metadata for a database table including schema, description, owner, and columns.",
        toolGroups = {"governance-tools"}
    )
    public TableMetadata getTableMetadata(String tableName, Ai ai) {
        log.info(">>> TitanGovernanceAgent.getTableMetadata for table: {}", tableName);

        TableMetadata result = ai.withAutoLlm().createObject(
            """
            Use the get_table_metadata tool to get information about table %s.
            Return the schema, description, owner, columns, and row count.
            """.formatted(tableName),
            TableMetadata.class
        );

        log.info("<<< getTableMetadata complete: {} columns, {} rows",
                 result.columns().size(), result.rowCount());
        return result;
    }

    /**
     * Trace data lineage.
     */
    @Action(
        description = "Trace data lineage for a table, showing upstream sources and downstream consumers.",
        toolGroups = {"governance-tools"}
    )
    public LineageResult traceDataLineage(String tableName, String direction, Ai ai) {
        log.info(">>> TitanGovernanceAgent.traceDataLineage for table: {}, direction: {}", tableName, direction);

        LineageResult result = ai.withAutoLlm().createObject(
            """
            Use the trace_data_lineage tool to trace lineage for table %s.
            Direction: %s
            Return the upstream sources and downstream consumers.
            """.formatted(tableName, direction != null ? direction : "BOTH"),
            LineageResult.class
        );

        log.info("<<< traceDataLineage complete: {} upstream, {} downstream",
                 result.upstreamSources().size(), result.downstreamConsumers().size());
        return result;
    }

    /**
     * Check data quality for a table.
     */
    @Action(
        description = "Check data quality for a table including null checks, uniqueness, and referential integrity.",
        toolGroups = {"governance-tools"}
    )
    public QualityResult checkDataQuality(String tableName, Ai ai) {
        log.info(">>> TitanGovernanceAgent.checkDataQuality for table: {}", tableName);

        QualityResult result = ai.withAutoLlm().createObject(
            """
            Use the check_data_quality tool to check quality for table %s.
            Return the overall status, quality score, and test results.
            """.formatted(tableName),
            QualityResult.class
        );

        log.info("<<< checkDataQuality complete: status={}, score={}",
                 result.overallStatus(), result.qualityScore());
        return result;
    }

    /**
     * Search data assets.
     */
    @Action(
        description = "Search for data assets across the catalog by keyword or domain.",
        toolGroups = {"governance-tools"}
    )
    public List<SearchResult> searchDataAssets(String query, String domain, Ai ai) {
        log.info(">>> TitanGovernanceAgent.searchDataAssets query: '{}', domain: {}", query, domain);

        @SuppressWarnings("unchecked")
        List<SearchResult> results = ai.withAutoLlm().createObject(
            """
            Use the search_data_assets tool to search for data assets matching: "%s"
            %s
            Return the list of matching assets with their details.
            """.formatted(query, domain != null ? "Filter by domain: " + domain : ""),
            List.class
        );

        log.info("<<< searchDataAssets complete: {} results", results != null ? results.size() : 0);
        return results;
    }

    /**
     * Trace a material batch for compliance.
     */
    @Action(
        description = "Trace a material batch for full traceability including supplier, certifications, and usage. Essential for FAA/ISO audits.",
        toolGroups = {"governance-tools"}
    )
    public BatchTraceResult traceMaterialBatch(String batchId, Ai ai) {
        log.info(">>> TitanGovernanceAgent.traceMaterialBatch for batch: {}", batchId);

        BatchTraceResult result = ai.withAutoLlm().createObject(
            """
            Use the trace_material_batch tool to get full traceability for batch %s.
            Return supplier info, certifications, storage location, and usage history.
            """.formatted(batchId),
            BatchTraceResult.class
        );

        log.info("<<< traceMaterialBatch complete: {} certifications, status={}",
                 result.certifications().size(), result.status());
        return result;
    }

    /**
     * Generate a compliance report.
     */
    @Action(
        description = "Generate a compliance report for regulatory audits (FAA, ISO, material traceability).",
        toolGroups = {"governance-tools"}
    )
    public ComplianceReport getComplianceReport(String reportType, String startDate, String endDate, Ai ai) {
        log.info(">>> TitanGovernanceAgent.getComplianceReport type: {}, range: {} to {}", reportType, startDate, endDate);

        ComplianceReport result = ai.withAutoLlm().createObject(
            """
            Use the get_compliance_report tool to generate a %s report.
            Date range: %s to %s
            Return the compliance status, items, and recommendations.
            """.formatted(reportType, startDate, endDate),
            ComplianceReport.class
        );

        log.info("<<< getComplianceReport complete: status={}", result.overallStatus());
        return result;
    }

    /**
     * Answer natural language governance queries.
     */
    @AchievesGoal(description = "Answer data governance, lineage, and compliance questions")
    @Action(
        description = "Process natural language governance queries using available tools",
        toolGroups = {"governance-tools"}
    )
    public GovernanceQueryResponse answerGovernanceQuery(String query, Ai ai) {
        log.info(">>> TitanGovernanceAgent.answerGovernanceQuery: {}", query);

        String response = ai.withAutoLlm().generateText(
            """
            You are a data governance assistant for Titan Manufacturing.
            Answer the following query using the governance tools available to you:
            - get_table_metadata: Get table schema, description, owner
            - trace_data_lineage: Trace data flow upstream/downstream
            - check_data_quality: Get data quality test results
            - search_data_assets: Search across data catalog
            - get_glossary_term: Get business glossary definition
            - trace_material_batch: Full batch traceability for audits
            - get_compliance_report: Generate compliance report

            Query: %s

            Provide a helpful, detailed response based on the data from the tools.
            """.formatted(query)
        );

        log.info("<<< answerGovernanceQuery complete");
        return new GovernanceQueryResponse(query, response);
    }
}
