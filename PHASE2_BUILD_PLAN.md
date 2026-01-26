# Titan Manufacturing 5.0 - Phase 2: Real-Time Data Integration

## Overview

Phase 2 focuses on making all 7 MCP agents work with dynamically generated, real-time data that simulates actual manufacturing operations. This creates a truly interactive demo where events flow through the system and agents respond to changing conditions.

**Goal**: Create an end-to-end data simulation pipeline where:
- Sensor data flows continuously (already working)
- Orders are generated and flow through fulfillment
- Inventory levels change dynamically
- Shipments progress through delivery stages
- Customer inquiries are generated based on events
- All agents can be triggered by real-world-like scenarios

---

## Agent Data Dependency Analysis

| Agent | Current State | Real-Time Ready? | Work Needed |
|-------|--------------|------------------|-------------|
| **Sensor** | MQTT @ 5s intervals | YES | Minor: Dashboard integration |
| **Maintenance** | ML predictions on sensor trends | PARTIAL | Wire anomaly → work order flow |
| **Inventory** | Static seeded data | NO | Stock lifecycle, order deductions |
| **Logistics** | Creates records, no updates | PARTIAL | Shipment status progression |
| **Order** | Manual orders only | NO | Order generation, fulfillment automation |
| **Communications** | Static templates | NO | Event-driven notifications |
| **Governance** | Pure audit (read-only) | YES | No changes needed |

---

## Increment Checklist

- [ ] **Inc 8: Enhanced Sensor Pipeline** - Dashboard ↔ Generator integration
- [ ] **Inc 9: Dynamic Order Generation** - Synthetic orders flowing through system
- [ ] **Inc 10: Inventory Lifecycle** - Stock deductions and replenishment
- [ ] **Inc 11: Logistics Simulation** - Shipment status progression
- [ ] **Inc 12: Event-Driven Communications** - Automated notifications
- [ ] **Inc 13: Data Generation Control Panel** - Dashboard UI for scenario control

---

## Increment 8: Enhanced Sensor Pipeline

### Objective
Connect the dashboard to the sensor data generator for real-time visualization and scenario control.

### Components
- Dashboard WebSocket/SSE connection to sensor data
- Generator REST API enhancements for scenario control
- Real-time sensor charts with live updates

### Deliverables Checklist

- [ ] WebSocket endpoint in sensor-mcp-server for live sensor streaming
- [ ] Dashboard SensorMonitor component uses real API instead of mock data
- [ ] Generator control API exposed through orchestrator
- [ ] Phoenix Incident trigger button in dashboard
- [ ] Equipment degradation pattern selector UI

### API Endpoints Needed

```
GET  /api/sensors/stream                    # SSE stream of sensor readings
POST /api/generator/equipment/{id}/pattern  # Set degradation pattern
POST /api/generator/scenarios/phoenix       # Trigger Phoenix Incident
GET  /api/generator/status                  # Generator health and equipment list
```

### Demo Capability
- Watch sensor values update in real-time on dashboard
- Click "Trigger Phoenix Incident" and see PHX-CNC-007 degrade
- Maintenance agent automatically detects and predicts failure

---

## Increment 9: Dynamic Order Generation

### Objective
Generate synthetic customer orders that flow through the fulfillment pipeline.

### Components
- **order-generator** service (new or add to existing generator)
- Order arrival patterns (Poisson process for realism)
- Customer-product affinity mapping

### Deliverables Checklist

- [ ] Order generator service with configurable arrival rate
- [ ] Customer purchase patterns based on division (Boeing buys AERO, Tesla buys MOBILITY)
- [ ] Order validation automatically triggers on creation
- [ ] Order events table populated with lifecycle events
- [ ] Dashboard OrderTracker shows live order flow

### Order Generation Patterns

```yaml
order-generator:
  enabled: true
  interval-ms: 30000           # New order every 30 seconds
  rush-order-probability: 0.1  # 10% are expedites
  customers:
    - id: BOEING
      products: [SKU-TB-%, SKU-EH-%]  # Turbine blades, engine housings
      avg-quantity: 100-500
    - id: TESLA
      products: [SKU-MH-%, SKU-BE-%]  # Motor housings, battery enclosures
      avg-quantity: 200-1000
```

### Database Updates

```sql
-- Trigger function for order creation
CREATE OR REPLACE FUNCTION on_order_created()
RETURNS TRIGGER AS $$
BEGIN
  INSERT INTO order_events (order_id, event_type, event_data, created_by)
  VALUES (NEW.order_id, 'ORDER_CREATED',
          jsonb_build_object('customer_id', NEW.customer_id, 'total', NEW.total_amount),
          'ORDER-GENERATOR');
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;
```

