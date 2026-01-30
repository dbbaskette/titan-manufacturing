# Embabel Multi-Step GOAP Redesign

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Replace the single-action monolithic agent with a multi-step GOAP chain where the planner discovers and sequences actions via the type graph, with 3 runtime branch points spanning 6 tool groups.

**Architecture:** Split `handleCriticalAnomaly` (one LLM call doing everything) into discrete actions connected by intermediate types. GOAP's A* search chains them automatically. Three branch points create different execution paths based on runtime data: urgency level, parts availability, and regulatory compliance requirements. Each action has a focused LLM prompt scoped to specific tool groups.

**Tech Stack:** Embabel Agent Framework (GOAP), Spring AI MCP, Java 21 records/sealed interfaces, RabbitMQ

---

## Why This Redesign

### Current Problem: GOAP as Router

```
CriticalAnomalyInput → [single LLM call does EVERYTHING] → CriticalAnomalyResponse
```

The GOAP planner does trivial work — one input type maps to one action. The LLM prompt contains ALL instructions (find parts, schedule maintenance, notify). If the LLM skips a step, it silently fails. There's no branching, no multi-step planning, no replanning.

### Target: Real GOAP Chain with 3 Branch Points

```
CriticalAnomalyInput
    → diagnoseAnomaly → FaultDiagnosis
    → [BRANCH 1: Urgency]
        IMMEDIATE → emergencyShutdown → ShutdownConfirmation → assessParts
        NON_IMMEDIATE → assessParts (directly)
    → assessParts → PartsAssessment
    → [BRANCH 2: Stock Availability]
        PartsAvailable → scheduleMaintenance
        PartsUnavailable → procureCrossFacility → CrossFacilityResult → scheduleMaintenance
    → scheduleMaintenance → MaintenanceOrder
    → [BRANCH 3: Regulatory Compliance]
        REGULATED equipment → verifyCompliance → ComplianceVerification → finalizeResponse
        UNREGULATED equipment → finalizeResponse (directly)
    → finalizeResponse → CriticalAnomalyResponse
```

Each action has a focused LLM prompt with scoped tool groups. The planner discovers the sequence. Branching happens because actions produce different types depending on runtime data — the planner finds different paths to `CriticalAnomalyResponse` depending on which types land on the blackboard.

**Tool group coverage across the chain:**

| Branch | Tool Groups Used |
|--------|-----------------|
| Branch 1 (Urgency) | `sensor-tools` — check equipment status, issue shutdown |
| Main chain | `maintenance-tools` — predictFailure, estimateRul, scheduleMaintenance |
| Branch 2 (Parts) | `inventory-tools` — getCompatibleParts, checkStock |
| Branch 2 (Cross-facility) | `logistics-tools` — estimateShipping, createShipment |
| Branch 3 (Compliance) | `governance-tools` — getComplianceReport, traceMaterialBatch |
| Post-agent (deterministic) | `communications-tools` — sendNotification (via NotificationService) |

All 6 tool groups are now used, with 5 used by the GOAP chain and 1 deterministically.

---

## Type Graph Design

### Intermediate Types (Blackboard Objects)

