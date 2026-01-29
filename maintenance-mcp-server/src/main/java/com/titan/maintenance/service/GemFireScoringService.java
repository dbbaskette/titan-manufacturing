package com.titan.maintenance.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.geode.cache.Region;
import org.apache.geode.cache.execute.FunctionService;
import org.apache.geode.cache.execute.ResultCollector;
import org.eclipse.paho.client.mqttv3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.titan.maintenance.model.AnomalyEvent;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
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

    // Normalization thresholds (matching equipment_ml_features view and PMML training data)
    private static final double VIBRATION_CRITICAL = 5.0;
    private static final double TEMPERATURE_CRITICAL = 85.0;
    private static final double POWER_MAX = 50.0;
    private static final double RPM_MAX = 10000.0;
    private static final double PRESSURE_MAX = 10.0;
    private static final double TORQUE_MAX = 80.0;

    private static final String MODEL_ID = "failure_predictor_v1";

    // Sliding window: 3 minutes of readings
    private static final long WINDOW_MS = 3 * 60 * 1000;

    private final GemFireService gemFireService;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final RabbitTemplate rabbitTemplate;

    // Track published alerts to avoid duplicates (cleared when equipment recovers)
    private final Set<String> publishedAlerts = ConcurrentHashMap.newKeySet();

    // Consecutive scoring cycle failures — triggers GemFire reconnect after threshold
    private int consecutiveScoringFailures = 0;
    private static final int RECONNECT_THRESHOLD = 3;

    // Per-equipment max anomaly level: "CRITICAL" (all), "HIGH" (HIGH only), "NONE" (disabled)
    // Missing entries default to "CRITICAL" (publish all)
    private final ConcurrentHashMap<String, String> equipmentAnomalyLevels = new ConcurrentHashMap<>();
    private volatile String defaultAnomalyLevel = "CRITICAL";


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

    @Value("${generator.url:http://sensor-data-generator:8090}")
    private String generatorUrl;

    @Value("${anomaly.critical-routing-key:anomaly.critical}")
    private String criticalRoutingKey;

    @Value("${anomaly.high-routing-key:anomaly.high}")
    private String highRoutingKey;

    private MqttClient mqttClient;

    // equipmentId → list of timestamped readings
    private final ConcurrentHashMap<String, CopyOnWriteArrayList<SensorSnapshot>> sensorWindows = new ConcurrentHashMap<>();

    // Cached equipment metadata from Greenplum (loaded once)
    private final Map<String, EquipmentMeta> equipmentMeta = new ConcurrentHashMap<>();

    public GemFireScoringService(GemFireService gemFireService, JdbcTemplate jdbcTemplate, RabbitTemplate rabbitTemplate) {
        this.gemFireService = gemFireService;
        this.jdbcTemplate = jdbcTemplate;
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    public String getAnomalyLevel(String equipmentId) {
        return equipmentAnomalyLevels.getOrDefault(equipmentId, defaultAnomalyLevel);
    }

    public void setAnomalyLevel(String equipmentId, String level) {
        if ("CRITICAL".equals(level)) {
            equipmentAnomalyLevels.remove(equipmentId); // default is CRITICAL
        } else {
            equipmentAnomalyLevels.put(equipmentId, level);
        }
        log.info("Anomaly level for {} set to: {}", equipmentId, level);
    }

    public String getDefaultAnomalyLevel() {
        return defaultAnomalyLevel;
    }

    public void setDefaultAnomalyLevel(String level) {
        defaultAnomalyLevel = level;
        log.info("Default anomaly level set to: {}", level);
    }

    public Map<String, String> getAllAnomalyLevels() {
        return new HashMap<>(equipmentAnomalyLevels);
    }

    @PostConstruct
    public void start() {
        loadEquipmentMetadata();
        connectMqtt();
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

    /**
     * Sync degradation caps from the generator service.
     * Maps generator degradationCap to scoring anomaly level:
     *   UNLIMITED → CRITICAL, HIGH → HIGH, NONE → NONE
     */
    private void syncDegradationCaps() {
        try {
            String url = generatorUrl + "/api/generator/equipment";
            String json = new org.springframework.web.client.RestTemplate().getForObject(url, String.class);
            JsonNode equipmentList = objectMapper.readTree(json);
            for (JsonNode eq : equipmentList) {
                String id = eq.get("equipmentId").asText();
                String cap = eq.has("degradationCap") ? eq.get("degradationCap").asText() : "UNLIMITED";
                String level = "UNLIMITED".equals(cap) ? "CRITICAL" : cap;
                equipmentAnomalyLevels.put(id, level);
            }
        } catch (Exception e) {
            log.debug("Could not sync degradation caps from generator: {}", e.getMessage());
        }
    }

    @Scheduled(fixedDelay = 30000, initialDelay = 15000)
    public void scoreAllEquipment() {
        syncDegradationCaps();

        if (!gemFireService.isConnected()) {
            log.debug("GemFire not connected, skipping scoring cycle");
            handleScoringFailure();
            return;
        }

        long now = System.currentTimeMillis();
        long cutoff = now - WINDOW_MS;
        int scored = 0;
        int failed = 0;

        Region<String, String> predictionsRegion;
        try {
            predictionsRegion = gemFireService.getSensorPredictionsRegion();
        } catch (Exception e) {
            log.warn("Cannot get GemFire predictions region: {}", e.getMessage());
            handleScoringFailure();
            return;
        }

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

                // Check for HIGH/CRITICAL and publish anomaly event
                publishAnomalyIfNeeded(equipmentId, predictionJson);
            } catch (Exception e) {
                failed++;
                log.debug("Scoring failed for {}: {}", equipmentId, e.getMessage());
            }
        }

        if (scored > 0) {
            consecutiveScoringFailures = 0; // Reset on any success
            log.info("GemFire scoring cycle: scored {} equipment", scored);
        } else if (failed > 0) {
            handleScoringFailure();
        }
    }

    private void handleScoringFailure() {
        consecutiveScoringFailures++;
        if (consecutiveScoringFailures >= RECONNECT_THRESHOLD) {
            log.warn("GemFire scoring failed {} consecutive cycles — forcing reconnect", consecutiveScoringFailures);
            consecutiveScoringFailures = 0;
            gemFireService.reconnect();
        } else {
            log.info("GemFire scoring cycle failed ({}/{}), will retry", consecutiveScoringFailures, RECONNECT_THRESHOLD);
        }
    }

    /**
     * Publish anomaly event to RabbitMQ if risk level is HIGH or CRITICAL.
     * Uses deduplication to avoid publishing on every scoring cycle.
     */
    private void publishAnomalyIfNeeded(String equipmentId, String predictionJson) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> prediction = objectMapper.readValue(predictionJson, Map.class);
            String riskLevel = (String) prediction.get("riskLevel");
            double failureProbability = ((Number) prediction.get("failureProbability")).doubleValue();

            // Check if anomaly publishing is gated by per-equipment level
            String maxLevel = equipmentAnomalyLevels.getOrDefault(equipmentId, defaultAnomalyLevel);
            if ("NONE".equals(maxLevel)) {
                return; // All anomaly publishing disabled for this equipment
            }
            if ("HIGH".equals(maxLevel) && "CRITICAL".equals(riskLevel)) {
                return; // Only HIGH allowed for this equipment, suppress CRITICAL
            }

            // Clear alerts if equipment has recovered
            if ("LOW".equals(riskLevel) || "MEDIUM".equals(riskLevel)) {
                boolean hadAlert = publishedAlerts.remove(equipmentId + ":CRITICAL") |
                                   publishedAlerts.remove(equipmentId + ":HIGH");
                if (hadAlert) {
                    log.info("Equipment {} recovered to {} - cleared alert flags", equipmentId, riskLevel);
                }
                return;
            }

            // Publish if HIGH or CRITICAL and not already published
            String alertKey = equipmentId + ":" + riskLevel;
            if (publishedAlerts.add(alertKey)) {
                String facilityId = equipmentId.substring(0, 3); // Extract facility from equipment ID
                String probableCause = (String) prediction.getOrDefault("probableCause", "Unknown");
                double vibrationAvg = ((Number) prediction.getOrDefault("vibrationAvg", 0.0)).doubleValue();
                double temperatureAvg = ((Number) prediction.getOrDefault("temperatureAvg", 0.0)).doubleValue();
                double powerAvg = ((Number) prediction.getOrDefault("powerAvg", 0.0)).doubleValue();
                double rpmAvg = ((Number) prediction.getOrDefault("rpmAvg", 0.0)).doubleValue();
                double pressureAvg = ((Number) prediction.getOrDefault("pressureAvg", 0.0)).doubleValue();
                double torqueAvg = ((Number) prediction.getOrDefault("torqueAvg", 0.0)).doubleValue();
                String scoredAt = (String) prediction.getOrDefault("scoredAt", Instant.now().toString());

                AnomalyEvent event = AnomalyEvent.create(
                    equipmentId,
                    facilityId,
                    riskLevel,
                    failureProbability,
                    probableCause,
                    vibrationAvg,
                    temperatureAvg,
                    powerAvg,
                    rpmAvg,
                    pressureAvg,
                    torqueAvg,
                    scoredAt
                );

                String routingKey = "CRITICAL".equals(riskLevel) ? criticalRoutingKey : highRoutingKey;
                rabbitTemplate.convertAndSend(routingKey, event);

                log.info("Published {} anomaly event for {} ({}% failure probability)",
                         riskLevel, equipmentId, Math.round(failureProbability * 100));
            }
        } catch (Exception e) {
            log.warn("Failed to publish anomaly event for {}: {}", equipmentId, e.getMessage());
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

        // When equipment is capped at HIGH, limit trend inputs so the model
        // naturally scores in the HIGH band (50-69%) instead of CRITICAL.
        // This caps the INPUT features, not the model output — the PMML model is unchanged.
        String maxLevel = equipmentAnomalyLevels.getOrDefault(equipmentId, defaultAnomalyLevel);
        if ("HIGH".equals(maxLevel)) {
            double HIGH_TREND_CAP = 0.05;
            vibrationTrendRate = Math.min(vibrationTrendRate, HIGH_TREND_CAP);
            temperatureTrendRate = Math.min(temperatureTrendRate, HIGH_TREND_CAP);
        }

        // Equipment metadata
        EquipmentMeta meta = equipmentMeta.getOrDefault(equipmentId, new EquipmentMeta(30, 2));
        int anomalyCount = 0; // default — could query periodically

        // ── Score via GemFire PMML Function ──────────────────────────────
        // Send normalized features to GemFire server-side PmmlScoringFunction.
        // GemFire evaluates the PMML model (trained in Greenplum, exported as PMML,
        // deployed to GemFire PmmlModels region) and returns probability + risk level.
        String[] functionArgs = new String[] {
            MODEL_ID,
            equipmentId,
            "vibration_normalized=" + vibrationNormalized,
            "temperature_normalized=" + temperatureNormalized,
            "vibration_trend_rate=" + vibrationTrendRate,
            "temperature_trend_rate=" + temperatureTrendRate,
            "days_since_maintenance=" + meta.daysSinceMaintenance,
            "equipment_age_years=" + meta.equipmentAgeYears,
            "anomaly_count=" + anomalyCount,
            "power_normalized=" + (powerAvg / POWER_MAX),
            "rpm_normalized=" + (rpmAvg / RPM_MAX),
            "pressure_normalized=" + (pressureAvg / PRESSURE_MAX),
            "torque_normalized=" + (torqueAvg / TORQUE_MAX)
        };

        @SuppressWarnings("unchecked")
        ResultCollector<String, List<String>> rc = (ResultCollector<String, List<String>>)
            FunctionService.onRegion(gemFireService.getPmmlModelsRegion())
                .setArguments(functionArgs)
                .execute("PmmlScoringFunction");

        List<String> results = rc.getResult();
        if (results == null || results.isEmpty()) {
            throw new RuntimeException("No result from GemFire PmmlScoringFunction");
        }

        String result = results.get(0);
        if (result.startsWith("ERROR|")) {
            throw new RuntimeException("GemFire PMML scoring error: " + result.substring(6));
        }

        // Parse "equipmentId|probability|riskLevel"
        String[] parts = result.split("\\|", 3);
        double probability = Double.parseDouble(parts[1]);
        String riskLevel = parts[2];

        // When equipment is capped at HIGH, clamp probability below CRITICAL threshold.
        if ("HIGH".equals(maxLevel) && probability > 0.69) {
            probability = 0.69;
            riskLevel = "HIGH";
        }

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
        prediction.put("readingsInWindow", snapshots.size());
        prediction.put("modelId", MODEL_ID);
        prediction.put("scoringEngine", "GemFire PMML");
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
                    "scoringSource", "GemFire PMML (server-side model evaluation via PmmlScoringFunction)"
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

        // Heuristic diagnosis based on sensor deviations from baselines.
        // Baselines: vib=2.0, temp=50, power=15, rpm=8500, pressure=6.0, torque=45
        // Thresholds tuned for early detection with HIGH degradation caps.
        boolean vibElevated = vibrationAvg > 2.3;
        boolean vibHigh = vibrationAvg > 3.0;
        boolean vibCritical = vibrationAvg > 4.0;
        boolean tempElevated = temperatureAvg > 52.5;  // +2.5°C from 50 baseline
        boolean tempHigh = temperatureAvg > 58.0;
        boolean tempCritical = temperatureAvg > 80.0;
        boolean powerElevated = powerAvg > 16.0;       // +1 kW from 15 baseline
        boolean powerHigh = powerAvg > 20.0;
        boolean powerSpike = powerAvg > 40.0;
        boolean rpmDegraded = rpmAvg < 8350.0;         // -150 from 8500 baseline
        boolean rpmLow = rpmAvg < 7500.0;
        boolean pressureLow = pressureAvg < 5.5;       // -0.5 from 6.0 baseline
        boolean pressureCritical = pressureAvg < 4.5;
        boolean torqueElevated = torqueAvg > 46.0;     // +1 from 45 baseline
        boolean torqueHigh = torqueAvg > 50.0;

        // ── MOTOR_BURNOUT: temperature dominant + power elevated ──
        if (tempCritical && powerHigh) {
            return "Motor burnout — temperature at " + String.format("%.0f", temperatureAvg)
                    + "°C (critical) with power draw at " + String.format("%.1f", powerAvg) + " kW";
        }
        if (tempHigh && powerElevated && !pressureLow) {
            return "Motor burnout — temperature at " + String.format("%.0f", temperatureAvg)
                    + "°C with power draw at " + String.format("%.1f", powerAvg)
                    + " kW, motor overheating under load";
        }
        if (tempElevated && powerElevated && tempTrendRate > 0.01 && !pressureLow) {
            return "Motor burnout risk — temperature at " + String.format("%.0f", temperatureAvg)
                    + "°C rising with elevated power (" + String.format("%.1f", powerAvg)
                    + " kW), check motor windings";
        }

        // ── COOLANT_FAILURE: temperature rises with pressure drop ──
        if (tempHigh && pressureLow) {
            return "Coolant system failure — temperature at " + String.format("%.0f", temperatureAvg)
                    + "°C with coolant pressure at " + String.format("%.1f", pressureAvg) + " bar (normal: 6.0)";
        }
        if (tempElevated && pressureLow) {
            return "Coolant system degradation — temperature at " + String.format("%.0f", temperatureAvg)
                    + "°C with pressure dropping to " + String.format("%.1f", pressureAvg) + " bar";
        }

        // ── ELECTRICAL_FAULT: power elevated + RPM drop, temperature not dominant ──
        if (powerSpike && vibElevated) {
            return "Electrical fault — power draw at " + String.format("%.1f", powerAvg)
                    + " kW (normal: 15) with vibration at " + String.format("%.1f", vibrationAvg) + " mm/s";
        }
        if (powerElevated && rpmDegraded && !tempHigh) {
            return "Electrical fault — power at " + String.format("%.1f", powerAvg)
                    + " kW (normal: 15), RPM degraded to " + String.format("%.0f", rpmAvg)
                    + " — motor drawing excess current";
        }
        if (powerHigh && vibElevated && !tempCritical) {
            return "Electrical fault — elevated power (" + String.format("%.1f", powerAvg)
                    + " kW) with vibration at " + String.format("%.1f", vibrationAvg) + " mm/s";
        }

        // ── SPINDLE_WEAR: RPM drops + torque/vibration increase ──
        if (rpmLow && (vibHigh || torqueHigh)) {
            return "Spindle wear — RPM degraded to " + String.format("%.0f", rpmAvg) + " (normal: 8500)"
                    + (torqueHigh ? ", torque at " + String.format("%.1f", torqueAvg) + " Nm" : "")
                    + (vibHigh ? ", vibration at " + String.format("%.1f", vibrationAvg) + " mm/s" : "");
        }
        if (rpmDegraded && (vibElevated || torqueElevated)) {
            return "Spindle wear — RPM at " + String.format("%.0f", rpmAvg) + " (normal: 8500)"
                    + (torqueElevated ? ", torque at " + String.format("%.1f", torqueAvg) + " Nm" : "")
                    + (vibElevated ? ", vibration at " + String.format("%.1f", vibrationAvg) + " mm/s" : "");
        }

        // ── BEARING_DEGRADATION: vibration dominant, other sensors mostly normal ──
        if (vibCritical) {
            return "Bearing degradation — vibration at " + String.format("%.1f", vibrationAvg)
                    + " mm/s" + (vibTrendRate > 0.01 ? ", trending upward" : "") + ", bearing wear pattern";
        }
        if (vibHigh && !powerElevated && !pressureLow) {
            return "Bearing wear — vibration elevated at " + String.format("%.1f", vibrationAvg) + " mm/s";
        }
        if (vibElevated && vibTrendRate > 0.01 && !powerElevated && !pressureLow && !rpmDegraded) {
            return "Early bearing wear — vibration at " + String.format("%.1f", vibrationAvg)
                    + " mm/s with upward trend, monitor for progressive degradation";
        }

        // ── Early trends (fallback) ──
        if (vibTrendRate > 0.02 && tempTrendRate > 0.01) {
            return "Early-stage degradation — vibration and temperature trending upward";
        }
        if (vibTrendRate > 0.02) {
            return "Emerging vibration anomaly — upward trend, monitor for bearing or spindle wear";
        }
        if (tempTrendRate > 0.02) {
            return "Emerging thermal anomaly — temperature trending upward, check coolant and motor";
        }

        // Fallback: identify the most deviated sensor relative to its baseline
        double vibDev = (vibrationAvg - 2.0) / 2.0;    // normalized deviation
        double tempDev = (temperatureAvg - 50.0) / 50.0;
        double powerDev = (powerAvg - 15.0) / 15.0;
        double rpmDev = (8500.0 - rpmAvg) / 8500.0;    // inverted — lower is worse
        double pressureDev = (6.0 - pressureAvg) / 6.0; // inverted
        double torqueDev = (torqueAvg - 45.0) / 45.0;

        // Find dominant deviation
        String dominant = "vibration";
        double maxDev = vibDev;
        if (tempDev > maxDev) { maxDev = tempDev; dominant = "temperature"; }
        if (powerDev > maxDev) { maxDev = powerDev; dominant = "power"; }
        if (rpmDev > maxDev) { maxDev = rpmDev; dominant = "rpm"; }
        if (pressureDev > maxDev) { maxDev = pressureDev; dominant = "pressure"; }
        if (torqueDev > maxDev) { maxDev = torqueDev; dominant = "torque"; }

        return switch (dominant) {
            case "vibration" -> "Vibration anomaly at " + String.format("%.1f", vibrationAvg) + " mm/s — check bearings and mounting";
            case "temperature" -> "Thermal anomaly at " + String.format("%.0f", temperatureAvg) + "°C — check cooling and motor";
            case "power" -> "Power anomaly at " + String.format("%.1f", powerAvg) + " kW — check electrical system";
            case "rpm" -> "RPM anomaly at " + String.format("%.0f", rpmAvg) + " — check spindle and drive";
            case "pressure" -> "Pressure anomaly at " + String.format("%.1f", pressureAvg) + " bar — check coolant system";
            case "torque" -> "Torque anomaly at " + String.format("%.1f", torqueAvg) + " Nm — check spindle load";
            default -> "Multiple sensor anomalies detected";
        };
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
