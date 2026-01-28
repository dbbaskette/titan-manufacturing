package com.titan.sensor.service;

import com.titan.sensor.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * MCP Tools for accessing IoT sensor data from Titan Manufacturing facilities.
 * Queries Greenplum database for equipment and sensor readings.
 */
@Service
public class SensorService {

    private static final Logger log = LoggerFactory.getLogger(SensorService.class);

    private final JdbcTemplate jdbcTemplate;

    public SensorService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * List equipment at a facility or across all facilities.
     */
    @McpTool(description = "List manufacturing equipment. Can filter by facility ID or list all equipment across facilities.")
    public List<Equipment> listEquipment(
            @McpToolParam(description = "Facility ID to filter by (e.g., PHX, MUC, SHA). Leave empty for all facilities.")
            String facilityId,

            @McpToolParam(description = "Equipment type to filter by (e.g., CNC_MILL, CNC_LATHE). Leave empty for all types.")
            String equipmentType,

            @McpToolParam(description = "Maximum number of results to return (default 50)")
            Integer limit
    ) {
        log.info("Listing equipment: facilityId={}, equipmentType={}, limit={}", facilityId, equipmentType, limit);

        StringBuilder sql = new StringBuilder("""
            SELECT equipment_id, facility_id, name, type, manufacturer, model,
                   install_date, last_maintenance, status
            FROM equipment
            WHERE 1=1
            """);

        List<Object> params = new ArrayList<>();

        if (facilityId != null && !facilityId.isBlank()) {
            sql.append(" AND facility_id = ?");
            params.add(facilityId.toUpperCase());
        }

        if (equipmentType != null && !equipmentType.isBlank()) {
            sql.append(" AND type = ?");
            params.add(equipmentType.toUpperCase().replace("_", "-"));
        }

        sql.append(" ORDER BY facility_id, equipment_id");
        sql.append(" LIMIT ?");
        params.add(limit != null && limit > 0 ? limit : 50);

        return jdbcTemplate.query(sql.toString(), params.toArray(),
            (rs, rowNum) -> new Equipment(
                rs.getString("equipment_id"),
                rs.getString("facility_id"),
                rs.getString("name"),
                rs.getString("type"),
                rs.getString("manufacturer"),
                rs.getString("model"),
                rs.getString("install_date"),
                rs.getString("last_maintenance"),
                rs.getString("status")
            ));
    }

    /**
     * Get current status and health of specific equipment.
     */
    @McpTool(description = "Get current health status of specific equipment including latest sensor readings and active anomalies.")
    public EquipmentStatus getEquipmentStatus(
            @McpToolParam(description = "Equipment ID (e.g., PHX-CNC-007)")
            String equipmentId
    ) {
        log.info("Getting equipment status: {}", equipmentId);

        // Get equipment info
        Equipment equipment = jdbcTemplate.queryForObject("""
            SELECT equipment_id, facility_id, name, type, manufacturer, model,
                   install_date, last_maintenance, status
            FROM equipment
            WHERE equipment_id = ?
            """,
            (rs, rowNum) -> new Equipment(
                rs.getString("equipment_id"),
                rs.getString("facility_id"),
                rs.getString("name"),
                rs.getString("type"),
                rs.getString("manufacturer"),
                rs.getString("model"),
                rs.getString("install_date"),
                rs.getString("last_maintenance"),
                rs.getString("status")
            ),
            equipmentId);

        // Get latest sensor readings
        List<SensorReading> readings = getLatestReadings(equipmentId);

        // Get active anomalies
        List<Anomaly> anomalies = getActiveAnomalies(equipmentId);

        // Determine overall health status
        String healthStatus = "HEALTHY";
        if (!anomalies.isEmpty()) {
            healthStatus = anomalies.stream()
                .anyMatch(a -> "CRITICAL".equals(a.severity()) || "HIGH".equals(a.severity()))
                ? "CRITICAL" : "WARNING";
        }

        // Build summary
        String summary = buildStatusSummary(equipment, readings, anomalies);

        return new EquipmentStatus(
            equipmentId,
            equipment.type(),
            equipment.facilityId(),
            healthStatus,
            readings,
            anomalies,
            summary
        );
    }