```java
// Step 1 output: Diagnosis from maintenance-tools
public record FaultDiagnosis(
    String equipmentId,
    String facilityId,
    String faultType,          // BEARING, MOTOR, SPINDLE, COOLANT, ELECTRICAL
    double failureProbability,
    String probableCause,
    int estimatedRulHours,     // remaining useful life
    String urgency,            // IMMEDIATE, WITHIN_24H, WITHIN_WEEK
    boolean regulatedEquipment // true if equipment produces aerospace/medical parts
) {}

// ── Branch 1: Urgency ──────────────────────────────────────────────────────

// Sealed interface for urgency branching
public sealed interface UrgencyAssessment permits ImmediateUrgency, DeferrableUrgency {}

// IMMEDIATE urgency — requires emergency shutdown before proceeding
public record ImmediateUrgency(
    String equipmentId,
    String facilityId,
    FaultDiagnosis diagnosis
) implements UrgencyAssessment {}

// NON-IMMEDIATE urgency — can proceed directly to parts assessment
public record DeferrableUrgency(
    String equipmentId,
    String facilityId,
    FaultDiagnosis diagnosis
) implements UrgencyAssessment {}

// Output of emergency shutdown action (only on IMMEDIATE path)
public record ShutdownConfirmation(
    String equipmentId,
    String facilityId,
    String shutdownStatus,     // CONFIRMED, ALREADY_IDLE
    String previousState,      // RUNNING, IDLE, ERROR
    Instant shutdownTime,
    FaultDiagnosis diagnosis   // carry forward for downstream actions
) {}

// ── Branch 2: Parts Availability ────────────────────────────────────────────

// Sealed interface for parts branching
public sealed interface PartsAssessment permits PartsAvailable, PartsUnavailable {}

public record PartsAvailable(
    String equipmentId,
    String facilityId,
    String faultType,
    List<CompatiblePart> parts,   // parts found at this facility
    double estimatedPartsCost
) implements PartsAssessment {}

public record PartsUnavailable(
    String equipmentId,
    String facilityId,
    String faultType,
    List<CompatiblePart> partsNeeded,   // what's needed but not in stock
    String nearestFacilityWithStock     // where to get them
) implements PartsAssessment {}

// Output of cross-facility procurement (only on PartsUnavailable path)
public record CrossFacilityResult(
    String equipmentId,
    String facilityId,
    String sourceFacility,
    String shipmentId,
    String estimatedArrival,
    List<CompatiblePart> partsShipped,
    double shippingCost
) {}

// ── Maintenance Order (converges both parts paths) ──────────────────────────

public record MaintenanceOrder(
    String equipmentId,
    String facilityId,
    String workOrderId,
    String maintenanceType,      // EMERGENCY, SCHEDULED
    List<ReservedPart> partsReserved,
    String scheduledDate,
    boolean regulatedEquipment,  // carry forward for compliance branch
    String summary
) {}

// ── Branch 3: Regulatory Compliance ─────────────────────────────────────────

// Output of compliance verification (only for regulated equipment)
public record ComplianceVerification(
    String equipmentId,
    String workOrderId,
    String complianceStatus,     // CLEARED, HOLD_REQUIRED, REVIEW_NEEDED
    String regulatoryFramework,  // FAA, ISO_13485, AS9100
    String materialBatchId,      // traceability reference
    String auditTrailRef,
    MaintenanceOrder order       // carry forward
) {}
```

### Complete Type Graph (How GOAP Sees It)

```
                         CriticalAnomalyInput
                                │
                    ┌───────────▼───────────┐
                    │    diagnoseAnomaly     │  tools: maintenance-tools
                    └───────────┬───────────┘
                                │
                         FaultDiagnosis
                                │
                    ┌───────────▼───────────┐
                    │   assessUrgency       │  (pure logic, no LLM)
                    └───────────┬───────────┘
                                │
               ┌────────────────┴────────────────┐
               │                                 │
        ImmediateUrgency                 DeferrableUrgency
               │                                 │
    ┌──────────▼──────────┐                      │
    │  emergencyShutdown  │ tools: sensor-tools  │
    └──────────┬──────────┘                      │
               │                                 │
       ShutdownConfirmation                      │
               │                                 │
               └────────────────┬────────────────┘
                                │
                    ┌───────────▼───────────┐
                    │     assessParts       │  tools: inventory-tools
                    └───────────┬───────────┘
                                │
               ┌────────────────┴────────────────┐
               │                                 │
        PartsAvailable                   PartsUnavailable
               │                                 │
               │                  ┌──────────────▼──────────────┐
               │                  │   procureCrossFacility      │ tools: logistics,
               │                  │                             │        inventory
               │                  └──────────────┬──────────────┘
               │                                 │
               │                          CrossFacilityResult
               │                                 │
               └────────────────┬────────────────┘
                                │
                    ┌───────────▼───────────┐
                    │  scheduleMaintenance  │  tools: maintenance-tools
                    └───────────┬───────────┘
                                │
                         MaintenanceOrder
                                │
               ┌────────────────┴────────────────┐
               │                                 │
        regulatedEquipment=true        regulatedEquipment=false
               │                                 │
    ┌──────────▼──────────┐                      │
    │  verifyCompliance   │ tools: governance    │
    └──────────┬──────────┘                      │
               │                                 │
       ComplianceVerification                    │
               │                                 │
               └────────────────┬────────────────┘
                                │
                    ┌───────────▼───────────┐
                    │   finalizeResponse    │  (pure logic, no LLM)
                    └───────────┬───────────┘
                                │
                     CriticalAnomalyResponse
                                │
                    ════════════╪════════════
                    (deterministic, post-agent)
                                │
                    ┌───────────▼───────────┐
                    │  NotificationService  │  comms MCP (JSON-RPC)
                    └───────────────────────┘
```

