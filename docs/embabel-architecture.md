# Embabel Agent Architecture in Titan Manufacturing

## What is Embabel?

Embabel is an agent framework for the JVM created by Rod Johnson (creator of Spring). It provides **Goal-Oriented Action Planning (GOAP)** — an AI planning algorithm borrowed from game AI — to dynamically determine *how* to achieve a stated goal by composing typed actions at runtime. It builds on top of Spring AI and the Model Context Protocol (MCP).

Key differentiator: unlike workflow frameworks where the developer codes each step, Embabel's planner **discovers action sequences automatically** based on type signatures. You define the goal and the available actions; Embabel figures out the path.

---

## Architecture Overview

```
┌──────────────────────────────────────────────────────────────────┐
│                        Titan Orchestrator                        │
│                                                                  │
│  ┌─────────────────┐    ┌──────────────┐    ┌────────────────┐  │
│  │ RabbitMQ Listener│───▶│ AgentPlatform│───▶│ GOAP Planner   │  │
│  │ (event trigger)  │    │ (registry)   │    │ (A* search)    │  │
│  └─────────────────┘    └──────────────┘    └───────┬────────┘  │
│                                                      │           │
│  ┌──────────────────────────────────────────────────▼────────┐  │
│  │                    TitanAnomalyAgent                       │  │
│  │                                                            │  │
│  │  @Action(toolGroups={"maintenance","inventory","comms"})   │  │
│  │  handleCriticalAnomaly(CriticalAnomalyInput) → Response    │  │
│  │                                                            │  │
│  │  @Action(toolGroups={"maintenance","inventory"})           │  │
│  │  handleHighAnomaly(HighAnomalyInput) → Response            │  │
│  └────────────────────────┬───────────────────────────────────┘  │
│                           │ LLM calls tools via MCP              │
│  ┌────────────────────────▼───────────────────────────────────┐  │
│  │              Tool Groups (McpToolGroup beans)               │  │
│  │  sensor-tools │ maintenance-tools │ inventory-tools │ ...   │  │
│  └───────┬──────────────┬─────────────────┬───────────────────┘  │
└──────────┼──────────────┼─────────────────┼──────────────────────┘
           │              │                 │
     ┌─────▼─────┐ ┌─────▼──────┐  ┌──────▼───────┐
     │ Sensor MCP│ │Maintenance │  │ Inventory MCP│  ... 4 more
     │  :8081    │ │ MCP :8082  │  │    :8083     │
     └───────────┘ └────────────┘  └──────────────┘
```

---

## Core Concepts

### 1. Goals and the GOAP Planner

GOAP works backward from a **goal** (desired output type) to find a sequence of **actions** that can produce it. The algorithm:

1. You invoke `AgentInvocation.create(agentPlatform, CriticalAnomalyResponse.class)`
2. The planner asks: "Which `@AchievesGoal` action returns `CriticalAnomalyResponse`?"
3. It finds `handleCriticalAnomaly(CriticalAnomalyInput, Ai)` → match
4. It checks: "Is `CriticalAnomalyInput` available on the blackboard?"
5. You call `invocation.invoke(new CriticalAnomalyInput(event))` — placing it on the blackboard
6. Preconditions met → action executes

For multi-step goals, the planner chains actions: if action B needs type X, and action A produces type X, the planner runs A then B. This is the A* search over the type graph.

### 2. The Blackboard

The blackboard is shared memory for a single agent execution. When you call `invocation.invoke(input)`, the input is placed on the blackboard. Each action's return value is also placed on the blackboard, making it available as input to subsequent actions.

### 3. Actions

An `@Action` method is a discrete step the agent can perform. Key properties:

| Property | Purpose |
|----------|---------|
| `description` | Human-readable explanation (also fed to LLM) |
| `toolGroups` | Which MCP tool groups this action can use |
| `cost` / `value` | Weights for the planner to prefer cheaper or more valuable actions |
| `canRerun` | Whether the action can execute more than once |

The action's **parameter types** are preconditions (must exist on blackboard). The **return type** is a postcondition (added to blackboard after execution).

### 4. Input Type Disambiguation