    /**
     * Get historical sensor readings for equipment.
     */
    @McpTool(description = "Get historical sensor readings for equipment. Can filter by sensor type and time range.")
    public List<SensorReading> getSensorReadings(
            @McpToolParam(description = "Equipment ID (e.g., PHX-CNC-007)")
            String equipmentId,

            @McpToolParam(description = "Sensor type to filter (vibration, temperature, rpm, torque, pressure). Leave empty for all.")
            String sensorType,

            @McpToolParam(description = "Number of hours of history to retrieve (default 24)")
            Integer hoursBack,

            @McpToolParam(description = "Maximum number of readings to return (default 100)")
            Integer limit
    ) {
        log.info("Getting sensor readings: equipment={}, sensorType={}, hours={}", equipmentId, sensorType, hoursBack);

        StringBuilder sql = new StringBuilder("""
            SELECT equipment_id, sensor_type, value, unit, time, quality_flag
            FROM sensor_readings
            WHERE equipment_id = ?
            """);

        List<Object> params = new ArrayList<>();
        params.add(equipmentId);

        if (sensorType != null && !sensorType.isBlank()) {
            sql.append(" AND sensor_type = ?");
            params.add(sensorType.toLowerCase());
        }

        int hours = hoursBack != null && hoursBack > 0 ? hoursBack : 24;
        sql.append(" AND time >= NOW() - INTERVAL '").append(hours).append(" hours'");

        sql.append(" ORDER BY time DESC");
        sql.append(" LIMIT ?");
        params.add(limit != null && limit > 0 ? limit : 100);

        return jdbcTemplate.query(sql.toString(), params.toArray(),
            (rs, rowNum) -> new SensorReading(
                rs.getString("equipment_id"),
                rs.getString("sensor_type"),
                rs.getDouble("value"),
                rs.getString("unit"),
                rs.getString("time"),
                rs.getString("quality_flag")
            ));
    }

    /**
     * Get facility-wide equipment health overview.
     */
    @McpTool(description = "Get overview of equipment health status for an entire facility.")
    public FacilityStatus getFacilityStatus(
            @McpToolParam(description = "Facility ID (e.g., PHX, MUC, SHA)")
            String facilityId
    ) {
        log.info("Getting facility status: {}", facilityId);

        // Get facility info
        Map<String, Object> facility = jdbcTemplate.queryForMap("""
            SELECT facility_id, name, city, country
            FROM titan_facilities
            WHERE facility_id = ?
            """, facilityId.toUpperCase());

        // Get equipment counts by status (lowercase in database)
        Map<String, Object> counts = jdbcTemplate.queryForMap("""
            SELECT
                COUNT(*) as total,
                COUNT(*) FILTER (WHERE status = 'operational') as operational,
                COUNT(*) FILTER (WHERE status = 'warning') as warning,
                COUNT(*) FILTER (WHERE status = 'critical') as critical,
                COUNT(*) FILTER (WHERE status = 'maintenance') as maintenance
            FROM equipment
            WHERE facility_id = ?
            """, facilityId.toUpperCase());

        // Get equipment with active (unresolved) anomalies
        List<String> equipmentWithAnomalies = jdbcTemplate.queryForList("""
            SELECT DISTINCT equipment_id
            FROM anomalies
            WHERE equipment_id LIKE ?
              AND resolved = false
            """, String.class, facilityId.toUpperCase() + "-%");

        int total = ((Number) counts.get("total")).intValue();
        int operational = ((Number) counts.get("operational")).intValue();
        int warning = ((Number) counts.get("warning")).intValue();
        int critical = ((Number) counts.get("critical")).intValue();
        int maintenance = ((Number) counts.get("maintenance")).intValue();

        double healthPercentage = total > 0 ? (operational * 100.0) / total : 0;

        String summary = String.format(
            "%s facility has %d equipment: %d operational, %d warning, %d critical, %d under maintenance. " +
            "Overall health: %.1f%%. %d equipment with active anomalies.",
            facility.get("name"), total, operational, warning, critical, maintenance,
            healthPercentage, equipmentWithAnomalies.size()
        );

        return new FacilityStatus(
            facilityId.toUpperCase(),
            (String) facility.get("name"),
            total,
            operational,
            warning,
            critical,
            maintenance,
            healthPercentage,
            equipmentWithAnomalies,
            summary
        );
    }

