package com.titan.generator.controller;

import com.titan.generator.model.DegradationPattern;
import com.titan.generator.model.EquipmentState;
import com.titan.generator.service.SensorDataGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.CrossOrigin;

import java.util.List;
import java.util.Map;

/**
 * REST API for controlling the sensor data generator.
 *
 * Allows:
 * - Starting/stopping data generation
 * - Setting degradation patterns on equipment
 * - Viewing equipment state
 * - Triggering failure scenarios
 */
@RestController
@RequestMapping("/api/generator")
@CrossOrigin(origins = "*")  // Allow dashboard to call this API
public class GeneratorController {

    private static final Logger log = LoggerFactory.getLogger(GeneratorController.class);

    private final SensorDataGenerator generator;

    public GeneratorController(SensorDataGenerator generator) {
        this.generator = generator;
    }

    /**
     * Get generator status.
     */
    @GetMapping("/status")
    public Map<String, Object> getStatus() {
        return Map.of(
            "enabled", generator.isEnabled(),
            "equipmentCount", generator.getEquipmentStates().size(),
            "patterns", DegradationPattern.values(),
            "speedMultiplier", generator.getSpeedMultiplier()
        );
    }

    /**
     * Set simulation speed multiplier (1-10x).
     * Higher values run multiple degradation cycles per tick for faster progression.
     */
    @PostMapping("/speed")
    public Map<String, Object> setSpeed(@RequestParam int multiplier) {
        generator.setSpeedMultiplier(multiplier);
        return Map.of("speedMultiplier", generator.getSpeedMultiplier());
    }

    /**
     * Enable or disable the generator.
     */
    @PostMapping("/enabled")
    public ResponseEntity<Map<String, Object>> setEnabled(@RequestParam boolean enabled) {
        generator.setEnabled(enabled);
        return ResponseEntity.ok(Map.of(
            "enabled", generator.isEnabled(),
            "message", enabled ? "Generator started" : "Generator stopped"
        ));
    }

    /**
     * List all equipment states.
     */
    @GetMapping("/equipment")
    public List<Map<String, Object>> listEquipment() {
        return generator.getEquipmentStates().values().stream()
            .map(state -> {
                Map<String, Object> m = new java.util.LinkedHashMap<>();
                m.put("equipmentId", state.getEquipmentId());
                m.put("facilityId", state.getFacilityId());
                m.put("pattern", state.getPattern().name());
                m.put("cycles", state.getCycleCount());
                m.put("vibration", state.getCurrentVibration());
                m.put("temperature", state.getCurrentTemperature());
                m.put("rpm", state.getCurrentRpm());
                m.put("power", state.getCurrentPower());
                m.put("pressure", state.getCurrentPressure());
                m.put("torque", state.getCurrentTorque());
                m.put("degradationCap", state.getDegradationCap());
                return m;
            })
            .toList();
    }

