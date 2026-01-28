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
    │                    CriticalResponseWorkflow
    │                    1. Map cause → parts
    │                    2. Reserve inventory
    │                    3. Schedule maintenance (EMERGENCY)
    │                    4. Notify plant manager
    │                    5. Record automated_action
    │
    └── HIGH detected → publish "titan.anomaly" / "anomaly.high"
                               │
                               ▼
                     titan-orchestrator
                     AnomalyEventListener
                               │
                               ▼
                     RecommendationService
                     1. Map cause → parts
                     2. Reserve inventory (48h expiry)
                     3. Create recommendation (PENDING)
                               │
                               ▼
                     Dashboard shows "Approve" / "Dismiss"
                               │
                     ┌─────────┴─────────┐
                     ▼                   ▼
                 Approve              Dismiss
                     │                   │
                     ▼                   ▼
            Execute Steps 3-5     Release reservation
            from CRITICAL flow    Status → DISMISSED
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
| Event Listener | `titan-orchestrator/.../AnomalyEventListener.java` | Consumes anomaly events |
| Recommendation Service | `titan-orchestrator/.../RecommendationService.java` | CRUD for recommendations |
| Automated Action Service | `titan-orchestrator/.../AutomatedActionService.java` | Records CRITICAL actions |
| Workflow Executor | `titan-orchestrator/.../CriticalResponseWorkflow.java` | Orchestrates 5-step flow |
| Database Schema | `greenplum-init/init.sql` | 2 new tables |
| API Controller | `titan-orchestrator/.../RecommendationController.java` | REST endpoints |
| Dashboard Component | `titan-dashboard/.../Recommendations.tsx` | UI for recommendations |

## Escalation Handling

When equipment escalates from HIGH → CRITICAL:
1. CRITICAL event fires (different routing key)
2. CRITICAL handler cancels pending HIGH recommendation (status → SUPERSEDED)
3. CRITICAL workflow executes (parts may already be reserved from HIGH)
