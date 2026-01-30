# Anomaly Response Orchestration Design

## Overview

Event-driven orchestration that automatically responds when ML predictions detect HIGH or CRITICAL risk levels. Part of Phase 2: Real-Time Data Integration.

## Behavior

| Risk Level | Trigger | Response |
|------------|---------|----------|
| **CRITICAL** (≥70%) | Auto-execute | Reserve parts → Schedule emergency maintenance → Notify manager |
| **HIGH** (≥50%) | Recommendation | Reserve parts → Create recommendation → Await human approval |
| MEDIUM/LOW | None | Continue monitoring |

## Event Flow Architecture

```
GemFireScoringService (every 30s)
    │
    ├── CRITICAL detected → publish "titan.anomaly" / "anomaly.critical"
    │                              │
    │                              ▼
    │                    titan-orchestrator
    │                    AnomalyEventListener
    │                              │
    │                              ▼
    │                    AgentInvocation.create(agentPlatform, CriticalAnomalyResponse.class)
    │                              │
    │                              ▼
    │                    Embabel GOAP Planner
    │                    (Dynamically decides which tools to call based on goal)
    │                    Available toolGroups: maintenance-tools, inventory-tools, communications-tools
    │                              │
    │                              ▼
    │                    Record result in automated_actions table
    │
    └── HIGH detected → publish "titan.anomaly" / "anomaly.high"
                               │
                               ▼
                     titan-orchestrator
                     AnomalyEventListener
                               │
                               ▼
                     AgentInvocation.create(agentPlatform, HighAnomalyResponse.class)
                               │
                               ▼
                     Embabel GOAP reserves parts, creates recommendation (PENDING)
                               │
                               ▼
                     Dashboard shows "Approve" / "Dismiss"
                               │
                     ┌─────────┴─────────┐
                     ▼                   ▼
                 Approve              Dismiss
                     │                   │
                     ▼                   ▼
         AgentInvocation for      Release reservation
         full maintenance goal    Status → DISMISSED
```

## Event Payload

```json
{
  "eventId": "evt-1706450400-PHX-CNC-007",
  "eventType": "ANOMALY_CRITICAL",
  "timestamp": "2026-01-28T15:00:00Z",
  "equipmentId": "PHX-CNC-007",
  "facilityId": "PHX",
  "prediction": {
    "failureProbability": 0.78,
    "riskLevel": "CRITICAL",
    "probableCause": "Bearing degradation — vibration at 4.8 mm/s with rising trend",
    "vibrationAvg": 4.8,
    "temperatureAvg": 68.5,
    "scoredAt": "2026-01-28T15:00:00Z"
  }
}
```

## Deduplication

Track published alerts to avoid duplicate events on each 30s scoring cycle:

```java
private final Set<String> publishedAlerts = ConcurrentHashMap.newKeySet();

// Key format: "equipmentId:riskLevel"
// Only publish once per equipment per risk level
// Clear when equipment recovers to MEDIUM/LOW
// HIGH → CRITICAL escalation: both fire (different keys), CRITICAL supersedes HIGH recommendation
```

## Embabel Goal-Driven Implementation

Following Embabel's GOAP (Goal Oriented Action Planning) pattern, we define goals and let the planner decide which tools to invoke.

### Goal Types

```java
// CRITICAL response - full automated workflow
public record CriticalAnomalyResponse(
    String equipmentId,
    String workOrderId,
    List<ReservedPart> partsReserved,
    boolean notificationSent,
    String summary
) {}

// HIGH response - recommendation with parts reserved
public record HighAnomalyResponse(
    String equipmentId,
    String recommendationId,
    List<ReservedPart> partsReserved,
    String recommendedAction,
    String summary
) {}
```

### Goal-Achieving Actions

