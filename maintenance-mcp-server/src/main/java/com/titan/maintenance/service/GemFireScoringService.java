package com.titan.maintenance.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.geode.cache.Region;
import org.eclipse.paho.client.mqttv3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Real-time PMML scoring service.
 * Subscribes to MQTT sensor data, scores with the logistic regression model,
 * and writes predictions to GemFire SensorPredictions region.
 */
@Service
public class GemFireScoringService implements MqttCallback {

    private static final Logger log = LoggerFactory.getLogger(GemFireScoringService.class);

    // Model coefficients — loaded dynamically from Greenplum ml_model_coefficients
    private volatile double intercept = -7.5;
    private volatile double coeffVibrationNorm = 6.0;
    private volatile double coeffTemperatureNorm = 3.5;
    private volatile double coeffVibrationTrend = 0.5;
    private volatile double coeffTemperatureTrend = 0.3;
    private volatile double coeffDaysSinceMaint = 0.005;
    private volatile double coeffEquipmentAge = 0.02;
    private volatile double coeffAnomalyCount = 2.0;
    private volatile double coeffPowerNorm = 0.0;
    private volatile double coeffRpmNorm = 0.0;
    private volatile double coeffPressureNorm = 0.0;
    private volatile double coeffTorqueNorm = 0.0;

    // Normalization thresholds (matching equipment_ml_features view)
    private static final double VIBRATION_CRITICAL = 5.0;
    private static final double TEMPERATURE_CRITICAL = 85.0;
    private static final double POWER_MAX = 50.0;
    private static final double RPM_MAX = 10000.0;
    private static final double PRESSURE_MAX = 10.0;
    private static final double TORQUE_MAX = 80.0;

    // Sliding window: 3 minutes of readings
    private static final long WINDOW_MS = 3 * 60 * 1000;

    private final GemFireService gemFireService;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    @Value("${mqtt.broker:tcp://localhost:1883}")
    private String mqttBroker;

    @Value("${mqtt.client-id:maintenance-scorer}")
    private String mqttClientId;

    @Value("${mqtt.username:titan}")
    private String mqttUsername;

    @Value("${mqtt.password:titan5.0}")
    private String mqttPassword;

    @Value("${mqtt.topic:titan/sensors/#}")
    private String mqttTopic;

    private MqttClient mqttClient;

    // equipmentId → list of timestamped readings
    private final ConcurrentHashMap<String, CopyOnWriteArrayList<SensorSnapshot>> sensorWindows = new ConcurrentHashMap<>();

    // Cached equipment metadata from Greenplum (loaded once)
    private final Map<String, EquipmentMeta> equipmentMeta = new ConcurrentHashMap<>();

    public GemFireScoringService(GemFireService gemFireService, JdbcTemplate jdbcTemplate) {
        this.gemFireService = gemFireService;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    @PostConstruct
    public void start() {
        loadCoefficients();
        loadEquipmentMetadata();
        connectMqtt();
    }

    /**
     * Load model coefficients from Greenplum ml_model_coefficients table.
     * Called at startup and can be called after retrain to pick up new values.
     */
    public void loadCoefficients() {
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT feature_name, coefficient FROM ml_model_coefficients WHERE model_id = 'failure_predictor_v1'");
            for (Map<String, Object> row : rows) {
                String feature = (String) row.get("feature_name");
                double coeff = ((Number) row.get("coefficient")).doubleValue();
                switch (feature) {
                    case "intercept" -> intercept = coeff;
                    case "vibration_normalized" -> coeffVibrationNorm = coeff;
                    case "temperature_normalized" -> coeffTemperatureNorm = coeff;
                    case "vibration_trend_rate" -> coeffVibrationTrend = coeff;
                    case "temperature_trend_rate" -> coeffTemperatureTrend = coeff;
                    case "days_since_maintenance" -> coeffDaysSinceMaint = coeff;
                    case "equipment_age_years" -> coeffEquipmentAge = coeff;
                    case "anomaly_count" -> coeffAnomalyCount = coeff;
                    case "power_normalized" -> coeffPowerNorm = coeff;
                    case "rpm_normalized" -> coeffRpmNorm = coeff;
                    case "pressure_normalized" -> coeffPressureNorm = coeff;
                    case "torque_normalized" -> coeffTorqueNorm = coeff;
                }
            }
            log.info("Loaded {} coefficients from Greenplum (intercept={}, vibration_norm={})",
                    rows.size(), intercept, coeffVibrationNorm);
        } catch (Exception e) {
            log.warn("Failed to load coefficients from Greenplum: {}. Using defaults.", e.getMessage());
        }
    }