---

## Increment 10: Inventory Lifecycle

### Objective
Make inventory levels dynamic - orders reduce stock, purchase orders replenish it.

### Components
- Stock deduction on order fulfillment
- Reorder point triggers
- Purchase order generation and arrival

### Deliverables Checklist

- [ ] Order fulfillment deducts from stock_levels
- [ ] Low stock alerts when below reorder_point
- [ ] Purchase order generation (simulated supplier orders)
- [ ] Stock replenishment after lead_time_days
- [ ] Dashboard inventory panel shows real levels

### Inventory Flow

```
Order Created
    ↓
Order Validated (stock reserved)
    ↓
Fulfillment Initiated (stock committed)
    ↓
Shipment Created (stock deducted)
    ↓
[If stock < reorder_point]
    ↓
Purchase Order Generated
    ↓
[After lead_time_days]
    ↓
Stock Replenished
```

### Database Updates

```sql
-- Stock reservation table for pending orders
CREATE TABLE stock_reservations (
  reservation_id SERIAL PRIMARY KEY,
  order_id VARCHAR(50) REFERENCES orders(order_id),
  sku VARCHAR(50),
  facility_id VARCHAR(10),
  quantity INTEGER,
  reserved_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  expires_at TIMESTAMP,
  status VARCHAR(20) DEFAULT 'RESERVED'  -- RESERVED, COMMITTED, RELEASED
);

-- Purchase orders table
CREATE TABLE purchase_orders (
  po_id VARCHAR(50) PRIMARY KEY,
  supplier_id VARCHAR(50) REFERENCES suppliers(supplier_id),
  sku VARCHAR(50),
  facility_id VARCHAR(10),
  quantity INTEGER,
  order_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  expected_delivery TIMESTAMP,
  status VARCHAR(20) DEFAULT 'PENDING'  -- PENDING, IN_TRANSIT, RECEIVED
);
```

---

## Increment 11: Logistics Simulation

### Objective
Shipments progress through realistic delivery stages over time.

### Components
- Shipment status state machine
- Delivery time simulation based on carrier/distance
- Tracking event generation

### Deliverables Checklist

- [ ] Shipment status progression (CREATED → PICKED_UP → IN_TRANSIT → OUT_FOR_DELIVERY → DELIVERED)
- [ ] Tracking events table with timestamps
- [ ] Simulated delays and exceptions (weather, customs)
- [ ] Delivery confirmation triggers order completion
- [ ] Dashboard shipment tracking map

### Shipment State Machine

```
CREATED (t=0)
    ↓ [+2-4 hours]
LABEL_PRINTED
    ↓ [+4-8 hours]
PICKED_UP
    ↓ [+1-3 days based on service]
IN_TRANSIT
    ↓ [80% proceed, 20% delay]
        ├─→ DELAYED (weather/customs) → IN_TRANSIT
        └─→ OUT_FOR_DELIVERY
    ↓ [+4-8 hours]
DELIVERED
    ↓
[Trigger: Communications Agent sends confirmation]
```

### Database Updates

```sql
-- Tracking events table
CREATE TABLE shipment_events (
  event_id SERIAL PRIMARY KEY,
  shipment_id VARCHAR(50) REFERENCES shipments(shipment_id),
  event_type VARCHAR(50),
  location VARCHAR(100),
  description TEXT,
  event_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Add status history to shipments
ALTER TABLE shipments ADD COLUMN current_location VARCHAR(100);
ALTER TABLE shipments ADD COLUMN last_event_time TIMESTAMP;
```

---

## Increment 12: Event-Driven Communications

### Objective
Automatically generate customer notifications and simulate customer inquiries.

### Components
- Event listener for order/shipment milestones
- Automated email notification generation
- Customer inquiry simulation

### Deliverables Checklist

