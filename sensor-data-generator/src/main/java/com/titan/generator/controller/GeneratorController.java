package com.titan.generator.controller;

import com.titan.generator.model.DegradationPattern;
import com.titan.generator.model.EquipmentState;
import com.titan.generator.service.SensorDataGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
            "patterns", DegradationPattern.values()
        );
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
            .map(state -> Map.<String, Object>of(
                "equipmentId", state.getEquipmentId(),
                "facilityId", state.getFacilityId(),
                "pattern", state.getPattern().name(),
                "cycles", state.getCycleCount(),
                "vibration", state.getCurrentVibration(),
                "temperature", state.getCurrentTemperature(),
                "rpm", state.getCurrentRpm()
            ))
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
     * Sets PHX-CNC-007 to bearing degradation if not already.
     */
    @PostMapping("/scenarios/phoenix-incident")
    public ResponseEntity<Map<String, Object>> triggerPhoenixIncident() {
        log.info("Triggering Phoenix Incident scenario");

        EquipmentState state = generator.getEquipmentStates().get("PHX-CNC-007");
        if (state == null) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "PHX-CNC-007 not found in generator"
            ));
        }

        generator.setEquipmentPattern("PHX-CNC-007", DegradationPattern.BEARING_DEGRADATION);

        return ResponseEntity.ok(Map.of(
            "scenario", "Phoenix Incident",
            "equipmentId", "PHX-CNC-007",
            "pattern", DegradationPattern.BEARING_DEGRADATION.name(),
            "description", "PHX-CNC-007 bearing degradation initiated. " +
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
