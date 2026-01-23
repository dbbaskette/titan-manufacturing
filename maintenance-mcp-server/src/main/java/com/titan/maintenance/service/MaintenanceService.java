package com.titan.maintenance.service;

import com.titan.maintenance.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * MCP Tools for predictive maintenance operations at Titan Manufacturing.
 * Enables the Phoenix Incident demo scenario - detecting PHX-CNC-007's
 * 73% failure probability and recommending bearing replacement.
 */
@Service
public class MaintenanceService {

    private static final Logger log = LoggerFactory.getLogger(MaintenanceService.class);
    private final JdbcTemplate jdbcTemplate;

    // Sensor thresholds for anomaly detection
    private static final Map<String, SensorThreshold> THRESHOLDS = Map.of(
        "vibration", new SensorThreshold(2.5, 3.5, 5.0, "mm/s"),
        "temperature", new SensorThreshold(55.0, 70.0, 85.0, "celsius"),
        "spindle_speed", new SensorThreshold(8000.0, 10000.0, 12000.0, "rpm")
    );

    private record SensorThreshold(double normal, double warning, double critical, String unit) {}
    private record TrendAnalysis(String sensorType, double latestValue, double avgValue,
                                  double trendRate, String trend, String unit) {}

    public MaintenanceService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Predict equipment failure probability using ML model in Greenplum.
     * Uses a logistic regression model trained on run-to-failure data (NASA C-MAPSS style).
     * This is the core tool for the Phoenix Incident demo.
     */
    @McpTool(description = "Predict failure probability for equipment using ML model trained on " +
                          "historical run-to-failure data. Analyzes vibration, temperature, and " +
                          "sensor trend patterns to calculate failure risk.")
    public FailurePrediction predictFailure(
            @McpToolParam(description = "Equipment ID to analyze (e.g., PHX-CNC-007)")
            String equipmentId,

            @McpToolParam(description = "Hours of sensor history to analyze (default 168 = 7 days)")
            Integer hoursBack
    ) {
        log.info("Predicting failure for equipment: {} using ML model", equipmentId);

        // Call the ML prediction function in Greenplum
        Map<String, Object> mlPrediction = callMlPredictionModel(equipmentId);

        if (mlPrediction == null || mlPrediction.isEmpty()) {
            log.warn("ML model returned no prediction for {}", equipmentId);
            return createFallbackPrediction(equipmentId, hoursBack);
        }

        // Extract ML model results
        double failureProbability = ((Number) mlPrediction.getOrDefault("failure_probability", 0.0)).doubleValue();
        String riskLevel = (String) mlPrediction.getOrDefault("risk_level", "LOW");
        int hoursToFailure = ((Number) mlPrediction.getOrDefault("hours_to_failure", 720)).intValue();
        double confidence = ((Number) mlPrediction.getOrDefault("confidence", 0.5)).doubleValue();
        String modelVersion = (String) mlPrediction.getOrDefault("model_version", "failure_predictor_v1");

        // Get individual feature contributions for explainability
        double vibrationContribution = ((Number) mlPrediction.getOrDefault("vibration_contribution", 0.0)).doubleValue();
        double temperatureContribution = ((Number) mlPrediction.getOrDefault("temperature_contribution", 0.0)).doubleValue();
        double trendContribution = ((Number) mlPrediction.getOrDefault("trend_contribution", 0.0)).doubleValue();

        // Build risk factors from sensor analysis for detailed reporting
        int analysisHours = hoursBack != null && hoursBack > 0 ? hoursBack : 168;
        List<RiskFactor> riskFactors = new ArrayList<>();

        TrendAnalysis vibrationTrend = analyzeSensorTrend(equipmentId, "vibration", analysisHours);
        if (vibrationTrend != null) {
            riskFactors.add(createRiskFactor(vibrationTrend));
        }

        TrendAnalysis tempTrend = analyzeSensorTrend(equipmentId, "temperature", analysisHours);
        if (tempTrend != null) {
            riskFactors.add(createRiskFactor(tempTrend));
        }

        // Generate recommendations based on risk level
        List<String> recommendations = generateRecommendations(equipmentId, riskFactors, riskLevel);

        // Build summary with ML model attribution
        String summary = String.format(
            "%s: ML model (v%s) predicts %.0f%% failure probability within %d hours. " +
            "Feature contributions - Vibration: %.2f, Temperature: %.2f, Trend: %.2f. %s",
            equipmentId, modelVersion, failureProbability * 100, hoursToFailure,
            vibrationContribution, temperatureContribution, trendContribution,
            riskLevel.equals("CRITICAL") || riskLevel.equals("HIGH")
                ? "IMMEDIATE ACTION RECOMMENDED."
                : "Continue monitoring."
        );

        log.info("ML prediction complete: {} - {}% probability, {} risk (model: {})",
                 equipmentId, Math.round(failureProbability * 100), riskLevel, modelVersion);

        return new FailurePrediction(
            equipmentId,
            Math.round(failureProbability * 100.0) / 100.0,
            (int) Math.round(failureProbability * 100),
            hoursToFailure,
            riskLevel,
            riskFactors,
            recommendations,
            confidence,
            summary
        );
    }

