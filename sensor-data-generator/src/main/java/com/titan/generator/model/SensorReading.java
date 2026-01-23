package com.titan.generator.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.Instant;

/**
 * Sensor reading message published to MQTT.
 */
public record SensorReading(
    String equipmentId,
    String facilityId,
    String sensorType,
    double value,
    String unit,
    String qualityFlag,
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    Instant timestamp
) {
    public static SensorReading of(String equipmentId, String facilityId, String sensorType,
                                    double value, String unit, String qualityFlag) {
        return new SensorReading(equipmentId, facilityId, sensorType, value, unit, qualityFlag, Instant.now());
    }
}