    /**
     * Get sensor thresholds for equipment.
     */
    @McpTool(description = "Get sensor thresholds for equipment. Returns equipment-specific overrides or global defaults from sensor_types table.")
    public List<SensorThreshold> getThresholds(
            @McpToolParam(description = "Equipment ID (optional). If not provided, returns all equipment-specific thresholds.")
            String equipmentId
    ) {
        log.info("Getting thresholds: equipmentId={}", equipmentId);

        if (equipmentId != null && !equipmentId.isBlank()) {
            // Get equipment-specific thresholds with global fallback
            return jdbcTemplate.query("""
                SELECT
                    st.threshold_id,
                    COALESCE(st.equipment_id, 'GLOBAL') as equipment_id,
                    st.sensor_type,
                    COALESCE(st.warning_threshold, sty.warning_threshold) as warning_threshold,
                    COALESCE(st.critical_threshold, sty.critical_threshold) as critical_threshold,
                    st.updated_at,
                    st.updated_by
                FROM sensor_types sty
                LEFT JOIN sensor_thresholds st ON sty.sensor_type = st.sensor_type
                    AND (st.equipment_id = ? OR st.equipment_id IS NULL)
                ORDER BY st.equipment_id NULLS LAST, sty.sensor_type
                """,
                (rs, rowNum) -> new SensorThreshold(
                    rs.getObject("threshold_id") != null ? rs.getInt("threshold_id") : null,
                    rs.getString("equipment_id"),
                    rs.getString("sensor_type"),
                    rs.getDouble("warning_threshold"),
                    rs.getDouble("critical_threshold"),
                    rs.getString("updated_at"),
                    rs.getString("updated_by")
                ),
                equipmentId);
        } else {
            // Get all equipment-specific overrides
            return jdbcTemplate.query("""
                SELECT threshold_id, equipment_id, sensor_type,
                       warning_threshold, critical_threshold, updated_at, updated_by
                FROM sensor_thresholds
                WHERE equipment_id IS NOT NULL
                ORDER BY equipment_id, sensor_type
                """,
                (rs, rowNum) -> new SensorThreshold(
                    rs.getInt("threshold_id"),
                    rs.getString("equipment_id"),
                    rs.getString("sensor_type"),
                    rs.getDouble("warning_threshold"),
                    rs.getDouble("critical_threshold"),
                    rs.getString("updated_at"),
                    rs.getString("updated_by")
                ));
        }
    }

    /**
     * Update sensor threshold for equipment.
     */
    @McpTool(description = "Update or create a sensor threshold for specific equipment. Use this to customize anomaly detection sensitivity.")
    public SensorThreshold updateThreshold(
            @McpToolParam(description = "Equipment ID for the threshold")
            String equipmentId,

            @McpToolParam(description = "Sensor type (vibration, temperature, spindle_speed, etc.)")
            String sensorType,

            @McpToolParam(description = "Warning threshold value (triggers warning alert)")
            Double warningThreshold,

            @McpToolParam(description = "Critical threshold value (triggers critical alert)")
            Double criticalThreshold,

            @McpToolParam(description = "User making the update (for audit trail)")
            String updatedBy
    ) {
        log.info("Updating threshold: equipment={}, sensor={}, warning={}, critical={}",
                 equipmentId, sensorType, warningThreshold, criticalThreshold);

        // Upsert the threshold
        jdbcTemplate.update("""
            INSERT INTO sensor_thresholds (equipment_id, sensor_type, warning_threshold, critical_threshold, updated_at, updated_by)
            VALUES (?, ?, ?, ?, NOW(), ?)
            ON CONFLICT (equipment_id, sensor_type)
            DO UPDATE SET
                warning_threshold = EXCLUDED.warning_threshold,
                critical_threshold = EXCLUDED.critical_threshold,
                updated_at = NOW(),
                updated_by = EXCLUDED.updated_by
            """,
            equipmentId, sensorType.toLowerCase(), warningThreshold, criticalThreshold, updatedBy != null ? updatedBy : "system");

        // Return the updated threshold
        return jdbcTemplate.queryForObject("""
            SELECT threshold_id, equipment_id, sensor_type,
                   warning_threshold, critical_threshold, updated_at, updated_by
            FROM sensor_thresholds
            WHERE equipment_id = ? AND sensor_type = ?
            """,
            (rs, rowNum) -> new SensorThreshold(
                rs.getInt("threshold_id"),
                rs.getString("equipment_id"),
                rs.getString("sensor_type"),
                rs.getDouble("warning_threshold"),
                rs.getDouble("critical_threshold"),
                rs.getString("updated_at"),
                rs.getString("updated_by")
            ),
            equipmentId, sensorType.toLowerCase());
    }