### How Branching Works

**Branch 1 — Urgency:**
`assessUrgency` is a pure-logic action (no LLM). It reads `FaultDiagnosis.urgency` and produces either `ImmediateUrgency` or `DeferrableUrgency`:
- **`ImmediateUrgency`** → `emergencyShutdown` runs (uses sensor-tools to halt the machine) → produces `ShutdownConfirmation` → `assessParts` accepts `ShutdownConfirmation` as input
- **`DeferrableUrgency`** → `assessParts` accepts `DeferrableUrgency` directly (overloaded input)

**Branch 2 — Parts Availability:**
`assessParts` returns a **sealed interface** (`PartsAssessment`). At runtime, the concrete type placed on the blackboard is either `PartsAvailable` or `PartsUnavailable`:
- **`PartsAvailable`** → `scheduleWithLocalParts` has precondition `(FaultDiagnosis, PartsAvailable)` → runs directly
- **`PartsUnavailable`** → `procureCrossFacility` has precondition `(FaultDiagnosis, PartsUnavailable)` → runs first → produces `CrossFacilityResult` → `scheduleWithProcuredParts` has precondition `(FaultDiagnosis, CrossFacilityResult)` → runs

**Branch 3 — Compliance:**
`MaintenanceOrder` carries a `regulatedEquipment` flag (propagated from `FaultDiagnosis`). A condition-checking action routes:
- **Regulated** → `verifyCompliance` runs (uses governance-tools: `getComplianceReport`, `traceMaterialBatch`) → produces `ComplianceVerification` → `finalizeResponse` accepts it
- **Unregulated** → `finalizeResponse` accepts `MaintenanceOrder` directly

GOAP discovers all paths at planning time via A* over the type graph. No if/else in our code.

**Important Embabel note:** The branching depends on Embabel's ability to handle sealed interfaces in the type graph and/or condition-based routing. If Embabel requires concrete types only, we may need to use `@Condition` annotations or flag-based conditions instead of sealed subtypes. This should be verified early (see Phase 1).

---

## Action Definitions

### Action 1: diagnoseAnomaly

```java
@AchievesGoal(description = "Diagnose the equipment fault using predictive maintenance data")
@Action(
    description = "Analyze equipment anomaly to determine fault type, RUL, and urgency",
    toolGroups = {"maintenance-tools"}
)
public FaultDiagnosis diagnoseAnomaly(CriticalAnomalyInput input, Ai ai) {
    AnomalyEvent event = input.event();
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
```

**Scoped tools:** `predictFailure`, `estimateRul` only.

### Action 2: assessUrgency (pure logic, Branch 1)

```java
@Action(description = "Determine urgency path — emergency shutdown vs direct assessment")
public UrgencyAssessment assessUrgency(FaultDiagnosis diagnosis) {
    // Pure logic — no LLM call. Route based on urgency field.
    if ("IMMEDIATE".equals(diagnosis.urgency())) {
        return new ImmediateUrgency(
            diagnosis.equipmentId(), diagnosis.facilityId(), diagnosis);
    }
    return new DeferrableUrgency(
        diagnosis.equipmentId(), diagnosis.facilityId(), diagnosis);
}
```

No LLM call — deterministic routing based on diagnosis data.

### Action 3: emergencyShutdown (Branch 1 — IMMEDIATE path only)

```java
@AchievesGoal(description = "Halt equipment to prevent imminent failure")
@Action(
    description = "Issue emergency shutdown for equipment with immediate failure risk",
    toolGroups = {"sensor-tools"}
)
public ShutdownConfirmation emergencyShutdown(ImmediateUrgency urgency, Ai ai) {
    return ai.withAutoLlm().createObject("""
        EMERGENCY: Equipment %s at %s has IMMEDIATE failure risk (< 24h RUL).

        1. Use getEquipmentStatus to check if the machine is currently running
        2. If running, this is critical — the machine must be halted immediately

        Report the equipment's current state and confirm shutdown status.
        """.formatted(urgency.equipmentId(), urgency.facilityId()),
        ShutdownConfirmation.class
    );
}
```

