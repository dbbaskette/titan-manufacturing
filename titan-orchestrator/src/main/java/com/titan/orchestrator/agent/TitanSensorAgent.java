package com.titan.orchestrator.agent;

import com.embabel.agent.api.annotation.Action;
import com.embabel.agent.api.annotation.Agent;
import com.embabel.agent.api.annotation.AchievesGoal;
import com.embabel.agent.api.common.Ai;
import com.titan.orchestrator.model.SensorData.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Titan Sensor Agent - Orchestrates IoT sensor data queries across manufacturing facilities.
 *
 * Uses Embabel's goal-based planning to coordinate with the Sensor MCP Server
 * for equipment monitoring and anomaly detection.
 */
@Agent(description = "Titan Sensor Agent - Monitors 600+ CNC machines across 12 global manufacturing " +
                     "facilities. Queries sensor data, detects anomalies, and provides equipment health status.")
@Component
public class TitanSensorAgent {

    private static final Logger log = LoggerFactory.getLogger(TitanSensorAgent.class);

    /**
     * List equipment at a facility.
     */
    @Action(
        description = "List manufacturing equipment at a specific facility or all facilities",
        toolGroups = {"sensor-tools"}
    )
    public List<Equipment> listEquipment(String facilityId, Ai ai) {
        log.info("Listing equipment for facility: {}", facilityId != null ? facilityId : "ALL");

        String prompt = facilityId != null && !facilityId.isBlank()
            ? """
              Use the list_equipment tool to get all equipment at facility "%s".
              Return the list of equipment found.
              """.formatted(facilityId)
            : """
              Use the list_equipment tool to get equipment across all facilities.
              Limit to 20 results. Return the list of equipment found.
              """;

        return ai.withAutoLlm().createObject(prompt, EquipmentListResult.class).equipment();
    }

    /**
     * Get current status of specific equipment.
     */
    @Action(
        description = "Get current health status and sensor readings for specific equipment",
        toolGroups = {"sensor-tools"}
    )
    public EquipmentStatus getEquipmentStatus(String equipmentId, Ai ai) {
        log.info("Getting status for equipment: {}", equipmentId);

        return ai.withAutoLlm().createObject(
            """
            Use the get_equipment_status tool to get the current health status of equipment "%s".

            Return the complete equipment status including:
            - Health status (HEALTHY, WARNING, CRITICAL)
            - Latest sensor readings
            - Any active anomalies
            - Status summary
            """.formatted(equipmentId),
            EquipmentStatus.class
        );
    }

    /**
     * Get sensor readings for equipment.
     */
    @Action(
        description = "Get historical sensor readings for equipment",
        toolGroups = {"sensor-tools"}
    )
    public List<SensorReading> getSensorReadings(String equipmentId, String sensorType, Integer hoursBack, Ai ai) {
        log.info("Getting sensor readings: equipment={}, type={}, hours={}", equipmentId, sensorType, hoursBack);

        String sensorFilter = sensorType != null ? "for sensor type '" + sensorType + "'" : "for all sensors";
        int hours = hoursBack != null ? hoursBack : 24;

        return ai.withAutoLlm().createObject(
            """
            Use the get_sensor_readings tool to get readings for equipment "%s" %s
            for the last %d hours.

            Return the list of sensor readings.
            """.formatted(equipmentId, sensorFilter, hours),
            SensorReadingsResult.class
        ).readings();
    }

    /**
     * Get facility-wide status overview.
     */
    @Action(
        description = "Get overview of equipment health status for an entire facility",
        toolGroups = {"sensor-tools"}
    )
    public FacilityStatus getFacilityStatus(String facilityId, Ai ai) {
        log.info("Getting facility status: {}", facilityId);

        return ai.withAutoLlm().createObject(
            """
            Use the get_facility_status tool to get the overall health status of facility "%s".

            Return the facility status including:
            - Total equipment count and status breakdown
            - Health percentage
            - Equipment with active anomalies
            - Summary
            """.formatted(facilityId),
            FacilityStatus.class
        );
    }