    /**
     * Detect anomalies in sensor readings for equipment.
     */
    @McpTool(description = "Check for anomalies in sensor readings. Returns active anomalies and performs basic threshold analysis.")
    public List<Anomaly> detectAnomaly(
            @McpToolParam(description = "Equipment ID to check (e.g., PHX-CNC-007)")
            String equipmentId,

            @McpToolParam(description = "Specific sensor type to analyze (optional)")
            String sensorType
    ) {
        log.info("Detecting anomalies: equipment={}, sensorType={}", equipmentId, sensorType);

        List<Anomaly> anomalies = new ArrayList<>();

        // Get existing active anomalies from database
        anomalies.addAll(getActiveAnomalies(equipmentId));

        // Perform real-time threshold analysis on recent readings
        List<SensorReading> recentReadings = getSensorReadings(equipmentId, sensorType, 1, 10);

        for (SensorReading reading : recentReadings) {
            Anomaly detected = analyzeReading(reading);
            if (detected != null && anomalies.stream().noneMatch(a ->
                    a.equipmentId().equals(detected.equipmentId()) &&
                    a.sensorType().equals(detected.sensorType()))) {
                anomalies.add(detected);
            }
        }

        return anomalies;
    }

    /**
     * ML-based anomaly detection using Greenplum's failure prediction model.
     */
    @McpTool(description = "Detect anomalies using machine learning model trained on historical failure data. Returns failure probability and contributing factors.")
    public MLPrediction detectAnomalyML(
            @McpToolParam(description = "Equipment ID to analyze (e.g., PHX-CNC-007)")
            String equipmentId,

            @McpToolParam(description = "Whether to create an anomaly record if probability exceeds threshold (default false)")
            Boolean createAnomaly
    ) {
        log.info("ML anomaly detection: equipment={}, createAnomaly={}", equipmentId, createAnomaly);

        try {
            // Call the Greenplum ML function
            Map<String, Object> result = jdbcTemplate.queryForMap("""
                SELECT * FROM predict_equipment_failure(?)
                """, equipmentId);

            Double probability = result.get("failure_probability") != null
                ? ((Number) result.get("failure_probability")).doubleValue() : 0.0;
            String riskLevel = (String) result.get("risk_level");
            Integer hoursToFailure = result.get("hours_to_failure") != null
                ? ((Number) result.get("hours_to_failure")).intValue() : null;
            Double confidence = result.get("confidence") != null
                ? ((Number) result.get("confidence")).doubleValue() : 0.5;
            Double vibrationContrib = result.get("vibration_contribution") != null
                ? ((Number) result.get("vibration_contribution")).doubleValue() : 0.0;
            Double temperatureContrib = result.get("temperature_contribution") != null
                ? ((Number) result.get("temperature_contribution")).doubleValue() : 0.0;
            Double trendContrib = result.get("trend_contribution") != null
                ? ((Number) result.get("trend_contribution")).doubleValue() : 0.0;
            String modelVersion = (String) result.get("model_version");

            // Build summary
            String summary = buildMLSummary(equipmentId, probability, riskLevel, hoursToFailure,
                                           vibrationContrib, temperatureContrib, trendContrib);

            // Optionally create anomaly record for high-risk predictions
            if (Boolean.TRUE.equals(createAnomaly) && probability >= 0.5) {
                createAnomalyFromPrediction(equipmentId, probability, riskLevel, hoursToFailure, summary);
            }

            return new MLPrediction(
                equipmentId,
                probability,
                riskLevel,
                hoursToFailure,
                confidence,
                vibrationContrib,
                temperatureContrib,
                trendContrib,
                modelVersion,
                summary
            );
        } catch (Exception e) {
            log.error("ML prediction failed for {}: {}", equipmentId, e.getMessage());
            return new MLPrediction(
                equipmentId,
                0.0,
                "UNKNOWN",
                null,
                0.0,
                0.0,
                0.0,
                0.0,
                "error",
                "ML prediction unavailable: " + e.getMessage()
            );
        }
    }