```java
@Agent(description = "Titan Anomaly Response Agent - Responds to equipment anomalies by coordinating " +
                     "maintenance scheduling, parts reservation, and personnel notification")
@Component
public class TitanAnomalyAgent {

    @AchievesGoal(description = "Prevent imminent equipment failure by reserving parts, " +
                                "scheduling emergency maintenance, and notifying personnel")
    @Action(
        description = "Respond to critical equipment anomaly to prevent failure",
        toolGroups = {"maintenance-tools", "inventory-tools", "communications-tools"}
    )
    public CriticalAnomalyResponse handleCriticalAnomaly(AnomalyEvent event, Ai ai) {
        return ai.withAutoLlm().createObject(
            """
            CRITICAL ALERT: Equipment %s at %s facility has %.0f%% failure probability.
            Probable cause: %s

            You must prevent this equipment failure. Use the available tools to:
            1. Determine what parts are needed based on the probable cause
            2. Check inventory and reserve the required parts
            3. Schedule emergency maintenance with type EMERGENCY
            4. Notify the plant manager about the scheduled maintenance

            Take all necessary actions now and report what was done.
            """.formatted(
                event.equipmentId(),
                event.facilityId(),
                event.prediction().failureProbability() * 100,
                event.prediction().probableCause()
            ),
            CriticalAnomalyResponse.class
        );
    }

    @AchievesGoal(description = "Prepare for potential equipment failure by reserving parts " +
                                "and creating a maintenance recommendation for human approval")
    @Action(
        description = "Respond to high-risk equipment anomaly with recommendation",
        toolGroups = {"maintenance-tools", "inventory-tools"}
    )
    public HighAnomalyResponse handleHighAnomaly(AnomalyEvent event, Ai ai) {
        return ai.withAutoLlm().createObject(
            """
            HIGH RISK ALERT: Equipment %s at %s facility has %.0f%% failure probability.
            Probable cause: %s

            Prepare for potential maintenance. Use the available tools to:
            1. Determine what parts are needed based on the probable cause
            2. Check inventory and reserve the required parts (48 hour hold)
            3. DO NOT schedule maintenance yet - this requires human approval

            Report what parts were reserved and recommend the maintenance action.
            """.formatted(
                event.equipmentId(),
                event.facilityId(),
                event.prediction().failureProbability() * 100,
                event.prediction().probableCause()
            ),
            HighAnomalyResponse.class
        );
    }
}
```

### Event Listener Triggering Goals

```java
@Component
public class AnomalyEventListener {

    private final AgentPlatform agentPlatform;
    private final RecommendationService recommendationService;
    private final AutomatedActionService automatedActionService;

    @RabbitListener(queues = "orchestrator.critical")
    public void handleCritical(AnomalyEvent event) {
        log.info("CRITICAL anomaly received for {}", event.equipmentId());

        // Cancel any pending HIGH recommendation (superseded)
        recommendationService.cancelPending(event.equipmentId(), "Superseded by CRITICAL alert");

        // Invoke Embabel goal - GOAP decides which tools to call
        var invocation = AgentInvocation.create(agentPlatform, CriticalAnomalyResponse.class);
        CriticalAnomalyResponse result = invocation.invoke(event);

        // Record the automated action
        automatedActionService.record(event, result);

        log.info("CRITICAL response complete: WO={}, parts={}",
                 result.workOrderId(), result.partsReserved());
    }

    @RabbitListener(queues = "orchestrator.high")
    public void handleHigh(AnomalyEvent event) {
        log.info("HIGH anomaly received for {}", event.equipmentId());

        // Invoke Embabel goal - GOAP decides which tools to call
        var invocation = AgentInvocation.create(agentPlatform, HighAnomalyResponse.class);
        HighAnomalyResponse result = invocation.invoke(event);

        // Create recommendation record for dashboard
        recommendationService.create(event, result);

        log.info("HIGH response complete: recommendation={}, parts reserved={}",
                 result.recommendationId(), result.partsReserved());
    }
}
```

## Cause → Parts Mapping

