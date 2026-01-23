# Titan 5.0 Orchestrator

Central coordinator for Titan Manufacturing's multi-agent AI platform.

## Role
- Receives requests from Titan Dashboard, operators, and executives
- Plans multi-step workflows across 7 specialized agents
- Coordinates responses to incidents like the Phoenix CNC-007 anomaly
- Synthesizes compliance reports for FAA audits

## Technology
- Spring AI with Multi-MCP Client
- Spring Boot 3.3+
- Java 21

## MCP Connections
| Agent | Endpoint | Purpose |
|-------|----------|---------|
| Sensor | sensor-mcp-server:8081 | 12 facility IoT data |
| Maintenance | maintenance-mcp-server:8082 | Predictive maintenance |
| Inventory | inventory-mcp-server:8083 | 50K+ SKU management |
| Logistics | logistics-mcp-server:8084 | Global shipping |
| Order | order-mcp-server:8085 | B2B fulfillment |
| Communications | communications-mcp-server:8086 | Customer notifications |
| Governance | governance-mcp-server:8087 | OpenMetadata integration |

## API Endpoints
```
POST /api/chat          - Natural language interaction
POST /api/workflow      - Structured workflow execution
GET  /api/agents        - List connected agents
GET  /api/facilities    - List Titan facilities
```