- [ ] Order confirmation email on ORDER_CREATED
- [ ] Shipment notification on PICKED_UP
- [ ] Delivery confirmation on DELIVERED
- [ ] Delay notification on shipment DELAYED
- [ ] Simulated customer inquiries (where's my order?, quality complaints)
- [ ] Dashboard communications log

### Event Triggers

| Event | Communication |
|-------|--------------|
| ORDER_CREATED | Order confirmation email |
| ORDER_VALIDATED | Contract terms reminder (if special) |
| SHIPMENT_CREATED | Tracking number notification |
| PICKED_UP | Shipment in transit alert |
| DELAYED | Delay apology + new ETA |
| DELIVERED | Delivery confirmation + feedback request |
| ANOMALY_DETECTED | Internal maintenance alert |

### Inquiry Generation Patterns

```yaml
inquiry-generator:
  enabled: true
  interval-ms: 60000  # Check every minute
  triggers:
    - condition: shipment.status = 'IN_TRANSIT' AND age > 3 days
      inquiry-type: STATUS
      probability: 0.3
    - condition: shipment.status = 'DELAYED'
      inquiry-type: EXPEDITE
      probability: 0.5
    - condition: order.delivered AND random < 0.05
      inquiry-type: QUALITY
      probability: 0.05
```

---

## Increment 13: Data Generation Control Panel

### Objective
Dashboard UI for controlling all data generation scenarios.

### Components
- New dashboard page: "Simulation Control"
- Equipment pattern controls
- Order generation controls
- Scenario triggers
- Data reset capabilities

### Deliverables Checklist

- [ ] SimulationControl.tsx dashboard component
- [ ] Equipment grid with pattern dropdown per machine
- [ ] Order generation rate slider
- [ ] Scenario buttons: Phoenix Incident, Boeing Expedite, Supply Crisis, FAA Audit
- [ ] Data injection controls (specific anomalies, orders, inquiries)
- [ ] Reset buttons (clear generated data, reset to baseline)
- [ ] Live event log showing system activity

### UI Wireframe

```
┌─────────────────────────────────────────────────────────────────────┐
│  SIMULATION CONTROL                                    [● RUNNING]  │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  ┌─── EQUIPMENT PATTERNS ───────────────────────────────────────┐  │
│  │ PHX-CNC-007  [BEARING_DEGRADATION ▼]  [Reset]               │  │
│  │ PHX-CNC-012  [NORMAL ▼]              [Inject Fault]         │  │
│  │ MUC-CNC-001  [MOTOR_BURNOUT ▼]       [Reset]               │  │
│  │ ... (scrollable list)                                        │  │
│  └──────────────────────────────────────────────────────────────┘  │
│                                                                     │
│  ┌─── DATA GENERATION ──────────────────────────────────────────┐  │
│  │ Sensor Data     [●] Enabled    Interval: [5s ▼]             │  │
│  │ Order Generator [○] Disabled   Rate: [1/min ▼]              │  │
│  │ Logistics Sim   [○] Disabled   Speed: [1x ▼]                │  │
│  │ Inquiry Gen     [○] Disabled   Rate: [low ▼]                │  │
│  └──────────────────────────────────────────────────────────────┘  │
│                                                                     │
│  ┌─── SCENARIO TRIGGERS ────────────────────────────────────────┐  │
│  │ [▶ Phoenix Incident]  [▶ Boeing Expedite]                   │  │
│  │ [▶ Supply Crisis]     [▶ FAA Audit]                         │  │
│  │ [▶ Full Demo Mode]    [⟲ Reset All Data]                    │  │
│  └──────────────────────────────────────────────────────────────┘  │
│                                                                     │
│  ┌─── LIVE EVENT LOG ───────────────────────────────────────────┐  │
│  │ 10:32:45  SENSOR     PHX-CNC-007 vibration=4.2mm/s WARNING  │  │
│  │ 10:32:40  ORDER      TM-2024-45893 created for Boeing       │  │
│  │ 10:32:35  SHIPMENT   SHIP-2024-002 picked up by FedEx       │  │
│  │ 10:32:30  ANOMALY    PHX-CNC-007 bearing degradation HIGH   │  │
│  │ ... (auto-scrolling)                                         │  │
│  └──────────────────────────────────────────────────────────────┘  │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

---

## Technical Architecture - Phase 2

```
                         ┌─────────────────────────┐
                         │   Titan Dashboard       │
                         │   + SimulationControl   │
                         └───────────┬─────────────┘
                                     │
                         ┌───────────▼─────────────┐
                         │  Titan Orchestrator     │
                         │  + Event Router         │
                         └───────────┬─────────────┘
                                     │
    ┌────────────────────────────────┼────────────────────────────────┐
    │        │        │        │        │        │        │           │
┌───▼───┐┌───▼───┐┌───▼───┐┌───▼───┐┌───▼───┐┌───▼───┐┌───▼───┐
│Sensor ││ Maint ││Invent ││Logist ││ Order ││ Comms ││Govern │
│ +SSE  ││+WrkOrd││+Stock ││+Track ││+Gen   ││+Events││(same) │
└───┬───┘└───┬───┘└───┬───┘└───┬───┘└───┬───┘└───┬───┘└───┬───┘
    │        │        │        │        │        │        │
    └────────┴────────┴────────┼────────┴────────┴────────┘
                               │
             ┌─────────────────┼─────────────────┐
             │                 │                 │
      ┌──────▼──────┐   ┌──────▼──────────────────────┐
      │  Greenplum  │   │        RabbitMQ             │
      │  + Triggers │   │  + Event Exchanges          │
      │  + PG_Notify│   │  + Topic Routing            │
      └─────────────┘   └─────────────────────────────┘
                               │
                    ┌──────────┴──────────┐
                    │                     │
             ┌──────▼──────┐       ┌──────▼──────┐
             │   Sensor    │       │   Order     │
             │  Generator  │       │  Generator  │
             │  (existing) │       │   (new)     │
             └─────────────┘       └─────────────┘
```

---

## Event Flow Architecture

### RabbitMQ Exchanges (New)

```
titan.events (topic exchange)
    ├── sensor.reading.#        → Sensor MCP Server
    ├── sensor.anomaly.#        → Maintenance MCP Server
    ├── order.created           → Inventory MCP Server (reserve stock)
    ├── order.fulfilled         → Logistics MCP Server (create shipment)
    ├── shipment.#              → Communications MCP Server (notifications)
    ├── inventory.low           → Communications MCP Server (alerts)
    └── *.* (all)               → Dashboard (live event log)
```

### PostgreSQL Triggers (New)

```sql
-- Trigger for anomaly detection → notify maintenance
CREATE TRIGGER anomaly_notify
AFTER INSERT ON anomalies
FOR EACH ROW EXECUTE FUNCTION pg_notify('maintenance_alert', NEW.equipment_id);

-- Trigger for low stock → notify procurement
CREATE TRIGGER low_stock_alert
AFTER UPDATE ON stock_levels
FOR EACH ROW
WHEN (NEW.quantity < NEW.reorder_point AND OLD.quantity >= OLD.reorder_point)
EXECUTE FUNCTION pg_notify('inventory_alert', NEW.sku);
```

---

## Demo Scenarios - Enhanced

### Phoenix Incident (Full Flow)
1. User triggers scenario in Simulation Control
2. Generator sets PHX-CNC-007 to BEARING_DEGRADATION
3. Sensor readings show vibration trending up
4. Maintenance agent detects anomaly, predicts 73% failure
5. **NEW**: Work order automatically created
6. **NEW**: Inventory agent checks bearing stock
7. **NEW**: If low, purchase order generated
8. **NEW**: Communications agent notifies plant manager

### Boeing Expedite (Full Flow)
1. Order generator creates urgent Boeing order
2. Order agent validates contract and compliance
3. Inventory agent reserves stock across facilities
4. **NEW**: Stock levels visibly decrease in dashboard
5. Logistics agent creates split shipments
6. **NEW**: Tracking events update in real-time
7. **NEW**: Communications sends ETA confirmation
8. **NEW**: Upon delivery, feedback request sent

### Supply Chain Crisis (Full Flow)
1. User injects supplier delay for NipponBearing
2. **NEW**: Pending POs marked as DELAYED
3. Inventory agent finds alternatives
4. **NEW**: Dashboard shows supply chain impact visualization
5. **NEW**: Affected orders automatically rerouted
6. Communications notifies affected customers

---

## Progress Tracking

- [ ] Review Phase 2 plan
- [ ] Increment 8 implementation
- [ ] Increment 9 implementation
- [ ] Increment 10 implementation
- [ ] Increment 11 implementation
- [ ] Increment 12 implementation
- [ ] Increment 13 implementation

---

## Technology Additions

| Component | Technology | Purpose |
|-----------|------------|---------|
| Event Streaming | RabbitMQ Topic Exchange | Cross-agent event routing |
| Real-time UI | Server-Sent Events (SSE) | Dashboard live updates |
| DB Notifications | PostgreSQL NOTIFY/LISTEN | Trigger-based alerts |
| State Machines | Spring State Machine | Shipment/Order lifecycles |
| Scheduling | Spring @Scheduled | Periodic data generation |

---

## Success Criteria

1. **Real-Time Visibility**: Dashboard shows live sensor data, orders, shipments
2. **End-to-End Flow**: Order creation → fulfillment → shipment → delivery → notification
3. **Predictive Response**: Anomaly detection → automatic work order creation
4. **Interactive Control**: Users can trigger any scenario from dashboard
5. **Audit Trail**: Every event logged and traceable through Governance agent
6. **Resilience**: System handles data bursts, failures gracefully

---

<p align="center">
  <b>Titan Manufacturing 5.0 - Phase 2</b><br>
  <i>"Making the demo come alive"</i>
</p>
