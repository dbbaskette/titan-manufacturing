# Titan Manufacturing 5.0 - Incremental Build Plan

## Overview

This document outlines an incremental approach to building the Titan Manufacturing demo. Each increment delivers a working, demoable component that builds upon previous work.

**Goal**: Create a multi-agent AI platform demonstrating Tanzu Data Intelligence capabilities for manufacturing operations.

**Key Technologies**:
- **Tanzu Products**: Greenplum 7 (pgvector) - single unified data store, RabbitMQ
- **Open Source**: OpenMetadata, Mosquitto MQTT
- **Orchestrator**: [Embabel Agent Framework](https://github.com/embabel/embabel-agent) (Rod Johnson's agentic flow framework)
- **Agent Protocol**: Model Context Protocol (MCP)

**Architecture Decision**: Consolidated to single Greenplum database (port 15432) for all data - products, equipment, sensors, orders. Simplifies deployment and demonstrates Greenplum's versatility.

---

## Increment Checklist

- [x] **Inc 1: Foundation** - Infrastructure running, data loaded
- [ ] **Inc 2: Core Orchestrator** - Basic agent coordination
- [ ] **Inc 3: Predictive Maintenance** - Phoenix CNC-007 anomaly detection
- [ ] **Inc 4: Supply Chain** - Inventory queries, semantic search
- [ ] **Inc 5: Order Fulfillment** - Boeing expedite scenario
- [ ] **Inc 6: Data Governance** - FAA audit with lineage
- [ ] **Inc 7: Dashboard & Integration** - Full multi-agent workflows

---

## Increment 1: Foundation & Data Layer

### Objective
Get all infrastructure running and loaded with realistic demo data.

### Components
- Docker Compose stack (database, message brokers, observability)
- **Greenplum** (single unified database):
  - Products catalog (500+ SKUs) with pgvector embeddings
  - Equipment registry (600+ machines across 12 facilities)
  - Sensor readings with time-series indexes
  - Orders and customers (B2B)
- RabbitMQ and Mosquitto MQTT brokers
- Grafana/Prometheus observability
- OpenMetadata for data governance

### Deliverables Checklist

- [x] Working `docker-compose up -d` with all infrastructure
- [x] Greenplum schemas created and populated with sample data
- [x] Synthetic sensor data with PHX-CNC-007 degradation pattern
- [ ] Product catalog with pgvector embeddings
- [ ] Test and verify stack startup

### Demo Capability

- [x] Greenplum running with realistic manufacturing data
- [x] Query sensor readings showing PHX-CNC-007 degradation
- [x] Query product catalog (500+ products across 4 divisions)
- [ ] RabbitMQ management console verified
- [ ] Grafana dashboards for infrastructure health

### Data Patterns (from public datasets)

- [x] AI4I 2020 Predictive Maintenance patterns (vibration/temp degradation)
- [x] DataCo SMART Supply Chain patterns (orders/inventory)
- [ ] NASA C-MAPSS patterns (RUL training data)

---

## Increment 2: Embabel Orchestrator + Sensor Agent

### Objective
Implement the central orchestrator using Embabel and first MCP agent for IoT data access.

### Components
- **titan-orchestrator** (Port 8080): [Embabel Agent Framework](https://github.com/embabel/embabel-agent)
  - Goal Oriented Action Planning (GOAP) for dynamic workflows
  - MCP Registry integration for tool discovery
  - Multi-LLM support (OpenAI, Anthropic, Ollama)
- **sensor-mcp-server** (Port 8081): IoT monitoring agent

### Deliverables Checklist

- [x] Embabel-based orchestrator with domain models for manufacturing
- [x] Sensor agent with MCP tools:
  - [x] `list_equipment` - Query 600+ CNC machines
  - [x] `get_sensor_readings` - Historical sensor data
  - [x] `get_facility_status` - Facility overview
  - [x] `detect_anomaly` - Basic anomaly detection
- [ ] MQTT subscription capability (optional real-time)

### Demo Scenarios

- [ ] "Show me all equipment in Phoenix facility"
- [ ] "What are the current sensor readings for PHX-CNC-007?"
- [ ] "Which machines have temperature above 75°C?"
- [ ] Basic anomaly detection alerts

### Dependencies

- [x] Increment 1 (infrastructure and data)

---

## Increment 3: Predictive Maintenance Agent

### Objective
Add predictive maintenance capabilities - the Phoenix incident scenario.

### Components
- **maintenance-mcp-server** (Port 8082): Predictive maintenance agent

### Deliverables Checklist

- [ ] Maintenance agent with MCP tools:
  - [ ] `predict_failure` - ML-based failure probability
  - [ ] `estimate_rul` - Remaining Useful Life estimation
  - [ ] `schedule_maintenance` - Create work orders
  - [ ] `get_maintenance_history` - Historical records
- [ ] Vibration anomaly detection model
- [ ] Temperature trend analysis
- [ ] Integration with sensor agent data

### Phoenix Incident Demo

- [ ] "Check the health status of PHX-CNC-007"
- [ ] Agent detects: Vibration trending up 0.8mm/s per hour
- [ ] Agent predicts: 73% failure probability within 48 hours
- [ ] Agent recommends: Schedule bearing replacement (SKU-BRG-7420)
- [ ] Shows how this prevents a $12M unplanned downtime

### Dependencies

- [ ] Increment 2 (orchestrator and sensor agent)

---

## Increment 4: Supply Chain Agents

### Objective
Add inventory management and logistics optimization with Greenplum/pgvector.

### Components
- **inventory-mcp-server** (Port 8083): 50K+ SKU management
- **logistics-mcp-server** (Port 8084): Shipping optimization

### Deliverables Checklist

- [ ] Inventory agent with MCP tools:
  - [ ] `check_stock` - Multi-facility inventory levels
  - [ ] `search_products` - Semantic search via pgvector
  - [ ] `find_alternative_supplier` - Crisis response
  - [ ] `reserve_inventory` - Stock allocation
  - [ ] `calculate_reorder` - Reorder point analysis
- [ ] Logistics agent with MCP tools:
  - [ ] `plan_route` - Shipping route optimization
  - [ ] `select_carrier` - FedEx/DHL/Maersk selection
  - [ ] `predict_eta` - Delivery estimation
  - [ ] `plan_split_shipment` - Multi-origin fulfillment
- [ ] pgvector embedding generation for products

### Supply Chain Crisis Demo

- [ ] "NipponBearing just declared force majeure - find alternatives"
- [ ] Semantic search finds similar bearings from SKF, Timken
- [ ] Shows stock levels across 12 facilities
- [ ] Calculates impact on maintenance schedules
- [ ] "What parts do we need to reorder at Phoenix?"
- [ ] "Find high-temperature resistant bearings for CNC machines"

**Requires:** Increment 3 (maintenance agent for cross-agent queries)

---

## Increment 5: Order Fulfillment & Communications

### Objective
Add B2B order processing with RabbitMQ and customer communications.

### Components
- **order-mcp-server** (Port 8085): B2B fulfillment with RabbitMQ
- **communications-mcp-server** (Port 8086): Customer notifications

### Deliverables Checklist

- [ ] Order agent with MCP tools:
  - [ ] `validate_order` - Contract and credit validation
  - [ ] `check_contract_terms` - Customer-specific terms
  - [ ] `initiate_fulfillment` - Start fulfillment workflow
  - [ ] `get_order_status` - Track orders
- [ ] Communications agent with MCP tools:
  - [ ] `send_notification` - Customer alerts
  - [ ] `handle_inquiry` - RAG-powered responses
  - [ ] `draft_customer_update` - Status communications
- [ ] RabbitMQ integration for order events
- [ ] RAG pipeline for customer service (pgvector)

### Boeing Expedite Demo

- [ ] "Boeing needs 500 turbine blade blanks ASAP - order TM-2024-45892"
- [ ] Order agent validates aerospace compliance
- [ ] Inventory agent finds: Phoenix 320 + Munich 400
- [ ] Logistics plans split shipment with air freight
- [ ] Communications confirms to Boeing with ETA
- [ ] Full event trail in RabbitMQ

**Requires:** Increment 4 (inventory and logistics agents)

---

## Increment 6: Data Governance & Compliance

### Objective
Add OpenMetadata integration for FAA compliance and data lineage.

### Components
- **governance-mcp-server** (Port 8087): OpenMetadata integration
- OpenMetadata catalog configuration

### Deliverables Checklist

- [ ] OpenMetadata fully configured:
  - [ ] Data source connections (Greenplum)
  - [ ] Business domains (Aerospace, Energy, Mobility, Industrial)
  - [ ] Glossary terms (RUL, SKU, MaterialBatch)
  - [ ] Classifications (PII, AerospaceCompliance)
- [ ] Governance agent with MCP tools:
  - [ ] `search_data_assets` - Catalog search
  - [ ] `get_upstream_lineage` - Trace data sources
  - [ ] `get_downstream_lineage` - Impact analysis
  - [ ] `get_column_lineage` - Field-level tracing
  - [ ] `trace_material_batch` - Batch traceability
  - [ ] `check_pii_columns` - Privacy compliance
  - [ ] `validate_aerospace_compliance` - FAA requirements

### FAA Audit Demo

- [ ] "Trace titanium batch TI-2024-0892 used in Boeing 787 landing gear"
- [ ] Shows complete upstream lineage to TIMET supplier
- [ ] Column-level lineage through QC processes
- [ ] Flags PII in operator certification records
- [ ] Generates compliance report
- [ ] "What data do we have about sensor readings?"
- [ ] "Who owns the customer order data?"

**Requires:** Increment 5 (all agents for complete lineage)

---

## Increment 7: Dashboard & Full Integration

### Objective
Build the React dashboard and demonstrate full multi-agent workflows.

### Components
- **titan-dashboard** (Port 3001): React UI

### Deliverables Checklist

- [ ] React dashboard with:
  - [ ] Facility overview map (12 facilities)
  - [ ] Real-time sensor monitoring panels
  - [ ] Equipment health status cards
  - [ ] Order fulfillment tracker
  - [ ] Natural language chat interface
- [ ] Full multi-agent workflow integration
- [ ] Demo script automation
- [ ] Grafana dashboard templates

### Full Platform Demo

- [ ] Visual dashboard showing all 12 facilities
- [ ] Click on Phoenix → see CNC-007 anomaly alert
- [ ] Chat: "What should we do about the failing bearing?"
- [ ] Watch agents coordinate: Sensor → Maintenance → Inventory → Logistics
- [ ] See order placed and tracked through fulfillment
- [ ] Pull up data lineage for any component

### Four Core Scenarios

- [ ] Phoenix Incident (Predictive Maintenance)
- [ ] Boeing Expedite (Order Fulfillment)
- [ ] FAA Audit (Data Governance)
- [ ] Supply Chain Crisis (Resilience)

**Requires:** All previous increments

---

## Technical Architecture Summary

```
                    ┌─────────────────────────┐
                    │   Titan Dashboard       │
                    │   (React - Port 3001)   │
                    └───────────┬─────────────┘
                                │
                    ┌───────────▼─────────────┐
                    │  Embabel Orchestrator   │
                    │  (GOAP - Port 8080)     │
                    └───────────┬─────────────┘
                                │ MCP Protocol
        ┌───────────────────────┼───────────────────────┐
        │           │           │           │           │
   ┌────▼────┐ ┌────▼────┐ ┌────▼────┐ ┌────▼────┐ ┌────▼────┐
   │ Sensor  │ │  Maint  │ │Inventory│ │Logistics│ │  Order  │
   │  8081   │ │  8082   │ │  8083   │ │  8084   │ │  8085   │
   └────┬────┘ └────┬────┘ └────┬────┘ └────┬────┘ └────┬────┘
        │           │           │           │           │
        └───────────┴───────────┼───────────┴───────────┘
                                │
              ┌─────────────────┼─────────────────┐
              │                 │                 │
       ┌──────▼──────┐   ┌──────▼──────┐   ┌──────▼──────┐
       │  Greenplum  │   │  RabbitMQ   │   │  Mosquitto  │
       │   15432     │   │    5672     │   │    1883     │
       │  (pgvector) │   │  (events)   │   │   (MQTT)    │
       └──────┬──────┘   └─────────────┘   └─────────────┘
              │
       ┌──────▼──────┐
       │ OpenMetadata│
       │    8585     │
       └─────────────┘

Greenplum contains: Products, Equipment, Sensors, Orders, Customers
```

---

## Progress Tracking

- [x] Review high-level plan
- [x] Increment 1 detailed planning
- [x] Increment 1 data layer implementation
- [ ] Test docker-compose startup
- [ ] Begin Increment 2 (Embabel orchestrator)

---

---

## Increment 1: Detailed Checklist

### Infrastructure

- [x] docker-compose.yml configured with consolidated Greenplum architecture
- [x] Greenplum using greenplum-sne-base image (port 15432)
- [x] RabbitMQ configured (port 5672, management 15672)
- [x] Mosquitto MQTT configured (port 1883)
- [x] OpenMetadata setup script ready
- [ ] Test `docker-compose up -d`
- [ ] Verify all services start correctly

### Greenplum Data

- [x] pgvector extension enabled
- [x] 4 divisions (AERO, ENERGY, MOBILITY, INDUSTRIAL)
- [x] 12 global facilities
- [x] 500+ products across divisions
- [x] 15 suppliers
- [x] 12 B2B customers
- [x] 600+ equipment records
- [x] Sensor readings with time-series indexes
- [x] PHX-CNC-007 degradation data (vibration trending 2.5→4.2 mm/s)
- [x] Anomaly detection records
- [x] Boeing expedite order (TM-2024-45892)
- [ ] pgvector embeddings for products (optional for Inc 1)

### Verification Commands

```bash
# Start the stack
docker-compose up -d

# Verify Greenplum/pgvector
docker exec titan-greenplum psql -U gpadmin -d titan_warehouse \
  -c "SELECT extname FROM pg_extension WHERE extname='vector';"

# Check data counts
docker exec titan-greenplum psql -U gpadmin -d titan_warehouse \
  -c "SELECT 'facilities' as table_name, COUNT(*) FROM titan_facilities
      UNION ALL SELECT 'products', COUNT(*) FROM products
      UNION ALL SELECT 'equipment', COUNT(*) FROM equipment
      UNION ALL SELECT 'anomalies', COUNT(*) FROM anomalies;"

# Show PHX-CNC-007 degradation
docker exec titan-greenplum psql -U gpadmin -d titan_warehouse \
  -c "SELECT time, value, quality_flag FROM sensor_readings
      WHERE equipment_id='PHX-CNC-007' AND sensor_type='vibration'
      ORDER BY time DESC LIMIT 10;"

# Verify RabbitMQ
curl -u titan:titan5.0 http://localhost:15672/api/overview
```

### Demo Message

"Our data infrastructure is running - Greenplum as unified data store with vector search capability, 600+ machines across 12 global facilities, and CNC-007's degradation pattern that mirrors the $12M Phoenix incident. RabbitMQ ready for event messaging. Ready for Increment 2: Embabel orchestrator."