Our project uses wrapper records to disambiguate GOAP routing:

```java
public record CriticalAnomalyInput(AnomalyEvent event) {}
public record HighAnomalyInput(AnomalyEvent event) {}
```

Without these, both `handleCriticalAnomaly` and `handleHighAnomaly` would accept `AnomalyEvent` — the planner couldn't distinguish which to run. The wrapper types create distinct preconditions so the planner routes correctly:

- `invoke(new CriticalAnomalyInput(event))` → only `handleCriticalAnomaly` matches
- `invoke(new HighAnomalyInput(event))` → only `handleHighAnomaly` matches

### 5. Tool Groups

Tool groups connect Embabel actions to MCP servers. Each group is a Spring bean of type `McpToolGroup`:

```java
@Bean
public ToolGroup maintenanceToolGroup() {
    return new McpToolGroup(
        ToolGroupDescription.Companion.invoke(
            "Predictive maintenance and scheduling tools",
            "maintenance-tools"    // role name — matches @Action(toolGroups)
        ),
        "maintenance-tools",       // name
        "TITAN-MAINTENANCE-MCP",   // provider
        Set.of(ToolGroupPermission.HOST_ACCESS),
        mcpSyncClients,            // injected MCP clients
        tool -> matchesToolName(tool, "predictFailure", "estimateRul",
                                "scheduleMaintenance", "getMaintenanceHistory")
    );
}
```

The filter predicate determines which tools from which MCP servers belong to this group. When an action declares `toolGroups = {"maintenance-tools"}`, only those filtered tools are available to the LLM.

**Current tool group → MCP server mapping:**

| Tool Group | MCP Server | Port | Tools |
|------------|-----------|------|-------|
| `sensor-tools` | sensor-mcp-server | 8081 | listEquipment, getEquipmentStatus, getSensorReadings, getFacilityStatus, detectAnomaly, detectAnomalyML, getThresholds, updateThreshold |
| `maintenance-tools` | maintenance-mcp-server | 8082 | predictFailure, estimateRul, scheduleMaintenance, getMaintenanceHistory |
| `inventory-tools` | inventory-mcp-server | 8083 | checkStock, searchProducts, findAlternatives, calculateReorder, getCompatibleParts |
| `logistics-tools` | logistics-mcp-server | 8084 | getCarriers, createShipment, trackShipment, estimateShipping |
| `order-tools` | order-mcp-server | 8085 | validateOrder, checkContractTerms, initiateFulfillment, getOrderStatus |
| `communications-tools` | communications-mcp-server | 8086 | sendNotification, handleInquiry, draftCustomerUpdate |
| `governance-tools` | governance-mcp-server | 8087 | getTableMetadata, traceDataLineage, checkDataQuality, searchDataAssets, traceMaterialBatch, getComplianceReport |

### 6. LLM Interaction: `Ai.withAutoLlm()`

Inside an action, `Ai` provides access to LLMs:

- **`ai.withAutoLlm().createObject(prompt, ResponseType.class)`** — sends the prompt to the LLM with the declared tool groups available. The LLM can call tools, then its output is parsed into the typed response record. This is how tool calling happens: the LLM decides which tools to invoke based on the prompt.

- **`ai.withAutoLlm().generateText(prompt)`** — same but returns unstructured text.

`withAutoLlm()` uses the configured default model (set via `embabel.models.default-llm` in application.yml). The framework supports role-based model selection so different actions can use different LLMs.

---

## How It Works End-to-End: Anomaly Response

### Event Flow

```
Sensor Generator (MQTT) → Sensor MCP (8081) → GemFire scoring
    → RabbitMQ (anomaly.critical / anomaly.high)
        → AnomalyEventListener
            → AgentInvocation → GOAP Planner → TitanAnomalyAgent action
                → LLM plans tool calls across MCP servers
                    → Typed response → Record in Greenplum
```

### CRITICAL Anomaly (≥70% failure probability)

