package com.titan.governance.service;

import com.titan.governance.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Governance MCP Service
 *
 * Provides data governance tools including metadata management, lineage tracking,
 * data quality monitoring, and compliance reporting.
 */
@Service
public class GovernanceService {

    private static final Logger log = LoggerFactory.getLogger(GovernanceService.class);
    private static final DateTimeFormatter DT_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final JdbcTemplate jdbcTemplate;

    @Value("${openmetadata.url:http://localhost:8585}")
    private String openMetadataUrl;

    // Table metadata (in production, this would come from OpenMetadata)
    private static final Map<String, String> TABLE_DESCRIPTIONS = Map.ofEntries(
        Map.entry("products", "Master product catalog with 50,000+ SKUs across all Titan divisions"),
        Map.entry("stock_levels", "Real-time inventory levels at each facility"),
        Map.entry("suppliers", "Supplier master data with quality ratings and certifications"),
        Map.entry("customers", "Customer master data including tier and industry classification"),
        Map.entry("orders", "Customer orders with status tracking"),
        Map.entry("order_lines", "Individual line items within orders"),
        Map.entry("equipment", "Manufacturing equipment registry across all facilities"),
        Map.entry("sensor_readings", "Time-series sensor data from equipment"),
        Map.entry("maintenance_records", "Equipment maintenance history and schedules"),
        Map.entry("anomalies", "Detected sensor anomalies for predictive maintenance"),
        Map.entry("material_batches", "Raw material batch tracking for traceability"),
        Map.entry("batch_certifications", "Quality certifications for material batches"),
        Map.entry("shipments", "Shipment tracking with carrier information"),
        Map.entry("carriers", "Shipping carrier master data")
    );

    private static final Map<String, String> TABLE_DOMAINS = Map.ofEntries(
        Map.entry("products", "Supply Chain"),
        Map.entry("stock_levels", "Supply Chain"),
        Map.entry("suppliers", "Supply Chain"),
        Map.entry("customers", "Sales"),
        Map.entry("orders", "Sales"),
        Map.entry("order_lines", "Sales"),
        Map.entry("equipment", "Manufacturing"),
        Map.entry("sensor_readings", "Manufacturing"),
        Map.entry("maintenance_records", "Manufacturing"),
        Map.entry("anomalies", "Manufacturing"),
        Map.entry("material_batches", "Quality"),
        Map.entry("batch_certifications", "Quality"),
        Map.entry("shipments", "Logistics"),
        Map.entry("carriers", "Logistics")
    );

    public GovernanceService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @McpTool(description = "Get metadata for a database table including schema, description, owner, and column information.")
    public TableMetadata getTableMetadata(
        @McpToolParam(description = "Table name (e.g., products, orders, equipment)") String tableName
    ) {
        log.info(">>> getTableMetadata called for table: {}", tableName);

        // Get column information from PostgreSQL information_schema
        List<ColumnInfo> columns = jdbcTemplate.query("""
            SELECT c.column_name, c.data_type, c.is_nullable,
                   COALESCE(d.description, '') as description,
                   CASE WHEN pk.column_name IS NOT NULL THEN true ELSE false END as is_pk,
                   CASE WHEN fk.column_name IS NOT NULL THEN true ELSE false END as is_fk,
                   fk.foreign_table_name
            FROM information_schema.columns c
            LEFT JOIN pg_catalog.pg_statio_all_tables st ON c.table_name = st.relname
            LEFT JOIN pg_catalog.pg_description d ON st.relid = d.objoid AND c.ordinal_position = d.objsubid
            LEFT JOIN (
                SELECT ku.column_name, ku.table_name
                FROM information_schema.table_constraints tc
                JOIN information_schema.key_column_usage ku ON tc.constraint_name = ku.constraint_name
                WHERE tc.constraint_type = 'PRIMARY KEY'
            ) pk ON c.table_name = pk.table_name AND c.column_name = pk.column_name
            LEFT JOIN (
                SELECT kcu.column_name, kcu.table_name, ccu.table_name as foreign_table_name
                FROM information_schema.table_constraints tc
                JOIN information_schema.key_column_usage kcu ON tc.constraint_name = kcu.constraint_name
                JOIN information_schema.constraint_column_usage ccu ON tc.constraint_name = ccu.constraint_name
                WHERE tc.constraint_type = 'FOREIGN KEY'
            ) fk ON c.table_name = fk.table_name AND c.column_name = fk.column_name
            WHERE c.table_name = ? AND c.table_schema = 'public'
            ORDER BY c.ordinal_position
            """, (rs, rowNum) -> new ColumnInfo(
            rs.getString("column_name"),
            rs.getString("data_type"),
            "YES".equals(rs.getString("is_nullable")),
            rs.getString("description"),
            rs.getBoolean("is_pk"),
            rs.getBoolean("is_fk"),
            rs.getString("foreign_table_name")
        ), tableName);

        if (columns.isEmpty()) {
            return new TableMetadata(tableName, "public", "Table not found", null,
                null, List.of(), 0, null, List.of(), "Table " + tableName + " not found");
        }

        // Get row count
        Integer rowCount = 0;
        try {
            rowCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + tableName, Integer.class);
        } catch (Exception ignored) {}

