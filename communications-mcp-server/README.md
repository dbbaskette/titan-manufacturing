# Communications MCP Server

Customer notification and inquiry handling agent.

## Role at Titan
- Sends order confirmations to Boeing, Tesla procurement teams
- Handles customer inquiries via RAG pipeline
- Drafts supply chain disruption notifications

## Tools
| Tool | Description |
|------|-------------|
| `send_notification` | Send email/notification |
| `handle_inquiry` | RAG-powered customer response |
| `get_order_status` | Customer-facing order status |
| `draft_customer_update` | Generate update message |
| `get_notification_history` | Past communications |
| `escalate_to_human` | Route to account manager |

## RAG Pipeline
- pgvector embeddings for product documentation
- Customer contract summaries
- Historical inquiry responses

## Port: 8086