    /**
     * Call the ML prediction function in Greenplum.
     */
    private Map<String, Object> callMlPredictionModel(String equipmentId) {
        try {
            return jdbcTemplate.queryForMap(
                "SELECT * FROM predict_equipment_failure(?)",
                equipmentId
            );
        } catch (Exception e) {
            log.error("Error calling ML prediction model: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Fallback prediction if ML model is unavailable.
     */
    private FailurePrediction createFallbackPrediction(String equipmentId, Integer hoursBack) {
        log.info("Using fallback prediction for {}", equipmentId);
        int analysisHours = hoursBack != null && hoursBack > 0 ? hoursBack : 168;

        List<Map<String, Object>> existingAnomalies = getExistingAnomalies(equipmentId);
        List<RiskFactor> riskFactors = new ArrayList<>();

        TrendAnalysis vibrationTrend = analyzeSensorTrend(equipmentId, "vibration", analysisHours);
        if (vibrationTrend != null) {
            riskFactors.add(createRiskFactor(vibrationTrend));
        }

        TrendAnalysis tempTrend = analyzeSensorTrend(equipmentId, "temperature", analysisHours);
        if (tempTrend != null) {
            riskFactors.add(createRiskFactor(tempTrend));
        }

        double failureProbability = calculateFailureProbability(existingAnomalies, riskFactors);
        int predictedHours = estimateHoursToFailure(riskFactors);
        String riskLevel = determineRiskLevel(failureProbability, predictedHours);
        List<String> recommendations = generateRecommendations(equipmentId, riskFactors, riskLevel);
        double confidence = calculateConfidence(riskFactors, existingAnomalies);
        String summary = "(Fallback) " + buildAnalysisSummary(equipmentId, failureProbability, predictedHours, riskFactors);

        return new FailurePrediction(
            equipmentId,
            Math.round(failureProbability * 100.0) / 100.0,
            (int) Math.round(failureProbability * 100),
            predictedHours,
            riskLevel,
            riskFactors,
            recommendations,
            confidence,
            summary
        );
    }

    /**
     * Estimate Remaining Useful Life (RUL) for equipment.
     */
    @McpTool(description = "Estimate remaining useful life for equipment based on sensor trends, " +
                          "equipment age, and maintenance history.")
    public RulEstimate estimateRul(
            @McpToolParam(description = "Equipment ID to analyze (e.g., PHX-CNC-007)")
            String equipmentId
    ) {
        log.info("Estimating RUL for equipment: {}", equipmentId);

        // Get equipment info
        Map<String, Object> equipment = getEquipmentInfo(equipmentId);
        if (equipment == null) {
            return new RulEstimate(equipmentId, 0, 0, 0, 0, 0, 0, 90,
                0.0, "Equipment not found", "Unable to estimate RUL - equipment not found: " + equipmentId);
        }

        // Parse dates
        String installDateStr = String.valueOf(equipment.get("install_date"));
        String lastMaintStr = String.valueOf(equipment.get("last_maintenance"));
        String equipmentType = String.valueOf(equipment.get("type"));

        LocalDate installDate = LocalDate.parse(installDateStr.substring(0, 10));
        LocalDate lastMaintenance = LocalDate.parse(lastMaintStr.substring(0, 10));

        int equipmentAgeDays = (int) ChronoUnit.DAYS.between(installDate, LocalDate.now());
        int daysSinceMaintenance = (int) ChronoUnit.DAYS.between(lastMaintenance, LocalDate.now());

        // Get maintenance interval
        int recommendedInterval = getMaintenanceInterval(equipmentType);

        // Analyze degradation
        TrendAnalysis vibrationTrend = analyzeSensorTrend(equipmentId, "vibration", 168);

        int baselineHours;
        String methodology;
        double confidence;

        if (vibrationTrend != null && vibrationTrend.trendRate > 0.001) {
            // Calculate based on vibration degradation rate
            SensorThreshold threshold = THRESHOLDS.get("vibration");
            double hoursToThreshold = (threshold.critical - vibrationTrend.latestValue) / vibrationTrend.trendRate;
            baselineHours = (int) Math.max(0, hoursToThreshold);
            methodology = String.format("Based on vibration degradation rate of %.4f mm/s per hour", vibrationTrend.trendRate);
            confidence = 0.75;
        } else {
            // Fall back to maintenance schedule
            int daysUntilMaint = recommendedInterval - daysSinceMaintenance;
            baselineHours = Math.max(24, daysUntilMaint * 24);
            methodology = "Based on maintenance schedule (no clear sensor degradation pattern)";
            confidence = 0.5;
        }

        int lowerBound = (int) (baselineHours * 0.7);
        int upperBound = (int) (baselineHours * 1.3);

        String summary = String.format("%s: Estimated %d hours (%d days) remaining useful life. " +
            "Last maintenance was %d days ago (recommended interval: %d days). %s",
            equipmentId, baselineHours, baselineHours / 24, daysSinceMaintenance,
            recommendedInterval, methodology);

        return new RulEstimate(
            equipmentId,
            baselineHours,
            baselineHours / 24,
            lowerBound,
            upperBound,
            equipmentAgeDays,
            daysSinceMaintenance,
            recommendedInterval,
            confidence,
            methodology,
            summary
        );
    }

    /**
     * Schedule maintenance work order for equipment.
     */
    @McpTool(description = "Create a maintenance work order for equipment. Automatically recommends " +
                          "parts based on failure prediction and maintenance history.")
    public MaintenanceScheduleResult scheduleMaintenance(
            @McpToolParam(description = "Equipment ID to schedule maintenance for")
            String equipmentId,

            @McpToolParam(description = "Type of maintenance: PREVENTIVE, CORRECTIVE, EMERGENCY, PREDICTIVE")
            String maintenanceType,

            @McpToolParam(description = "Scheduled date (ISO format: YYYY-MM-DD). Defaults to tomorrow if not specified.")
            String scheduledDate,

            @McpToolParam(description = "Notes or reason for maintenance")
            String notes
    ) {
        log.info("Scheduling {} maintenance for equipment: {}", maintenanceType, equipmentId);

        // Validate equipment
        Map<String, Object> equipment = getEquipmentInfo(equipmentId);
        if (equipment == null) {
            return new MaintenanceScheduleResult(null, equipmentId, maintenanceType, null, null,
                List.of(), 0.0, 0.0, "ROUTINE", false, "Equipment not found: " + equipmentId);
        }

        // Determine schedule date
        LocalDateTime scheduleDateTime;
        if (scheduledDate != null && !scheduledDate.isBlank()) {
            scheduleDateTime = LocalDate.parse(scheduledDate).atTime(8, 0);
        } else {
            scheduleDateTime = LocalDate.now().plusDays(1).atTime(8, 0);
        }

        // Assign technician based on facility
        String facilityId = String.valueOf(equipment.get("facility_id"));
        String technicianId = "TECH-" + facilityId + "-001";
        String technicianName = getTechnicianName(facilityId);

        // Determine priority
        String priority = switch (maintenanceType.toUpperCase()) {
            case "EMERGENCY" -> "EMERGENCY";
            case "PREDICTIVE", "CORRECTIVE" -> "URGENT";
            default -> "ROUTINE";
        };

        // Recommend parts
        List<PartRecommendation> recommendedParts = recommendParts(equipmentId);

        // Estimate labor and cost
        double estimatedLabor = estimateLaborHours(maintenanceType, recommendedParts);
        double estimatedCost = estimateCost(estimatedLabor, recommendedParts);

        // Generate work order ID
        String workOrderId = "WO-" + LocalDate.now().getYear() + "-" +
                            String.format("%05d", System.currentTimeMillis() % 100000);

        // Build parts description
        String partsDescription = recommendedParts.stream()
            .map(p -> p.sku() + " (" + p.name() + ")")
            .reduce((a, b) -> a + ", " + b)
            .orElse("");

        // Insert work order
        try {
            jdbcTemplate.update("""
                INSERT INTO maintenance_records
                (equipment_id, maintenance_type, scheduled_date, technician_id, technician_name,
                 work_order_id, status, parts_used, labor_hours, cost, notes)
                VALUES (?, ?, ?, ?, ?, ?, 'SCHEDULED', ?, ?, ?, ?)
                """,
                equipmentId,
                maintenanceType.toUpperCase(),
                scheduleDateTime,
                technicianId,
                technicianName,
                workOrderId,
                partsDescription,
                BigDecimal.valueOf(estimatedLabor),
                BigDecimal.valueOf(estimatedCost),
                notes != null ? notes : "Scheduled via predictive maintenance system"
            );

            String message = String.format("Maintenance scheduled: %s for %s on %s. " +
                "Technician: %s. Estimated cost: $%.2f. Work Order: %s",
                maintenanceType, equipmentId, scheduleDateTime.toLocalDate(),
                technicianName, estimatedCost, workOrderId);

            return new MaintenanceScheduleResult(
                workOrderId, equipmentId, maintenanceType.toUpperCase(),
                scheduleDateTime.toString(), technicianName,
                recommendedParts, estimatedLabor, estimatedCost,
                priority, true, message
            );
        } catch (Exception e) {
            log.error("Failed to schedule maintenance: {}", e.getMessage());
            return new MaintenanceScheduleResult(null, equipmentId, maintenanceType, null, null,
                List.of(), 0.0, 0.0, priority, false, "Failed to schedule: " + e.getMessage());
        }
    }

    /**
     * Get maintenance history for equipment.
     */
    @McpTool(description = "Get maintenance history for equipment including completed, scheduled, " +
                          "and in-progress work orders.")
    public List<MaintenanceRecord> getMaintenanceHistory(
            @McpToolParam(description = "Equipment ID (e.g., PHX-CNC-007)")
            String equipmentId,

            @McpToolParam(description = "Filter by status: ALL, COMPLETED, SCHEDULED, IN_PROGRESS (default ALL)")
            String status,

            @McpToolParam(description = "Maximum records to return (default 20)")
            Integer limit
    ) {
        log.info("Getting maintenance history for: {}", equipmentId);

        StringBuilder sql = new StringBuilder("""
            SELECT record_id, equipment_id, maintenance_type, scheduled_date, completed_date,
                   technician_id, technician_name, work_order_id, status, parts_used,
                   labor_hours, cost, notes
            FROM maintenance_records
            WHERE equipment_id = ?
            """);

        List<Object> params = new ArrayList<>();
        params.add(equipmentId);

        if (status != null && !status.isBlank() && !"ALL".equalsIgnoreCase(status)) {
            sql.append(" AND status = ?");
            params.add(status.toUpperCase());
        }

        sql.append(" ORDER BY COALESCE(scheduled_date, completed_date) DESC");
        sql.append(" LIMIT ?");
        params.add(limit != null && limit > 0 ? limit : 20);

        try {
            return jdbcTemplate.query(sql.toString(), params.toArray(),
                (rs, rowNum) -> new MaintenanceRecord(
                    rs.getInt("record_id"),
                    rs.getString("equipment_id"),
                    rs.getString("maintenance_type"),
                    rs.getString("scheduled_date"),
                    rs.getString("completed_date"),
                    rs.getString("technician_id"),
                    rs.getString("technician_name"),
                    rs.getString("work_order_id"),
                    rs.getString("status"),
                    rs.getString("parts_used"),
                    rs.getBigDecimal("labor_hours"),
                    rs.getBigDecimal("cost"),
                    rs.getString("notes")
                ));
        } catch (Exception e) {
            log.warn("Error fetching maintenance history: {}", e.getMessage());
            return List.of();
        }
    }

    // =========================================================================
    // HELPER METHODS
    // =========================================================================

    private List<Map<String, Object>> getExistingAnomalies(String equipmentId) {
        try {
            return jdbcTemplate.queryForList("""
                SELECT sensor_type, confidence_score, predicted_failure_date, severity
                FROM anomalies
                WHERE equipment_id = ? AND resolved = false
                """, equipmentId);
        } catch (Exception e) {
            log.warn("Error fetching anomalies: {}", e.getMessage());
            return List.of();
        }
    }

    private TrendAnalysis analyzeSensorTrend(String equipmentId, String sensorType, int hoursBack) {
        try {
            List<Map<String, Object>> readings = jdbcTemplate.queryForList("""
                SELECT EXTRACT(EPOCH FROM time) as epoch, value
                FROM sensor_readings
                WHERE equipment_id = ? AND sensor_type = ?
                AND time >= NOW() - INTERVAL '%d hours'
                ORDER BY time
                """.formatted(hoursBack), equipmentId, sensorType);

            if (readings.size() < 2) return null;

            // Simple linear regression
            double n = readings.size();
            double sumX = 0, sumY = 0, sumXY = 0, sumX2 = 0;
            double latestValue = 0;

            for (int i = 0; i < readings.size(); i++) {
                double x = i;
                double y = ((Number) readings.get(i).get("value")).doubleValue();
                sumX += x;
                sumY += y;
                sumXY += x * y;
                sumX2 += x * x;
                latestValue = y;
            }

            double slope = (n * sumXY - sumX * sumY) / (n * sumX2 - sumX * sumX);
            double avgValue = sumY / n;

            // Convert slope to per-hour rate (assuming readings are roughly hourly)
            double hoursPerReading = (double) hoursBack / n;
            double trendRate = slope / hoursPerReading;

            String trend = trendRate > 0.001 ? "INCREASING" : (trendRate < -0.001 ? "DECREASING" : "STABLE");

            SensorThreshold threshold = THRESHOLDS.get(sensorType);
            String unit = threshold != null ? threshold.unit : "";

            return new TrendAnalysis(sensorType, latestValue, avgValue, trendRate, trend, unit);
        } catch (Exception e) {
            log.warn("Error analyzing trend for {} {}: {}", equipmentId, sensorType, e.getMessage());
            return null;
        }
    }

    private RiskFactor createRiskFactor(TrendAnalysis trend) {
        SensorThreshold threshold = THRESHOLDS.get(trend.sensorType);
        double warningThreshold = threshold != null ? threshold.warning : 0;
        double criticalThreshold = threshold != null ? threshold.critical : 0;

        // Calculate risk contribution
        double proximityRisk = threshold != null ?
            Math.min(1.0, trend.latestValue / threshold.critical) : 0.5;
        double trendRisk = trend.trend.equals("INCREASING") ? 0.3 : 0;
        double riskContribution = Math.min(1.0, (proximityRisk + trendRisk) / 1.3);

        return new RiskFactor(
            trend.sensorType,
            trend.latestValue,
            trend.unit,
            warningThreshold,
            criticalThreshold,
            trend.trend,
            trend.trendRate,
            riskContribution
        );
    }

    private double calculateFailureProbability(List<Map<String, Object>> anomalies,
                                               List<RiskFactor> riskFactors) {
        // Start with anomaly-based probability
        double anomalyProbability = anomalies.stream()
            .filter(a -> a.get("confidence_score") != null)
            .mapToDouble(a -> ((Number) a.get("confidence_score")).doubleValue())
            .max().orElse(0.0);

        // Add risk factor contributions
        double riskProbability = riskFactors.stream()
            .mapToDouble(RiskFactor::riskContribution)
            .average().orElse(0.0);

        // Weighted combination
        return Math.min(1.0, anomalyProbability * 0.6 + riskProbability * 0.4);
    }

    private int estimateHoursToFailure(List<RiskFactor> riskFactors) {
        int minHours = Integer.MAX_VALUE;

        for (RiskFactor factor : riskFactors) {
            if (factor.trend().equals("INCREASING") && factor.trendRate() > 0) {
                double hoursToThreshold = (factor.criticalThreshold() - factor.currentValue()) / factor.trendRate();
                minHours = Math.min(minHours, (int) Math.max(0, hoursToThreshold));
            }
        }

        return minHours == Integer.MAX_VALUE ? 720 : minHours; // Default 30 days
    }

    private String determineRiskLevel(double probability, int hoursToFailure) {
        if (probability >= 0.7 || hoursToFailure <= 48) return "CRITICAL";
        if (probability >= 0.5 || hoursToFailure <= 168) return "HIGH";
        if (probability >= 0.3) return "MEDIUM";
        return "LOW";
    }

    private List<String> generateRecommendations(String equipmentId,
                                                  List<RiskFactor> riskFactors,
                                                  String riskLevel) {
        List<String> recommendations = new ArrayList<>();

        if (riskLevel.equals("CRITICAL") || riskLevel.equals("HIGH")) {
            recommendations.add("Schedule immediate maintenance inspection");
        }

        for (RiskFactor factor : riskFactors) {
            if (factor.sensorType().equals("vibration") && factor.trend().equals("INCREASING")) {
                recommendations.add("Check bearing condition - vibration pattern suggests bearing wear");
                recommendations.add("Recommend replacement: SKU-BRG-7420 (Spindle Bearing)");
            }
            if (factor.sensorType().equals("temperature") && factor.currentValue() > 60) {
                recommendations.add("Inspect cooling system and lubrication");
            }
        }

        if (riskLevel.equals("CRITICAL")) {
            recommendations.add("Consider reducing equipment load until maintenance");
        }

        if (recommendations.isEmpty()) {
            recommendations.add("Continue monitoring - no immediate action required");
        }

        return recommendations;
    }

    private double calculateConfidence(List<RiskFactor> riskFactors,
                                       List<Map<String, Object>> anomalies) {
        double dataConfidence = Math.min(1.0, riskFactors.size() * 0.3);
        double anomalyConfidence = anomalies.isEmpty() ? 0 : 0.4;
        return Math.min(1.0, 0.3 + dataConfidence + anomalyConfidence);
    }

    private String buildAnalysisSummary(String equipmentId, double probability,
                                        int hoursToFailure, List<RiskFactor> factors) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%s has %.0f%% failure probability within %d hours. ",
            equipmentId, probability * 100, hoursToFailure));

        for (RiskFactor factor : factors) {
            if (factor.trend().equals("INCREASING")) {
                sb.append(String.format("%s trending up (%.2f %s at %.4f/hour). ",
                    factor.sensorType(), factor.currentValue(), factor.unit(), factor.trendRate()));
            }
        }

        return sb.toString();
    }

