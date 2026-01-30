# Titan Manufacturing — Full Audit Checklist

> **Last verified:** 2026-01-29 — every tool and screen checked against source code.

## MCP Servers

### 1. sensor-mcp-server (Port 8081)
**Purpose:** IoT sensor data from 600+ CNC machines across 12 facilities

| Tool | Inputs | Outputs | Real or Fake? |
|------|--------|---------|---------------|
| `listEquipment` | facilityId?, equipmentType?, limit | Equipment list | **REAL** — queries `equipment` table |
| `getEquipmentStatus` | equipmentId | Health status, readings, anomalies | **REAL** — queries equipment + sensor_readings + anomalies |
| `getSensorReadings` | equipmentId, sensorType?, hoursBack?, limit | Sensor readings list | **REAL** — queries `sensor_readings` table |
| `getFacilityStatus` | facilityId | Equipment counts by status, health % | **REAL** — aggregates from equipment + anomalies |
| `getThresholds` | equipmentId? | Warning/critical thresholds per sensor | **REAL** — queries sensor_thresholds; hardcoded fallback |
| `updateThreshold` | equipmentId, sensorType, warning, critical, updatedBy | Updated threshold | **REAL** — UPSERT into sensor_thresholds |
| `detectAnomaly` | equipmentId, sensorType?, hoursBack? | Anomaly detection results | **REAL** — statistical analysis (z-score, trend, rate-of-change) on sensor_readings |
| `detectAnomalyML` | equipmentId, sensorType?, hoursBack? | ML-based anomaly results | **REAL** — calls Greenplum ML function + statistical analysis |

**Data sources:** Greenplum (JDBC), MQTT broker
**Status:** ✅ Fully connected to real data

---

### 2. maintenance-mcp-server (Port 8082)
**Purpose:** Predictive maintenance using ML models; GemFire PMML scoring; anomaly publishing

| Tool | Inputs | Outputs | Real or Fake? |
|------|--------|---------|---------------|
| `predictFailure` | equipmentId, hoursBack? | Failure probability, risk level, risk factors | **REAL** — calls Greenplum ML function `predict_equipment_failure()` |
| `estimateRul` | equipmentId | RUL hours, bounds, methodology | **REAL** — queries equipment age + vibration trends |
| `scheduleMaintenance` | equipmentId, type, date, notes | Work order ID, technician, parts, cost | **REAL** — inserts into `maintenance_records` |
| `getMaintenanceHistory` | equipmentId, status?, limit | Maintenance records | **REAL** — queries `maintenance_records` |
| `getGemFirePredictions` | (none) | Equipment predictions from GemFire | **REAL** — reads GemFire SensorPredictions region |

**Additional REST endpoints (non-MCP):** `/ml/model`, `/ml/predictions`, `/ml/gemfire/status`, `/ml/pmml`, `/ml/retrain`, `/ml/deploy`, `/ml/training/generate`, `/ml/predictions/reset`, `/ml/anomaly-level`
**Data sources:** Greenplum, MQTT, GemFire, RabbitMQ
**Status:** ✅ Fully connected — most sophisticated server

---

### 3. inventory-mcp-server (Port 8083)
**Purpose:** 50,000+ SKU management across 12 facilities

| Tool | Inputs | Outputs | Real or Fake? |
|------|--------|---------|---------------|
| `checkStock` | sku, facilityId? | Stock levels per facility, reorder needs | **REAL** — queries products + stock_levels |
| `searchProducts` | query, division?, category?, limit | Product matches with stock | **PARTIAL** — text ILIKE search (pgvector fallback not working) |
| `findAlternatives` | sku, quantityNeeded? | Alt suppliers + similar products | **REAL** — queries product_suppliers + suppliers + stock_levels |
| `calculateReorder` | sku, facilityId, dailyDemand? | EOQ, safety stock, order date, cost | **REAL** — EOQ formula with real product/supplier data |
| `getCompatibleParts` | equipmentId, faultType | Compatible parts with stock levels | **REAL** — queries equipment_parts_compatibility + products + stock_levels |

**Data sources:** Greenplum (products, stock_levels, suppliers, product_suppliers, equipment_parts_compatibility)
**Status:** ✅ Mostly real — vector search falls back to text search

---

### 4. logistics-mcp-server (Port 8084)
**Purpose:** Shipment management across global supply chain

| Tool | Inputs | Outputs | Real or Fake? |
|------|--------|---------|---------------|
| `getCarriers` | serviceType?, activeOnly? | Carrier list | **REAL** — queries `carriers` table |
| `createShipment` | orderId, carrierId, originFacility, serviceLevel | Shipment ID, tracking#, cost | **REAL** — inserts into `shipments`, calculates from `shipping_rates` |
| `trackShipment` | shipmentIdOrTracking | Status, carrier, tracking URL, dates | **REAL** — queries `shipments` + `carriers` |
| `estimateShipping` | originFacility, destRegion, weightKg, serviceLevel? | Cost/transit estimates per carrier | **REAL** — queries `shipping_rates` + `carriers` |