**Scoped tools:** `getEquipmentStatus`. Only runs when `ImmediateUrgency` is on the blackboard.

### Action 4: assessParts (two overloads for Branch 1 convergence)

```java
// After emergency shutdown
@AchievesGoal(description = "Assess parts availability after emergency shutdown")
@Action(
    description = "Find compatible parts for shut-down equipment",
    toolGroups = {"inventory-tools"}
)
public PartsAssessment assessPartsAfterShutdown(ShutdownConfirmation shutdown, Ai ai) {
    FaultDiagnosis diagnosis = shutdown.diagnosis();
    return doAssessParts(diagnosis, ai);
}

// Direct path (non-immediate urgency)
@AchievesGoal(description = "Assess parts availability for diagnosed fault")
@Action(
    description = "Find compatible parts and check local stock availability",
    toolGroups = {"inventory-tools"}
)
public PartsAssessment assessPartsDirect(DeferrableUrgency urgency, Ai ai) {
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
```

**Scoped tools:** `getCompatibleParts`, `checkStock`. Two entry points converge Branch 1.

### Action 5: procureCrossFacility (Branch 2 — PartsUnavailable path only)

```java
@AchievesGoal(description = "Procure parts from another facility when local stock is insufficient")
@Action(
    description = "Arrange cross-facility parts transfer for out-of-stock items",
    toolGroups = {"logistics-tools", "inventory-tools"}
)
public CrossFacilityResult procureCrossFacility(
        FaultDiagnosis diagnosis, PartsUnavailable assessment, Ai ai) {
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
```

**Scoped tools:** `checkStock`, `estimateShipping`, `createShipment`. Only runs when `PartsUnavailable` is on the blackboard.

### Action 6: scheduleMaintenance (two overloads for Branch 2 convergence)

```java
// Path A: Parts were available locally
@AchievesGoal(description = "Schedule maintenance with locally available parts")
@Action(
    description = "Schedule emergency maintenance using available local parts",
    toolGroups = {"maintenance-tools"}
)
public MaintenanceOrder scheduleWithLocalParts(
        FaultDiagnosis diagnosis, PartsAvailable parts, Ai ai) {
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

// Path B: Parts were procured cross-facility
@AchievesGoal(description = "Schedule maintenance after cross-facility procurement")
@Action(
    description = "Schedule maintenance coordinated with incoming parts shipment",
    toolGroups = {"maintenance-tools"}
)
public MaintenanceOrder scheduleWithProcuredParts(
        FaultDiagnosis diagnosis, CrossFacilityResult procurement, Ai ai) {
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
```

### Action 7: checkComplianceRequired (pure logic, Branch 3)

```java
@Action(description = "Determine if maintenance requires regulatory compliance verification")
public Object checkComplianceRequired(MaintenanceOrder order, FaultDiagnosis diagnosis) {
    // Pure logic — no LLM call. Route based on regulated flag.
    if (diagnosis.regulatedEquipment()) {
        return new RegulatedMaintenanceOrder(order);  // triggers compliance path
    }
    return new UnregulatedMaintenanceOrder(order);    // skips to finalize
}
```

Wrapper types for Branch 3 routing:
```java
public record RegulatedMaintenanceOrder(MaintenanceOrder order) {}
public record UnregulatedMaintenanceOrder(MaintenanceOrder order) {}
```

### Action 8: verifyCompliance (Branch 3 — regulated path only)

```java
@AchievesGoal(description = "Verify regulatory compliance before maintenance proceeds")
@Action(
    description = "Check compliance status and trace material batches for regulated equipment",
    toolGroups = {"governance-tools"}
)
public ComplianceVerification verifyCompliance(
        RegulatedMaintenanceOrder regulated, FaultDiagnosis diagnosis, Ai ai) {
    MaintenanceOrder order = regulated.order();
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
```

**Scoped tools:** `getComplianceReport`, `traceMaterialBatch`. Only runs for regulated equipment.

### Action 9: finalizeResponse (two overloads for Branch 3 convergence)