| probableCause contains | Required SKU | Part Name |
|------------------------|--------------|-----------|
| "Bearing" | INDL-BRG-7420 | Spindle Bearing SKF-7420 |
| "Motor" | INDL-MOT-5500 | CNC Motor Assembly |
| "Coolant" | INDL-PMP-2200 | Coolant Pump |
| "Electrical" | INDL-CTR-1100 | Motor Controller |
| "Spindle" | INDL-SPD-3300 | Spindle Assembly |

## Database Schema

```sql
-- Stores HIGH recommendations awaiting approval
CREATE TABLE maintenance_recommendations (
    recommendation_id VARCHAR(50) PRIMARY KEY,
    equipment_id VARCHAR(50) NOT NULL,
    facility_id VARCHAR(10) NOT NULL,
    risk_level VARCHAR(20) NOT NULL,
    failure_probability DECIMAL(5,4),
    probable_cause TEXT,
    recommended_action TEXT,
    recommended_parts JSONB,
    reservation_id VARCHAR(50),
    estimated_cost DECIMAL(10,2),
    status VARCHAR(20) DEFAULT 'PENDING',  -- PENDING, APPROVED, DISMISSED, SUPERSEDED, EXPIRED
    created_at TIMESTAMP DEFAULT NOW(),
    expires_at TIMESTAMP,
    approved_at TIMESTAMP,
    approved_by VARCHAR(100),
    work_order_id VARCHAR(50),
    notes TEXT
);

-- Tracks CRITICAL auto-actions for audit/dashboard
CREATE TABLE automated_actions (
    action_id VARCHAR(50) PRIMARY KEY,
    event_id VARCHAR(100) NOT NULL,
    equipment_id VARCHAR(50) NOT NULL,
    facility_id VARCHAR(10) NOT NULL,
    action_type VARCHAR(50),
    risk_level VARCHAR(20),
    failure_probability DECIMAL(5,4),
    probable_cause TEXT,
    work_order_id VARCHAR(50),
    parts_reserved JSONB,
    notification_sent BOOLEAN DEFAULT FALSE,
    status VARCHAR(20) DEFAULT 'COMPLETED',
    executed_at TIMESTAMP DEFAULT NOW()
);
```

## API Endpoints

| Method | Endpoint | Purpose |
|--------|----------|---------|
| GET | `/api/recommendations` | List pending recommendations |
| GET | `/api/recommendations/{id}` | Get single recommendation |
| POST | `/api/recommendations/{id}/approve` | Approve → schedule maintenance |
| POST | `/api/recommendations/{id}/dismiss` | Dismiss → release reservation |
| GET | `/api/automated-actions` | List recent auto-actions |

## Implementation Components

| Component | Location | Description |
|-----------|----------|-------------|
| RabbitMQ Publisher | `maintenance-mcp-server/.../GemFireScoringService.java` | Publish on HIGH/CRITICAL |
| RabbitMQ Config | `maintenance-mcp-server/.../RabbitConfig.java` | Exchange, queues, bindings |
| Anomaly Agent | `titan-orchestrator/.../agent/TitanAnomalyAgent.java` | @AchievesGoal actions for CRITICAL/HIGH |
| Event Listener | `titan-orchestrator/.../AnomalyEventListener.java` | RabbitMQ → AgentInvocation |
| Recommendation Service | `titan-orchestrator/.../RecommendationService.java` | CRUD for recommendations |
| Automated Action Service | `titan-orchestrator/.../AutomatedActionService.java` | Records CRITICAL actions |
| Database Schema | `greenplum-init/init.sql` | 2 new tables |
| API Controller | `titan-orchestrator/.../RecommendationController.java` | REST endpoints |
| Dashboard Component | `titan-dashboard/.../Recommendations.tsx` | UI for recommendations |

## Escalation Handling

When equipment escalates from HIGH → CRITICAL:
1. CRITICAL event fires (different routing key)
2. CRITICAL handler cancels pending HIGH recommendation (status → SUPERSEDED)
3. CRITICAL workflow executes (parts may already be reserved from HIGH)