    @PreDestroy
    public void stop() {
        if (mqttClient != null && mqttClient.isConnected()) {
            try {
                mqttClient.disconnect();
                mqttClient.close();
            } catch (MqttException e) {
                log.warn("Error disconnecting MQTT: {}", e.getMessage());
            }
        }
    }

    private void loadEquipmentMetadata() {
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT equipment_id,
                       COALESCE(EXTRACT(EPOCH FROM (NOW() - last_maintenance)) / 86400, 30) AS days_since_maintenance,
                       COALESCE(EXTRACT(EPOCH FROM (NOW() - install_date)) / (86400 * 365.25), 2) AS equipment_age_years
                FROM equipment
                """);
            for (Map<String, Object> row : rows) {
                String id = (String) row.get("equipment_id");
                double days = ((Number) row.get("days_since_maintenance")).doubleValue();
                double age = ((Number) row.get("equipment_age_years")).doubleValue();
                equipmentMeta.put(id, new EquipmentMeta(Math.min(days, 90), Math.min(age, 20)));
            }
            log.info("Loaded metadata for {} equipment from Greenplum", equipmentMeta.size());
        } catch (Exception e) {
            log.warn("Failed to load equipment metadata: {}. Using defaults.", e.getMessage());
        }
    }

    private void connectMqtt() {
        try {
            mqttClient = new MqttClient(mqttBroker, mqttClientId + "-" + System.currentTimeMillis());
            MqttConnectOptions options = new MqttConnectOptions();
            options.setUserName(mqttUsername);
            options.setPassword(mqttPassword.toCharArray());
            options.setAutomaticReconnect(true);
            options.setCleanSession(true);
            mqttClient.setCallback(this);
            mqttClient.connect(options);
            mqttClient.subscribe(mqttTopic, 0);
            log.info("MQTT connected to {} — subscribing to {}", mqttBroker, mqttTopic);
        } catch (MqttException e) {
            log.warn("MQTT connection failed ({}). Scoring will retry.", e.getMessage());
        }
    }

    // ── MQTT Callback ──────────────────────────────────────────────────────

    @Override
    public void connectionLost(Throwable cause) {
        log.warn("MQTT connection lost: {}. Auto-reconnect enabled.", cause.getMessage());
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) {
        try {
            JsonNode json = objectMapper.readTree(message.getPayload());
            String equipmentId = json.get("equipmentId").asText();
            String sensorType = json.get("sensorType").asText();
            double value = json.get("value").asDouble();

            SensorSnapshot snap = new SensorSnapshot(sensorType, value, System.currentTimeMillis());
            sensorWindows.computeIfAbsent(equipmentId, k -> new CopyOnWriteArrayList<>()).add(snap);
        } catch (Exception e) {
            // silently drop malformed messages
        }
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
        // not used for subscriber
    }

    // ── Scheduled Scoring ──────────────────────────────────────────────────

    @Scheduled(fixedDelay = 30000, initialDelay = 15000)
    public void scoreAllEquipment() {
        if (!gemFireService.isConnected()) {
            log.debug("GemFire not connected, skipping scoring cycle");
            return;
        }

        long now = System.currentTimeMillis();
        long cutoff = now - WINDOW_MS;
        int scored = 0;

        Region<String, String> predictionsRegion = gemFireService.getSensorPredictionsRegion();

        for (Map.Entry<String, CopyOnWriteArrayList<SensorSnapshot>> entry : sensorWindows.entrySet()) {
            String equipmentId = entry.getKey();
            CopyOnWriteArrayList<SensorSnapshot> snapshots = entry.getValue();

            // Evict old readings
            snapshots.removeIf(s -> s.timestamp < cutoff);

            if (snapshots.size() < 4) continue; // need at least a few readings

            try {
                String predictionJson = scoreEquipment(equipmentId, snapshots, now);
                predictionsRegion.put(equipmentId, predictionJson);
                scored++;
            } catch (Exception e) {
                log.debug("Scoring failed for {}: {}", equipmentId, e.getMessage());
            }
        }

        if (scored > 0) {
            log.info("GemFire scoring cycle: scored {} equipment", scored);
        }
    }

    private String scoreEquipment(String equipmentId, List<SensorSnapshot> snapshots, long now) throws Exception {
        // Compute feature averages and collect time-series for trend calculation
        double vibrationSum = 0, vibrationCount = 0;
        double temperatureSum = 0, temperatureCount = 0;
        double powerSum = 0, powerCount = 0;
        double rpmSum = 0, rpmCount = 0;
        double pressureSum = 0, pressureCount = 0;
        double torqueSum = 0, torqueCount = 0;
        List<double[]> vibReadings = new ArrayList<>();  // [timeHours, value]
        List<double[]> tempReadings = new ArrayList<>();

        long baseTime = snapshots.get(0).timestamp;
        for (SensorSnapshot s : snapshots) {
            double timeHours = (s.timestamp - baseTime) / 3_600_000.0;
            switch (s.sensorType) {
                case "vibration" -> { vibrationSum += s.value; vibrationCount++; vibReadings.add(new double[]{timeHours, s.value}); }
                case "temperature" -> { temperatureSum += s.value; temperatureCount++; tempReadings.add(new double[]{timeHours, s.value}); }
                case "power", "power_draw" -> { powerSum += s.value; powerCount++; }
                case "spindle_speed" -> { rpmSum += s.value; rpmCount++; }
                case "pressure" -> { pressureSum += s.value; pressureCount++; }
                case "torque" -> { torqueSum += s.value; torqueCount++; }
            }
        }

        double vibrationAvg = vibrationCount > 0 ? vibrationSum / vibrationCount : 2.0;
        double temperatureAvg = temperatureCount > 0 ? temperatureSum / temperatureCount : 50.0;
        double powerAvg = powerCount > 0 ? powerSum / powerCount : 15.0;
        double rpmAvg = rpmCount > 0 ? rpmSum / rpmCount : 8500.0;
        double pressureAvg = pressureCount > 0 ? pressureSum / pressureCount : 6.0;
        double torqueAvg = torqueCount > 0 ? torqueSum / torqueCount : 45.0;

        // Normalized features
        double vibrationNormalized = Math.min(vibrationAvg / VIBRATION_CRITICAL, 1.0);
        double temperatureNormalized = Math.min(temperatureAvg / TEMPERATURE_CRITICAL, 1.0);

        // Trend rates via least-squares regression slope (per hour), then normalized
        // to match the training data scale. The training features used normalized trends:
        //   vibration_trend_rate = raw_slope / VIBRATION_CRITICAL
        //   temperature_trend_rate = raw_slope / TEMPERATURE_CRITICAL
        // This keeps trends in the same [0..1] scale the MADlib model was trained on.
        // We gate on R² > 0.5 and require ≥20 readings spanning ≥60s
        // to suppress startup noise and random jitter.
        double vibrationTrendRate = 0;
        double temperatureTrendRate = 0;
        if (vibReadings.size() >= 20) {
            double timeSpan = vibReadings.get(vibReadings.size() - 1)[0] - vibReadings.get(0)[0];
            if (timeSpan > 1.0 / 60.0) { // > 1 minute in hours
                double[] vibResult = leastSquaresFit(vibReadings);
                if (vibResult[1] > 0.5) {
                    double normalizedSlope = vibResult[0] / VIBRATION_CRITICAL;
                    vibrationTrendRate = Math.max(-0.5, Math.min(0.5, normalizedSlope));
                }
            }
        }
        if (tempReadings.size() >= 20) {
            double timeSpan = tempReadings.get(tempReadings.size() - 1)[0] - tempReadings.get(0)[0];
            if (timeSpan > 1.0 / 60.0) { // > 1 minute in hours
                double[] tempResult = leastSquaresFit(tempReadings);
                if (tempResult[1] > 0.5) {
                    double normalizedSlope = tempResult[0] / TEMPERATURE_CRITICAL;
                    temperatureTrendRate = Math.max(-0.5, Math.min(0.5, normalizedSlope));
                }
            }
        }

        // Equipment metadata
        EquipmentMeta meta = equipmentMeta.getOrDefault(equipmentId, new EquipmentMeta(30, 2));
        int anomalyCount = 0; // default — could query periodically

        // Logistic regression scoring
        double logit = intercept
                + coeffVibrationNorm * vibrationNormalized
                + coeffTemperatureNorm * temperatureNormalized
                + coeffVibrationTrend * vibrationTrendRate
                + coeffTemperatureTrend * temperatureTrendRate
                + coeffDaysSinceMaint * meta.daysSinceMaintenance
                + coeffEquipmentAge * meta.equipmentAgeYears
                + coeffAnomalyCount * anomalyCount
                + coeffPowerNorm * (powerAvg / POWER_MAX)
                + coeffRpmNorm * (rpmAvg / RPM_MAX)
                + coeffPressureNorm * (pressureAvg / PRESSURE_MAX)
                + coeffTorqueNorm * (torqueAvg / TORQUE_MAX);

        double probability = 1.0 / (1.0 + Math.exp(-logit));

        String riskLevel;
        if (probability >= 0.7) riskLevel = "CRITICAL";
        else if (probability >= 0.5) riskLevel = "HIGH";
        else if (probability >= 0.3) riskLevel = "MEDIUM";
        else riskLevel = "LOW";

        // ── Feature contribution analysis ─────────────────────────────────
        // Each feature's contribution = coefficient × feature value.
        // The sign and magnitude tell us what's driving the prediction.
        double contribVibNorm = coeffVibrationNorm * vibrationNormalized;
        double contribTempNorm = coeffTemperatureNorm * temperatureNormalized;
        double contribVibTrend = coeffVibrationTrend * vibrationTrendRate;
        double contribTempTrend = coeffTemperatureTrend * temperatureTrendRate;
        double contribPower = coeffPowerNorm * (powerAvg / POWER_MAX);
        double contribRpm = coeffRpmNorm * (rpmAvg / RPM_MAX);
        double contribPressure = coeffPressureNorm * (pressureAvg / PRESSURE_MAX);
        double contribTorque = coeffTorqueNorm * (torqueAvg / TORQUE_MAX);

        // Identify primary and secondary drivers (only positive contributions matter)
        Map<String, Double> drivers = new LinkedHashMap<>();
        if (contribVibNorm > 0.5) drivers.put("vibration_level", contribVibNorm);
        if (contribTempNorm > 0.5) drivers.put("temperature_level", contribTempNorm);
        if (contribVibTrend > 0.5) drivers.put("vibration_trend", contribVibTrend);
        if (contribTempTrend > 0.5) drivers.put("temperature_trend", contribTempTrend);
        if (contribPower > 0.5) drivers.put("power_level", contribPower);
        if (contribRpm > 0.5) drivers.put("rpm_level", contribRpm);
        if (contribPressure > 0.5) drivers.put("pressure_level", contribPressure);
        if (contribTorque > 0.5) drivers.put("torque_level", contribTorque);

        // Map contribution pattern to probable failure cause using all 6 sensor types
        String probableCause = diagnoseProbableCause(
                vibrationAvg, temperatureAvg, powerAvg, rpmAvg, pressureAvg, torqueAvg,
                vibrationTrendRate, temperatureTrendRate);

        // Build JSON result
        Map<String, Object> prediction = new LinkedHashMap<>();
        prediction.put("equipmentId", equipmentId);
        prediction.put("failureProbability", Math.round(probability * 1000.0) / 1000.0);
        prediction.put("riskLevel", riskLevel);
        prediction.put("probableCause", probableCause);
        prediction.put("vibrationAvg", Math.round(vibrationAvg * 100.0) / 100.0);
        prediction.put("temperatureAvg", Math.round(temperatureAvg * 100.0) / 100.0);
        prediction.put("powerAvg", Math.round(powerAvg * 100.0) / 100.0);
        prediction.put("rpmAvg", Math.round(rpmAvg * 10.0) / 10.0);
        prediction.put("pressureAvg", Math.round(pressureAvg * 100.0) / 100.0);
        prediction.put("torqueAvg", Math.round(torqueAvg * 100.0) / 100.0);
        prediction.put("vibrationTrend", Math.round(vibrationTrendRate * 1000.0) / 1000.0);
        prediction.put("temperatureTrend", Math.round(temperatureTrendRate * 1000.0) / 1000.0);
        prediction.put("drivers", drivers);
        prediction.put("readingsInWindow", snapshots.size());
        prediction.put("modelId", "failure_predictor_v1");
        prediction.put("scoredAt", Instant.ofEpochMilli(now).toString());

        return objectMapper.writeValueAsString(prediction);
    }

    /**
     * Clear all sensor windows and GemFire predictions.
     * Called after simulation reset to flush stale data from the scoring pipeline.
     */
    public Map<String, Object> clearAllPredictions() {
        int windowsCleared = sensorWindows.size();
        sensorWindows.clear();

        int predictionsCleared = 0;
        try {
            if (gemFireService.isConnected()) {
                Region<String, String> region = gemFireService.getSensorPredictionsRegion();
                Set<String> keys = region.keySetOnServer();
                predictionsCleared = keys.size();
                for (String key : keys) {
                    region.remove(key);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to clear GemFire predictions: {}", e.getMessage());
        }

        log.info("Cleared {} sensor windows and {} GemFire predictions", windowsCleared, predictionsCleared);
        return Map.of(
                "success", true,
                "windowsCleared", windowsCleared,
                "predictionsCleared", predictionsCleared
        );
    }

    // ── MCP Tool ───────────────────────────────────────────────────────────

    @McpTool(description = "Get real-time ML predictions from GemFire SensorPredictions region. " +
            "Shows failure probability scored from live MQTT sensor data using the PMML model.")
    public Map<String, Object> getGemFirePredictions() {
        log.info("Retrieving GemFire predictions");

        try {
            Region<String, String> region = gemFireService.getSensorPredictionsRegion();
            Set<String> keys = region.keySetOnServer();

            List<Map<String, Object>> predictions = new ArrayList<>();
            for (String key : keys) {
                String json = region.get(key);
                if (json != null) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> pred = objectMapper.readValue(json, Map.class);
                    predictions.add(pred);
                }
            }

            // Sort by probability descending
            predictions.sort((a, b) -> {
                double pa = ((Number) a.get("failureProbability")).doubleValue();
                double pb = ((Number) b.get("failureProbability")).doubleValue();
                return Double.compare(pb, pa);
            });

            long critical = predictions.stream()
                    .filter(p -> "CRITICAL".equals(p.get("riskLevel")))
                    .count();

            return Map.of(
                    "success", true,
                    "totalEquipment", predictions.size(),
                    "criticalCount", critical,
                    "predictions", predictions,
                    "scoringSource", "GemFire SensorPredictions (PMML logistic regression)"
            );
        } catch (Exception e) {
            log.error("Failed to retrieve GemFire predictions: {}", e.getMessage());
            return Map.of("success", false, "error", e.getMessage());
        }
    }

    /**
     * Diagnose probable failure cause using all 6 sensor types.
     * Each degradation pattern has a distinct sensor signature:
     *   BEARING_DEGRADATION: vibration↑↑, temp↑, power/rpm/torque/pressure ~normal
     *   ELECTRICAL_FAULT:    vibration↑, temp↑, power↑↑, rpm may drop
     *   COOLANT_FAILURE:     temp↑↑, pressure↓, vibration ~normal
     *   MOTOR_BURNOUT:       temp↑↑↑ (rapid), vibration irregular, power↑
     *   SPINDLE_WEAR:        rpm↓, vibration↑ (slow), torque fluctuations
     */
    private String diagnoseProbableCause(
            double vibrationAvg, double temperatureAvg, double powerAvg,
            double rpmAvg, double pressureAvg, double torqueAvg,
            double vibTrendRate, double tempTrendRate) {

        // Deviation from normal baselines
        boolean vibHigh = vibrationAvg > 3.0;
        boolean vibCritical = vibrationAvg > 4.0;
        boolean tempHigh = temperatureAvg > 65.0;
        boolean tempCritical = temperatureAvg > 80.0;
        boolean powerHigh = powerAvg > 25.0;   // normal ~15 kW
        boolean powerSpike = powerAvg > 40.0;
        boolean rpmLow = rpmAvg < 7500.0;      // normal ~8500
        boolean pressureLow = pressureAvg < 4.5; // normal ~6.0
        boolean torqueHigh = torqueAvg > 55.0;  // normal ~45

        // MOTOR_BURNOUT: temperature is the dominant signal — check first to avoid
        // misdiagnosis as electrical (motor burnout also raises power and vibration)
        if (tempCritical && powerHigh) {
            return "Motor burnout — temperature at " + String.format("%.0f", temperatureAvg)
                    + "°C (critical) with elevated power draw (" + String.format("%.0f", powerAvg)
                    + " kW), motor overheating under load";
        }
        if (tempCritical && tempTrendRate > 0.02) {
            return "Motor burnout risk — temperature at " + String.format("%.0f", temperatureAvg)
                    + "°C rising rapidly, check motor windings and cooling";
        }

        // COOLANT_FAILURE: temperature rises with pressure drop, vibration stays normal-ish
        if (tempHigh && pressureLow) {
            return "Coolant system failure — temperature at " + String.format("%.0f", temperatureAvg)
                    + "°C with coolant pressure dropped to " + String.format("%.1f", pressureAvg)
                    + " bar (normal: 6.0 bar)";
        }

        // ELECTRICAL_FAULT: power surge is the distinguishing signal, often with RPM drop
        // Only matches when temperature is NOT critical (otherwise it's motor burnout)
        if (powerSpike && vibHigh) {
            return "Electrical fault — power draw at " + String.format("%.0f", powerAvg)
                    + " kW (normal: 15 kW) with vibration at " + String.format("%.1f", vibrationAvg)
                    + " mm/s, consistent with electrical resistance or winding degradation";
        }
        if (powerHigh && vibHigh && !tempCritical) {
            return "Electrical fault — elevated power (" + String.format("%.0f", powerAvg)
                    + " kW, normal: 15 kW) with vibration at " + String.format("%.1f", vibrationAvg)
                    + " mm/s"
                    + (rpmLow ? ", RPM degraded to " + String.format("%.0f", rpmAvg) : "")
                    + " — motor drawing excess current";
        }

        // SPINDLE_WEAR: RPM drops, torque increases, vibration rises slowly
        if (rpmLow && (vibHigh || torqueHigh)) {
            return "Spindle wear — RPM degraded to " + String.format("%.0f", rpmAvg)
                    + " (normal: 8500)"
                    + (torqueHigh ? ", torque elevated at " + String.format("%.0f", torqueAvg) + " Nm" : "")
                    + (vibHigh ? ", vibration at " + String.format("%.1f", vibrationAvg) + " mm/s" : "");
        }

        // BEARING_DEGRADATION: vibration dominant, other sensors mostly normal
        if (vibCritical) {
            if (vibTrendRate > 0.02) {
                return "Bearing degradation — vibration at " + String.format("%.1f", vibrationAvg)
                        + " mm/s with rising trend, consistent with bearing wear pattern";
            }
            return "Probable bearing wear — sustained high vibration at " + String.format("%.1f", vibrationAvg) + " mm/s";
        }
        if (vibHigh && !powerHigh && !pressureLow) {
            return "Early bearing wear — vibration elevated at " + String.format("%.1f", vibrationAvg)
                    + " mm/s, monitor for progressive degradation";
        }

        // Early trends
        if (vibTrendRate > 0.03 && tempTrendRate > 0.02) {
            return "Early-stage degradation — vibration and temperature trending upward";
        }
        if (vibTrendRate > 0.03) {
            return "Emerging vibration anomaly — upward trend, monitor for bearing or spindle wear";
        }
        if (tempTrendRate > 0.03) {
            return "Emerging thermal anomaly — temperature trending upward, check coolant and motor";
        }

        return "Elevated sensor readings — multiple parameters outside normal range";
    }

    /**
     * Compute least-squares linear regression slope and R² from time-series data.
     * Returns [slope_per_hour, r_squared]. R² indicates how much variance is explained
     * by the trend — low R² means the slope is just noise.
     */
    private double[] leastSquaresFit(List<double[]> readings) {
        int n = readings.size();
        if (n < 2) return new double[]{0.0, 0.0};

        double sumX = 0, sumY = 0, sumXY = 0, sumX2 = 0, sumY2 = 0;
        for (double[] r : readings) {
            sumX += r[0];
            sumY += r[1];
            sumXY += r[0] * r[1];
            sumX2 += r[0] * r[0];
            sumY2 += r[1] * r[1];
        }
        double denomX = n * sumX2 - sumX * sumX;
        if (Math.abs(denomX) < 1e-12) return new double[]{0.0, 0.0};

        double slope = (n * sumXY - sumX * sumY) / denomX;

        // R² = (correlation coefficient)²
        double denomY = n * sumY2 - sumY * sumY;
        double r2 = 0.0;
        if (Math.abs(denomY) > 1e-12) {
            double r = (n * sumXY - sumX * sumY) / Math.sqrt(denomX * denomY);
            r2 = r * r;
        }
        return new double[]{slope, r2};
    }

    // ── Inner types ────────────────────────────────────────────────────────

    private record SensorSnapshot(String sensorType, double value, long timestamp) {}
    private record EquipmentMeta(double daysSinceMaintenance, double equipmentAgeYears) {}
}