```java
// Regulated path — includes compliance data
@AchievesGoal(description = "Compile final response with compliance verification")
@Action(description = "Build CriticalAnomalyResponse including compliance data")
public CriticalAnomalyResponse finalizeWithCompliance(
        ComplianceVerification compliance) {
    MaintenanceOrder order = compliance.order();
    return new CriticalAnomalyResponse(
        order.equipmentId(),
        order.workOrderId(),
        order.partsReserved(),
        false,  // notification handled deterministically by listener
        "%s | Compliance: %s (%s) | Batch: %s".formatted(
            order.summary(),
            compliance.complianceStatus(),
            compliance.regulatoryFramework(),
            compliance.materialBatchId()
        )
    );
}

// Unregulated path — standard response
@AchievesGoal(description = "Compile final response for unregulated equipment")
@Action(description = "Build CriticalAnomalyResponse from maintenance order")
public CriticalAnomalyResponse finalizeStandard(
        UnregulatedMaintenanceOrder unregulated) {
    MaintenanceOrder order = unregulated.order();
    return new CriticalAnomalyResponse(
        order.equipmentId(),
        order.workOrderId(),
        order.partsReserved(),
        false,
        order.summary()
    );
}
```

No LLM calls — deterministic assembly from blackboard state.

---

## HIGH Anomaly Chain

The HIGH path is shorter — no scheduling, no cross-facility procurement, no compliance:

```
HighAnomalyInput
    → diagnoseAnomaly → FaultDiagnosis
    → assessUrgency → UrgencyAssessment (IMMEDIATE or DEFERRABLE)
    → [BRANCH 1: if IMMEDIATE → emergencyShutdown → ShutdownConfirmation]
    → assessParts → PartsAssessment
    → finalizeHighResponse → HighAnomalyResponse
```

This reuses `diagnoseAnomaly`, `assessUrgency`, `emergencyShutdown`, and `assessParts` from the CRITICAL chain. Only the finalize action differs — it creates a recommendation instead of scheduling maintenance.

```java
@AchievesGoal(description = "Compile recommendation for human approval")
@Action(description = "Build maintenance recommendation from diagnosis and parts assessment")
public HighAnomalyResponse finalizeHighResponse(
        FaultDiagnosis diagnosis, PartsAssessment assessment) {
    // Deterministic — no LLM call
    List<ReservedPart> parts = switch (assessment) {
        case PartsAvailable pa -> mapToReservedParts(pa.parts());
        case PartsUnavailable pu -> List.of(); // no parts to reserve yet
    };
    String action = "Schedule %s maintenance for %s fault. %s".formatted(
        "IMMEDIATE".equals(diagnosis.urgency()) ? "emergency" : "preventive",
        diagnosis.faultType(),
        assessment instanceof PartsUnavailable pu
            ? "Parts need cross-facility procurement from " + pu.nearestFacilityWithStock()
            : "Parts available locally."
    );
    return new HighAnomalyResponse(
        diagnosis.equipmentId(), null, parts, action,
        "Diagnosed %s with %d%% failure probability".formatted(
            diagnosis.faultType(), Math.round(diagnosis.failureProbability() * 100))
    );
}
```

---

## Execution Path Examples

### Example 1: CRITICAL, Immediate Urgency, Parts Available, Unregulated

Equipment: `ATL-CNC-003` (Atlanta, DMG MORI DMU 50, bearing degradation, 85% failure, 8h RUL)

```
1. diagnoseAnomaly      → FaultDiagnosis(BEARING, IMMEDIATE, regulated=false)  [maintenance-tools]
2. assessUrgency        → ImmediateUrgency                                     [pure logic]
3. emergencyShutdown    → ShutdownConfirmation(CONFIRMED)                      [sensor-tools]
4. assessPartsAfterShutdown → PartsAvailable(INDL-BRG-7420, in stock)          [inventory-tools]
5. scheduleWithLocalParts   → MaintenanceOrder(WO-xxx, EMERGENCY)              [maintenance-tools]
6. checkComplianceRequired  → UnregulatedMaintenanceOrder                      [pure logic]
7. finalizeStandard         → CriticalAnomalyResponse                          [pure logic]
   (then) NotificationService.sendMaintenanceAlert()                           [deterministic]
```

