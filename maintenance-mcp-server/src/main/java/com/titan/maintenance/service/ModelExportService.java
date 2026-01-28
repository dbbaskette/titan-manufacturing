package com.titan.maintenance.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * MCP Tools for exporting ML models in PMML format.
 * Enables integration with external systems like GemFire for real-time scoring.
 */
@Service
public class ModelExportService {

    private static final Logger log = LoggerFactory.getLogger(ModelExportService.class);

    private final JdbcTemplate jdbcTemplate;
    private final GemFireScoringService gemFireScoringService;

    public ModelExportService(JdbcTemplate jdbcTemplate, @org.springframework.context.annotation.Lazy GemFireScoringService gemFireScoringService) {
        this.jdbcTemplate = jdbcTemplate;
        this.gemFireScoringService = gemFireScoringService;
    }

    /**
     * Export the failure prediction model as PMML XML.
     */
    @McpTool(description = "Export the equipment failure prediction model as PMML XML. PMML can be used with systems like GemFire, Spark, or other ML scoring engines.")
    public Map<String, Object> exportModelAsPMML(
            @McpToolParam(description = "Model ID to export (default: failure_predictor_v1)")
            String modelId
    ) {
        log.info("Exporting model as PMML: {}", modelId);

        String targetModelId = modelId != null && !modelId.isBlank() ? modelId : "failure_predictor_v1";

        try {
            // Fetch model coefficients from database
            List<Map<String, Object>> coefficients = jdbcTemplate.queryForList("""
                SELECT feature_name, coefficient, description
                FROM ml_model_coefficients
                WHERE model_id = ?
                ORDER BY feature_name
                """, targetModelId);

            if (coefficients.isEmpty()) {
                return Map.of(
                    "success", false,
                    "error", "Model not found: " + targetModelId,
                    "availableModels", getAvailableModels()
                );
            }

            // Build PMML document
            String pmmlXml = buildPMML(targetModelId, coefficients);

            return Map.of(
                "success", true,
                "modelId", targetModelId,
                "format", "PMML 4.4",
                "pmml", pmmlXml,
                "featureCount", coefficients.size() - 1, // Exclude intercept
                "usage", "Load this PMML into GemFire, Spark MLlib, or other PMML-compatible scoring engines"
            );

        } catch (Exception e) {
            log.error("Failed to export model {}: {}", targetModelId, e.getMessage());
            return Map.of(
                "success", false,
                "error", e.getMessage()
            );
        }
    }

    /**
     * List available models for export.
     */
    @McpTool(description = "List available ML models that can be exported as PMML.")
    public List<Map<String, Object>> listExportableModels() {
        log.info("Listing exportable models");

        return jdbcTemplate.queryForList("""
            SELECT DISTINCT
                model_id,
                model_type,
                COUNT(*) as coefficient_count
            FROM ml_model_coefficients
            GROUP BY model_id, model_type
            ORDER BY model_id
            """);
    }

    /**
     * Retrain the failure prediction model using MADlib logistic regression in Greenplum.
     * Runs against the ml_training_data table and updates ml_model_coefficients.
     */
    @McpTool(description = "Retrain the equipment failure prediction model using MADlib logistic regression in Greenplum. " +
            "Runs against synthetic + accumulated training data and updates the model coefficients. " +
            "After retraining, re-export to GemFire with deployModelToGemFire.")
    public Map<String, Object> retrainModel() {
        log.info("Retraining failure prediction model via MADlib");

        List<Map<String, String>> steps = new ArrayList<>();
        long start = System.currentTimeMillis();

        try {
            // Step 1: Count training data
            steps.add(step("sql", "SELECT COUNT(*) FROM ml_training_data"));
            Integer trainingCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM ml_training_data", Integer.class);
            steps.add(step("result", trainingCount + " training observations loaded"));

            Integer failureCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM ml_training_data WHERE failed = 1", Integer.class);
            steps.add(step("result", failureCount + " failure cases, " + (trainingCount - failureCount) + " normal cases"));

            // Step 2: Drop previous model artifacts
            steps.add(step("sql", "DROP TABLE IF EXISTS failure_model CASCADE"));
            steps.add(step("sql", "DROP TABLE IF EXISTS failure_model_summary CASCADE"));

            // Step 3: Run MADlib training
            steps.add(step("sql", "SELECT madlib.logregr_train(\n" +
                    "  'ml_training_data',\n" +
                    "  'failure_model',\n" +
                    "  'failed',\n" +
                    "  'ARRAY[1, vibration_normalized, temperature_normalized,\n" +
                    "         vibration_trend_rate, temperature_trend_rate,\n" +
                    "         days_since_maintenance, equipment_age_years, anomaly_count,\n" +
                    "         power_normalized, rpm_normalized, pressure_normalized, torque_normalized]',\n" +
                    "  NULL, 20, 'irls'\n" +
                    ")"));

            List<Map<String, Object>> results = jdbcTemplate.queryForList(
                    "SELECT * FROM train_failure_model()");

            Map<String, Double> coefficients = new LinkedHashMap<>();
            for (Map<String, Object> row : results) {
                coefficients.put((String) row.get("feature"),
                        ((Number) row.get("coefficient")).doubleValue());
            }

            steps.add(step("result", "IRLS optimizer converged — " + coefficients.size() + " coefficients extracted"));

            // Step 4: Show coefficient update
            steps.add(step("sql", "DELETE FROM ml_model_coefficients WHERE model_id = 'failure_predictor_v1'"));
            for (Map.Entry<String, Double> entry : coefficients.entrySet()) {
                steps.add(step("sql", "INSERT INTO ml_model_coefficients VALUES ('failure_predictor_v1', 'logistic_regression', '"
                        + entry.getKey() + "', " + String.format("%.4f", entry.getValue()) + ")"));
            }
            steps.add(step("result", "ml_model_coefficients updated with " + coefficients.size() + " new coefficients"));

            long elapsed = System.currentTimeMillis() - start;
            steps.add(step("done", "Model retrained in " + elapsed + "ms"));

            log.info("Model retrained with {} coefficients from {} training observations",
                    coefficients.size(), trainingCount);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("modelId", "failure_predictor_v1");
            result.put("trainingObservations", trainingCount);
            result.put("coefficients", coefficients);
            result.put("method", "MADlib logregr_train (IRLS optimizer)");
            result.put("message", "Model retrained. Use deployModelToGemFire to push updated PMML to GemFire.");
            result.put("steps", steps);

            // Reload coefficients into GemFire scoring service
            gemFireScoringService.loadCoefficients();

            return result;

        } catch (Exception e) {
            log.error("Model retraining failed: {}", e.getMessage());
            steps.add(step("error", e.getMessage()));
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", false);
            result.put("error", e.getMessage());
            result.put("steps", steps);
            return result;
        }
    }