1. **Trigger**: `AnomalyEventListener.handleCritical()` receives event from RabbitMQ
2. **Invocation**: `AgentInvocation.create(agentPlatform, CriticalAnomalyResponse.class)`
3. **Planning**: GOAP finds `handleCriticalAnomaly` (matches input type + goal type)
4. **Execution**: Action runs, giving the LLM access to maintenance, inventory, and communications tools
5. **LLM Tool Calls**: The LLM reads the prompt and decides to call:
   - `checkStock` / `findAlternatives` (inventory-tools)
   - `scheduleMaintenance` (maintenance-tools)
   - `sendNotification` (communications-tools)
6. **Response**: LLM output is parsed into `CriticalAnomalyResponse` record
7. **Audit**: `automatedActionService.record()` saves to Greenplum

### HIGH Anomaly (≥50% failure probability)

1. Same trigger/invocation/planning pattern but targets `HighAnomalyResponse`
2. Action only has `maintenance-tools` and `inventory-tools` (no communications)
3. Does NOT schedule maintenance — creates a recommendation for human approval
4. Dashboard shows recommendation; human clicks "Approve" or "Dismiss"

### Approval Flow

1. `RecommendationController.approveRecommendation()` receives HTTP POST
2. Reconstructs an `AnomalyEvent` from the stored recommendation
3. Invokes `AgentInvocation.create(agentPlatform, CriticalAnomalyResponse.class)` — same goal as CRITICAL
4. GOAP routes to `handleCriticalAnomaly` which now schedules maintenance + notifies

---

## What Embabel Adds vs. Direct Coding

### Without Embabel (manual orchestration)

```java
// Developer explicitly codes every step and every branch
public void handleAnomaly(AnomalyEvent event) {
    var prediction = maintenanceClient.predictFailure(event.equipmentId());
    var stock = inventoryClient.checkStock(getSkuForCause(prediction.cause()));
    if (stock.available()) {
        inventoryClient.reserveParts(stock.sku(), 1);
    }
    maintenanceClient.scheduleMaintenance(event.equipmentId(), "EMERGENCY");
    commsClient.sendNotification(event.facilityId(), "MAINTENANCE_ALERT", ...);
}
```

Problems: rigid workflow, no adaptability, every branch hand-coded, no intelligence in decision-making.

### With Embabel (goal-driven)

```java
@AchievesGoal(description = "Prevent equipment failure by reserving parts, " +
                            "scheduling maintenance, and notifying personnel")
@Action(toolGroups = {"maintenance-tools", "inventory-tools", "communications-tools"})
public CriticalAnomalyResponse handleCriticalAnomaly(CriticalAnomalyInput input, Ai ai) {
    return ai.withAutoLlm().createObject(prompt, CriticalAnomalyResponse.class);
}
```

The LLM decides:
- **Which** parts to reserve based on the probable cause (bearing → SKU X, motor → SKU Y)
- **Whether** alternatives are needed if primary parts are out of stock
- **What** maintenance notes to include
- **How** to compose the notification

### Value Proposition

| Capability | Without Embabel | With Embabel |
|-----------|----------------|-------------|
| **Action selection** | Hardcoded if/else | GOAP planner finds path from input type to goal type |
| **Tool orchestration** | Developer writes each call | LLM decides which tools to call and in what order |
| **Adaptability** | New scenarios = new code | LLM adapts to novel situations using existing tools |
| **Type safety** | Manual validation | Java records enforce structure; planner validates type graph |
| **Multi-LLM** | Manual client switching | `withAutoLlm()` routes by role; config-driven |
| **Tool access control** | Everything or nothing | `toolGroups` scopes what each action can access |
| **Replanning** | Not possible | After each action, planner reassesses and can change course |

### Where Embabel is Most Appropriate

Embabel shines when:
- **Multiple tools** could be combined in different ways depending on context
- **Business logic is nuanced** — an LLM can interpret "bearing degradation" and map it to the right SKU, while code would need an explicit lookup table
- **Goals are clearer than procedures** — "prevent equipment failure" is easier to state than to exhaustively code
- **The action graph may grow** — adding a new MCP server with new tools doesn't require rewriting orchestration logic

### Where Embabel is Overkill