**Actions: 7 | LLM calls: 4 | Branch path: IMMEDIATE → LOCAL_PARTS → UNREGULATED**

### Example 2: CRITICAL, Non-Immediate, Parts Unavailable, Unregulated

Equipment: `PHX-CNC-007` (Phoenix, Mazak VTC-800, motor burnout, 73% failure, 48h RUL)

```
1. diagnoseAnomaly      → FaultDiagnosis(MOTOR, WITHIN_24H, regulated=false)   [maintenance-tools]
2. assessUrgency        → DeferrableUrgency                                    [pure logic]
3. assessPartsDirect    → PartsUnavailable(INDL-MOT-5501, nearest: ATL)        [inventory-tools]
4. procureCrossFacility → CrossFacilityResult(ATL→PHX, shipment SHP-xxx)       [logistics+inventory]
5. scheduleWithProcuredParts → MaintenanceOrder(WO-xxx, SCHEDULED)             [maintenance-tools]
6. checkComplianceRequired   → UnregulatedMaintenanceOrder                     [pure logic]
7. finalizeStandard          → CriticalAnomalyResponse                         [pure logic]
   (then) NotificationService.sendMaintenanceAlert()                           [deterministic]
```

**Actions: 7 | LLM calls: 4 | Branch path: DEFERRABLE → CROSS_FACILITY → UNREGULATED**

### Example 3: CRITICAL, Immediate, Parts Available, Regulated

Equipment: `TYO-CNC-002` (Tokyo, aerospace facility, spindle wear, 90% failure, 4h RUL)

```
1. diagnoseAnomaly      → FaultDiagnosis(SPINDLE, IMMEDIATE, regulated=true)   [maintenance-tools]
2. assessUrgency        → ImmediateUrgency                                     [pure logic]
3. emergencyShutdown    → ShutdownConfirmation(CONFIRMED)                      [sensor-tools]
4. assessPartsAfterShutdown → PartsAvailable(INDL-SPN-8800, in stock)          [inventory-tools]
5. scheduleWithLocalParts   → MaintenanceOrder(WO-xxx, EMERGENCY)              [maintenance-tools]
6. checkComplianceRequired  → RegulatedMaintenanceOrder                        [pure logic]
7. verifyCompliance         → ComplianceVerification(CLEARED, FAA)             [governance-tools]
8. finalizeWithCompliance   → CriticalAnomalyResponse                          [pure logic]
   (then) NotificationService.sendMaintenanceAlert()                           [deterministic]
```

**Actions: 8 | LLM calls: 5 | Branch path: IMMEDIATE → LOCAL_PARTS → REGULATED**

---

## Files to Modify

| # | File | Change |
|---|------|--------|
| 1 | `titan-orchestrator/.../model/AnomalyResponse.java` | Add intermediate types: `FaultDiagnosis`, `UrgencyAssessment` (sealed), `ImmediateUrgency`, `DeferrableUrgency`, `ShutdownConfirmation`, `PartsAssessment` (sealed), `PartsAvailable`, `PartsUnavailable`, `CrossFacilityResult`, `MaintenanceOrder`, `RegulatedMaintenanceOrder`, `UnregulatedMaintenanceOrder`, `ComplianceVerification` |
| 2 | `titan-orchestrator/.../model/AnomalyEvent.java` | No changes (existing `CriticalAnomalyInput` / `HighAnomalyInput` stay) |
| 3 | `titan-orchestrator/.../agent/TitanAnomalyAgent.java` | Replace 2 monolithic actions with ~11 discrete actions (9 CRITICAL chain + reuse for HIGH) |
| 4 | `titan-orchestrator/.../listener/AnomalyEventListener.java` | Update goal type if needed (still targets `CriticalAnomalyResponse`) |
| 5 | `titan-orchestrator/.../controller/RecommendationController.java` | Update approval flow if response type changes |
| 6 | `titan-orchestrator/.../config/McpToolGroupsConfiguration.java` | No changes — all tool groups already defined as beans |
| 7 | `docs/embabel-architecture.md` | Update architecture section with multi-step chain diagrams and all 3 branches |

---

## Risks and Open Questions

1. **Sealed interface support in Embabel type graph**: Does GOAP handle `sealed interface PartsAssessment` with two implementing records? If not, we need a different branching mechanism (e.g., `@Condition` annotations, or wrapper records like we use for Branch 3).

