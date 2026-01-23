package com.titan.generator.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.titan.generator.model.DegradationPattern;
import com.titan.generator.model.EquipmentState;
import com.titan.generator.model.SensorReading;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Generates realistic sensor data for manufacturing equipment and publishes via MQTT.
 *
 * Implements degradation patterns based on NASA C-MAPSS research:
 * - Normal operation with realistic noise
 * - Bearing degradation (exponential vibration increase)
 * - Motor burnout (temperature spike)
 * - Spindle wear (RPM decrease)
 */
@Service
public class SensorDataGenerator {

    private static final Logger log = LoggerFactory.getLogger(SensorDataGenerator.class);

    private final MqttClient mqttClient;
    private final ObjectMapper objectMapper;
    private final Map<String, EquipmentState> equipmentStates = new ConcurrentHashMap<>();

    @Value("${generator.equipment-count:10}")
    private int equipmentCount;

    @Value("${generator.facilities:PHX,MUC,SHA}")
    private String[] facilities;

    @Value("${generator.topic-prefix:titan/sensors}")
    private String topicPrefix;

    @Value("${generator.enabled:true}")
    private boolean generatorEnabled;

    // Thresholds for quality flags
    private static final double VIBRATION_WARNING = 3.5;
    private static final double VIBRATION_CRITICAL = 5.0;
    private static final double TEMP_WARNING = 70.0;
    private static final double TEMP_CRITICAL = 85.0;

