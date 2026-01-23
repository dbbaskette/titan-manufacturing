# Sensor MCP Server

IoT sensor data agent for Titan Manufacturing's 12 global facilities.

## Role at Titan
- Monitors 600+ CNC machines across 12 facilities
- Detects anomalies like the Phoenix CNC-007 vibration pattern
- Provides real-time equipment status to maintenance team

## MCP Tools

| Tool | Description |
|------|-------------|
| `list_equipment` | List equipment by facility or across all facilities |
| `get_equipment_status` | Get current health status and readings for equipment |
| `get_sensor_readings` | Query historical sensor data with time range |
| `get_facility_status` | Facility-wide health overview |
| `detect_anomaly` | Check for anomalies in sensor readings |

## Data Store
- **Greenplum** (PostgreSQL-compatible) - single unified data store
- Queries `equipment`, `sensor_readings`, and `anomalies` tables

## Monitored Sensor Types
- Vibration (mm/s)
- Temperature (°C)
- RPM
- Torque (Nm)
- Pressure (PSI)

## Configuration

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:15432/postgres
    username: gpadmin
    password: VMware1!
```

## Running Locally

```bash
# Build
mvn clean package -DskipTests

# Run
java -jar target/sensor-mcp-server.jar
```

## Docker

```bash
# Build image
docker build -t titan-sensor-agent .

# Run container
docker run -p 8081:8081 \
  -e GREENPLUM_URL=jdbc:postgresql://host.docker.internal:15432/postgres \
  titan-sensor-agent
```

## Port: 8081

## MCP Endpoint: /mcp
