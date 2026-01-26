package com.titan.governance;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Titan Governance MCP Server
 *
 * Provides data governance tools for the Titan Manufacturing AI platform,
 * integrating with OpenMetadata for metadata management, lineage tracking,
 * and data quality monitoring.
 *
 * MCP Tools:
 * - get_table_metadata: Get table schema, description, owner
 * - trace_data_lineage: Trace data flow upstream/downstream
 * - check_data_quality: Get latest quality test results
 * - search_data_assets: Search across all data assets
 * - get_glossary_term: Get business glossary definition
 * - trace_material_batch: Full traceability for a material batch
 * - get_compliance_report: Generate compliance report for audits
 */
@SpringBootApplication
public class GovernanceApplication {

    public static void main(String[] args) {
        SpringApplication.run(GovernanceApplication.class, args);
    }
}
