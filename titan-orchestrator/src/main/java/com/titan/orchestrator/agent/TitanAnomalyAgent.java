package com.titan.orchestrator.agent;

import com.embabel.agent.api.annotation.Action;
import com.embabel.agent.api.annotation.AchievesGoal;
import com.embabel.agent.api.annotation.Agent;
import com.embabel.agent.api.common.Ai;
import com.titan.orchestrator.model.AnomalyEvent;
import com.titan.orchestrator.model.AnomalyEvent.CriticalAnomalyInput;
import com.titan.orchestrator.model.AnomalyEvent.HighAnomalyInput;
import com.titan.orchestrator.model.AnomalyResponse.CriticalAnomalyResponse;
import com.titan.orchestrator.model.AnomalyResponse.HighAnomalyResponse;
import com.titan.orchestrator.model.AnomalyResponse.ReservedPart;
import com.titan.orchestrator.model.GoapTypes.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Titan Anomaly Response Agent — Multi-step GOAP chain with 3 branch points.
 *
 * Replaces the monolithic single-action agent with discrete actions connected
 * by intermediate types. GOAP's A* search chains them automatically based on
 * the type graph.
 *
 * CRITICAL chain:
 *   CriticalAnomalyInput → diagnoseAnomaly → FaultDiagnosis
 *     → assessUrgency → [BRANCH 1: ImmediateUrgency | DeferrableUrgency]
 *     → assessParts → [BRANCH 2: PartsAvailable | PartsUnavailable]
 *     → scheduleMaintenance → MaintenanceOrder
 *     → checkComplianceRequired → [BRANCH 3: Regulated | Unregulated]
 *     → finalizeResponse → CriticalAnomalyResponse
 *
 * HIGH chain (reuses diagnosis + urgency + parts, different finalize):
 *   HighAnomalyInput → diagnoseAnomaly → assessUrgency → assessParts
 *     → finalizeHighResponse → HighAnomalyResponse
 */
@Agent(description = "Titan Anomaly Response Agent - Multi-step GOAP chain that diagnoses faults, " +
                     "manages emergency shutdowns, assesses parts availability, schedules maintenance, " +
                     "and verifies regulatory compliance through 3 runtime branch points.")
@Component
public class TitanAnomalyAgent {

    private static final Logger log = LoggerFactory.getLogger(TitanAnomalyAgent.class);

    // ═══════════════════════════════════════════════════════════════════════
    // Action 1: Diagnose the anomaly
    // ═══════════════════════════════════════════════════════════════════════

    @Action(
        description = "Analyze equipment anomaly to determine fault type, RUL, and urgency",
        toolGroups = {"maintenance-tools"}
    )
    public FaultDiagnosis diagnoseAnomaly(CriticalAnomalyInput input, Ai ai) {
        return doDiagnose("diagnoseAnomaly", input.event(), ai);
    }

    @Action(
        description = "Analyze high-risk equipment anomaly to determine fault type, RUL, and urgency",
        toolGroups = {"maintenance-tools"}
    )
    public FaultDiagnosis diagnoseHighAnomaly(HighAnomalyInput input, Ai ai) {
        return doDiagnose("diagnoseHighAnomaly", input.event(), ai);
    }

