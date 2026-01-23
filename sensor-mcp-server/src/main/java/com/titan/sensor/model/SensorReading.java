package com.titan.sensor.model;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/**
 * Sensor reading from manufacturing equipment.
 */
public record SensorReading(
    @JsonPropertyDescription("Equipment identifier")
    String equipmentId,

    @JsonPropertyDescription("Type of sensor (vibration, temperature, rpm, torque, pressure)")
    String sensorType,

    @JsonPropertyDescription("Sensor value")
    Double value,

    @JsonPropertyDescription("Unit of measurement")
    String unit,

    @JsonPropertyDescription("Timestamp of reading")
    String timestamp,

    @JsonPropertyDescription("Quality flag (GOOD, WARNING, CRITICAL)")
    String qualityFlag
) {}