    /**
     * Get specific equipment state.
     */
    @GetMapping("/equipment/{equipmentId}")
    public ResponseEntity<Map<String, Object>> getEquipment(@PathVariable String equipmentId) {
        EquipmentState state = generator.getEquipmentStates().get(equipmentId);
        if (state == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(Map.of(
            "equipmentId", state.getEquipmentId(),
            "facilityId", state.getFacilityId(),
            "equipmentType", state.getEquipmentType(),
            "pattern", state.getPattern().name(),
            "cycles", state.getCycleCount(),
            "sensors", Map.of(
                "vibration", Map.of("value", state.getCurrentVibration(), "baseline", state.getVibrationBaseline()),
                "temperature", Map.of("value", state.getCurrentTemperature(), "baseline", state.getTemperatureBaseline()),
                "rpm", Map.of("value", state.getCurrentRpm(), "baseline", state.getRpmBaseline()),
                "torque", Map.of("value", state.getCurrentTorque(), "baseline", state.getTorqueBaseline()),
                "pressure", Map.of("value", state.getCurrentPressure(), "baseline", state.getPressureBaseline()),
                "power", Map.of("value", state.getCurrentPower(), "baseline", state.getPowerBaseline())
            )
        ));
    }

    /**
     * Set degradation pattern for equipment.
     */
    @PostMapping("/equipment/{equipmentId}/pattern")
    public ResponseEntity<Map<String, Object>> setPattern(
            @PathVariable String equipmentId,
            @RequestParam DegradationPattern pattern) {

        if (!generator.getEquipmentStates().containsKey(equipmentId)) {
            return ResponseEntity.notFound().build();
        }

        generator.setEquipmentPattern(equipmentId, pattern);

        return ResponseEntity.ok(Map.of(
            "equipmentId", equipmentId,
            "pattern", pattern.name(),
            "message", "Pattern set successfully"
        ));
    }

    /**
     * Trigger the Phoenix Incident scenario.
     * Sets PHX-CNC-001 to bearing degradation to simulate the infamous Phoenix facility incident.
     */
    @PostMapping("/scenarios/phoenix-incident")
    public ResponseEntity<Map<String, Object>> triggerPhoenixIncident() {
        log.info("Triggering Phoenix Incident scenario");

        // Find the first Phoenix CNC machine
        String targetEquipment = generator.getEquipmentStates().keySet().stream()
            .filter(id -> id.startsWith("PHX-CNC"))
            .sorted()
            .findFirst()
            .orElse(null);

        if (targetEquipment == null) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "No Phoenix facility equipment found in generator"
            ));
        }

        generator.setEquipmentPattern(targetEquipment, DegradationPattern.BEARING_DEGRADATION);

        return ResponseEntity.ok(Map.of(
            "scenario", "Phoenix Incident",
            "equipmentId", targetEquipment,
            "pattern", DegradationPattern.BEARING_DEGRADATION.name(),
            "description", targetEquipment + " bearing degradation initiated. " +
                          "Vibration will increase from 2.5 to 5.0+ mm/s. " +
                          "ML model should predict ~73% failure probability within 48 hours."
        ));
    }

    /**
     * Reset an equipment to normal operation.
     */
    @PostMapping("/equipment/{equipmentId}/reset")
    public ResponseEntity<Map<String, Object>> resetEquipment(@PathVariable String equipmentId) {
        if (!generator.getEquipmentStates().containsKey(equipmentId)) {
            return ResponseEntity.notFound().build();
        }

        generator.setEquipmentPattern(equipmentId, DegradationPattern.NORMAL);

        return ResponseEntity.ok(Map.of(
            "equipmentId", equipmentId,
            "pattern", DegradationPattern.NORMAL.name(),
            "message", "Equipment reset to normal operation"
        ));
    }

    /**
     * Adjust sensor values for equipment.
     * Accepts delta values to nudge vibration and/or temperature up or down.
     */
    @PostMapping("/equipment/{equipmentId}/adjust")
    public ResponseEntity<Map<String, Object>> adjustSensors(
            @PathVariable String equipmentId,
            @RequestParam(required = false) Double vibration,
            @RequestParam(required = false) Double temperature,
            @RequestParam(required = false) Double rpm,
            @RequestParam(required = false) Double power,
            @RequestParam(required = false) Double pressure,
            @RequestParam(required = false) Double torque) {

        EquipmentState state = generator.getEquipmentStates().get(equipmentId);
        if (state == null) {
            return ResponseEntity.notFound().build();
        }

        if (vibration != null) {
            double newVib = Math.max(0, state.getCurrentVibration() + vibration);
            state.setCurrentVibration(newVib);
            state.setVibrationBaseline(newVib);
        }
        if (temperature != null) {
            double newTemp = Math.max(0, state.getCurrentTemperature() + temperature);
            state.setCurrentTemperature(newTemp);
            state.setTemperatureBaseline(newTemp);
        }
        if (rpm != null) {
            double newRpm = Math.max(0, state.getCurrentRpm() + rpm);
            state.setCurrentRpm(newRpm);
            state.setRpmBaseline(newRpm);
        }
        if (power != null) {
            double newPower = Math.max(0, state.getCurrentPower() + power);
            state.setCurrentPower(newPower);
            state.setPowerBaseline(newPower);
        }
        if (pressure != null) {
            double newPressure = Math.max(0, state.getCurrentPressure() + pressure);
            state.setCurrentPressure(newPressure);
            state.setPressureBaseline(newPressure);
        }
        if (torque != null) {
            double newTorque = Math.max(0, state.getCurrentTorque() + torque);
            state.setCurrentTorque(newTorque);
            state.setTorqueBaseline(newTorque);
        }

        return ResponseEntity.ok(Map.of(
            "equipmentId", equipmentId,
            "vibration", state.getCurrentVibration(),
            "temperature", state.getCurrentTemperature(),
            "message", "Sensor values adjusted"
        ));
    }

    /**
     * Set degradation cap for equipment.
     * Values: UNLIMITED (default), HIGH (cap before CRITICAL), NONE (disable degradation).
     * Cycle limits are pattern-specific so the ML model naturally scores within the desired band.
     */
    @PostMapping("/equipment/{equipmentId}/degradation-cap")
    public ResponseEntity<Map<String, Object>> setDegradationCap(
            @PathVariable String equipmentId,
            @RequestParam String cap) {

        EquipmentState state = generator.getEquipmentStates().get(equipmentId);
        if (state == null) {
            return ResponseEntity.notFound().build();
        }

        if (!List.of("UNLIMITED", "HIGH", "NONE").contains(cap)) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "Invalid cap. Must be UNLIMITED, HIGH, or NONE"));
        }

        state.setDegradationCap(cap);
        log.info("Set degradationCap={} for {}", cap, equipmentId);

        return ResponseEntity.ok(Map.of(
            "equipmentId", equipmentId,
            "degradationCap", cap
        ));
    }

    /**
     * Reset all equipment to normal.
     */
    @PostMapping("/reset-all")
    public ResponseEntity<Map<String, Object>> resetAll() {
        generator.getEquipmentStates().keySet()
            .forEach(id -> generator.setEquipmentPattern(id, DegradationPattern.NORMAL));

        return ResponseEntity.ok(Map.of(
            "message", "All equipment reset to normal operation",
            "count", generator.getEquipmentStates().size()
        ));
    }
}