    private FaultDiagnosis doDiagnose(String action, AnomalyEvent event, Ai ai) {
        log.info(">>> [{}] Diagnosing {} at {}", action, event.equipmentId(), event.facilityId());

        return ai.withAutoLlm().createObject("""
            Equipment %s at %s has %.0f%% failure probability.
            Probable cause: %s

            Use predictFailure and estimateRul to analyze this equipment.

            Determine:
            1. The fault type: BEARING, MOTOR, SPINDLE, COOLANT, or ELECTRICAL
            2. Estimated remaining useful life in hours
            3. Urgency: IMMEDIATE (< 24h RUL), WITHIN_24H (24-72h), WITHIN_WEEK (> 72h)
            4. Whether this equipment produces regulated output (aerospace or medical)
               - Equipment IDs starting with TYO or MUN are aerospace-regulated
               - All others are unregulated
            """.formatted(
                event.equipmentId(), event.facilityId(),
                event.prediction().failureProbability() * 100,
                event.prediction().probableCause()
            ),
            FaultDiagnosis.class
        );
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Action 2: Assess urgency (pure logic, Branch 1)
    // ═══════════════════════════════════════════════════════════════════════

    @Action(description = "Determine urgency path — emergency shutdown vs direct assessment")
    public UrgencyAssessment assessUrgency(FaultDiagnosis diagnosis) {
        log.info(">>> [assessUrgency] Urgency={} for {}", diagnosis.urgency(), diagnosis.equipmentId());

        if ("IMMEDIATE".equals(diagnosis.urgency())) {
            return new ImmediateUrgency(
                diagnosis.equipmentId(), diagnosis.facilityId(), diagnosis);
        }
        return new DeferrableUrgency(
            diagnosis.equipmentId(), diagnosis.facilityId(), diagnosis);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Action 3: Emergency shutdown (Branch 1 — IMMEDIATE path only)
    // ═══════════════════════════════════════════════════════════════════════

    @Action(
        description = "Issue emergency shutdown for equipment with immediate failure risk",
        toolGroups = {"sensor-tools"}
    )
    public ShutdownConfirmation emergencyShutdown(ImmediateUrgency urgency, Ai ai) {
        log.info(">>> [emergencyShutdown] EMERGENCY shutdown for {}", urgency.equipmentId());

        return ai.withAutoLlm().createObject("""
            EMERGENCY: Equipment %s at %s has IMMEDIATE failure risk (< 24h RUL).

            1. Use getEquipmentStatus to check if the machine is currently running
            2. If running, this is critical — the machine must be halted immediately

            Report the equipment's current state and confirm shutdown status.
            """.formatted(urgency.equipmentId(), urgency.facilityId()),
            ShutdownConfirmation.class
        );
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Action 4: Assess parts (two entry points for Branch 1 convergence)
    // ═══════════════════════════════════════════════════════════════════════

    @Action(
        description = "Find compatible parts for shut-down equipment",
        toolGroups = {"inventory-tools"}
    )
    public PartsAssessment assessPartsAfterShutdown(ShutdownConfirmation shutdown, Ai ai) {
        log.info(">>> [assessPartsAfterShutdown] Checking parts for {}", shutdown.equipmentId());
        return doAssessParts(shutdown.diagnosis(), ai);
    }

    @Action(
        description = "Find compatible parts and check local stock availability",
        toolGroups = {"inventory-tools"}
    )
    public PartsAssessment assessPartsDirect(DeferrableUrgency urgency, Ai ai) {
        log.info(">>> [assessPartsDirect] Checking parts for {}", urgency.equipmentId());
        return doAssessParts(urgency.diagnosis(), ai);
    }

    private PartsAssessment doAssessParts(FaultDiagnosis diagnosis, Ai ai) {
        return ai.withAutoLlm().createObject("""
            Equipment %s at facility %s has a %s fault.

            1. Use getCompatibleParts with equipmentId="%s" and faultType="%s"
            2. Check if the primary parts are in stock at facility %s

            If ALL required primary parts are in stock locally:
              Return PartsAvailable with the parts list and estimated cost.
            If ANY required primary part is out of stock locally:
              Return PartsUnavailable with what's needed and identify which
              other facility has stock (the tool returns totalStock across facilities).
            """.formatted(
                diagnosis.equipmentId(), diagnosis.facilityId(),
                diagnosis.faultType(), diagnosis.equipmentId(),
                diagnosis.faultType(), diagnosis.facilityId()
            ),
            PartsAssessment.class
        );
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Action 5: Cross-facility procurement (Branch 2 — PartsUnavailable only)
    // ═══════════════════════════════════════════════════════════════════════

    @Action(
        description = "Arrange cross-facility parts transfer for out-of-stock items",
        toolGroups = {"logistics-tools", "inventory-tools"}
    )
    public CrossFacilityResult procureCrossFacility(
            FaultDiagnosis diagnosis, PartsUnavailable assessment, Ai ai) {
        log.info(">>> [procureCrossFacility] Procuring from {} for {}",
                 assessment.nearestFacilityWithStock(), assessment.equipmentId());

        return ai.withAutoLlm().createObject("""
            Equipment %s at %s needs parts for a %s fault but local stock is insufficient.
            Parts needed: %s
            Nearest facility with stock: %s

            1. Use checkStock to verify availability at %s
            2. Use estimateShipping to get cost/time from %s to %s
            3. Use createShipment to arrange expedited transfer

            Report the shipment details and estimated arrival.
            """.formatted(
                assessment.equipmentId(), assessment.facilityId(),
                diagnosis.faultType(), assessment.partsNeeded(),
                assessment.nearestFacilityWithStock(),
                assessment.nearestFacilityWithStock(),
                assessment.nearestFacilityWithStock(), assessment.facilityId()
            ),
            CrossFacilityResult.class
        );
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Action 6: Schedule maintenance (two entry points for Branch 2 convergence)
    // ═══════════════════════════════════════════════════════════════════════

    @Action(
        description = "Schedule emergency maintenance using available local parts",
        toolGroups = {"maintenance-tools"}
    )
    public MaintenanceOrder scheduleWithLocalParts(
            FaultDiagnosis diagnosis, PartsAvailable parts, Ai ai) {
        log.info(">>> [scheduleWithLocalParts] Scheduling for {} with local parts", diagnosis.equipmentId());

        return ai.withAutoLlm().createObject("""
            Schedule EMERGENCY maintenance for equipment %s at %s.
            Fault: %s (%.0f%% failure probability, %s urgency)
            Parts available locally: %s

            Use scheduleMaintenance to create the work order.
            Include the compatible parts and fault details in the notes.
            """.formatted(
                diagnosis.equipmentId(), diagnosis.facilityId(),
                diagnosis.faultType(), diagnosis.failureProbability() * 100,
                diagnosis.urgency(), parts.parts()
            ),
            MaintenanceOrder.class
        );
    }

    @Action(
        description = "Schedule maintenance coordinated with incoming parts shipment",
        toolGroups = {"maintenance-tools"}
    )
    public MaintenanceOrder scheduleWithProcuredParts(
            FaultDiagnosis diagnosis, CrossFacilityResult procurement, Ai ai) {
        log.info(">>> [scheduleWithProcuredParts] Scheduling for {} with shipped parts", diagnosis.equipmentId());

        return ai.withAutoLlm().createObject("""
            Schedule maintenance for equipment %s at %s.
            Fault: %s (%.0f%% failure probability, %s urgency)
            Parts shipping from %s, arriving %s (shipment %s).

            Use scheduleMaintenance to create the work order.
            Type: EMERGENCY if urgency is IMMEDIATE, else SCHEDULED.
            Note that parts are in transit — include shipment ID and ETA in notes.
            """.formatted(
                diagnosis.equipmentId(), diagnosis.facilityId(),
                diagnosis.faultType(), diagnosis.failureProbability() * 100,
                diagnosis.urgency(), procurement.sourceFacility(),
                procurement.estimatedArrival(), procurement.shipmentId()
            ),
            MaintenanceOrder.class
        );
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Action 7: Check compliance requirement (pure logic, Branch 3)
    // ═══════════════════════════════════════════════════════════════════════

    @Action(description = "Determine if maintenance requires regulatory compliance verification")
    public Object checkComplianceRequired(MaintenanceOrder order, FaultDiagnosis diagnosis) {
        log.info(">>> [checkComplianceRequired] regulated={} for {}", diagnosis.regulatedEquipment(), order.equipmentId());

        if (diagnosis.regulatedEquipment()) {
            return new RegulatedMaintenanceOrder(order);
        }
        return new UnregulatedMaintenanceOrder(order);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Action 8: Verify compliance (Branch 3 — regulated path only)
    // ═══════════════════════════════════════════════════════════════════════

    @Action(
        description = "Check compliance status and trace material batches for regulated equipment",
        toolGroups = {"governance-tools"}
    )
    public ComplianceVerification verifyCompliance(
            RegulatedMaintenanceOrder regulated, FaultDiagnosis diagnosis, Ai ai) {
        MaintenanceOrder order = regulated.order();
        log.info(">>> [verifyCompliance] Checking compliance for {} (WO: {})", order.equipmentId(), order.workOrderId());

        return ai.withAutoLlm().createObject("""
            Equipment %s at %s is REGULATED (produces aerospace/medical parts).
            Work Order %s has been created for %s maintenance.

            Before maintenance can proceed, verify compliance:
            1. Use getComplianceReport for equipment %s to check regulatory status
            2. Use traceMaterialBatch to get traceability data for the current production batch
            3. Determine if maintenance can proceed or requires a regulatory hold

            Report compliance status (CLEARED, HOLD_REQUIRED, REVIEW_NEEDED),
            the applicable regulatory framework (FAA, ISO_13485, AS9100),
            and the material batch reference for audit trail.
            """.formatted(
                order.equipmentId(), order.facilityId(),
                order.workOrderId(), order.maintenanceType(),
                order.equipmentId()
            ),
            ComplianceVerification.class
        );
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Action 9: Finalize response (two entry points for Branch 3 convergence)
    // ═══════════════════════════════════════════════════════════════════════

    @AchievesGoal(description = "Compile final response with compliance verification")
    @Action(description = "Build CriticalAnomalyResponse including compliance data")
    public CriticalAnomalyResponse finalizeWithCompliance(ComplianceVerification compliance) {
        MaintenanceOrder order = compliance.order();
        log.info(">>> [finalizeWithCompliance] Finalizing for {} (compliance: {})",
                 order.equipmentId(), compliance.complianceStatus());

        return new CriticalAnomalyResponse(
            order.equipmentId(),
            order.workOrderId(),
            order.partsReserved(),
            false,
            "%s | Compliance: %s (%s) | Batch: %s".formatted(
                order.summary(),
                compliance.complianceStatus(),
                compliance.regulatoryFramework(),
                compliance.materialBatchId()
            )
        );
    }

    @AchievesGoal(description = "Compile final response for unregulated equipment")
    @Action(description = "Build CriticalAnomalyResponse from maintenance order")
    public CriticalAnomalyResponse finalizeStandard(UnregulatedMaintenanceOrder unregulated) {
        MaintenanceOrder order = unregulated.order();
        log.info(">>> [finalizeStandard] Finalizing for {} (unregulated)", order.equipmentId());

        return new CriticalAnomalyResponse(
            order.equipmentId(),
            order.workOrderId(),
            order.partsReserved(),
            false,
            order.summary()
        );
    }

    // ═══════════════════════════════════════════════════════════════════════
    // HIGH anomaly path — finalize with recommendation (no scheduling)
    // ═══════════════════════════════════════════════════════════════════════

    @AchievesGoal(description = "Compile recommendation for human approval")
    @Action(description = "Build maintenance recommendation from diagnosis and parts assessment")
    public HighAnomalyResponse finalizeHighResponse(
            FaultDiagnosis diagnosis, PartsAssessment assessment) {
        log.info(">>> [finalizeHighResponse] Building recommendation for {}", diagnosis.equipmentId());

        List<ReservedPart> parts = switch (assessment) {
            case PartsAvailable pa -> pa.parts().stream()
                .map(cp -> new ReservedPart(cp.sku(), cp.name(), cp.quantityNeeded(), cp.unitPrice(), null))
                .toList();
            case PartsUnavailable pu -> List.of();
        };

        String action = "Schedule %s maintenance for %s fault. %s".formatted(
            "IMMEDIATE".equals(diagnosis.urgency()) ? "emergency" : "preventive",
            diagnosis.faultType(),
            assessment instanceof PartsUnavailable pu
                ? "Parts need cross-facility procurement from " + pu.nearestFacilityWithStock()
                : "Parts available locally."
        );

        return new HighAnomalyResponse(
            diagnosis.equipmentId(),
            null,
            parts,
            action,
            "Diagnosed %s with %d%% failure probability".formatted(
                diagnosis.faultType(), Math.round(diagnosis.failureProbability() * 100))
        );
    }
}
