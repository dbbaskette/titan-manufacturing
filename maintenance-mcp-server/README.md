# Maintenance MCP Server

Predictive maintenance agent — critical after Titan's $12M Phoenix incident.

## Role at Titan
- Predicts equipment failures before they cascade
- Estimates Remaining Useful Life (RUL) for all CNC equipment
- Schedules preventive maintenance to avoid unplanned downtime
- Integrates with inventory for parts availability

## Tools
| Tool | Description |
|------|-------------|
| `predict_failure` | ML-based failure probability prediction |
| `estimate_rul` | Remaining Useful Life calculation |
| `get_failure_probability` | Real-time failure risk score |
| `schedule_maintenance` | Create maintenance work order |
| `get_maintenance_history` | Equipment maintenance records |
| `check_parts_availability` | Query inventory for required parts |
| `get_similar_incidents` | Find historical similar failures |
| `get_recommended_actions` | AI-suggested next steps |

## ML Models
- Vibration anomaly classifier (trained on Phoenix incident data)
- Temperature trend predictor
- RUL regression model (NASA C-MAPSS based)

## The Phoenix Incident
In 2023, a bearing failure in PHX-CNC-007 cascaded to adjacent machines,
causing $12M in unplanned downtime. This agent was built to ensure it
never happens again.

## Port: 8082