**Data sources:** Greenplum (carriers, shipments, orders, shipping_rates)
**Status:** ✅ Fully connected to real data

---

### 5. order-mcp-server (Port 8085)
**Purpose:** Order validation, fulfillment, status tracking

| Tool | Inputs | Outputs | Real or Fake? |
|------|--------|---------|---------------|
| `validateOrder` | orderId | Inventory check, credit check, issues | **REAL** — queries order_lines + stock_levels + customer_contracts |
| `checkContractTerms` | customerId | Contract terms, available credit | **REAL** — queries customer_contracts + outstanding orders |
| `initiateFulfillment` | orderId, expedite? | Allocations, shipments, delivery ETA | **REAL** — queries stock_levels for allocation, shipments table for tracking, all from DB |
| `getOrderStatus` | orderId | Order info, line items, events, shipments | **REAL** — queries orders + order_lines + order_events + shipments |

**Additional REST endpoints:** `/orders`, `/orders/counts`, `/orders/{id}`, `/orders/{id}/events`, `/orders/{id}/status`
**Data sources:** Greenplum (orders, customers, order_lines, products, stock_levels, shipments)
**Status:** ✅ Fully connected to real data

---

### 6. communications-mcp-server (Port 8086)
**Purpose:** Customer notifications and inquiry handling

| Tool | Inputs | Outputs | Real or Fake? |
|------|--------|---------|---------------|
| `sendNotification` | recipientId, templateType, variablesJson | Notification ID, subject, recipient, sent status | **REAL** — SMTP email via JavaMailSender when configured; simulated fallback when SMTP_USER not set. Queries customers/facilities for recipient info, uses communication_templates for content |
| `handleInquiry` | customerId, inquiryText, orderId? | Inquiry type, suggested response, similar inquiries | **REAL** — uses LLM (Spring AI ChatClient) to classify inquiry and generate response; queries customer_inquiries for similar past inquiries |
| `draftCustomerUpdate` | orderId, updateType | Subject, body draft, recommended action | **REAL** — uses LLM (Spring AI ChatClient) to draft update; queries orders + order_events for context |

**Data sources:** Greenplum (customers, communication_templates, customer_inquiries, facilities, orders), Spring AI ChatClient (LLM)
**Status:** ✅ Fully connected — all tools use real DB queries + LLM where appropriate

---

### 7. governance-mcp-server (Port 8087)
**Purpose:** Data governance, lineage, quality, compliance

| Tool | Inputs | Outputs | Real or Fake? |
|------|--------|---------|---------------|
| `getTableMetadata` | tableName | Columns, types, row count, tags | **PARTIAL** — real information_schema queries, but descriptions/domains hardcoded |
| `traceDataLineage` | tableName, direction | Lineage graph (nodes + edges) | **REAL** — queries FK relationships from information_schema |
| `checkDataQuality` | tableName | NULL/uniqueness/referential integrity checks | **REAL** — runs actual SQL quality checks |
| `searchDataAssets` | query, domain? | Matching assets | **PARTIAL** — queries information_schema metadata catalog; domain/description enrichment hardcoded |
| `getGlossaryTerm` | term | Definition, synonyms, related tables | **FAKE** — hardcoded glossary (4 terms: SKU, LEAD_TIME, QUALITY_RATING, RUL) |
| `traceMaterialBatch` | batchId | Batch info, certs, usage history | **REAL** — queries material_batches + suppliers + batch_certifications |
| `getComplianceReport` | reportType, startDate, endDate | Compliance items, status, recommendations | **REAL** — queries relevant tables for FAA/ISO/material/supplier checks |

**Data sources:** Greenplum (information_schema + business tables)
**Status:** ⚠️ Mixed — lineage/quality/compliance/search real; metadata descriptions and glossary hardcoded

---

## Dashboard Screens

### 1. Global Overview
**Displays:** World map with 12 facilities, summary cards, regional breakdown
**API calls:** `GET /api/equipment`, `GET /api/ml/predictions`
**Data source:** Equipment from generator:8090, ML predictions from GemFire via maintenance:8082
**Real or Fake?** ⚠️ Equipment + ML = **REAL**; facility metadata (coords, names, specializations) = **HARDCODED** in component
**Should be:** Facilities could come from a database table or facilities endpoint

---

### 2. Sensor Monitor
**Displays:** Live 6-sensor charts (2-min window), anomaly detection, fleet summary
**API calls:** SSE stream for live data, `GET /api/ml/predictions`
**Data source:** SSE from sensor-data-generator, ML predictions from GemFire
**Real or Fake?** ✅ **REAL** — live SSE sensor stream + real ML predictions
**Thresholds:** Hardcoded in component (vibration: 3.0/3.5, temp: 70/85, power: 50/55)
**Should be:** Thresholds should come from sensor-mcp-server `getThresholds` tool

---

