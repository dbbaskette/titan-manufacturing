package com.titan.maintenance.controller;

import com.titan.maintenance.service.GemFireScoringService;
import com.titan.maintenance.service.GemFireService;
import com.titan.maintenance.service.ModelExportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * REST API for ML pipeline data — serves the ML Dashboard UI.
 */
@RestController
@RequestMapping("/ml")
@CrossOrigin(origins = "*")
public class MLController {

    private static final Logger log = LoggerFactory.getLogger(MLController.class);

    private final JdbcTemplate jdbcTemplate;
    private final ModelExportService modelExportService;
    private final GemFireService gemFireService;
    private final GemFireScoringService scoringService;

    public MLController(JdbcTemplate jdbcTemplate,
                        ModelExportService modelExportService,
                        GemFireService gemFireService,
                        GemFireScoringService scoringService) {
        this.jdbcTemplate = jdbcTemplate;
        this.modelExportService = modelExportService;
        this.gemFireService = gemFireService;
        this.scoringService = scoringService;
    }

    /**
     * Get model coefficients from Greenplum.
     */
    @GetMapping("/model")
    public Map<String, Object> getModel() {
        List<Map<String, Object>> coefficients = jdbcTemplate.queryForList("""
            SELECT feature_name, coefficient, description
            FROM ml_model_coefficients
            WHERE model_id = 'failure_predictor_v1'
            ORDER BY feature_name
            """);

        Integer trainingCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ml_training_data", Integer.class);

        Integer failureCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ml_training_data WHERE failed = 1", Integer.class);

        return Map.of(
                "modelId", "failure_predictor_v1",
                "method", "MADlib logregr_train (IRLS)",
                "coefficients", coefficients,
                "trainingObservations", trainingCount != null ? trainingCount : 0,
                "failureObservations", failureCount != null ? failureCount : 0
        );
    }

    /**
     * Get real-time predictions from GemFire.
     */
    @GetMapping("/predictions")
    public Map<String, Object> getPredictions() {
        return scoringService.getGemFirePredictions();
    }

    /**
     * Get GemFire deployment status.
     */
    @GetMapping("/gemfire/status")
    public Map<String, Object> getGemFireStatus() {
        boolean connected = gemFireService.isConnected();
        Map<String, Object> deployed = connected ? gemFireService.getDeployedModels() : Map.of();

        return Map.of(
                "connected", connected,
                "deployedModels", deployed
        );
    }

    /**
     * Get PMML export for the model.
     */
    @GetMapping("/pmml")
    public Map<String, Object> getPmml() {
        return modelExportService.exportModelAsPMML("failure_predictor_v1");
    }

    /**
     * Retrain the model.
     */
    @PostMapping("/retrain")
    public Map<String, Object> retrain() {
        log.info("ML Dashboard triggered model retrain");
        return modelExportService.retrainModel();
    }

    /**
     * Export PMML and deploy to GemFire.
     */
    @PostMapping("/deploy")
    public Map<String, Object> deploy() {
        log.info("ML Dashboard triggered model deploy to GemFire");
        return gemFireService.deployModelToGemFire("failure_predictor_v1");
    }

    /**
     * Clear all sensor windows and GemFire predictions.
     * Called after a simulation reset to flush stale data from the scoring pipeline.
     */
    @PostMapping("/predictions/reset")
    public Map<String, Object> resetPredictions() {
        log.info("Clearing all predictions and sensor windows");
        return scoringService.clearAllPredictions();
    }

