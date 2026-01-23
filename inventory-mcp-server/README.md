# Inventory MCP Server

Parts and SKU management agent for Titan's 50,000+ product catalog.

## Role at Titan
- Manages inventory across 4 divisions (Aerospace, Energy, Mobility, Industrial)
- Tracks stock levels at all 12 facilities
- Semantic search for alternative parts and suppliers
- Handles supply chain crisis (like NipponBearing force majeure)

## Tools
| Tool | Description |
|------|-------------|
| `check_stock` | Get stock level for SKU at facility |
| `check_availability` | Verify quantity available for order |
| `calculate_reorder_point` | Optimal reorder calculation |
| `search_products` | Semantic search via pgvector |
| `get_product_details` | Full product specifications |
| `find_alternative_supplier` | Find backup suppliers for SKU |
| `reserve_inventory` | Soft reserve for pending orders |
| `get_low_stock_alerts` | Items below reorder threshold |

## Product Catalog
| Division | Example SKUs |
|----------|--------------|
| Aerospace | AERO-TB-001 (Turbine Blade), AERO-LG-001 (Landing Gear) |
| Energy | ENRG-GB-001 (Gearbox), ENRG-VL-001 (Valve) |
| Mobility | MOBL-MH-001 (Motor Housing), MOBL-BE-001 (Battery Enclosure) |
| Industrial | INDL-BRG-7420 (CNC Bearing), INDL-HP-001 (Hydraulic Pump) |

## Data Store
- **Greenplum** with pgvector extension for semantic search

## Port: 8083