    /**
     * Detect anomalies in sensor readings.
     */
    @Action(
        description = "Check for anomalies in sensor readings for equipment",
        toolGroups = {"sensor-tools"}
    )
    public List<Anomaly> detectAnomalies(String equipmentId, Ai ai) {
        log.info("Detecting anomalies for equipment: {}", equipmentId);

        return ai.withAutoLlm().createObject(
            """
            Use the detect_anomaly tool to check for anomalies in equipment "%s".

            Return any detected anomalies with their severity, description, and recommended actions.
            """.formatted(equipmentId),
            AnomalyListResult.class
        ).anomalies();
    }

    /**
     * Analyze equipment health and report findings.
     * Called via dedicated /equipment/{id}/health endpoint.
     */
    @Action(
        description = "Complete health analysis of equipment including sensor data and anomaly detection",
        toolGroups = {"sensor-tools"}
    )
    public HealthAnalysisReport analyzeEquipmentHealth(String equipmentId, Ai ai) {
        log.info(">>> Performing complete health analysis for: {}", equipmentId);

        // Get equipment status
        EquipmentStatus status = getEquipmentStatus(equipmentId, ai);

        // Detect any anomalies
        List<Anomaly> anomalies = detectAnomalies(equipmentId, ai);

        // Generate analysis report
        String analysis = ai.withAutoLlm().generateText(
            """
            Based on the equipment status and anomalies detected, provide a brief analysis:

            Equipment: %s
            Health Status: %s
            Status Summary: %s
            Anomalies Detected: %d

            Provide:
            1. Overall assessment (1-2 sentences)
            2. Key concerns if any
            3. Recommended next steps
            """.formatted(
                equipmentId,
                status.healthStatus(),
                status.statusSummary(),
                anomalies.size()
            )
        );

        log.info("<<< Health analysis complete for: {}", equipmentId);

        return new HealthAnalysisReport(
            equipmentId,
            status.healthStatus(),
            status.latestReadings(),
            anomalies,
            analysis
        );
    }

    /**
     * GOAL: Answer natural language queries about manufacturing operations.
     * This is the main entry point for chat-based interactions.
     */
    @AchievesGoal(description = "Answer questions about Titan Manufacturing sensor data, equipment status, and facility operations")
    @Action(
        description = "Process natural language queries about manufacturing equipment and sensor data",
        toolGroups = {"sensor-tools"}
    )
    public ChatQueryResponse answerQuery(String query, Ai ai) {
        log.info(">>> Processing query: {}", query);

        String response = ai.withAutoLlm().generateText(
            """
            You are Titan Manufacturing's AI assistant with access to sensor data from 600+ CNC machines
            across 12 global facilities (PHX, DET, ATL, DAL, MUC, MAN, LYN, SHA, TYO, SEO, SYD, MEX).

            Use the available sensor tools to answer this query:
            %s

            Available tools:
            - list_equipment: List equipment by facility (facility_id, equipment_type, limit)
            - get_equipment_status: Get current status of equipment (equipment_id)
            - get_sensor_readings: Get historical readings (equipment_id, sensor_type, hours_back, limit)
            - get_facility_status: Get facility overview (facility_id)
            - detect_anomaly: Check for anomalies (equipment_id, sensor_type)

            Provide a helpful, concise response based on the data.
            """.formatted(query)
        );

        log.info("<<< Query response generated");
        return new ChatQueryResponse(query, response);
    }

    /**
     * Response wrapper for chat queries - used as Embabel goal type.
     */
    public record ChatQueryResponse(String query, String response) {}

    // Result wrapper records for LLM responses

    public record EquipmentListResult(List<Equipment> equipment) {}
    public record SensorReadingsResult(List<SensorReading> readings) {}
    public record AnomalyListResult(List<Anomaly> anomalies) {}

    public record HealthAnalysisReport(
        String equipmentId,
        String healthStatus,
        List<SensorReading> latestReadings,
        List<Anomaly> anomalies,
        String analysisNarrative
    ) {}
}