- **Deterministic workflows** where the steps never vary (e.g., always send notification to the same address with the same template)
- **Simple CRUD operations** that don't benefit from LLM reasoning
- **Latency-critical paths** — each `createObject` call involves an LLM round-trip (seconds, not milliseconds)

---

## Current Agents in Titan Manufacturing

### TitanAnomalyAgent

**Purpose**: Respond to ML-detected equipment anomalies.

| Action | Goal | Tool Groups | Trigger |
|--------|------|-------------|---------|
| `handleCriticalAnomaly` | Prevent failure: reserve parts, schedule maintenance, notify | maintenance, inventory, communications | RabbitMQ CRITICAL event or approval |
| `handleHighAnomaly` | Prepare for failure: reserve parts, create recommendation | maintenance, inventory | RabbitMQ HIGH event |

### TitanSensorAgent

**Purpose**: Query sensor data and equipment status.

| Action | Goal | Tool Groups | Trigger |
|--------|------|-------------|---------|
| `getEquipmentStatus` | Get health + sensor readings | sensor | API call |
| `answerQuery` | Answer natural language questions about equipment | sensor | Chat interface |

### TitanMaintenanceAgent

**Purpose**: Predictive maintenance analysis.

| Action | Goal | Tool Groups | Trigger |
|--------|------|-------------|---------|
| `predictFailure` | Failure probability + risk factors | maintenance | API call |
| `estimateRul` | Remaining useful life estimate | maintenance | API call |
| `analyzeMaintenanceNeeds` | Combined prediction + RUL + recommendations | maintenance | API call |

---

## Configuration Reference

### application.yml (orchestrator)

```yaml
spring.ai.mcp.client:
  connections:
    maintenance-tools:
      url: http://maintenance-mcp-server:8082
      endpoint: /mcp           # MCP stateless HTTP endpoint

embabel:
  models:
    default-llm: gpt-4.1      # which LLM the planner and actions use
  agent:
    platform:
      scanning:
        packages: com.embabel.agent,com.titan.orchestrator
```

### Dependencies (pom.xml)

```xml
<dependency>
    <groupId>com.embabel.agent</groupId>
    <artifactId>embabel-agent-starter</artifactId>        <!-- core framework -->
</dependency>
<dependency>
    <groupId>com.embabel.agent</groupId>
    <artifactId>embabel-agent-starter-openai</artifactId> <!-- OpenAI LLM support -->
</dependency>
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-mcp-client</artifactId> <!-- MCP client -->
</dependency>
```

---

## Known Limitations and Considerations

### LLM Tool-Call Reliability

The LLM inside `createObject()` decides which tools to call. It may skip tools even when instructed (e.g., calling `checkStock` but skipping `sendNotification`). This is inherent to the approach — the LLM has agency over tool selection. Mitigations:

- **Explicit prompting**: The prompt must strongly instruct the LLM to complete all steps
- **Post-action deterministic steps**: For critical side-effects (like sending notifications), consider calling them deterministically after the agent completes rather than relying on the LLM
- **Model selection**: More capable models (GPT-4.1, Claude) are more reliable at multi-tool orchestration than smaller models

### Latency

Each `createObject()` / `generateText()` call is an LLM round-trip. If the LLM makes multiple tool calls, each tool call is also a network hop to an MCP server. A single anomaly response can take 10-40 seconds depending on the model and number of tool calls.

### Debugging

Embabel logs action execution, tool calls, and planner decisions. Key log patterns:
- `Embabel - [session] (action) calling tool toolName(args)` — tool invocation
- `Embabel - [session] goal ... achieved in PT...S` — goal completion
- `Embabel - [session] ready to plan from: {conditions}` — planner state

---

## Equipment Parts Compatibility Matrix

The `equipment_parts_compatibility` table links equipment types and models to compatible replacement parts, enabling the agent to find model-specific parts rather than guessing from a diagnosis string.

### Schema

