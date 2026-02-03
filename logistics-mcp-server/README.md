# Logistics MCP Server

Global shipping optimization agent for Titan Manufacturing.

## Role at Titan
- Optimizes shipping from 12 facilities to global B2B customers
- Selects carriers for aerospace (expedite) vs standard shipments
- Handles split shipments (e.g., Apex order from Phoenix + Munich)

## Tools
| Tool | Description |
|------|-------------|
| `plan_route` | Optimize delivery route |
| `select_carrier` | Choose best carrier for shipment |
| `predict_eta` | Estimated arrival time |
| `track_shipment` | Real-time tracking |
| `get_carrier_rates` | Compare shipping costs |
| `calculate_shipping_cost` | Cost estimation |
| `plan_split_shipment` | Multi-facility fulfillment |

## Carriers
- FedEx Express (Air) — Aerospace expedites
- DHL Global (International)
- Maersk (Ocean Freight)

## Port: 8084
