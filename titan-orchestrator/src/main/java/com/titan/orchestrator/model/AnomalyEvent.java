package com.titan.orchestrator.model;

import java.time.Instant;

/**
 * Event received from RabbitMQ when equipment reaches HIGH or CRITICAL risk level.
 * Published by maintenance-mcp-server's GemFireScoringService.
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
}
