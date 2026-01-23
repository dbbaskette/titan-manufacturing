# Governance MCP Server

Data governance agent powered by OpenMetadata — critical for FAA compliance.

## Role at Titan
- Data discovery across all Titan data sources
- Lineage tracking for aerospace compliance (FAA/EASA audits)
- PII detection in operator certification records
- Data quality monitoring

## Tools (18 total)

### Discovery
| Tool | Description |
|------|-------------|
| `search_data_assets` | Natural language catalog search |
| `get_table_metadata` | Schema and column details |
| `find_tables_by_tag` | Filter by classification |

### Lineage
| Tool | Description |
|------|-------------|
| `get_upstream_lineage` | Trace data sources |
| `get_downstream_lineage` | Track data consumers |
| `get_column_lineage` | Column-level transformations |
| `trace_material_batch` | Full traceability for FAA |

### Compliance
| Tool | Description |
|------|-------------|
| `check_pii_columns` | Detect PII/sensitive data |
| `validate_aerospace_compliance` | FAA audit readiness |
| `get_data_owner` | Find data steward |

### Quality
| Tool | Description |
|------|-------------|
| `get_quality_tests` | List configured tests |
| `get_quality_results` | Test execution results |

## FAA Audit Support
When auditors request traceability for titanium batch TI-2024-0892:
1. Search batch in catalog
2. Trace upstream to TIMET supplier
3. Get column lineage through QC process
4. Flag PII in operator records
5. Generate compliance report

## Port: 8087
