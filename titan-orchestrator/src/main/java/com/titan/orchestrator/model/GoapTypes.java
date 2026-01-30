package com.titan.orchestrator.model;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.time.Instant;
import java.util.List;

/**
 * Intermediate types for the multi-step GOAP anomaly response chain.
 *
 * These records form the "blackboard" objects that GOAP uses to plan and
 * chain actions. Sealed interfaces enable type-graph branching — the planner
 * discovers different paths depending on which concrete type lands on the
 * blackboard at runtime.
 *
 * Type graph:
 *   CriticalAnomalyInput → FaultDiagnosis → UrgencyAssessment (branch 1)
 *     → PartsAssessment (branch 2) → MaintenanceOrder
 *     → Compliance routing (branch 3) → CriticalAnomalyResponse
 */
public class GoapTypes {

    // ── Step 1 output: Diagnosis ────────────────────────────────────────────

    public record FaultDiagnosis(
        String equipmentId,
        String facilityId,
        String faultType,
        double failureProbability,
        String probableCause,
        int estimatedRulHours,
        String urgency,
        boolean regulatedEquipment
    ) {}

    // ── Branch 1: Urgency ───────────────────────────────────────────────────

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
    @JsonSubTypes({
        @JsonSubTypes.Type(value = ImmediateUrgency.class, name = "IMMEDIATE"),
        @JsonSubTypes.Type(value = DeferrableUrgency.class, name = "DEFERRABLE")
    })
    public sealed interface UrgencyAssessment permits ImmediateUrgency, DeferrableUrgency {}

    public record ImmediateUrgency(
        String equipmentId,
        String facilityId,
        FaultDiagnosis diagnosis
    ) implements UrgencyAssessment {}

    public record DeferrableUrgency(
        String equipmentId,
        String facilityId,
        FaultDiagnosis diagnosis
    ) implements UrgencyAssessment {}

    public record ShutdownConfirmation(
        String equipmentId,
        String facilityId,
        String shutdownStatus,
        String previousState,
        Instant shutdownTime,
        FaultDiagnosis diagnosis
    ) {}

    // ── Branch 2: Parts Availability ────────────────────────────────────────

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
    @JsonSubTypes({
        @JsonSubTypes.Type(value = PartsAvailable.class, name = "AVAILABLE"),
        @JsonSubTypes.Type(value = PartsUnavailable.class, name = "UNAVAILABLE")
    })
    public sealed interface PartsAssessment permits PartsAvailable, PartsUnavailable {}

    public record PartsAvailable(
        String equipmentId,
        String facilityId,
        String faultType,
        List<CompatiblePart> parts,
        double estimatedPartsCost
    ) implements PartsAssessment {}

    public record PartsUnavailable(
        String equipmentId,
        String facilityId,
        String faultType,
        List<CompatiblePart> partsNeeded,
        String nearestFacilityWithStock
    ) implements PartsAssessment {}

    public record CompatiblePart(
        String sku,
        String name,
        int quantityNeeded,
        int quantityInStock,
        double unitPrice
    ) {}

    public record CrossFacilityResult(
        String equipmentId,
        String facilityId,
        String sourceFacility,
        String shipmentId,
        String estimatedArrival,
        List<CompatiblePart> partsShipped,
        double shippingCost
    ) {}

    // ── Maintenance Order (converges both parts paths) ──────────────────────

    public record MaintenanceOrder(
        String equipmentId,
        String facilityId,
        String workOrderId,
        String maintenanceType,
        List<AnomalyResponse.ReservedPart> partsReserved,
        String scheduledDate,
        boolean regulatedEquipment,
        String summary
    ) {}

    // ── Branch 3: Regulatory Compliance routing ─────────────────────────────

    public record RegulatedMaintenanceOrder(MaintenanceOrder order) {}

    public record UnregulatedMaintenanceOrder(MaintenanceOrder order) {}

    public record ComplianceVerification(
        String equipmentId,
        String workOrderId,
        String complianceStatus,
        String regulatoryFramework,
        String materialBatchId,
        String auditTrailRef,
        MaintenanceOrder order
    ) {}
}