2. **Action reuse across goals**: Can `diagnoseAnomaly`, `assessUrgency`, `emergencyShutdown`, and `assessParts` be reused for both CRITICAL and HIGH paths? Embabel should support this if the type graph resolves — the same action can appear in multiple plans.

3. **Blackboard accumulation**: As more types land on the blackboard through the chain, each action's parameter types serve as preconditions. We need to ensure the planner correctly requires ALL parameter types, not just any one of them.

4. **Multiple actions producing the same output type**: `scheduleWithLocalParts` and `scheduleWithProcuredParts` both produce `MaintenanceOrder`. GOAP should choose based on which precondition types are available. Similarly for the two `finalize` overloads.

5. **Pure-logic actions**: `assessUrgency` and `checkComplianceRequired` don't use LLM or tools — they're deterministic routing. Verify Embabel supports actions without `Ai` parameter or tool groups.

6. **Chain depth**: The longest path (IMMEDIATE → CROSS_FACILITY → REGULATED) is 8 actions with 5 LLM calls. This will add latency (~30-60s total). Acceptable for a demo but worth noting.

7. **Regulated equipment determination**: The plan uses equipment ID prefix (TYO, MUN) to determine regulation status. This could alternatively be a column on the `equipment` table or queried from governance-tools. For the demo, hardcoding by facility prefix is simplest.

---

## Development Checklist

### Phase 1: Verify Embabel Capabilities

- [ ] **1.1** Create a minimal test agent with a 2-action chain (Input → Intermediate → Output) to verify GOAP chains actions via the type graph
- [ ] **1.2** Test sealed interface branching — can GOAP route differently based on which implementing type lands on the blackboard?
- [ ] **1.3** Test multiple actions producing the same output type — does GOAP choose based on available preconditions?
- [ ] **1.4** Test action reuse across goals — can the same `@Action` participate in both CRITICAL and HIGH goal plans?
- [ ] **1.5** Test pure-logic actions (no `Ai` param, no tool groups) — does Embabel execute them?
- [ ] **1.6** Document findings and adjust design if any capability is missing
- [ ] **1.7** If sealed interfaces don't work, switch to wrapper-record pattern (like `RegulatedMaintenanceOrder`) for all branches

### Phase 2: Define Intermediate Types

- [ ] **2.1** Add `FaultDiagnosis` record
- [ ] **2.2** Add `UrgencyAssessment` sealed interface with `ImmediateUrgency` and `DeferrableUrgency`
- [ ] **2.3** Add `ShutdownConfirmation` record
- [ ] **2.4** Add `PartsAssessment` sealed interface with `PartsAvailable` and `PartsUnavailable`
- [ ] **2.5** Add `CrossFacilityResult` record
- [ ] **2.6** Add `MaintenanceOrder` record
- [ ] **2.7** Add `RegulatedMaintenanceOrder` and `UnregulatedMaintenanceOrder` wrapper records
- [ ] **2.8** Add `ComplianceVerification` record
- [ ] **2.9** Verify all records compile and are serializable by Jackson (sealed interfaces may need `@JsonTypeInfo`)

### Phase 3: Implement Actions — Diagnosis + Urgency Branch

- [ ] **3.1** Implement `diagnoseAnomaly(CriticalAnomalyInput, Ai) → FaultDiagnosis` — scoped to `maintenance-tools`
- [ ] **3.2** Implement `assessUrgency(FaultDiagnosis) → UrgencyAssessment` — pure logic, no LLM
- [ ] **3.3** Implement `emergencyShutdown(ImmediateUrgency, Ai) → ShutdownConfirmation` — scoped to `sensor-tools`
- [ ] **3.4** Build and verify the chain resolves: Input → Diagnosis → Urgency branch

### Phase 4: Implement Actions — Parts Assessment + Procurement Branch

- [ ] **4.1** Implement `assessPartsAfterShutdown(ShutdownConfirmation, Ai) → PartsAssessment` — scoped to `inventory-tools`
- [ ] **4.2** Implement `assessPartsDirect(DeferrableUrgency, Ai) → PartsAssessment` — scoped to `inventory-tools`
- [ ] **4.3** Implement `procureCrossFacility(FaultDiagnosis, PartsUnavailable, Ai) → CrossFacilityResult` — scoped to `logistics-tools`, `inventory-tools`
- [ ] **4.4** Build and verify: both urgency paths converge into parts assessment, then branch on stock