    public SensorDataGenerator(MqttClient mqttClient) {
        this.mqttClient = mqttClient;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    @PostConstruct
    public void initialize() {
        log.info("Initializing sensor data generator with {} equipment across {} facilities",
                 equipmentCount, facilities.length);

        // Create equipment states for each facility
        int equipmentPerFacility = equipmentCount / facilities.length;
        for (String facility : facilities) {
            for (int i = 1; i <= equipmentPerFacility; i++) {
                String equipmentId = String.format("%s-CNC-%03d", facility, i);
                String type = (i % 3 == 0) ? "CNC-LATHE" : "CNC-MILL";
                EquipmentState state = new EquipmentState(equipmentId, facility, type);

                // Add some variety to baselines
                state.setVibrationBaseline(1.8 + ThreadLocalRandom.current().nextDouble(0.4));
                state.setTemperatureBaseline(48 + ThreadLocalRandom.current().nextDouble(6));
                state.setRpmBaseline(8000 + ThreadLocalRandom.current().nextInt(1000));

                equipmentStates.put(equipmentId, state);
                log.debug("Initialized equipment: {}", equipmentId);
            }
        }

        // Set up PHX-CNC-007 with bearing degradation pattern (Phoenix Incident)
        if (equipmentStates.containsKey("PHX-CNC-007")) {
            EquipmentState phoenix = equipmentStates.get("PHX-CNC-007");
            phoenix.setPattern(DegradationPattern.BEARING_DEGRADATION);
            // Start with already-elevated values (mid-degradation)
            phoenix.setCurrentVibration(3.2);
            phoenix.setCurrentTemperature(58);
            log.info("PHX-CNC-007 initialized with BEARING_DEGRADATION pattern (Phoenix Incident)");
        }

        log.info("Sensor data generator initialized with {} equipment", equipmentStates.size());
    }

    /**
     * Generate and publish sensor readings at regular intervals.
     */
    @Scheduled(fixedRateString = "${generator.interval-ms:5000}")
    public void generateReadings() {
        if (!generatorEnabled) return;

        for (EquipmentState state : equipmentStates.values()) {
            try {
                generateAndPublish(state);
            } catch (Exception e) {
                log.error("Error generating readings for {}: {}", state.getEquipmentId(), e.getMessage());
            }
        }
    }

    private void generateAndPublish(EquipmentState state) throws MqttException {
        // Apply degradation pattern
        applyDegradationPattern(state);

        // Generate readings for each sensor type
        publishReading(state, "vibration", state.getCurrentVibration(), "mm/s");
        publishReading(state, "temperature", state.getCurrentTemperature(), "celsius");
        publishReading(state, "spindle_speed", state.getCurrentRpm(), "rpm");
        publishReading(state, "torque", state.getCurrentTorque(), "Nm");
        publishReading(state, "pressure", state.getCurrentPressure(), "bar");
        publishReading(state, "power", state.getCurrentPower(), "kW");

        state.incrementCycle();

        if (state.getCycleCount() % 100 == 0) {
            log.debug("Generated {} cycles for {}: {}", state.getCycleCount(),
                     state.getEquipmentId(), state);
        }
    }

    private void publishReading(EquipmentState state, String sensorType, double value, String unit)
            throws MqttException {

        String qualityFlag = determineQualityFlag(sensorType, value);

        SensorReading reading = SensorReading.of(
            state.getEquipmentId(),
            state.getFacilityId(),
            sensorType,
            Math.round(value * 100.0) / 100.0,  // Round to 2 decimal places
            unit,
            qualityFlag
        );

        String topic = String.format("%s/%s/%s/%s",
                                      topicPrefix, state.getFacilityId(),
                                      state.getEquipmentId(), sensorType);

        try {
            String payload = objectMapper.writeValueAsString(reading);
            MqttMessage message = new MqttMessage(payload.getBytes());
            message.setQos(1);

            if (mqttClient.isConnected()) {
                mqttClient.publish(topic, message);
            }
        } catch (Exception e) {
            log.warn("Failed to publish to {}: {}", topic, e.getMessage());
        }
    }

    /**
     * Apply the equipment's degradation pattern to update sensor values.
     */
    private void applyDegradationPattern(EquipmentState state) {
        double noise = ThreadLocalRandom.current().nextGaussian() * 0.1;
        int cycles = state.getCycleCount();

        switch (state.getPattern()) {
            case NORMAL -> applyNormalPattern(state, noise);
            case BEARING_DEGRADATION -> applyBearingDegradation(state, noise, cycles);
            case MOTOR_BURNOUT -> applyMotorBurnout(state, noise, cycles);
            case SPINDLE_WEAR -> applySpindleWear(state, noise, cycles);
            case COOLANT_FAILURE -> applyCoolantFailure(state, noise, cycles);
            case ELECTRICAL_FAULT -> applyElectricalFault(state, noise, cycles);
        }
    }

    private void applyNormalPattern(EquipmentState state, double noise) {
        state.setCurrentVibration(state.getVibrationBaseline() + noise * 0.3);
        state.setCurrentTemperature(state.getTemperatureBaseline() + noise * 2);
        state.setCurrentRpm(state.getRpmBaseline() + noise * 50);
        state.setCurrentTorque(state.getTorqueBaseline() + noise * 2);
        state.setCurrentPressure(state.getPressureBaseline() + noise * 0.2);
        state.setCurrentPower(state.getPowerBaseline() + noise);
    }

    /**
     * Bearing degradation - the Phoenix Incident pattern.
     * Vibration increases exponentially, temperature follows.
     */
    private void applyBearingDegradation(EquipmentState state, double noise, int cycles) {
        // Exponential degradation rate
        double degradationFactor = 1.0 + (cycles * 0.002);  // 0.2% increase per cycle
        double randomSpike = ThreadLocalRandom.current().nextDouble() > 0.95 ? 0.3 : 0;

        // Vibration increases exponentially
        double vibration = state.getCurrentVibration() * (1.0 + 0.001 * degradationFactor) + noise * 0.2 + randomSpike;
        state.setCurrentVibration(Math.min(vibration, 8.0));  // Cap at catastrophic level

        // Temperature correlates with vibration (friction heat)
        double temp = state.getTemperatureBaseline() +
                      (state.getCurrentVibration() - state.getVibrationBaseline()) * 5 +
                      noise * 2;
        state.setCurrentTemperature(Math.min(temp, 95.0));

        // RPM may fluctuate under load
        state.setCurrentRpm(state.getRpmBaseline() - (cycles * 0.5) + noise * 30);

        // Torque increases as bearing drags
        state.setCurrentTorque(state.getTorqueBaseline() + (cycles * 0.02) + noise * 1.5);

        // Power consumption increases
        state.setCurrentPower(state.getPowerBaseline() + (cycles * 0.01) + noise);

        state.setCurrentPressure(state.getPressureBaseline() + noise * 0.1);
    }

    /**
     * Motor burnout - rapid temperature increase.
     */
    private void applyMotorBurnout(EquipmentState state, double noise, int cycles) {
        // Temperature increases rapidly
        double tempIncrease = Math.pow(1.02, cycles);  // Exponential temperature rise
        state.setCurrentTemperature(Math.min(
            state.getTemperatureBaseline() + tempIncrease + noise * 3,
            120.0  // Max temp before complete failure
        ));

        // Vibration becomes erratic
        state.setCurrentVibration(state.getVibrationBaseline() +
                                   Math.abs(noise * 2) +
                                   (ThreadLocalRandom.current().nextDouble() > 0.8 ? 1.5 : 0));

        // Power consumption spikes
        state.setCurrentPower(state.getPowerBaseline() * (1 + cycles * 0.05) + noise * 2);

        // RPM drops as motor struggles
        state.setCurrentRpm(Math.max(state.getRpmBaseline() - (cycles * 10), 1000));

        state.setCurrentTorque(state.getTorqueBaseline() + noise * 3);
        state.setCurrentPressure(state.getPressureBaseline() + noise * 0.2);
    }

    /**
     * Spindle wear - gradual RPM loss with increased vibration.
     */
    private void applySpindleWear(EquipmentState state, double noise, int cycles) {
        // RPM gradually decreases (can't maintain speed)
        double rpmLoss = cycles * 2;
        state.setCurrentRpm(Math.max(state.getRpmBaseline() - rpmLoss + noise * 20, 5000));

        // Vibration gradually increases
        state.setCurrentVibration(state.getVibrationBaseline() + (cycles * 0.005) + noise * 0.3);

        // Temperature slightly elevated
        state.setCurrentTemperature(state.getTemperatureBaseline() + (cycles * 0.02) + noise * 2);

        // Torque becomes inconsistent
        double torqueVariation = cycles * 0.03 + Math.abs(noise * 3);
        state.setCurrentTorque(state.getTorqueBaseline() + torqueVariation);

        state.setCurrentPower(state.getPowerBaseline() + (cycles * 0.005) + noise);
        state.setCurrentPressure(state.getPressureBaseline() + noise * 0.1);
    }

    private void applyCoolantFailure(EquipmentState state, double noise, int cycles) {
        // Temperature rises due to cooling inefficiency
        state.setCurrentTemperature(state.getTemperatureBaseline() + (cycles * 0.1) + noise * 3);

        // Pressure drops
        state.setCurrentPressure(Math.max(state.getPressureBaseline() - (cycles * 0.02), 1.0));

        state.setCurrentVibration(state.getVibrationBaseline() + noise * 0.3);
        state.setCurrentRpm(state.getRpmBaseline() + noise * 30);
        state.setCurrentTorque(state.getTorqueBaseline() + noise * 2);
        state.setCurrentPower(state.getPowerBaseline() + noise);
    }

    private void applyElectricalFault(EquipmentState state, double noise, int cycles) {
        // Power consumption becomes erratic
        double powerSpike = ThreadLocalRandom.current().nextDouble() > 0.9 ? 10 : 0;
        state.setCurrentPower(state.getPowerBaseline() + noise * 5 + powerSpike);

        // Intermittent sensor readings (simulated by high noise)
        state.setCurrentVibration(state.getVibrationBaseline() + noise * 1.5);
        state.setCurrentTemperature(state.getTemperatureBaseline() + noise * 5);
        state.setCurrentRpm(state.getRpmBaseline() + noise * 100);
        state.setCurrentTorque(state.getTorqueBaseline() + noise * 5);
        state.setCurrentPressure(state.getPressureBaseline() + noise * 0.5);
    }

    private String determineQualityFlag(String sensorType, double value) {
        return switch (sensorType) {
            case "vibration" -> value >= VIBRATION_CRITICAL ? "CRITICAL" :
                                value >= VIBRATION_WARNING ? "WARNING" : "GOOD";
            case "temperature" -> value >= TEMP_CRITICAL ? "CRITICAL" :
                                  value >= TEMP_WARNING ? "WARNING" : "GOOD";
            default -> "GOOD";
        };
    }

    // Public API for controlling equipment patterns

    public void setEquipmentPattern(String equipmentId, DegradationPattern pattern) {
        EquipmentState state = equipmentStates.get(equipmentId);
        if (state != null) {
            state.setPattern(pattern);
            log.info("Set pattern {} for equipment {}", pattern, equipmentId);
        } else {
            log.warn("Equipment not found: {}", equipmentId);
        }
    }

    public Map<String, EquipmentState> getEquipmentStates() {
        return Collections.unmodifiableMap(equipmentStates);
    }

    public void setEnabled(boolean enabled) {
        this.generatorEnabled = enabled;
        log.info("Sensor data generator {}", enabled ? "enabled" : "disabled");
    }

    public boolean isEnabled() {
        return generatorEnabled;
    }
}
