package com.titan.orchestrator.model;

import java.util.List;

/**
 * Response types for anomaly handling goals.
 */
public class AnomalyResponse {

    /**
     * Response from CRITICAL anomaly handling - full automated workflow.
     */
    public record CriticalAnomalyResponse(
        String equipmentId,
        String workOrderId,
        List<ReservedPart> partsReserved,
        boolean notificationSent,
        String summary
    ) {}

    /**
     * Response from HIGH anomaly handling - recommendation with parts reserved.
     */
    public record HighAnomalyResponse(
        String equipmentId,
        String recommendationId,
        List<ReservedPart> partsReserved,
        String recommendedAction,
        String summary
    ) {}

    /**
     * Reserved part information.
     */
    public record ReservedPart(
        String sku,
        String name,
        int quantity,
        double unitPrice,
        String reservationId
    ) {}
}