    private Map<String, Object> getEquipmentInfo(String equipmentId) {
        try {
            return jdbcTemplate.queryForMap("""
                SELECT equipment_id, facility_id, type, install_date, last_maintenance, criticality
                FROM equipment WHERE equipment_id = ?
                """, equipmentId);
        } catch (Exception e) {
            log.warn("Equipment not found: {}", equipmentId);
            return null;
        }
    }

    private int getMaintenanceInterval(String equipmentType) {
        try {
            Integer interval = jdbcTemplate.queryForObject("""
                SELECT maintenance_interval_days FROM equipment_types WHERE type_code = ?
                """, Integer.class, equipmentType);
            return interval != null ? interval : 90;
        } catch (Exception e) {
            return 90; // Default 90 days
        }
    }

    private String getTechnicianName(String facilityId) {
        return switch (facilityId.toUpperCase()) {
            case "PHX" -> "Marcus Johnson";
            case "MUC" -> "Hans Mueller";
            case "SHA" -> "Wei Zhang";
            case "ATL" -> "Sarah Williams";
            default -> "Unassigned Technician";
        };
    }

    private List<PartRecommendation> recommendParts(String equipmentId) {
        List<PartRecommendation> parts = new ArrayList<>();

        // Check vibration trend
        TrendAnalysis vibration = analyzeSensorTrend(equipmentId, "vibration", 168);
        if (vibration != null && vibration.trend.equals("INCREASING")) {
            parts.add(new PartRecommendation(
                "SKU-BRG-7420",
                "Spindle Bearing SKF-7420",
                1,
                1250.00,
                "Vibration pattern indicates bearing wear"
            ));
        }

        // Always recommend consumables
        parts.add(new PartRecommendation(
            "SKU-OIL-CNC01",
            "CNC Machine Oil - Premium",
            2,
            85.00,
            "Standard maintenance consumable"
        ));

        return parts;
    }

    private double estimateLaborHours(String maintenanceType, List<PartRecommendation> parts) {
        double baseHours = switch (maintenanceType.toUpperCase()) {
            case "EMERGENCY" -> 8.0;
            case "CORRECTIVE", "PREDICTIVE" -> 6.0;
            default -> 4.0;
        };

        // Add time for bearing replacement
        boolean hasBearing = parts.stream().anyMatch(p -> p.sku().contains("BRG"));
        if (hasBearing) baseHours += 4.0;

        return baseHours;
    }

    private double estimateCost(double laborHours, List<PartRecommendation> parts) {
        double laborRate = 75.0;
        double laborCost = laborHours * laborRate;
        double partsCost = parts.stream()
            .mapToDouble(p -> p.unitPrice() * p.quantity())
            .sum();
        return laborCost + partsCost;
    }
}