```sql
CREATE TABLE equipment_parts_compatibility (
    id SERIAL PRIMARY KEY,
    equipment_type VARCHAR(20) NOT NULL,    -- e.g., CNC-MILL, CNC-LATHE
    equipment_model VARCHAR(100),           -- e.g., 'DMG MORI DMU 50' (NULL = all models of type)
    sku VARCHAR(50) NOT NULL,               -- references products table
    part_role VARCHAR(50) NOT NULL,         -- e.g., 'spindle_bearing', 'spindle_motor'
    is_primary BOOLEAN DEFAULT TRUE,        -- primary vs alternative fit
    notes TEXT
);
```

### Part Role to Fault Type Mapping

The `getCompatibleParts` tool maps fault types to part roles:

| Fault Type | Part Roles |
|-----------|-----------|
| BEARING | spindle_bearing, ball_screw_bearing, spindle_seal |
| MOTOR | spindle_motor, motor_controller, encoder, contactor, overload_relay |
| SPINDLE | spindle_cartridge, spindle_drawbar, spindle_seal, spindle_bearing |
| COOLANT | coolant_pump, coolant_pump_hp, coolant_filter, coolant_sensor, coolant_chiller |
| ELECTRICAL | motor_controller, power_supply, circuit_breaker, surge_protector, emc_filter |

### Model-Specific Parts

Different models use different parts. For example:

| Equipment Model | Spindle Motor | VFD | Spindle Bearing |
|----------------|--------------|-----|-----------------|
| DMG MORI DMU 50 (CNC-MILL) | INDL-MOT-5500 (15kW) | INDL-CTR-1100 (15kW) | INDL-BRG-7420 (angular contact) |
| Mazak VTC-800 (CNC-MILL) | INDL-MOT-5501 (22kW) | INDL-CTR-1101 (22kW) | INDL-BRG-7421 (cylindrical) |
| Haas VF-4 (CNC-MILL) | INDL-MOT-5500 (15kW) | INDL-CTR-1100 (15kW) | INDL-BRG-7420 (angular contact) |
| Okuma LB3000 (CNC-LATHE) | INDL-MOT-5501 (22kW) | INDL-CTR-1101 (22kW) | INDL-BRG-7421 (cylindrical) |
| Mazak QT-250 (CNC-LATHE) | INDL-MOT-5500 (15kW) | INDL-CTR-1100 (15kW) | INDL-BRG-7420 (angular contact) |
| DMG MORI DMU 80P (CNC-5AX) | INDL-MOT-5501 (22kW) | INDL-CTR-1101 (22kW) | INDL-BRG-7420 (angular contact) |
| Makino D500 (CNC-5AX) | INDL-MOT-5502 (30kW) | INDL-CTR-1102 (30kW) | INDL-BRG-7421 (cylindrical) |

Common parts (coolant, electrical protection, servo motors) are mapped at the equipment type level with `equipment_model = NULL`, so they apply to all models of that type.

### How the Agent Uses It

The agent prompt now instructs the LLM:

```
1. FIND COMPATIBLE PARTS: Use getCompatibleParts with equipmentId="ATL-CNC-001" and
   faultType="BEARING"
```

The `getCompatibleParts` tool:
1. Looks up the equipment's type, model, and facility from the `equipment` table
2. Maps the fault type to relevant part roles
3. Queries the compatibility matrix (model-specific + type-level entries)
4. Joins with `products` and `stock_levels` to return parts with current stock at the equipment's facility

This replaces the previous `searchProducts` approach where the LLM had to guess search terms.

---

## References

- [Embabel Official Documentation](https://docs.embabel.com/embabel-agent/guide/0.3.0/)
- [GitHub: embabel/embabel-agent](https://github.com/embabel/embabel-agent)
- [InfoQ: Introducing Embabel](https://www.infoq.com/news/2025/06/introducing-embabel-ai-agent/)
- [Rod Johnson: How and Why Embabel Plans](https://medium.com/@springrod/ai-for-your-gen-ai-how-and-why-embabel-plans-3930244218f6)
- [Baeldung: Creating an AI Agent with Embabel](https://www.baeldung.com/java-embabel-agent-framework)
- [Rod Johnson: Year-End Update (Dec 2025)](https://medium.com/@springrod/embabel-year-end-update-building-the-best-agent-framework-25ed98728e79)
