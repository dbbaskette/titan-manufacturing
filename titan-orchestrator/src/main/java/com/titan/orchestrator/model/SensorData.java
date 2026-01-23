package com.titan.orchestrator.model;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;

/**
 * Domain models for sensor data from the Sensor MCP Agent.
 */
public class SensorData {

    @JsonClassDescription("Equipment information from Titan Manufacturing facilities")
    public record Equipment(
        @JsonPropertyDescription("Unique equipment identifier")
        String equipmentId,

        @JsonPropertyDescription("Facility ID where equipment is located")
        String facilityId,

        @JsonPropertyDescription("Type of equipment (CNC_MILL, CNC_LATHE, etc.)")
        String equipmentType,

        @JsonPropertyDescription("Equipment manufacturer")
        String manufacturer,

        @JsonPropertyDescription("Equipment model")
        String model,

        @JsonPropertyDescription("Current operational status")
        String status
    ) {}

    @JsonClassDescription("Sensor reading from manufacturing equipment")
    public record SensorReading(
        @JsonPropertyDescription("Equipment identifier")
        String equipmentId,

        @JsonPropertyDescription("Type of sensor (vibration, temperature, rpm, torque, pressure)")
        String sensorType,

        @JsonPropertyDescription("Sensor value")
        Double value,

        @JsonPropertyDescription("Unit of measurement")
        String unit,

        @JsonPropertyDescription("Quality flag (GOOD, WARNING, CRITICAL)")
        String qualityFlag
    ) {}

    @JsonClassDescription("Detected anomaly in sensor readings")
    public record Anomaly(
        @JsonPropertyDescription("Equipment with detected anomaly")
        String equipmentId,

        @JsonPropertyDescription("Type of anomaly detected")
        String anomalyType,

        @JsonPropertyDescription("Affected sensor type")
        String sensorType,

        @JsonPropertyDescription("Severity level")
        String severity,

        @JsonPropertyDescription("Current measured value")
        Double currentValue,

        @JsonPropertyDescription("Expected/normal value")
        Double expectedValue,

        @JsonPropertyDescription("Description of the anomaly")
        String description,

        @JsonPropertyDescription("Recommended action")
        String recommendedAction
    ) {}

    @JsonClassDescription("Current health status of equipment")
    public record EquipmentStatus(
        @JsonPropertyDescription("Equipment identifier")
        String equipmentId,

        @JsonPropertyDescription("Overall health status")
        String healthStatus,

        @JsonPropertyDescription("Latest sensor readings")
        List<SensorReading> latestReadings,

        @JsonPropertyDescription("Active anomalies")
        List<Anomaly> activeAnomalies,

        @JsonPropertyDescription("Status summary")
        String statusSummary
    ) {}

    @JsonClassDescription("Overview of facility equipment health")
    public record FacilityStatus(
        @JsonPropertyDescription("Facility identifier")
        String facilityId,

        @JsonPropertyDescription("Facility name")
        String facilityName,

        @JsonPropertyDescription("Total equipment count")
        int totalEquipment,

        @JsonPropertyDescription("Operational equipment count")
        int operationalCount,

        @JsonPropertyDescription("Equipment in warning state")
        int warningCount,

        @JsonPropertyDescription("Equipment in critical state")
        int criticalCount,

        @JsonPropertyDescription("Overall facility health percentage")
        double healthPercentage,

        @JsonPropertyDescription("Equipment with active anomalies")
        List<String> equipmentWithAnomalies,

        @JsonPropertyDescription("Facility status summary")
        String summary
    ) {}
}