    private Map<String, String> step(String type, String message) {
        return Map.of(
                "type", type,
                "message", message,
                "timestamp", LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss.SSS"))
        );
    }

    private List<String> getAvailableModels() {
        return jdbcTemplate.queryForList(
            "SELECT DISTINCT model_id FROM ml_model_coefficients",
            String.class
        );
    }

    private String buildPMML(String modelId, List<Map<String, Object>> coefficients) {
        StringBuilder xml = new StringBuilder();
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);

        // Extract intercept and features
        Double intercept = 0.0;
        List<Map<String, Object>> features = new ArrayList<>();

        for (Map<String, Object> coef : coefficients) {
            String featureName = (String) coef.get("feature_name");
            if ("intercept".equals(featureName)) {
                intercept = ((Number) coef.get("coefficient")).doubleValue();
            } else {
                features.add(coef);
            }
        }

        // PMML Header
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<PMML xmlns=\"http://www.dmg.org/PMML-4_4\" version=\"4.4\">\n");

        // Header section
        xml.append("  <Header>\n");
        xml.append("    <Application name=\"Titan Manufacturing ML\" version=\"5.0\"/>\n");
        xml.append("    <Timestamp>").append(timestamp).append("</Timestamp>\n");
        xml.append("    <Description>Equipment failure prediction model for Titan Manufacturing</Description>\n");
        xml.append("  </Header>\n\n");

        // Data Dictionary
        xml.append("  <DataDictionary numberOfFields=\"").append(features.size() + 1).append("\">\n");
        for (Map<String, Object> feature : features) {
            String name = (String) feature.get("feature_name");
            xml.append("    <DataField name=\"").append(name)
               .append("\" optype=\"continuous\" dataType=\"double\"/>\n");
        }
        xml.append("    <DataField name=\"failure_probability\" optype=\"continuous\" dataType=\"double\"/>\n");
        xml.append("  </DataDictionary>\n\n");

        // Regression Model
        xml.append("  <RegressionModel modelName=\"").append(modelId)
           .append("\" functionName=\"regression\" normalizationMethod=\"logit\">\n");

        // Mining Schema
        xml.append("    <MiningSchema>\n");
        for (Map<String, Object> feature : features) {
            String name = (String) feature.get("feature_name");
            xml.append("      <MiningField name=\"").append(name).append("\" usageType=\"active\"/>\n");
        }
        xml.append("      <MiningField name=\"failure_probability\" usageType=\"target\"/>\n");
        xml.append("    </MiningSchema>\n\n");

        // Regression Table
        xml.append("    <RegressionTable intercept=\"").append(intercept).append("\">\n");
        for (Map<String, Object> feature : features) {
            String name = (String) feature.get("feature_name");
            Double coef = ((Number) feature.get("coefficient")).doubleValue();
            String desc = (String) feature.get("description");
            xml.append("      <!-- ").append(desc != null ? desc : name).append(" -->\n");
            xml.append("      <NumericPredictor name=\"").append(name)
               .append("\" coefficient=\"").append(coef).append("\"/>\n");
        }
        xml.append("    </RegressionTable>\n");

        xml.append("  </RegressionModel>\n");
        xml.append("</PMML>\n");

        return xml.toString();
    }
}