### 3. Equipment Health
**Displays:** Equipment inventory, health status, RUL estimates, risk drivers, recommended actions
**API calls:** `GET /api/equipment`, `GET /api/ml/predictions`
**Data source:** Equipment from generator, ML predictions from GemFire
**Real or Fake?** ⚠️ Equipment + ML = **REAL**; RUL profiles + recommended actions/parts = **HARDCODED** in component
**Should be:** RUL from maintenance-mcp-server `estimateRul`; actions from maintenance agent

---

### 4. Order Tracker
**Displays:** Order pipeline (Pending→Validated→Processing→Shipped→Delivered), order details, events, shipments
**API calls:** `GET /api/orders`, `GET /api/orders/counts`, `GET /api/orders/{id}`
**Data source:** order-mcp-server:8085 → Greenplum
**Real or Fake?** ✅ **REAL** — all data from Greenplum order tables

---

### 5. Recommendations
**Displays:** Pending/Approved/Denied/Auto tabs, approval workflow, processing panel
**API calls:** `GET /api/recommendations`, `GET /api/recommendations/resolved`, `GET /api/automated-actions`, `POST .../approve`, `POST .../dismiss`
**Data source:** orchestrator → Greenplum maintenance_recommendations + automated_actions; approve triggers Embabel agent
**Real or Fake?** ✅ **REAL** — all from Greenplum, approve invokes real Embabel agent

---

### 6. AI Chat
**Displays:** Chat interface with suggested prompts
**API calls:** `POST /api/chat`
**Data source:** Frontend has MOCK_RESPONSES object; does not call real API
**Real or Fake?** ❌ **FAKE** — `ChatInterface.tsx` returns canned responses from a hardcoded MOCK_RESPONSES map matching keywords ("phx-cnc-007", "stock", "phoenix"). No real API call is made.
**Should be:** Call `POST /api/chat` on the orchestrator, which routes to real Embabel agents

---

### 7. Demo Scenarios
**Displays:** 4 pre-built demo scenarios (Phoenix Incident, Boeing Expedite, FAA Audit, Supply Chain Crisis)
**API calls:** None directly — sends to chat
**Real or Fake?** ❌ **HARDCODED** scenario descriptions — intentionally static content
**Should be:** Fine as-is (educational/demo content)

---

### 8. Agent Status
**Displays:** 7 agent cards with metrics, GOAP flow diagram (SVG)
**API calls:** `GET /api/agents`
**Data source:** Orchestrator returns hardcoded agent list; metrics generated with Math.random() in frontend
**Real or Fake?** ❌ **FAKE** — agent list is hardcoded Map in orchestrator, metrics are random. GOAP diagram is static SVG.
**Should be:** Real health checks to each MCP server, actual request counts from logs/metrics

---

### 9. ML Pipeline
**Displays:** Training→Model→PMML→Scoring pipeline, coefficients, prediction distribution, GemFire status
**API calls:** `GET /api/ml/model`, `GET /api/ml/predictions`, `GET /api/ml/gemfire/status`, `GET /api/ml/pmml`, `POST /api/ml/retrain`, `POST /api/ml/deploy`
**Data source:** maintenance-mcp-server:8082 → Greenplum + GemFire
**Real or Fake?** ✅ **REAL** — model coefficients from MADlib, predictions from GemFire, real retrain/deploy

---

### 10. Simulation Control
**Displays:** Generator controls, equipment list, pattern injection, speed multiplier
**API calls:** Direct to `http://localhost:8090/api/generator/*`
**Data source:** sensor-data-generator:8090
**Real or Fake?** ✅ **REAL** — controls real generator state

---

### 11. Settings
**Displays:** Anomaly detection level slider, threshold configuration, system info
**API calls:** `GET /api/ml/anomaly-level`, `POST /api/ml/anomaly-level`, `GET /api/equipment` (for threshold management)
**Data source:** maintenance-mcp-server:8082 for anomaly level; sensor-mcp-server:8081 for thresholds
**Real or Fake?** ✅ **REAL** — anomaly level persisted via API; thresholds from sensor_thresholds table

---

## Summary: What Needs Fixing

### MCP Servers — Priority Issues
| # | Server | Issue | Severity |
|---|--------|-------|----------|
| 1 | governance (8087) | Metadata descriptions/domains and glossary hardcoded in Java Maps | MEDIUM |
| 2 | inventory (8083) | pgvector search falls back to text ILIKE (getCompatibleParts is fully real) | LOW |

### Dashboard Screens — Priority Issues
| # | Screen | Issue | Severity |
|---|--------|-------|----------|
| 1 | Agent Status | Metrics are Math.random(), agent list hardcoded (GOAP diagram added but static) | HIGH |
| 2 | AI Chat | 100% mock — MOCK_RESPONSES in frontend, no real API calls | HIGH |
| 3 | Equipment Health | RUL profiles + recommended actions hardcoded in component | MEDIUM |
| 4 | Global Overview | Facility metadata hardcoded in component | MEDIUM |
| 5 | Sensor Monitor | Thresholds hardcoded instead of from sensor-mcp-server | LOW |