    /**
     * Generate comprehensive training data following ML_TRAINING_DATA_MATRIX.md.
     * Creates 16 states: 1 healthy + 5 patterns × 3 severity levels (early/moderate/critical).
     * Uses proper label mixing to create probability gradients.
     */
    @PostMapping("/training/generate")
    public Map<String, Object> generateTrainingData(
            @RequestParam(defaultValue = "1000") int normalCount,
            @RequestParam(defaultValue = "200") int failureCountPerPattern
    ) {
        log.info("Generating comprehensive 16-state training data: {} normal, {} per pattern",
                normalCount, failureCountPerPattern);

        long start = System.currentTimeMillis();
        int totalInserted = 0;
        Map<String, Integer> distribution = new LinkedHashMap<>();

        try {
            // Clear existing training data
            jdbcTemplate.update("DELETE FROM ml_training_data");

            // Normalization constants (must match GemFireScoringService)
            final double VIB_MAX = 5.0, TEMP_MAX = 85.0, POWER_MAX = 50.0;
            final double RPM_MAX = 10000.0, PRESSURE_MAX = 10.0, TORQUE_MAX = 80.0;

            // Distribution per pattern: 60% early, 30% moderate, 10% critical
            int earlyCount = (int)(failureCountPerPattern * 0.6);
            int moderateCount = (int)(failureCountPerPattern * 0.3);
            int criticalCount = failureCountPerPattern - earlyCount - moderateCount;

            // ═══════════════════════════════════════════════════════════════════
            // NORMAL OPERATION (Healthy) — Target: 0-15% probability, Label: failed=0
            // ═══════════════════════════════════════════════════════════════════
            for (int i = 0; i < normalCount; i++) {
                double vib = 1.5 + Math.random() * 1.0;       // 1.5-2.5 mm/s
                double temp = 45 + Math.random() * 10;         // 45-55°C
                double rpm = 8000 + Math.random() * 1500;      // 8000-9500 RPM
                double power = 20 + Math.random() * 10;        // 20-30 kW
                double pressure = 5.5 + Math.random() * 1.0;   // 5.5-6.5 bar
                double torque = 40 + Math.random() * 15;       // 40-55 Nm

                // 5% noise: small chance of failed=1 even in normal (sensor noise)
                int failed = Math.random() < 0.05 ? 1 : 0;

                insertTrainingRow(vib/VIB_MAX, temp/TEMP_MAX,
                        (Math.random()-0.5)*0.05, (Math.random()-0.5)*0.1,
                        5 + Math.random() * 40, 0.5 + Math.random() * 5,
                        0, power/POWER_MAX, rpm/RPM_MAX, pressure/PRESSURE_MAX, torque/TORQUE_MAX, failed);
                totalInserted++;
            }
            distribution.put("normal", normalCount);

            // ═══════════════════════════════════════════════════════════════════
            // BEARING DEGRADATION — HIGH vibration ↑↑, HIGH torque ↑↑
            // ═══════════════════════════════════════════════════════════════════

            // Early Stage (15-40% probability) — Label: 70% failed=0, 30% failed=1
            for (int i = 0; i < earlyCount; i++) {
                double vib = 2.5 + Math.random() * 0.8;        // 2.5-3.3 mm/s
                double temp = 50 + Math.random() * 15;         // 50-65°C
                double rpm = 7800 + Math.random() * 1200;      // 7800-9000 RPM
                double power = 25 + Math.random() * 10;        // 25-35 kW
                double pressure = 5.0 + Math.random() * 1.0;   // 5.0-6.0 bar
                double torque = 50 + Math.random() * 12;       // 50-62 Nm

                int failed = Math.random() < 0.30 ? 1 : 0;
                insertTrainingRow(vib/VIB_MAX, temp/TEMP_MAX,
                        0.02 + Math.random() * 0.08, 0.01 + Math.random() * 0.05,
                        20 + Math.random() * 30, 2 + Math.random() * 5,
                        Math.random() < 0.2 ? 1 : 0, power/POWER_MAX, rpm/RPM_MAX, pressure/PRESSURE_MAX, torque/TORQUE_MAX, failed);
                totalInserted++;
            }

            // Moderate Stage (40-70% probability) — Label: 20% failed=0, 80% failed=1
            for (int i = 0; i < moderateCount; i++) {
                double vib = 3.0 + Math.random() * 1.0;        // 3.0-4.0 mm/s
                double temp = 60 + Math.random() * 15;         // 60-75°C
                double rpm = 7200 + Math.random() * 1300;      // 7200-8500 RPM
                double power = 28 + Math.random() * 12;        // 28-40 kW
                double pressure = 4.8 + Math.random() * 1.0;   // 4.8-5.8 bar
                double torque = 55 + Math.random() * 15;       // 55-70 Nm

                int failed = Math.random() < 0.80 ? 1 : 0;
                insertTrainingRow(vib/VIB_MAX, temp/TEMP_MAX,
                        0.05 + Math.random() * 0.15, 0.03 + Math.random() * 0.10,
                        30 + Math.random() * 40, 3 + Math.random() * 7,
                        Math.random() < 0.4 ? 1 : 0, power/POWER_MAX, rpm/RPM_MAX, pressure/PRESSURE_MAX, torque/TORQUE_MAX, failed);
                totalInserted++;
            }

            // Critical Stage (70-100% probability) — Label: 100% failed=1
            for (int i = 0; i < criticalCount; i++) {
                double vib = 4.0 + Math.random() * 3.0;        // 4.0-7.0+ mm/s
                double temp = 65 + Math.random() * 25;         // 65-90°C
                double rpm = 6500 + Math.random() * 1500;      // 6500-8000 RPM
                double power = 28 + Math.random() * 17;        // 28-45 kW
                double pressure = 4.5 + Math.random() * 1.0;   // 4.5-5.5 bar
                double torque = 60 + Math.random() * 25;       // 60-85 Nm

                insertTrainingRow(Math.min(vib/VIB_MAX, 1.4), temp/TEMP_MAX,
                        0.10 + Math.random() * 0.25, 0.05 + Math.random() * 0.15,
                        40 + Math.random() * 50, 5 + Math.random() * 10,
                        1 + (int)(Math.random() * 2), power/POWER_MAX, rpm/RPM_MAX, pressure/PRESSURE_MAX, Math.min(torque/TORQUE_MAX, 1.1), 1);
                totalInserted++;
            }
            distribution.put("bearing_early", earlyCount);
            distribution.put("bearing_moderate", moderateCount);
            distribution.put("bearing_critical", criticalCount);

            // ═══════════════════════════════════════════════════════════════════
            // MOTOR BURNOUT — HIGH temperature ↑↑, HIGH power ↑↑, LOW RPM ↓↓
            // ═══════════════════════════════════════════════════════════════════

            // Early Stage
            for (int i = 0; i < earlyCount; i++) {
                double vib = 2.0 + Math.random() * 0.8;        // 2.0-2.8 mm/s
                double temp = 60 + Math.random() * 12;         // 60-72°C
                double rpm = 7000 + Math.random() * 1500;      // 7000-8500 RPM
                double power = 30 + Math.random() * 10;        // 30-40 kW
                double pressure = 5.0 + Math.random() * 1.0;   // 5.0-6.0 bar
                double torque = 45 + Math.random() * 13;       // 45-58 Nm

                int failed = Math.random() < 0.30 ? 1 : 0;
                insertTrainingRow(vib/VIB_MAX, temp/TEMP_MAX,
                        (Math.random()-0.3)*0.1, 0.05 + Math.random() * 0.10,
                        15 + Math.random() * 30, 2 + Math.random() * 6,
                        Math.random() < 0.2 ? 1 : 0, power/POWER_MAX, rpm/RPM_MAX, pressure/PRESSURE_MAX, torque/TORQUE_MAX, failed);
                totalInserted++;
            }

            // Moderate Stage
            for (int i = 0; i < moderateCount; i++) {
                double vib = 2.2 + Math.random() * 1.0;        // 2.2-3.2 mm/s
                double temp = 72 + Math.random() * 10;         // 72-82°C
                double rpm = 6200 + Math.random() * 1300;      // 6200-7500 RPM
                double power = 38 + Math.random() * 10;        // 38-48 kW
                double pressure = 4.8 + Math.random() * 1.0;   // 4.8-5.8 bar
                double torque = 48 + Math.random() * 14;       // 48-62 Nm

                int failed = Math.random() < 0.80 ? 1 : 0;
                insertTrainingRow(vib/VIB_MAX, temp/TEMP_MAX,
                        0.02 + Math.random() * 0.08, 0.10 + Math.random() * 0.20,
                        25 + Math.random() * 35, 3 + Math.random() * 7,
                        Math.random() < 0.5 ? 1 : 0, power/POWER_MAX, rpm/RPM_MAX, pressure/PRESSURE_MAX, torque/TORQUE_MAX, failed);
                totalInserted++;
            }

            // Critical Stage
            for (int i = 0; i < criticalCount; i++) {
                double vib = 2.5 + Math.random() * 1.5;        // 2.5-4.0 mm/s
                double temp = 85 + Math.random() * 25;         // 85-110+°C
                double rpm = 5000 + Math.random() * 2000;      // 5000-7000 RPM
                double power = 42 + Math.random() * 23;        // 42-65 kW
                double pressure = 4.5 + Math.random() * 1.0;   // 4.5-5.5 bar
                double torque = 50 + Math.random() * 15;       // 50-65 Nm

                insertTrainingRow(vib/VIB_MAX, Math.min(temp/TEMP_MAX, 1.3),
                        0.03 + Math.random() * 0.10, 0.20 + Math.random() * 0.35,
                        30 + Math.random() * 50, 4 + Math.random() * 10,
                        1 + (int)(Math.random() * 2), Math.min(power/POWER_MAX, 1.3), rpm/RPM_MAX, pressure/PRESSURE_MAX, torque/TORQUE_MAX, 1);
                totalInserted++;
            }
            distribution.put("motor_early", earlyCount);
            distribution.put("motor_moderate", moderateCount);
            distribution.put("motor_critical", criticalCount);

            // ═══════════════════════════════════════════════════════════════════
            // ELECTRICAL FAULT — Power SPIKES ↑↑↑, RPM erratic/dropping ↓↓
            // ═══════════════════════════════════════════════════════════════════

            // Early Stage
            for (int i = 0; i < earlyCount; i++) {
                double vib = 2.3 + Math.random() * 0.9;        // 2.3-3.2 mm/s
                double temp = 52 + Math.random() * 16;         // 52-68°C
                double rpm = 7000 + Math.random() * 1500;      // 7000-8500 RPM (erratic start)
                double power = 32 + Math.random() * 10;        // 32-42 kW (occasional spikes)
                double pressure = 4.8 + Math.random() * 1.0;   // 4.8-5.8 bar
                double torque = 45 + Math.random() * 13;       // 45-58 Nm

                int failed = Math.random() < 0.30 ? 1 : 0;
                insertTrainingRow(vib/VIB_MAX, temp/TEMP_MAX,
                        0.02 + Math.random() * 0.06, 0.02 + Math.random() * 0.08,
                        20 + Math.random() * 30, 1 + Math.random() * 5,
                        Math.random() < 0.3 ? 1 : 0, power/POWER_MAX, rpm/RPM_MAX, pressure/PRESSURE_MAX, torque/TORQUE_MAX, failed);
                totalInserted++;
            }

            // Moderate Stage
            for (int i = 0; i < moderateCount; i++) {
                double vib = 2.8 + Math.random() * 1.2;        // 2.8-4.0 mm/s
                double temp = 58 + Math.random() * 20;         // 58-78°C
                double rpm = 5800 + Math.random() * 2000;      // 5800-7800 RPM (dropping/erratic)
                double power = 40 + Math.random() * 12;        // 40-52 kW (spikes)
                double pressure = 4.5 + Math.random() * 1.0;   // 4.5-5.5 bar
                double torque = 48 + Math.random() * 14;       // 48-62 Nm

                int failed = Math.random() < 0.80 ? 1 : 0;
                insertTrainingRow(vib/VIB_MAX, temp/TEMP_MAX,
                        0.04 + Math.random() * 0.10, 0.05 + Math.random() * 0.12,
                        25 + Math.random() * 35, 2 + Math.random() * 6,
                        1, Math.min(power/POWER_MAX, 1.05), rpm/RPM_MAX, pressure/PRESSURE_MAX, torque/TORQUE_MAX, failed);
                totalInserted++;
            }

            // Critical Stage
            for (int i = 0; i < criticalCount; i++) {
                double vib = 3.5 + Math.random() * 3.0;        // 3.5-6.5 mm/s
                double temp = 60 + Math.random() * 30;         // 60-90°C
                double rpm = 4500 + Math.random() * 3000;      // 4500-7500 RPM (erratic/low)
                double power = 48 + Math.random() * 27;        // 48-75 kW (severe spikes)
                double pressure = 4.2 + Math.random() * 1.0;   // 4.2-5.2 bar
                double torque = 48 + Math.random() * 17;       // 48-65 Nm

                insertTrainingRow(Math.min(vib/VIB_MAX, 1.3), temp/TEMP_MAX,
                        0.05 + Math.random() * 0.15, 0.08 + Math.random() * 0.18,
                        30 + Math.random() * 45, 3 + Math.random() * 8,
                        2 + (int)(Math.random() * 2), Math.min(power/POWER_MAX, 1.5), rpm/RPM_MAX, pressure/PRESSURE_MAX, torque/TORQUE_MAX, 1);
                totalInserted++;
            }
            distribution.put("electrical_early", earlyCount);
            distribution.put("electrical_moderate", moderateCount);
            distribution.put("electrical_critical", criticalCount);

            // ═══════════════════════════════════════════════════════════════════
            // COOLANT FAILURE — HIGH temperature ↑↑, LOW pressure ↓↓
            // ═══════════════════════════════════════════════════════════════════

            // Early Stage
            for (int i = 0; i < earlyCount; i++) {
                double vib = 2.0 + Math.random() * 0.8;        // 2.0-2.8 mm/s
                double temp = 60 + Math.random() * 12;         // 60-72°C
                double rpm = 7500 + Math.random() * 1300;      // 7500-8800 RPM
                double power = 22 + Math.random() * 10;        // 22-32 kW
                double pressure = 4.5 + Math.random() * 1.0;   // 4.5-5.5 bar (starting to drop)
                double torque = 42 + Math.random() * 13;       // 42-55 Nm

                int failed = Math.random() < 0.30 ? 1 : 0;
                insertTrainingRow(vib/VIB_MAX, temp/TEMP_MAX,
                        (Math.random()-0.2)*0.05, 0.05 + Math.random() * 0.10,
                        20 + Math.random() * 30, 2 + Math.random() * 5,
                        0, power/POWER_MAX, rpm/RPM_MAX, pressure/PRESSURE_MAX, torque/TORQUE_MAX, failed);
                totalInserted++;
            }

            // Moderate Stage
            for (int i = 0; i < moderateCount; i++) {
                double vib = 2.2 + Math.random() * 1.0;        // 2.2-3.2 mm/s
                double temp = 72 + Math.random() * 13;         // 72-85°C
                double rpm = 7200 + Math.random() * 1300;      // 7200-8500 RPM
                double power = 24 + Math.random() * 11;        // 24-35 kW
                double pressure = 3.5 + Math.random() * 1.3;   // 3.5-4.8 bar (LOW)
                double torque = 44 + Math.random() * 14;       // 44-58 Nm

                int failed = Math.random() < 0.80 ? 1 : 0;
                insertTrainingRow(vib/VIB_MAX, temp/TEMP_MAX,
                        0.01 + Math.random() * 0.05, 0.10 + Math.random() * 0.18,
                        28 + Math.random() * 35, 3 + Math.random() * 6,
                        Math.random() < 0.3 ? 1 : 0, power/POWER_MAX, rpm/RPM_MAX, pressure/PRESSURE_MAX, torque/TORQUE_MAX, failed);
                totalInserted++;
            }

            // Critical Stage
            for (int i = 0; i < criticalCount; i++) {
                double vib = 2.5 + Math.random() * 1.3;        // 2.5-3.8 mm/s
                double temp = 80 + Math.random() * 30;         // 80-110+°C
                double rpm = 7000 + Math.random() * 1200;      // 7000-8200 RPM
                double power = 25 + Math.random() * 13;        // 25-38 kW
                double pressure = 2.0 + Math.random() * 2.0;   // 2.0-4.0 bar (VERY LOW!)
                double torque = 42 + Math.random() * 16;       // 42-58 Nm

                insertTrainingRow(vib/VIB_MAX, Math.min(temp/TEMP_MAX, 1.3),
                        0.02 + Math.random() * 0.06, 0.15 + Math.random() * 0.30,
                        35 + Math.random() * 45, 4 + Math.random() * 8,
                        1 + (int)(Math.random() * 2), power/POWER_MAX, rpm/RPM_MAX, pressure/PRESSURE_MAX, torque/TORQUE_MAX, 1);
                totalInserted++;
            }
            distribution.put("coolant_early", earlyCount);
            distribution.put("coolant_moderate", moderateCount);
            distribution.put("coolant_critical", criticalCount);

            // ═══════════════════════════════════════════════════════════════════
            // SPINDLE WEAR — HIGH vibration ↑↑, HIGH torque ↑↑, LOW RPM ↓↓
            // ═══════════════════════════════════════════════════════════════════

            // Early Stage
            for (int i = 0; i < earlyCount; i++) {
                double vib = 2.5 + Math.random() * 1.0;        // 2.5-3.5 mm/s
                double temp = 52 + Math.random() * 13;         // 52-65°C
                double rpm = 7200 + Math.random() * 1300;      // 7200-8500 RPM (starting to drop)
                double power = 28 + Math.random() * 10;        // 28-38 kW
                double pressure = 5.0 + Math.random() * 1.0;   // 5.0-6.0 bar
                double torque = 55 + Math.random() * 13;       // 55-68 Nm (rising)

                int failed = Math.random() < 0.30 ? 1 : 0;
                insertTrainingRow(vib/VIB_MAX, temp/TEMP_MAX,
                        0.03 + Math.random() * 0.08, 0.02 + Math.random() * 0.06,
                        25 + Math.random() * 30, 3 + Math.random() * 6,
                        Math.random() < 0.2 ? 1 : 0, power/POWER_MAX, rpm/RPM_MAX, pressure/PRESSURE_MAX, torque/TORQUE_MAX, failed);
                totalInserted++;
            }

            // Moderate Stage
            for (int i = 0; i < moderateCount; i++) {
                double vib = 3.2 + Math.random() * 1.3;        // 3.2-4.5 mm/s
                double temp = 55 + Math.random() * 17;         // 55-72°C
                double rpm = 6200 + Math.random() * 1600;      // 6200-7800 RPM (dropping)
                double power = 30 + Math.random() * 12;        // 30-42 kW
                double pressure = 4.8 + Math.random() * 1.0;   // 4.8-5.8 bar
                double torque = 62 + Math.random() * 13;       // 62-75 Nm (HIGH)

                int failed = Math.random() < 0.80 ? 1 : 0;
                insertTrainingRow(vib/VIB_MAX, temp/TEMP_MAX,
                        0.05 + Math.random() * 0.12, 0.03 + Math.random() * 0.08,
                        35 + Math.random() * 35, 4 + Math.random() * 7,
                        Math.random() < 0.4 ? 1 : 0, power/POWER_MAX, rpm/RPM_MAX, pressure/PRESSURE_MAX, torque/TORQUE_MAX, failed);
                totalInserted++;
            }

            // Critical Stage
            for (int i = 0; i < criticalCount; i++) {
                double vib = 3.8 + Math.random() * 2.7;        // 3.8-6.5 mm/s
                double temp = 58 + Math.random() * 20;         // 58-78°C
                double rpm = 5500 + Math.random() * 1700;      // 5500-7200 RPM (VERY LOW)
                double power = 32 + Math.random() * 13;        // 32-45 kW
                double pressure = 4.5 + Math.random() * 1.0;   // 4.5-5.5 bar
                double torque = 68 + Math.random() * 22;       // 68-90 Nm (VERY HIGH!)

                insertTrainingRow(Math.min(vib/VIB_MAX, 1.3), temp/TEMP_MAX,
                        0.08 + Math.random() * 0.18, 0.04 + Math.random() * 0.10,
                        45 + Math.random() * 45, 5 + Math.random() * 10,
                        1 + (int)(Math.random() * 2), power/POWER_MAX, rpm/RPM_MAX, pressure/PRESSURE_MAX, Math.min(torque/TORQUE_MAX, 1.15), 1);
                totalInserted++;
            }
            distribution.put("spindle_early", earlyCount);
            distribution.put("spindle_moderate", moderateCount);
            distribution.put("spindle_critical", criticalCount);

            long elapsed = System.currentTimeMillis() - start;

            // Count final distribution
            Integer normalTotal = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM ml_training_data WHERE failed = 0", Integer.class);
            Integer failureTotal = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM ml_training_data WHERE failed = 1", Integer.class);

            log.info("Generated {} training observations ({} normal, {} failure) in {}ms",
                    totalInserted, normalTotal, failureTotal, elapsed);

            return Map.of(
                    "success", true,
                    "totalObservations", totalInserted,
                    "normalObservations", normalTotal != null ? normalTotal : 0,
                    "failureObservations", failureTotal != null ? failureTotal : 0,
                    "patterns", 5,
                    "stagesPerPattern", 3,
                    "totalStates", 16,
                    "distribution", distribution,
                    "elapsedMs", elapsed,
                    "message", "Comprehensive 16-state training data generated. Call /ml/retrain to train the model."
            );

        } catch (Exception e) {
            log.error("Training data generation failed: {}", e.getMessage());
            return Map.of("success", false, "error", e.getMessage());
        }
    }