    private String buildMLSummary(String equipmentId, Double probability, String riskLevel,
                                  Integer hoursToFailure, Double vibrationContrib,
                                  Double temperatureContrib, Double trendContrib) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("ML Analysis for %s: %.1f%% failure probability (%s risk). ",
                               equipmentId, probability * 100, riskLevel));

        if (hoursToFailure != null && hoursToFailure < 720) {
            int days = hoursToFailure / 24;
            if (days > 0) {
                sb.append(String.format("Estimated %d days to potential failure. ", days));
            } else {
                sb.append(String.format("Estimated %d hours to potential failure. ", hoursToFailure));
            }
        }

        // Explain contributing factors
        List<String> factors = new ArrayList<>();
        if (vibrationContrib > 0.1) factors.add(String.format("vibration (+%.0f%%)", vibrationContrib * 100));
        if (temperatureContrib > 0.1) factors.add(String.format("temperature (+%.0f%%)", temperatureContrib * 100));
        if (trendContrib > 0.1) factors.add(String.format("trending metrics (+%.0f%%)", trendContrib * 100));

        if (!factors.isEmpty()) {
            sb.append("Key contributors: ").append(String.join(", ", factors)).append(".");
        }

        return sb.toString();
    }

    private void createAnomalyFromPrediction(String equipmentId, Double probability, String riskLevel,
                                             Integer hoursToFailure, String description) {
        try {
            // Check if similar anomaly already exists
            Integer existingCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM anomalies
                WHERE equipment_id = ?
                  AND anomaly_type = 'ML_PREDICTION'
                  AND resolved = false
                  AND detected_at > NOW() - INTERVAL '24 hours'
                """, Integer.class, equipmentId);

            if (existingCount == null || existingCount == 0) {
                jdbcTemplate.update("""
                    INSERT INTO anomalies (equipment_id, anomaly_type, sensor_type, severity,
                                          detected_at, description, predicted_failure_date, confidence_score)
                    VALUES (?, 'ML_PREDICTION', 'multi-sensor', ?, NOW(), ?, NOW() + (? || ' hours')::interval, ?)
                    """,
                    equipmentId,
                    riskLevel,
                    description,
                    hoursToFailure != null ? hoursToFailure.toString() : "720",
                    probability);
                log.info("Created ML-based anomaly record for {}", equipmentId);
            }
        } catch (Exception e) {
            log.warn("Failed to create anomaly record: {}", e.getMessage());
        }
    }

    // Helper methods

    private List<SensorReading> getLatestReadings(String equipmentId) {
        return jdbcTemplate.query("""
            SELECT DISTINCT ON (sensor_type)
                   equipment_id, sensor_type, value, unit, time, quality_flag
            FROM sensor_readings
            WHERE equipment_id = ?
            ORDER BY sensor_type, time DESC
            """,
            (rs, rowNum) -> new SensorReading(
                rs.getString("equipment_id"),
                rs.getString("sensor_type"),
                rs.getDouble("value"),
                rs.getString("unit"),
                rs.getString("time"),
                rs.getString("quality_flag")
            ),
            equipmentId);
    }

    private List<Anomaly> getActiveAnomalies(String equipmentId) {
        try {
            return jdbcTemplate.query("""
                SELECT anomaly_id, equipment_id, anomaly_type, sensor_type, severity,
                       detected_at, description, predicted_failure_date, confidence_score
                FROM anomalies
                WHERE equipment_id = ?
                  AND resolved = false
                ORDER BY detected_at DESC
                """,
                (rs, rowNum) -> new Anomaly(
                    String.valueOf(rs.getInt("anomaly_id")),
                    rs.getString("equipment_id"),
                    rs.getString("anomaly_type"),
                    rs.getString("sensor_type"),
                    rs.getString("severity"),
                    rs.getString("detected_at"),
                    rs.getString("description"),
                    rs.getString("predicted_failure_date"),
                    rs.getDouble("confidence_score")
                ),
                equipmentId);
        } catch (Exception e) {
            log.warn("Error fetching anomalies for {}: {}", equipmentId, e.getMessage());
            return List.of();
        }
    }

    private Anomaly analyzeReading(SensorReading reading) {
        // Get thresholds from database (equipment-specific or global)
        Double[] thresholds = getThresholdsFromDb(reading.equipmentId(), reading.sensorType());
        Double warningThreshold = thresholds[0];
        Double criticalThreshold = thresholds[1];

        if (criticalThreshold == null) return null;

        String severity = null;
        Double threshold = null;

        if (reading.value() >= criticalThreshold) {
            severity = "CRITICAL";
            threshold = criticalThreshold;
        } else if (warningThreshold != null && reading.value() >= warningThreshold) {
            severity = "HIGH";
            threshold = warningThreshold;
        }

        if (severity != null && threshold != null) {
            double deviation = ((reading.value() - threshold) / threshold) * 100;

            return new Anomaly(
                "RT-" + System.currentTimeMillis(),
                reading.equipmentId(),
                "THRESHOLD_EXCEEDED",
                reading.sensorType(),
                severity,
                reading.timestamp(),
                String.format("%s reading of %.2f %s exceeds %s threshold of %.2f. %s",
                    reading.sensorType(), reading.value(), reading.unit(),
                    severity.equals("CRITICAL") ? "critical" : "warning",
                    threshold, getRecommendation(reading.sensorType(), severity)),
                null,  // predictedFailureDate
                Math.min(deviation / 100.0, 1.0)  // confidence based on deviation
            );
        }

        return null;
    }

    private Double[] getThresholdsFromDb(String equipmentId, String sensorType) {
        try {
            // First try equipment-specific threshold
            List<Map<String, Object>> results = jdbcTemplate.queryForList("""
                SELECT warning_threshold, critical_threshold
                FROM sensor_thresholds
                WHERE equipment_id = ? AND sensor_type = ?
                """, equipmentId, sensorType.toLowerCase());

            if (!results.isEmpty()) {
                Map<String, Object> row = results.get(0);
                return new Double[] {
                    row.get("warning_threshold") != null ? ((Number) row.get("warning_threshold")).doubleValue() : null,
                    row.get("critical_threshold") != null ? ((Number) row.get("critical_threshold")).doubleValue() : null
                };
            }

            // Fall back to global defaults from sensor_types
            results = jdbcTemplate.queryForList("""
                SELECT warning_threshold, critical_threshold
                FROM sensor_types
                WHERE sensor_type = ?
                """, sensorType.toLowerCase());

            if (!results.isEmpty()) {
                Map<String, Object> row = results.get(0);
                return new Double[] {
                    row.get("warning_threshold") != null ? ((Number) row.get("warning_threshold")).doubleValue() : null,
                    row.get("critical_threshold") != null ? ((Number) row.get("critical_threshold")).doubleValue() : null
                };
            }
        } catch (Exception e) {
            log.warn("Error getting thresholds for {}/{}: {}", equipmentId, sensorType, e.getMessage());
        }

        // Hardcoded fallback if database unavailable
        return switch (sensorType.toLowerCase()) {
            case "vibration" -> new Double[] { 3.5, 5.0 };
            case "temperature" -> new Double[] { 70.0, 85.0 };
            case "spindle_speed" -> new Double[] { 16000.0, 18000.0 };
            case "power_draw" -> new Double[] { 65.0, 80.0 };
            default -> new Double[] { null, null };
        };
    }

    private String getRecommendation(String sensorType, String severity) {
        if ("CRITICAL".equals(severity)) {
            return "Immediate inspection required. Consider stopping equipment.";
        }
        return switch (sensorType.toLowerCase()) {
            case "vibration" -> "Schedule bearing inspection and lubrication check.";
            case "temperature" -> "Check cooling system and reduce load if possible.";
            case "rpm" -> "Verify speed settings and check for mechanical issues.";
            case "torque" -> "Check for binding or excessive load.";
            case "pressure" -> "Inspect seals and check for blockages.";
            default -> "Schedule maintenance inspection.";
        };
    }

    private String buildStatusSummary(Equipment equipment, List<SensorReading> readings, List<Anomaly> anomalies) {
        StringBuilder sb = new StringBuilder();
        sb.append(equipment.equipmentId()).append(" (").append(equipment.type()).append(") ");
        sb.append("at ").append(equipment.facilityId()).append(" facility. ");
        sb.append("Status: ").append(equipment.status()).append(". ");

        if (!anomalies.isEmpty()) {
            sb.append(anomalies.size()).append(" active anomaly(s) detected: ");
            for (Anomaly a : anomalies) {
                sb.append(a.sensorType()).append(" ").append(a.severity()).append("; ");
            }
        } else {
            sb.append("No anomalies detected. ");
        }

        // Add key readings
        for (SensorReading r : readings) {
            if ("vibration".equals(r.sensorType()) || "temperature".equals(r.sensorType())) {
                sb.append(r.sensorType()).append(": ").append(String.format("%.2f", r.value()))
                  .append(" ").append(r.unit()).append(". ");
            }
        }

        return sb.toString();
    }
}