### Phase 5: Implement Actions — Scheduling + Compliance Branch

- [ ] **5.1** Implement `scheduleWithLocalParts(FaultDiagnosis, PartsAvailable, Ai) → MaintenanceOrder` — scoped to `maintenance-tools`
- [ ] **5.2** Implement `scheduleWithProcuredParts(FaultDiagnosis, CrossFacilityResult, Ai) → MaintenanceOrder` — scoped to `maintenance-tools`
- [ ] **5.3** Implement `checkComplianceRequired(MaintenanceOrder, FaultDiagnosis) → RegulatedMaintenanceOrder | UnregulatedMaintenanceOrder` — pure logic
- [ ] **5.4** Implement `verifyCompliance(RegulatedMaintenanceOrder, FaultDiagnosis, Ai) → ComplianceVerification` — scoped to `governance-tools`
- [ ] **5.5** Implement `finalizeWithCompliance(ComplianceVerification) → CriticalAnomalyResponse` — pure logic
- [ ] **5.6** Implement `finalizeStandard(UnregulatedMaintenanceOrder) → CriticalAnomalyResponse` — pure logic
- [ ] **5.7** Remove old `handleCriticalAnomaly` monolithic action

### Phase 6: Implement HIGH Path

- [ ] **6.1** Implement `finalizeHighResponse(FaultDiagnosis, PartsAssessment) → HighAnomalyResponse` — pure logic
- [ ] **6.2** Verify `diagnoseAnomaly`, `assessUrgency`, `emergencyShutdown`, and `assessParts` are reused by GOAP for the HIGH goal
- [ ] **6.3** Remove old `handleHighAnomaly` monolithic action
- [ ] **6.4** Update `HighAnomalyInput` handling if needed to chain into `diagnoseAnomaly`

### Phase 7: Update Listener and Controller

- [ ] **7.1** Verify `AnomalyEventListener.handleCritical()` still works — goal type `CriticalAnomalyResponse` unchanged
- [ ] **7.2** Verify `AnomalyEventListener.handleHigh()` still works — goal type `HighAnomalyResponse` unchanged
- [ ] **7.3** Verify `RecommendationController.approveRecommendation()` still works
- [ ] **7.4** Update `AutomatedActionService.record()` if `CriticalAnomalyResponse` fields changed
- [ ] **7.5** Verify deterministic `NotificationService` calls still fire after agent completes

### Phase 8: Update Architecture Documentation

- [ ] **8.1** Update `docs/embabel-architecture.md` with the multi-step chain diagrams
- [ ] **8.2** Update the "How It Works End-to-End" section with the new action sequence
- [ ] **8.3** Update the "What Embabel Adds" section — now shows genuine GOAP value with 3 branch points
- [ ] **8.4** Add "Branch Point Examples" section showing all 3 decisions
- [ ] **8.5** Update the TitanAnomalyAgent action table with all ~11 actions

### Phase 9: Build, Deploy, and Verify

- [ ] **9.1** Build orchestrator: `mvn package -pl titan-orchestrator -am -DskipTests`
- [ ] **9.2** Rebuild and deploy: `docker compose build --no-cache titan-orchestrator && docker compose up -d titan-orchestrator`
- [ ] **9.3** Test Path 1: CRITICAL + IMMEDIATE + LOCAL_PARTS + UNREGULATED (e.g., ATL-CNC-003 bearing)
- [ ] **9.4** Test Path 2: CRITICAL + DEFERRABLE + CROSS_FACILITY + UNREGULATED (e.g., PHX-CNC-007 motor)
- [ ] **9.5** Test Path 3: CRITICAL + IMMEDIATE + LOCAL_PARTS + REGULATED (e.g., TYO-CNC-002 spindle)
- [ ] **9.6** Test Path 4: HIGH anomaly — verify diagnosis + assessment chain with recommendation
- [ ] **9.7** Test Path 5: Approve a HIGH recommendation — verify it triggers full CRITICAL chain
- [ ] **9.8** Check logs for GOAP planner output showing action sequencing and branching decisions
- [ ] **9.9** Commit and push
