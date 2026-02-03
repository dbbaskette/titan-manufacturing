# Embabel Multi-Step GOAP Redesign

**Goal:** Replace the single-action monolithic agent with a multi-step GOAP chain where the planner discovers and sequences actions via the type graph, with 3 runtime branch points spanning 6 tool groups.

**Architecture:** Split `handleCriticalAnomaly` (one LLM call doing everything) into discrete actions connected by intermediate types. GOAP's A* search chains them automatically. Three branch points create different execution paths based on runtime data: urgency level, parts availability, and regulatory compliance requirements.

**Tech Stack:** Embabel Agent Framework (GOAP), Spring AI MCP, Java 21 records/sealed interfaces, RabbitMQ

---

## Status

| Phase | Status | Notes |
|-------|--------|-------|
| Intermediate types (GoapTypes.java) | DONE | All records and sealed interfaces created |
| Agent actions (TitanAnomalyAgent.java) | DONE | 14 actions across CRITICAL + HIGH chains |
| Architecture docs | DONE | embabel-architecture.md + README updated |
| Dashboard GOAP diagram | DONE | SVG flow diagram in AgentStatus.tsx |
| Build + deploy + verify | **TODO** | Need to build orchestrator and test all 5 branch paths |
| Embabel capability verification | **TODO** | Sealed interface routing, multi-action same output type |

---

## GOAP Chain Design

```
CriticalAnomalyInput
    → diagnoseAnomaly → FaultDiagnosis
    → [BRANCH 1: Urgency]
        IMMEDIATE → emergencyShutdown → ShutdownConfirmation → assessParts
        DEFERRABLE → assessParts (directly)
    → assessParts → PartsAssessment
    → [BRANCH 2: Stock Availability]
        PartsAvailable → scheduleWithLocalParts
        PartsUnavailable → procureCrossFacility → scheduleWithProcuredParts
    → scheduleMaintenance → MaintenanceOrder
    → [BRANCH 3: Regulatory Compliance]
        REGULATED → verifyCompliance → ComplianceVerification → finalizeResponse
        UNREGULATED → finalizeResponse (directly)
    → finalizeResponse → CriticalAnomalyResponse
```

**Branch paths and tool coverage:**

| Path | Branch Decisions | Tool Groups Used | LLM Calls |
|------|-----------------|------------------|-----------|
| Shortest | DEFERRABLE → LOCAL_PARTS → UNREGULATED | maintenance, inventory | 3 |
| Emergency | IMMEDIATE → LOCAL_PARTS → UNREGULATED | maintenance, sensor, inventory | 4 |
| Cross-facility | DEFERRABLE → CROSS_FACILITY → UNREGULATED | maintenance, inventory, logistics | 4 |
| Regulated | IMMEDIATE → LOCAL_PARTS → REGULATED | maintenance, sensor, inventory, governance | 5 |
| Longest | IMMEDIATE → CROSS_FACILITY → REGULATED | maintenance, sensor, inventory, logistics, governance | 6 |

---

## Files Modified

| File | Change | Status |
|------|--------|--------|
| `titan-orchestrator/.../model/GoapTypes.java` | New — all intermediate types | DONE |
| `titan-orchestrator/.../agent/TitanAnomalyAgent.java` | Rewritten — 14 discrete actions replacing 2 monolithic | DONE |
| `titan-dashboard/src/components/AgentStatus.tsx` | Added GOAP flow diagram SVG | DONE |
| `docs/embabel-architecture.md` | Updated with multi-step chain, branch paths, action table | DONE |
| `README.md` | Added GOAP chain mermaid diagram, anomaly response section | DONE |

---

## Remaining Work

### 1. Build and Verify GOAP Chain

- [ ] Build orchestrator: `mvn package -pl titan-orchestrator -am -DskipTests`
- [ ] Deploy: `docker compose build --no-cache titan-orchestrator && docker compose up -d titan-orchestrator`
- [ ] Verify GOAP planner discovers the action chain (check startup logs)
- [ ] Test sealed interface routing — does Embabel route based on `PartsAvailable` vs `PartsUnavailable`?
- [ ] If sealed interfaces don't work, switch all branches to wrapper-record pattern (like Branch 3)

### 2. Test All 5 Branch Paths

- [ ] Path 1: CRITICAL + IMMEDIATE + LOCAL_PARTS + UNREGULATED (e.g., ATL bearing fault)
- [ ] Path 2: CRITICAL + DEFERRABLE + CROSS_FACILITY + UNREGULATED (e.g., PHX motor fault)
- [ ] Path 3: CRITICAL + IMMEDIATE + LOCAL_PARTS + REGULATED (e.g., TYO spindle fault)
- [ ] Path 4: HIGH anomaly → verify diagnosis + parts assessment → recommendation created
- [ ] Path 5: Approve HIGH recommendation → verify full CRITICAL chain triggers

### 3. Verify Listener and Controller Integration

- [ ] `AnomalyEventListener.handleCritical()` — goal type unchanged, should work
- [ ] `AnomalyEventListener.handleHigh()` — goal type unchanged, should work
- [ ] `RecommendationController.approveRecommendation()` — approval triggers CRITICAL chain
- [ ] `AutomatedActionService.record()` — verify response fields match
- [ ] `NotificationService.sendMaintenanceAlert()` — still fires deterministically post-agent

### 4. Sensor Data Generator Updates

- [ ] Generator currently simulates CNC-MILL and CNC-LATHE only
- [ ] Consider adding equipment IDs with TYO/MUN prefixes to trigger regulated path
- [ ] Consider adding scenarios that trigger cross-facility procurement (low stock at one facility)

### 5. Dashboard Enhancements

- [ ] Show which branch path was taken in anomaly response details
- [ ] Display compliance verification status for regulated equipment
- [ ] Show cross-facility shipment tracking when procurement is triggered
- [ ] Add emergency shutdown indicator in equipment health view

---

## Risks

1. **Sealed interface support**: Embabel may not handle `sealed interface PartsAssessment` in the type graph. Fallback: use wrapper records for all branches (currently Branch 3 uses this pattern).

2. **Chain depth latency**: Longest path is 8 actions / 6 LLM calls (~30-60s). Acceptable for demo.

3. **Blackboard accumulation**: Actions with multiple parameters (e.g., `procureCrossFacility(FaultDiagnosis, PartsUnavailable)`) need both types on the blackboard. Verify GOAP handles multi-parameter preconditions.

4. **Action reuse across goals**: `diagnoseAnomaly`, `assessUrgency`, `assessParts` are shared between CRITICAL and HIGH paths. Verify the planner can reuse actions across different goal types.
