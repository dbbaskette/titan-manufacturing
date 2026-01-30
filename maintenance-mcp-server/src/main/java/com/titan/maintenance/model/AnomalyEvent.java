package com.titan.maintenance.model;

import java.time.Instant;

/**
 * Event published to RabbitMQ when equipment reaches HIGH or CRITICAL risk level.
 * Consumed by titan-orchestrator to trigger Embabel workflows.
 */
public record AnomalyEvent(
    String eventId,
    String eventType,           // ANOMALY_CRITICAL or ANOMALY_HIGH
    Instant timestamp,
    String equipmentId,
    String facilityId,
    Prediction prediction
) {
    public record Prediction(
        double failureProbability,
        String riskLevel,
        String probableCause,
        double vibrationAvg,
        double temperatureAvg,
        double powerAvg,
        double rpmAvg,
        double pressureAvg,
        double torqueAvg,
        String scoredAt
    ) {}

    public static AnomalyEvent create(
            String equipmentId,
            String facilityId,
            String riskLevel,
            double failureProbability,
            String probableCause,
            double vibrationAvg,
            double temperatureAvg,
            double powerAvg,
            double rpmAvg,
            double pressureAvg,
            double torqueAvg,
            String scoredAt
    ) {
        String eventId = "evt-" + System.currentTimeMillis() + "-" + equipmentId;
        String eventType = "CRITICAL".equals(riskLevel) ? "ANOMALY_CRITICAL" : "ANOMALY_HIGH";

        return new AnomalyEvent(
            eventId,
            eventType,
            Instant.now(),
            equipmentId,
            facilityId,
            new Prediction(
                failureProbability,
                riskLevel,
                probableCause,
                vibrationAvg,
                temperatureAvg,
                powerAvg,
                rpmAvg,
                pressureAvg,
                torqueAvg,
                scoredAt
            )
        );
    }
}
