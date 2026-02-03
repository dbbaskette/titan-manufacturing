# Order MCP Server

B2B order fulfillment agent for Titan's enterprise customers.

## Role at Titan
- Processes orders from Apex Aerospace, Tesla, GE, and other strategic accounts
- Validates against contract terms and credit limits
- Handles expedite requests (like Apex order TM-2024-45892)

## Tools
| Tool | Description |
|------|-------------|
| `validate_order` | Validate order details and contract |
| `check_contract_terms` | Verify customer agreement |
| `initiate_fulfillment` | Start fulfillment workflow |
| `get_order_status` | Current order state |
| `update_order` | Modify order details |
| `cancel_order` | Cancel pending order |

## Customer Tiers
- **STRATEGIC**: Apex Aerospace, Horizon Aircraft, Tesla — $25M+ credit, 45-day terms
- **MAJOR**: GE, Ford, Caterpillar — $10M+ credit, 30-day terms
- **STANDARD**: Smaller accounts

## Data Store
- **PostgreSQL** for order data
- **RabbitMQ** for fulfillment events

## Port: 8085