    private void insertTrainingRow(double vib, double temp, double vibTrend, double tempTrend,
                                    double daysMaint, double age, int anomalies,
                                    double power, double rpm, double pressure, double torque, int failed) {
        jdbcTemplate.update("""
            INSERT INTO ml_training_data
            (vibration_normalized, temperature_normalized, vibration_trend_rate, temperature_trend_rate,
             days_since_maintenance, equipment_age_years, anomaly_count,
             power_normalized, rpm_normalized, pressure_normalized, torque_normalized, failed)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            Math.min(vib, 1.5), Math.min(temp, 1.2),
            Math.max(-0.5, Math.min(vibTrend, 0.5)), Math.max(-1.0, Math.min(tempTrend, 1.0)),
            Math.min(daysMaint, 90), Math.min(age, 20), anomalies,
            Math.min(power, 1.2), Math.min(rpm, 1.0), Math.min(pressure, 1.0), Math.min(torque, 1.2), failed);
    }

    /**
     * Get training data statistics and sample rows.
     */
    @GetMapping("/training/stats")
    public Map<String, Object> getTrainingStats() {
        Integer total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ml_training_data", Integer.class);
        Integer normal = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ml_training_data WHERE failed = 0", Integer.class);
        Integer failure = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ml_training_data WHERE failed = 1", Integer.class);

        // Get feature ranges for failure cases
        Map<String, Object> failureRanges = jdbcTemplate.queryForMap("""
            SELECT
                MIN(vibration_normalized) as vib_min, MAX(vibration_normalized) as vib_max,
                MIN(temperature_normalized) as temp_min, MAX(temperature_normalized) as temp_max,
                MIN(power_normalized) as power_min, MAX(power_normalized) as power_max
            FROM ml_training_data WHERE failed = 1
            """);

        return Map.of(
                "totalObservations", total != null ? total : 0,
                "normalObservations", normal != null ? normal : 0,
                "failureObservations", failure != null ? failure : 0,
                "failureFeatureRanges", failureRanges
        );
    }
}
