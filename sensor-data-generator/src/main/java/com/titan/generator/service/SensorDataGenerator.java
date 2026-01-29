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

    @Value("${generator.equipment-count:72}")
    private int equipmentCount;

    @Value("${generator.facilities:PHX,MUC,SHA,DET,ATL,DAL,LYN,MAN,MEX,SEO,SYD,TYO}")
    private String[] facilities;

    @Value("${generator.topic-prefix:titan/sensors}")
    private String topicPrefix;

    @Value("${generator.enabled:true}")
    private boolean generatorEnabled;

    // Speed multiplier: 1x = normal (1 cycle per tick), 2x = 2 cycles per tick, etc.
    private volatile int speedMultiplier = 1;

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

        // All equipment starts in NORMAL mode by default.
        // Use POST /api/generator/scenarios/phoenix-incident to trigger the Phoenix Incident demo.
        log.info("Sensor data generator initialized with {} equipment (all NORMAL)", equipmentStates.size());
    }

    /**
     * Generate and publish sensor readings at regular intervals.
     */
    @Scheduled(fixedRateString = "${generator.interval-ms:5000}")
    public void generateReadings() {
        if (!generatorEnabled) return;

        log.info("Generating readings for {} equipment (speed={}x, mqtt={}) START", equipmentStates.size(), speedMultiplier, mqttClient.isConnected());
        int cycles = speedMultiplier;
        for (EquipmentState state : equipmentStates.values()) {
            try {
                // At higher speeds, run multiple degradation cycles but only publish final values
                for (int i = 0; i < cycles - 1; i++) {
                    applyDegradationPattern(state);
                    state.incrementCycle();
                }
                // Publish the final cycle's readings
                generateAndPublish(state);
            } catch (Exception e) {
                log.error("Error generating readings for {}: {}", state.getEquipmentId(), e.getMessage());
            }
        }
        log.info("Generating readings DONE");
    }

    public int getSpeedMultiplier() { return speedMultiplier; }
    public void setSpeedMultiplier(int multiplier) {
        this.speedMultiplier = Math.max(1, Math.min(10, multiplier));
        log.info("Speed multiplier set to {}x", this.speedMultiplier);
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
        // When cycle-capped, amplify noise so sensors visibly fluctuate around the plateau
        boolean capped = state.getDegradationCap().equals("HIGH") &&
                         state.getPattern() != DegradationPattern.NORMAL &&
                         state.getCycleCount() >= state.getMaxCycles();
        double noise = ThreadLocalRandom.current().nextGaussian() * (capped ? 0.4 : 0.1);
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
        // Increased noise multipliers for more visible "alive" fluctuations
        state.setCurrentVibration(state.getVibrationBaseline() + noise * 1.5);
        state.setCurrentTemperature(state.getTemperatureBaseline() + noise * 8);
        state.setCurrentRpm(state.getRpmBaseline() + noise * 200);
        state.setCurrentTorque(state.getTorqueBaseline() + noise * 8);
        state.setCurrentPressure(state.getPressureBaseline() + noise * 0.8);
        state.setCurrentPower(state.getPowerBaseline() + noise * 4);
    }

    /**
     * Bearing degradation - the Phoenix Incident pattern.
     * Vibration increases exponentially, temperature follows.
     * Demo-tuned: reaches warning levels in ~1-2 minutes
     */
    private void applyBearingDegradation(EquipmentState state, double noise, int cycles) {
        double randomSpike = ThreadLocalRandom.current().nextDouble() > 0.9 ? 0.3 : 0;

        // Vibration increases - baseline ~2.0, warning at 3.5, critical at 5.0
        // Reaches warning in ~45 cycles (~3.75 min), critical in ~90 cycles (~7.5 min)
        double vibration = state.getVibrationBaseline() + (cycles * 0.035) + noise * 0.3 + randomSpike;
        state.setCurrentVibration(Math.min(vibration, 8.0));

        // Temperature correlates with vibration (friction heat)
        double temp = state.getTemperatureBaseline() +
                      (state.getCurrentVibration() - state.getVibrationBaseline()) * 6 +
                      noise * 2;
        state.setCurrentTemperature(Math.min(temp, 95.0));

        // RPM drops as bearing degrades
        state.setCurrentRpm(state.getRpmBaseline() - (cycles * 2) + noise * 30);

        // Torque increases as bearing drags
        state.setCurrentTorque(state.getTorqueBaseline() + (cycles * 0.05) + noise * 1.5);

        // Power consumption increases
        state.setCurrentPower(state.getPowerBaseline() + (cycles * 0.03) + noise);

        state.setCurrentPressure(state.getPressureBaseline() + noise * 0.2);
    }

    /**
     * Motor burnout - rapid temperature increase.
     * Demo-tuned: reaches critical temp in ~2 minutes
     */
    private void applyMotorBurnout(EquipmentState state, double noise, int cycles) {
        // Temperature increases - ~0.35°C per cycle
        // From 50°C baseline, reaches 70°C warning in ~57 cycles (~4.75 min), critical 80°C in ~86 cycles (~7 min)
        state.setCurrentTemperature(Math.min(
            state.getTemperatureBaseline() + (cycles * 0.35) + noise * 3,
            120.0  // Max temp before complete failure
        ));

        // Vibration becomes erratic with random spikes
        double erraticSpike = ThreadLocalRandom.current().nextDouble() > 0.7 ? 1.0 : 0;
        state.setCurrentVibration(state.getVibrationBaseline() + (cycles * 0.02) +
                                   Math.abs(noise * 1.0) + erraticSpike);

        // Power consumption rises
        state.setCurrentPower(state.getPowerBaseline() + (cycles * 0.12) + noise * 2);

        // RPM drops as motor struggles
        state.setCurrentRpm(Math.max(state.getRpmBaseline() - (cycles * 18), 3000));

        state.setCurrentTorque(state.getTorqueBaseline() + (cycles * 0.04) + noise * 3);
        state.setCurrentPressure(state.getPressureBaseline() + noise * 0.2);
    }

    /**
     * Spindle wear - gradual RPM loss with increased vibration.
     * Demo-tuned: noticeable RPM drop in ~1 minute
     */
    private void applySpindleWear(EquipmentState state, double noise, int cycles) {
        // RPM gradually decreases - 10 rpm per cycle
        // From 8500 baseline, drops to ~8000 in 50 cycles (~4.2 min)
        state.setCurrentRpm(Math.max(state.getRpmBaseline() - (cycles * 10) + noise * 50, 5000));

        // Vibration gradually increases - warning at 3.5
        state.setCurrentVibration(state.getVibrationBaseline() + (cycles * 0.03) + noise * 0.5);

        // Temperature slightly elevated from friction
        state.setCurrentTemperature(state.getTemperatureBaseline() + (cycles * 0.05) + noise * 2);

        // Torque becomes inconsistent with random spikes
        double torqueSpike = ThreadLocalRandom.current().nextDouble() > 0.8 ? 3 : 0;
        state.setCurrentTorque(state.getTorqueBaseline() + (cycles * 0.07) + torqueSpike + noise * 3);

        state.setCurrentPower(state.getPowerBaseline() + (cycles * 0.02) + noise);
        state.setCurrentPressure(state.getPressureBaseline() + noise * 0.2);
    }

    private void applyCoolantFailure(EquipmentState state, double noise, int cycles) {
        // Temperature rises due to cooling inefficiency - 0.18°C per cycle
        // At 5s intervals: ~2°C/min, reaches warning (70°C) in ~5.5 min from 50°C baseline
        state.setCurrentTemperature(Math.min(
            state.getTemperatureBaseline() + (cycles * 0.18) + noise * 3,
            95.0  // Cap at critical
        ));

        // Pressure drops - 0.035 bar per cycle
        state.setCurrentPressure(Math.max(state.getPressureBaseline() - (cycles * 0.035), 1.0));

        // Slight vibration increase from thermal expansion
        state.setCurrentVibration(state.getVibrationBaseline() + (cycles * 0.008) + noise * 0.5);
        state.setCurrentRpm(state.getRpmBaseline() + noise * 30);
        state.setCurrentTorque(state.getTorqueBaseline() + (cycles * 0.02) + noise * 2);
        state.setCurrentPower(state.getPowerBaseline() + (cycles * 0.01) + noise);
    }

    /**
     * Electrical fault - erratic power with intermittent sensor readings.
     * Demo-tuned: immediately visible erratic behavior
     */
    private void applyElectricalFault(EquipmentState state, double noise, int cycles) {
        // Power consumption surges — motor drawing excess current
        double powerSpike = ThreadLocalRandom.current().nextDouble() > 0.6 ? 8 : 0;
        state.setCurrentPower(state.getPowerBaseline() + (cycles * 0.12) + noise * 5 + powerSpike);

        // RPM drops — motor struggling to convert power to mechanical output
        double rpmDrop = cycles * 15;  // progressive drop
        double erraticFactor = ThreadLocalRandom.current().nextDouble() > 0.7 ? 3 : 1;
        state.setCurrentRpm(Math.max(3000, state.getRpmBaseline() - rpmDrop + noise * 200 * erraticFactor));

        // Vibration rises from electrical interference
        state.setCurrentVibration(state.getVibrationBaseline() + (cycles * 0.015) + noise * 1.5 * erraticFactor);
        // Temperature climbs from inefficiency
        state.setCurrentTemperature(state.getTemperatureBaseline() + (cycles * 0.06) + noise * 5);
        // Torque becomes erratic
        state.setCurrentTorque(state.getTorqueBaseline() + (cycles * 0.04) + noise * 8 * erraticFactor);
        // Pressure slight drop
        state.setCurrentPressure(state.getPressureBaseline() - (cycles * 0.008) + noise * 1.0);
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
            if (pattern == DegradationPattern.NORMAL) {
                // Full reset: restore factory baselines + current values
                state.resetToDefaults();
                state.setPattern(pattern);
            } else {
                // setPattern() resets cycles to 0 and sensor values to current baseline
                state.setPattern(pattern);
            }
            log.info("Set pattern {} for equipment {} (values reset)", pattern, equipmentId);
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