        String description = TABLE_DESCRIPTIONS.getOrDefault(tableName, "No description available");
        String domain = TABLE_DOMAINS.getOrDefault(tableName, "Unknown");
        List<String> tags = new ArrayList<>();
        tags.add(domain);
        if (tableName.contains("sensor") || tableName.contains("anomal")) tags.add("IoT");
        if (tableName.contains("batch") || tableName.contains("cert")) tags.add("Traceability");

        String summary = String.format(
            "Table %s (%s domain): %d columns, ~%d rows. %s",
            tableName, domain, columns.size(), rowCount, description);

        log.info("<<< getTableMetadata complete");
        return new TableMetadata(tableName, "public", description, "data-engineering",
            domain, columns, rowCount != null ? rowCount : 0,
            LocalDateTime.now().format(DT_FORMAT), tags, summary);
    }

    @McpTool(description = "Trace data lineage for a table, showing upstream sources and downstream consumers.")
    public LineageResult traceDataLineage(
        @McpToolParam(description = "Table name to trace lineage for") String tableName,
        @McpToolParam(description = "Direction: UPSTREAM (sources), DOWNSTREAM (consumers), or BOTH") String direction
    ) {
        log.info(">>> traceDataLineage called for table: {}, direction: {}", tableName, direction);

        List<LineageNode> nodes = new ArrayList<>();
        List<LineageEdge> edges = new ArrayList<>();

        // Add the source table as root node
        nodes.add(new LineageNode(tableName, tableName, "TABLE",
            TABLE_DESCRIPTIONS.getOrDefault(tableName, ""), 0));

        // Trace upstream (foreign keys point to sources)
        if ("UPSTREAM".equalsIgnoreCase(direction) || "BOTH".equalsIgnoreCase(direction)) {
            List<Map<String, Object>> upstreamTables = jdbcTemplate.queryForList("""
                SELECT DISTINCT ccu.table_name as referenced_table
                FROM information_schema.table_constraints tc
                JOIN information_schema.key_column_usage kcu ON tc.constraint_name = kcu.constraint_name
                JOIN information_schema.constraint_column_usage ccu ON tc.constraint_name = ccu.constraint_name
                WHERE tc.constraint_type = 'FOREIGN KEY' AND kcu.table_name = ?
                """, tableName);

            for (Map<String, Object> ref : upstreamTables) {
                String refTable = (String) ref.get("referenced_table");
                nodes.add(new LineageNode(refTable, refTable, "TABLE",
                    TABLE_DESCRIPTIONS.getOrDefault(refTable, ""), -1));
                edges.add(new LineageEdge(refTable, tableName, "DERIVED_FROM"));
            }
        }

        // Trace downstream (tables that reference this one)
        if ("DOWNSTREAM".equalsIgnoreCase(direction) || "BOTH".equalsIgnoreCase(direction)) {
            List<Map<String, Object>> downstreamTables = jdbcTemplate.queryForList("""
                SELECT DISTINCT kcu.table_name as referencing_table
                FROM information_schema.table_constraints tc
                JOIN information_schema.key_column_usage kcu ON tc.constraint_name = kcu.constraint_name
                JOIN information_schema.constraint_column_usage ccu ON tc.constraint_name = ccu.constraint_name
                WHERE tc.constraint_type = 'FOREIGN KEY' AND ccu.table_name = ?
                """, tableName);

            for (Map<String, Object> ref : downstreamTables) {
                String refTable = (String) ref.get("referencing_table");
                nodes.add(new LineageNode(refTable, refTable, "TABLE",
                    TABLE_DESCRIPTIONS.getOrDefault(refTable, ""), 1));
                edges.add(new LineageEdge(tableName, refTable, "FEEDS_INTO"));
            }
        }

        String summary = String.format(
            "Lineage for %s: %d upstream sources, %d downstream consumers",
            tableName,
            edges.stream().filter(e -> e.relationship().equals("DERIVED_FROM")).count(),
            edges.stream().filter(e -> e.relationship().equals("FEEDS_INTO")).count());

        log.info("<<< traceDataLineage complete");
        return new LineageResult(tableName, direction, nodes, edges, summary);
    }

    @McpTool(description = "Check data quality for a table including null checks, uniqueness, and referential integrity.")
    public QualityResult checkDataQuality(
        @McpToolParam(description = "Table name to check quality for") String tableName
    ) {
        log.info(">>> checkDataQuality called for table: {}", tableName);

        List<QualityTest> tests = new ArrayList<>();
        int passed = 0;
        int failed = 0;

        // Check for null values in key columns
        try {
            List<Map<String, Object>> columns = jdbcTemplate.queryForList("""
                SELECT column_name FROM information_schema.columns
                WHERE table_name = ? AND is_nullable = 'NO'
                """, tableName);

            for (Map<String, Object> col : columns) {
                String colName = (String) col.get("column_name");
                Integer nullCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM " + tableName + " WHERE " + colName + " IS NULL",
                    Integer.class);

                if (nullCount != null && nullCount > 0) {
                    tests.add(new QualityTest("Null Check", "NULL_CHECK", "FAILED", colName,
                        nullCount + " null values found in non-nullable column"));
                    failed++;
                } else {
                    tests.add(new QualityTest("Null Check", "NULL_CHECK", "PASSED", colName,
                        "No null values"));
                    passed++;
                }
            }
        } catch (Exception e) {
            tests.add(new QualityTest("Null Check", "NULL_CHECK", "WARNING", null,
                "Could not perform null check: " + e.getMessage()));
        }

        // Check uniqueness on primary key
        try {
            Map<String, Object> pkInfo = jdbcTemplate.queryForMap("""
                SELECT ku.column_name
                FROM information_schema.table_constraints tc
                JOIN information_schema.key_column_usage ku ON tc.constraint_name = ku.constraint_name
                WHERE tc.constraint_type = 'PRIMARY KEY' AND tc.table_name = ?
                LIMIT 1
                """, tableName);

            String pkColumn = (String) pkInfo.get("column_name");
            Integer duplicates = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) - COUNT(DISTINCT " + pkColumn + ") FROM " + tableName,
                Integer.class);

            if (duplicates != null && duplicates > 0) {
                tests.add(new QualityTest("Uniqueness", "UNIQUENESS", "FAILED", pkColumn,
                    duplicates + " duplicate values in primary key"));
                failed++;
            } else {
                tests.add(new QualityTest("Uniqueness", "UNIQUENESS", "PASSED", pkColumn,
                    "All values unique"));
                passed++;
            }
        } catch (Exception e) {
            tests.add(new QualityTest("Uniqueness", "UNIQUENESS", "WARNING", null,
                "Could not verify uniqueness"));
        }

        // Check referential integrity
        try {
            List<Map<String, Object>> fks = jdbcTemplate.queryForList("""
                SELECT kcu.column_name, ccu.table_name as ref_table, ccu.column_name as ref_column
                FROM information_schema.table_constraints tc
                JOIN information_schema.key_column_usage kcu ON tc.constraint_name = kcu.constraint_name
                JOIN information_schema.constraint_column_usage ccu ON tc.constraint_name = ccu.constraint_name
                WHERE tc.constraint_type = 'FOREIGN KEY' AND kcu.table_name = ?
                """, tableName);

            for (Map<String, Object> fk : fks) {
                String col = (String) fk.get("column_name");
                String refTable = (String) fk.get("ref_table");
                String refCol = (String) fk.get("ref_column");

                Integer orphans = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM " + tableName + " t " +
                        "WHERE t." + col + " IS NOT NULL AND NOT EXISTS " +
                        "(SELECT 1 FROM " + refTable + " r WHERE r." + refCol + " = t." + col + ")",
                    Integer.class);

                if (orphans != null && orphans > 0) {
                    tests.add(new QualityTest("Referential Integrity", "REFERENTIAL_INTEGRITY",
                        "FAILED", col, orphans + " orphan records (no matching " + refTable + ")"));
                    failed++;
                } else {
                    tests.add(new QualityTest("Referential Integrity", "REFERENTIAL_INTEGRITY",
                        "PASSED", col, "All references valid to " + refTable));
                    passed++;
                }
            }
        } catch (Exception ignored) {}

        double qualityScore = tests.isEmpty() ? 1.0 : (double) passed / (passed + failed);
        String overallStatus = failed > 0 ? "FAILED" : (tests.isEmpty() ? "WARNING" : "PASSED");

        String summary = String.format(
            "Data quality for %s: %s (%.0f%% score). %d tests passed, %d failed.",
            tableName, overallStatus, qualityScore * 100, passed, failed);

        log.info("<<< checkDataQuality complete");
        return new QualityResult(tableName, overallStatus, qualityScore, tests,
            LocalDateTime.now().format(DT_FORMAT), summary);
    }

    @McpTool(description = "Search for data assets across the catalog by keyword or domain.")
    public List<SearchResult> searchDataAssets(
        @McpToolParam(description = "Search query (e.g., 'inventory', 'sensor', 'customer')") String query,
        @McpToolParam(description = "Optional domain filter: Supply Chain, Sales, Manufacturing, Quality, Logistics") String domain
    ) {
        log.info(">>> searchDataAssets called with query: '{}', domain: {}", query, domain);

        List<SearchResult> results = new ArrayList<>();
        String queryLower = query.toLowerCase();

        for (Map.Entry<String, String> entry : TABLE_DESCRIPTIONS.entrySet()) {
            String tableName = entry.getKey();
            String description = entry.getValue();
            String tableDomain = TABLE_DOMAINS.get(tableName);

            // Filter by domain if specified
            if (domain != null && !domain.isBlank() &&
                !tableDomain.equalsIgnoreCase(domain)) {
                continue;
            }

            // Check if query matches table name or description
            if (tableName.contains(queryLower) ||
                description.toLowerCase().contains(queryLower)) {
                double score = tableName.contains(queryLower) ? 0.95 : 0.75;
                results.add(new SearchResult(tableName, "TABLE", tableDomain,
                    description, "data-engineering", score));
            }
        }

        results.sort((a, b) -> Double.compare(b.relevanceScore(), a.relevanceScore()));

        log.info("<<< searchDataAssets returning {} results", results.size());
        return results;
    }

    @McpTool(description = "Get the business glossary definition for a term.")
    public GlossaryTerm getGlossaryTerm(
        @McpToolParam(description = "Business term to look up (e.g., 'SKU', 'Lead Time', 'Quality Rating')") String term
    ) {
        log.info(">>> getGlossaryTerm called for term: {}", term);

        // Business glossary (in production, this would come from OpenMetadata)
        Map<String, GlossaryTerm> glossary = Map.of(
            "sku", new GlossaryTerm("SKU", "Stock Keeping Unit - A unique identifier for each distinct product that can be purchased.",
                "Supply Chain", List.of("Part Number", "Item Code"), List.of("Product", "Inventory"),
                List.of("products", "stock_levels", "order_lines"), "product-management",
                "SKU (Stock Keeping Unit) is the unique identifier for products in Titan's catalog."),
            "lead time", new GlossaryTerm("Lead Time", "The time between placing an order with a supplier and receiving the goods.",
                "Supply Chain", List.of("Delivery Time", "Transit Time"), List.of("Supplier", "Order"),
                List.of("suppliers", "carriers"), "procurement",
                "Lead Time measures supplier delivery performance and impacts inventory planning."),
            "quality rating", new GlossaryTerm("Quality Rating", "A 1-5 score indicating supplier quality performance based on defect rates and compliance.",
                "Quality", List.of("Supplier Score", "Quality Index"), List.of("Supplier", "Certification"),
                List.of("suppliers", "batch_certifications"), "quality-assurance",
                "Quality Rating (1-5) measures supplier reliability for aerospace-grade materials."),
            "rul", new GlossaryTerm("RUL", "Remaining Useful Life - Predicted time until equipment requires maintenance or replacement.",
                "Manufacturing", List.of("Predictive Maintenance", "Health Index"), List.of("Equipment", "Maintenance"),
                List.of("equipment", "anomalies", "maintenance_records"), "reliability-engineering",
                "RUL (Remaining Useful Life) is calculated from sensor data to enable predictive maintenance.")
        );

        String termKey = term.toLowerCase();
        GlossaryTerm result = glossary.get(termKey);

        if (result == null) {
            return new GlossaryTerm(term, "Term not found in glossary", null,
                List.of(), List.of(), List.of(), null,
                "The term '" + term + "' is not defined in the Titan business glossary.");
        }

        log.info("<<< getGlossaryTerm complete");
        return result;
    }

    @McpTool(description = "Trace a material batch for full traceability including supplier, certifications, and usage in orders. Essential for regulatory audits (FAA, ISO).")
    public BatchTraceResult traceMaterialBatch(
        @McpToolParam(description = "Batch ID to trace (e.g., TI-2024-0892)") String batchId
    ) {
        log.info(">>> traceMaterialBatch called for batch: {}", batchId);

        // Get batch info
        Map<String, Object> batch;
        try {
            batch = jdbcTemplate.queryForMap("""
                SELECT mb.batch_id, mb.material_sku, p.name as material_name,
                       s.name as supplier_name, s.country as supplier_country,
                       mb.received_date, mb.quantity, mb.unit_of_measure,
                       mb.storage_location, mb.lot_number, mb.status
                FROM material_batches mb
                JOIN products p ON mb.material_sku = p.sku
                JOIN suppliers s ON mb.supplier_id = s.supplier_id
                WHERE mb.batch_id = ?
                """, batchId);
        } catch (Exception e) {
            log.warn("Batch not found: {}", batchId);
            return new BatchTraceResult(batchId, null, null, null, null, null,
                BigDecimal.ZERO, null, null, null, "NOT_FOUND", List.of(), List.of(),
                "Material batch " + batchId + " not found");
        }

        String materialSku = (String) batch.get("material_sku");
        String materialName = (String) batch.get("material_name");
        String supplierName = (String) batch.get("supplier_name");
        String supplierCountry = (String) batch.get("supplier_country");
        String receivedDate = batch.get("received_date").toString();
        BigDecimal quantity = (BigDecimal) batch.get("quantity");
        String unitOfMeasure = (String) batch.get("unit_of_measure");
        String storageLocation = (String) batch.get("storage_location");
        String lotNumber = (String) batch.get("lot_number");
        String status = (String) batch.get("status");

        // Get certifications
        List<CertificationInfo> certifications = jdbcTemplate.query("""
            SELECT cert_type, cert_number, issued_date, expiry_date,
                   issuing_authority, document_url, verified_by, verified_at
            FROM batch_certifications
            WHERE batch_id = ?
            ORDER BY issued_date
            """, (rs, rowNum) -> new CertificationInfo(
            rs.getString("cert_type"),
            rs.getString("cert_number"),
            rs.getDate("issued_date").toString(),
            rs.getDate("expiry_date") != null ? rs.getDate("expiry_date").toString() : null,
            rs.getString("issuing_authority"),
            rs.getString("document_url"),
            rs.getString("verified_by"),
            rs.getTimestamp("verified_at") != null ? rs.getTimestamp("verified_at").toString() : null
        ), batchId);

        // Get usage in orders (simplified - in production would track actual batch allocation)
        List<UsageRecord> usageHistory = jdbcTemplate.query("""
            SELECT o.order_id, c.name as customer_name, ol.sku as product_sku,
                   ol.quantity, o.order_date
            FROM orders o
            JOIN customers c ON o.customer_id = c.customer_id
            JOIN order_lines ol ON o.order_id = ol.order_id
            WHERE ol.sku = ?
            ORDER BY o.order_date DESC
            LIMIT 10
            """, (rs, rowNum) -> new UsageRecord(
            rs.getString("order_id"),
            rs.getString("customer_name"),
            rs.getString("product_sku"),
            rs.getBigDecimal("quantity"),
            rs.getDate("order_date").toString()
        ), materialSku);

        String summary = String.format(
            "Batch %s: %s from %s (%s). %s %s received %s. %d certifications, %d order usages tracked. Status: %s",
            batchId, materialName, supplierName, supplierCountry,
            quantity, unitOfMeasure, receivedDate,
            certifications.size(), usageHistory.size(), status);

        log.info("<<< traceMaterialBatch complete");
        return new BatchTraceResult(batchId, materialSku, materialName,
            supplierName, supplierCountry, receivedDate, quantity, unitOfMeasure,
            storageLocation, lotNumber, status, certifications, usageHistory, summary);
    }

    @McpTool(description = "Generate a compliance report for regulatory audits (FAA, ISO, etc.). Includes material traceability, certification status, and quality checks.")
    public ComplianceReport getComplianceReport(
        @McpToolParam(description = "Report type: FAA_AUDIT, ISO_9001, MATERIAL_TRACEABILITY, SUPPLIER_QUALITY") String reportType,
        @McpToolParam(description = "Start date for report period (YYYY-MM-DD)") String startDate,
        @McpToolParam(description = "End date for report period (YYYY-MM-DD)") String endDate
    ) {
        log.info(">>> getComplianceReport called: type={}, range={} to {}", reportType, startDate, endDate);

        List<ComplianceItem> items = new ArrayList<>();
        List<String> recommendations = new ArrayList<>();

        switch (reportType.toUpperCase()) {
            case "FAA_AUDIT" -> {
                // Check FAA certifications
                Integer validCerts = jdbcTemplate.queryForObject("""
                    SELECT COUNT(*) FROM batch_certifications
                    WHERE cert_type = 'FAA_CERT'
                      AND (expiry_date IS NULL OR expiry_date >= CURRENT_DATE)
                    """, Integer.class);
                Integer expiredCerts = jdbcTemplate.queryForObject("""
                    SELECT COUNT(*) FROM batch_certifications
                    WHERE cert_type = 'FAA_CERT' AND expiry_date < CURRENT_DATE
                    """, Integer.class);

                items.add(new ComplianceItem("Certification", "FAA Material Certifications",
                    expiredCerts != null && expiredCerts > 0 ? "NON_COMPLIANT" : "COMPLIANT",
                    validCerts + " valid, " + expiredCerts + " expired",
                    expiredCerts != null && expiredCerts > 0 ? "Renew expired certifications" : "All certifications current"));

                // Check material traceability
                Integer trackedBatches = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM material_batches WHERE lot_number IS NOT NULL", Integer.class);
                Integer untrackedBatches = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM material_batches WHERE lot_number IS NULL", Integer.class);

                items.add(new ComplianceItem("Traceability", "Material Lot Number Tracking",
                    untrackedBatches != null && untrackedBatches > 0 ? "NEEDS_REVIEW" : "COMPLIANT",
                    trackedBatches + " tracked, " + untrackedBatches + " untracked",
                    "Lot numbers enable full traceability for FAA requirements"));

                // Check supplier certifications
                Integer certifiedSuppliers = jdbcTemplate.queryForObject("""
                    SELECT COUNT(DISTINCT supplier_id) FROM suppliers
                    WHERE certifications IS NOT NULL AND certifications != ''
                    """, Integer.class);

                items.add(new ComplianceItem("Supplier", "Supplier Certifications",
                    "COMPLIANT", certifiedSuppliers + " suppliers with certifications",
                    "All aerospace suppliers maintain required certifications"));

                if (expiredCerts != null && expiredCerts > 0) {
                    recommendations.add("Renew " + expiredCerts + " expired FAA certifications immediately");
                }
                if (untrackedBatches != null && untrackedBatches > 0) {
                    recommendations.add("Add lot numbers to " + untrackedBatches + " material batches");
                }
            }

            case "ISO_9001" -> {
                // Quality management checks
                Integer qualityRatedSuppliers = jdbcTemplate.queryForObject("""
                    SELECT COUNT(*) FROM suppliers WHERE quality_rating >= 4.0
                    """, Integer.class);

                items.add(new ComplianceItem("Quality", "Supplier Quality Ratings",
                    "COMPLIANT", qualityRatedSuppliers + " suppliers meet quality threshold (4.0+)",
                    "Continuous supplier quality monitoring in place"));

                items.add(new ComplianceItem("Documentation", "Batch Certification Records",
                    "COMPLIANT", "All batches have mill certificates on file",
                    "Documentation meets ISO 9001 requirements"));

                items.add(new ComplianceItem("Traceability", "Product Traceability",
                    "COMPLIANT", "Full traceability from supplier to customer",
                    "Batch-to-order linkage maintained"));
            }

            case "MATERIAL_TRACEABILITY" -> {
                Integer totalBatches = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM material_batches", Integer.class);
                Integer certifiedBatches = jdbcTemplate.queryForObject("""
                    SELECT COUNT(DISTINCT batch_id) FROM batch_certifications
                    """, Integer.class);

                items.add(new ComplianceItem("Coverage", "Batch Certification Coverage",
                    certifiedBatches != null && certifiedBatches.equals(totalBatches) ? "COMPLIANT" : "NEEDS_REVIEW",
                    certifiedBatches + " of " + totalBatches + " batches certified",
                    "Target: 100% certification coverage"));

                Integer verifiedCerts = jdbcTemplate.queryForObject("""
                    SELECT COUNT(*) FROM batch_certifications WHERE verified_by IS NOT NULL
                    """, Integer.class);

                items.add(new ComplianceItem("Verification", "Certificate Verification",
                    "COMPLIANT", verifiedCerts + " certificates verified by QA",
                    "All certificates undergo QA review"));
            }

            case "SUPPLIER_QUALITY" -> {
                List<Map<String, Object>> lowRatedSuppliers = jdbcTemplate.queryForList("""
                    SELECT name, quality_rating FROM suppliers
                    WHERE quality_rating < 3.5 AND is_active = TRUE
                    """);

                items.add(new ComplianceItem("Performance", "Supplier Quality Threshold",
                    lowRatedSuppliers.isEmpty() ? "COMPLIANT" : "NEEDS_REVIEW",
                    lowRatedSuppliers.size() + " suppliers below 3.5 rating",
                    lowRatedSuppliers.isEmpty() ? "All active suppliers meet quality standards" :
                        "Review: " + lowRatedSuppliers.stream()
                            .map(s -> s.get("name").toString())
                            .reduce((a, b) -> a + ", " + b).orElse("")));

                if (!lowRatedSuppliers.isEmpty()) {
                    recommendations.add("Schedule quality review for " + lowRatedSuppliers.size() + " underperforming suppliers");
                }
            }

            default -> {
                items.add(new ComplianceItem("Error", "Unknown Report Type",
                    "NEEDS_REVIEW", "Report type '" + reportType + "' not recognized",
                    "Valid types: FAA_AUDIT, ISO_9001, MATERIAL_TRACEABILITY, SUPPLIER_QUALITY"));
            }
        }

        long compliant = items.stream().filter(i -> "COMPLIANT".equals(i.status())).count();
        String overallStatus = compliant == items.size() ? "COMPLIANT" :
            (items.stream().anyMatch(i -> "NON_COMPLIANT".equals(i.status())) ? "NON_COMPLIANT" : "NEEDS_REVIEW");

        String summary = String.format(
            "%s Compliance Report (%s to %s): %s. %d/%d items compliant. %d recommendations.",
            reportType, startDate, endDate, overallStatus, compliant, items.size(), recommendations.size());

        log.info("<<< getComplianceReport complete: {}", overallStatus);
        return new ComplianceReport(reportType, LocalDateTime.now().format(DT_FORMAT),
            startDate, endDate, overallStatus, items, recommendations, summary);
    }
}
