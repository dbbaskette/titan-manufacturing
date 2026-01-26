# Titan Manufacturing 5.0 - Incremental Build Plan

## Overview

This document outlines an incremental approach to building the Titan Manufacturing demo. Each increment delivers a working, demoable component that builds upon previous work.

**Goal**: Create a multi-agent AI platform demonstrating Tanzu Data Intelligence capabilities for manufacturing operations.

**Key Technologies**:
- **Tanzu Products**: Greenplum 7 (pgvector) - single unified data store, RabbitMQ (with MQTT plugin)
- **Open Source**: OpenMetadata
- **Orchestrator**: [Embabel Agent Framework](https://github.com/embabel/embabel-agent) (Rod Johnson's agentic flow framework)
- **Agent Protocol**: Model Context Protocol (MCP)

**Architecture Decision**: Consolidated to single Greenplum database (port 15432) for all data - products, equipment, sensors, orders. Simplifies deployment and demonstrates Greenplum's versatility.

---

## Increment Checklist

- [x] **Inc 1: Foundation** - Infrastructure running, data loaded
- [x] **Inc 2: Core Orchestrator** - Basic agent coordination
- [x] **Inc 3: Predictive Maintenance** - Phoenix CNC-007 anomaly detection + sensor data generator
- [x] **Inc 4: Supply Chain** - Inventory + Logistics agents with pgvector + OpenMetadata
- [x] **Inc 5: Order Fulfillment** - Boeing expedite scenario
- [x] **Inc 6: Data Governance** - FAA audit with lineage
- [x] **Inc 7: Dashboard & Integration** - Full multi-agent workflows

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
- RabbitMQ (with MQTT plugin for IoT sensor data)
- Grafana/Prometheus observability
- OpenMetadata for data governance

### Deliverables Checklist

- [x] Working `docker-compose up -d` with all infrastructure
- [x] Greenplum schemas created and populated with sample data
- [x] Synthetic sensor data with PHX-CNC-007 degradation pattern
- [x] Auto-initialization via greenplum-init service
- [ ] Product catalog with pgvector embeddings (optional - text search works)

### Demo Capability

- [x] Greenplum running with realistic manufacturing data
- [x] Query sensor readings showing PHX-CNC-007 degradation
- [x] Query product catalog (500+ products across 4 divisions)
- [x] RabbitMQ management console verified
- [ ] Grafana dashboards for infrastructure health (optional)

### Data Patterns (from public datasets)

- [x] AI4I 2020 Predictive Maintenance patterns (vibration/temp degradation)
- [x] DataCo SMART Supply Chain patterns (orders/inventory)
- [x] NASA C-MAPSS patterns (RUL training data in run_to_failure_data table)

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
- [x] MQTT subscription capability (SensorDataConsumer writes to Greenplum)

### Demo Scenarios

- [x] "Show me all equipment in Phoenix facility"
- [x] "What are the current sensor readings for PHX-CNC-007?"
- [x] "Which machines have temperature above 75°C?"
- [x] Basic anomaly detection alerts

### Dependencies

- [x] Increment 1 (infrastructure and data)

---

## Increment 3: Predictive Maintenance Agent

### Objective
Add predictive maintenance capabilities - the Phoenix incident scenario.

### Components
- **maintenance-mcp-server** (Port 8082): Predictive maintenance agent
- **sensor-data-generator** (Port 8090): IoT sensor simulator with degradation patterns

### Deliverables Checklist

- [x] Maintenance agent with MCP tools:
  - [x] `predict_failure` - ML-based failure probability (logistic regression in Greenplum)
  - [x] `estimate_rul` - Remaining Useful Life estimation
  - [x] `schedule_maintenance` - Create work orders
  - [x] `get_maintenance_history` - Historical records
- [x] ML model infrastructure in Greenplum:
  - [x] `ml_model_coefficients` table with logistic regression weights
  - [x] `equipment_ml_features` view for feature engineering
  - [x] `predict_equipment_failure()` SQL function
  - [x] `estimate_equipment_rul()` SQL function
  - [x] `run_to_failure_data` table (NASA C-MAPSS style)
- [x] Sensor data pipeline:
  - [x] Generator publishes to RabbitMQ via MQTT
  - [x] Consumer writes to Greenplum sensor_readings
  - [x] Degradation patterns: NORMAL, BEARING_DEGRADATION, MOTOR_BURNOUT, SPINDLE_WEAR, COOLANT_FAILURE, ELECTRICAL_FAULT

### Phoenix Incident Demo

- [x] "Check the health status of PHX-CNC-007"
- [x] Agent detects: Vibration trending up (2.5→4.2 mm/s)
- [x] Agent predicts: ~73% failure probability within 48 hours
- [x] Agent recommends: Schedule bearing replacement (SKU-BRG-7420)
- [x] Shows how this prevents a $12M unplanned downtime

### Dependencies

- [x] Increment 2 (orchestrator and sensor agent)

---

## Increment 4: Supply Chain Agents

### Objective
Add inventory management and logistics optimization with Greenplum/pgvector.

### Components
- **inventory-mcp-server** (Port 8083): 50K+ SKU management with pgvector semantic search
- **logistics-mcp-server** (Port 8084): Shipping optimization with FedEx, UPS, DHL, Maersk
- **OpenMetadata Integration**: Schema registration and data governance

### Deliverables Checklist

- [x] Inventory agent with MCP tools:
  - [x] `check_stock` - Multi-facility inventory levels
  - [x] `search_products` - Semantic search via pgvector (text search fallback when no embeddings)
  - [x] `find_alternatives` - Alternative products/suppliers for stockouts
  - [x] `calculate_reorder` - EOQ-based reorder calculations with safety stock
- [x] Logistics agent with MCP tools:
  - [x] `get_carriers` - List FedEx, UPS, DHL, Maersk carriers
  - [x] `create_shipment` - Create shipment with tracking number
  - [x] `track_shipment` - Real-time tracking status
  - [x] `estimate_shipping` - Cost/time estimates by carrier and service level
- [x] Logistics database tables:
  - [x] `carriers` - 10 carriers (FedEx Express/Ground/Freight, UPS, DHL, Maersk, XPO)
  - [x] `shipments` - Shipment records with tracking
  - [x] `shipping_rates` - Cost matrix by region, weight, service level
- [x] pgvector embedding generation script (scripts/generate_embeddings.py)
- [x] OpenMetadata schema registration (setup-titan.py extended)
- [x] Orchestrator integration:
  - [x] TitanInventoryAgent with @Action methods
  - [x] TitanLogisticsAgent with @Action methods
  - [x] ToolGroup beans for inventory-tools and logistics-tools

### Supply Chain Crisis Demo

- [x] "What's the stock level for SKU-BRG-7420 across all facilities?"
- [x] "Find alternative suppliers for NipponBearing products"
- [x] "Create a shipment for order TM-2024-45892 using FedEx Express"
- [x] "Track shipment SHIP-2024-001"
- [x] "Estimate shipping from Phoenix to Europe for 50kg"
- [x] "What parts need reorder at Phoenix?"

### Dependencies

- [x] Increment 3 (maintenance agent for cross-agent queries)

---

## Increment 5: Order Fulfillment & Communications

### Objective
Add B2B order processing with RabbitMQ and customer communications.

### Components
- **order-mcp-server** (Port 8085): B2B fulfillment with RabbitMQ
- **communications-mcp-server** (Port 8086): Customer notifications

### Deliverables Checklist

- [x] Order agent with MCP tools:
  - [x] `validate_order` - Contract and credit validation
  - [x] `check_contract_terms` - Customer-specific terms
  - [x] `initiate_fulfillment` - Start fulfillment workflow
  - [x] `get_order_status` - Track orders
- [x] Communications agent with MCP tools:
  - [x] `send_notification` - Customer alerts
  - [x] `handle_inquiry` - RAG-powered responses
  - [x] `draft_customer_update` - Status communications
- [x] Database tables for order events and customer contracts
- [x] RAG pipeline for customer service (pgvector-based inquiry similarity)

### Boeing Expedite Demo

- [x] "Boeing needs 500 turbine blade blanks ASAP - order TM-2024-45892"
- [x] Order agent validates aerospace compliance
- [x] Inventory agent finds: Phoenix 320 + Munich 400
- [x] Logistics plans split shipment with air freight
- [x] Communications confirms to Boeing with ETA
- [x] Full event trail via order_events table

**Requires:** Increment 4 (inventory and logistics agents)

---

## Increment 6: Data Governance & Compliance

### Objective
Add OpenMetadata integration for FAA compliance and data lineage.

### Components
- **governance-mcp-server** (Port 8087): OpenMetadata integration
- OpenMetadata catalog configuration

### Deliverables Checklist

- [x] Governance agent with MCP tools:
  - [x] `get_table_metadata` - Get table schema, description, owner
  - [x] `trace_data_lineage` - Trace upstream/downstream data flow
  - [x] `check_data_quality` - Get data quality test results
  - [x] `search_data_assets` - Catalog search
  - [x] `get_glossary_term` - Business glossary definitions
  - [x] `trace_material_batch` - Batch traceability for audits
  - [x] `get_compliance_report` - Generate compliance reports (FAA/ISO)
- [x] Database tables for material batches and certifications:
  - [x] `material_batches` - Raw material batch tracking
  - [x] `batch_certifications` - Quality certs, mill certs, CoC documents
- [x] Sample data for titanium batch TI-2024-0892 with FAA certifications
- [ ] OpenMetadata optional integration (governance profile):
  - [ ] Data source connections (Greenplum)
  - [ ] Business domains (Aerospace, Energy, Mobility, Industrial)
  - [ ] Glossary terms (RUL, SKU, MaterialBatch)
  - [ ] Classifications (PII, AerospaceCompliance)

### FAA Audit Demo

- [x] "Trace titanium batch TI-2024-0892 used in Boeing 787 landing gear"
- [x] Shows complete upstream lineage to TIMET supplier
- [x] Batch certifications (FAA-8110-3 Form, Mill Certificate, CoC)
- [x] Generates compliance report for regulatory audits
- [x] "What data do we have about sensor readings?"
- [x] "Who owns the customer order data?"

**Requires:** Increment 5 (all agents for complete lineage)

---

## Increment 7: Dashboard & Full Integration

### Objective
Build the React dashboard and demonstrate full multi-agent workflows.

### Components
- **titan-dashboard** (Port 3001): React UI

### Deliverables Checklist

- [x] React dashboard with:
  - [x] Facility overview map (12 facilities)
  - [x] Real-time sensor monitoring panels
  - [x] Equipment health status cards
  - [x] Order fulfillment tracker
  - [x] Natural language chat interface
  - [x] Industrial control room UI aesthetic (Sora font, noise texture, scanlines)
  - [x] Docker container with nginx (port 3001)
- [x] Full multi-agent workflow integration
- [x] Demo script automation (Demo Scenarios component)
- [x] README.md updated with complete documentation

### Full Platform Demo

- [x] Visual dashboard showing all 12 facilities
- [x] Click on Phoenix → see CNC-007 anomaly alert
- [x] Chat: "What should we do about the failing bearing?"
- [x] Watch agents coordinate: Sensor → Maintenance → Inventory → Logistics
- [x] See order placed and tracked through fulfillment
- [x] Pull up data lineage for any component

### Four Core Scenarios

- [x] Phoenix Incident (Predictive Maintenance)
- [x] Boeing Expedite (Order Fulfillment)
- [x] FAA Audit (Data Governance)
- [x] Supply Chain Crisis (Resilience)

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
    ┌────────────────────────────────┼────────────────────────────────┐
    │        │        │        │        │        │        │           │
┌───▼───┐┌───▼───┐┌───▼───┐┌───▼───┐┌───▼───┐┌───▼───┐┌───▼───┐
│Sensor ││ Maint ││Invent ││Logist ││ Order ││ Comms ││Govern │
│ 8081  ││ 8082  ││ 8083  ││ 8084  ││ 8085  ││ 8086  ││ 8087  │
└───┬───┘└───┬───┘└───┬───┘└───┬───┘└───┬───┘└───┬───┘└───┬───┘
    │        │        │        │        │        │        │
    └────────┴────────┴────────┼────────┴────────┴────────┘
                               │
             ┌─────────────────┼─────────────────┐
             │                 │                 │
      ┌──────▼──────┐   ┌──────▼──────────────────────┐
      │  Greenplum  │   │        RabbitMQ             │
      │   15432     │   │  5672 (AMQP) + 1883 (MQTT)  │
      │  (pgvector) │   │    (events + IoT sensors)   │
      └──────┬──────┘   └─────────────────────────────┘
             │
      ┌──────▼──────┐
      │ OpenMetadata│ (optional - governance profile)
      │    8585     │
      └─────────────┘

Greenplum contains: Products, Equipment, Sensors, Orders, Customers,
                   Contracts, Material Batches, Certifications
```

---

## Progress Tracking

- [x] Review high-level plan
- [x] Increment 1 detailed planning
- [x] Increment 1 data layer implementation
- [x] Test docker-compose startup
- [x] Increment 2 (Embabel orchestrator + Sensor Agent) complete
- [x] Increment 3 (Predictive Maintenance Agent + Sensor Data Generator) complete
- [x] Increment 4 (Supply Chain: Inventory + Logistics Agents + OpenMetadata) complete
- [x] Increment 5 (Order Fulfillment: order-mcp-server + communications-mcp-server) complete
- [x] Increment 6 (Data Governance: governance-mcp-server with batch traceability) complete
- [x] Increment 7 (Dashboard & Full Integration) complete

---

---

## Increment 1: Detailed Checklist

### Infrastructure

- [x] docker-compose.yml configured with consolidated Greenplum architecture
- [x] Greenplum using greenplum-sne-base image (port 15432)
- [x] RabbitMQ configured (port 5672, management 15672, MQTT 1883)
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
