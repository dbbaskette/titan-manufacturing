# Titan Manufacturing — Cloud Foundry Services Setup

This document describes all the services required to deploy Titan Manufacturing on Cloud Foundry.

---

## Quick Reference

| Service Name | Service Type | Create Command |
|--------------|--------------|----------------|
| `titan-gemfire` | VMware GemFire | `cf create-service p-cloudcache <plan> titan-gemfire` |
| `titan-rabbitmq` | RabbitMQ | `cf create-service p-rabbitmq <plan> titan-rabbitmq` |
| `titan-ai` | GenAI | `cf create-service genai <plan> titan-ai` |
| `titan-greenplum-ups` | User-Provided | `cf cups titan-greenplum-ups -p '{...}'` |

---

## Service Binding Summary

| Application | Services Bound |
|-------------|----------------|
| titan-orchestrator | titan-ai |
| titan-sensor-agent | titan-rabbitmq, titan-greenplum-ups |
| titan-maintenance-agent | titan-rabbitmq, titan-gemfire, titan-greenplum-ups |
| titan-inventory-agent | titan-greenplum-ups |
| titan-logistics-agent | titan-greenplum-ups |
| titan-sensor-generator | titan-rabbitmq |
| titan-dashboard | (none - static files) |

---

## Platform Services

### 1. titan-gemfire (GemFire)

Real-time ML model scoring and caching for predictive maintenance.

```bash
# List available GemFire plans
cf marketplace -e p-cloudcache

# Create the service instance
cf create-service p-cloudcache <plan-name> titan-gemfire

# Wait for provisioning (can take several minutes)
cf service titan-gemfire
```

**Used by:** maintenance-agent  
**Purpose:** PMML model deployment, real-time scoring, equipment state caching

---

### 2. titan-rabbitmq (RabbitMQ)

Message broker for IoT sensor data streaming (MQTT protocol).

```bash
# List available RabbitMQ plans
cf marketplace -e p-rabbitmq

# Create the service instance
cf create-service p-rabbitmq <plan-name> titan-rabbitmq
```

**Used by:** sensor-agent, maintenance-agent, sensor-generator  
**Purpose:** MQTT messaging for sensor telemetry (`titan/sensors/#` topic)

---

### 3. titan-ai (GenAI Service)

Platform AI service for LLM access.

```bash
# List available GenAI plans
cf marketplace -e genai

# Create the service instance
cf create-service genai <plan-name> titan-ai
```

**Used by:** orchestrator  
**Purpose:** Natural language query processing, agent coordination

---

## External Services (User-Provided)

### 4. titan-greenplum-ups (Greenplum Database)

Greenplum is hosted off-platform. Create a user-provided service to inject credentials.

```bash
cf create-user-provided-service titan-greenplum-ups -p '{
  "uri": "jdbc:postgresql://YOUR_HOST:5432/titan-manufacturing",
  "username": "gpadmin",
  "password": "YOUR_PASSWORD",
  "hostname": "YOUR_HOST",
  "port": "5432",
  "database": "titan-manufacturing"
}'
```

**Used by:** All MCP agents (sensor, maintenance, inventory, logistics)  
**Requirements:**
- Greenplum 7.x with extensions: pgvector, MADlib, PostGIS
- Database `titan-manufacturing` initialized with `config/greenplum/init.sql`

---

## Deployment Steps

### 1. Create Services

```bash
# Edit the script with your Greenplum credentials
vim cf-manifests/create-services.sh

# Run the service creation script
./cf-manifests/create-services.sh

# Wait for GemFire to provision
watch cf service titan-gemfire
```

### 2. Build Applications

```bash
./cf-manifests/build-apps.sh
```

### 3. Update Variables

Edit `cf-manifests/vars.yml` with your apps domain:

```yaml
apps-domain: apps.your-foundation.com
```

### 4. Deploy

```bash
cf push -f cf-manifests/manifest.yml --vars-file cf-manifests/vars.yml
```

### 5. Create Network Policies

The MCP agents use internal routes. Create network policies to allow the orchestrator to reach them:

```bash
cf add-network-policy titan-orchestrator titan-sensor-agent --protocol tcp --port 8080
cf add-network-policy titan-orchestrator titan-maintenance-agent --protocol tcp --port 8080
cf add-network-policy titan-orchestrator titan-inventory-agent --protocol tcp --port 8080
cf add-network-policy titan-orchestrator titan-logistics-agent --protocol tcp --port 8080
```

### 6. Verify Deployment

```bash
# Check all apps are running
cf apps

# Check service bindings
cf services

# Test orchestrator health
curl https://titan-orchestrator.apps.your-foundation.com/actuator/health

# Test dashboard
open https://titan-dashboard.apps.your-foundation.com
```

---

## Application Configuration

Each application includes an `application-cloud.yml` profile that reads credentials from VCAP_SERVICES:

| Application | Cloud Config File |
|-------------|-------------------|
| titan-orchestrator | `titan-orchestrator/src/main/resources/application-cloud.yml` |
| sensor-mcp-server | `sensor-mcp-server/src/main/resources/application-cloud.yml` |
| maintenance-mcp-server | `maintenance-mcp-server/src/main/resources/application-cloud.yml` |
| inventory-mcp-server | `inventory-mcp-server/src/main/resources/application-cloud.yml` |
| logistics-mcp-server | `logistics-mcp-server/src/main/resources/application-cloud.yml` |
| sensor-data-generator | `sensor-data-generator/src/main/resources/application-cloud.yml` |

The `SPRING_PROFILES_ACTIVE=cloud` environment variable activates these profiles.

---

## Troubleshooting

### Service not found in marketplace
```bash
cf marketplace
# If service not listed, contact platform team to enable it
```

### Greenplum connection fails
```bash
# Test connectivity
cf ssh titan-sensor-agent -c "nc -zv YOUR_GREENPLUM_HOST 5432"

# Check bound credentials
cf env titan-sensor-agent | grep -A10 titan-greenplum-ups
```

### GemFire connection fails
```bash
# Check service status
cf service titan-gemfire

# Check maintenance-agent logs
cf logs titan-maintenance-agent --recent | grep -i gemfire
```

### RabbitMQ/MQTT connection fails
```bash
# Check service credentials
cf env titan-sensor-generator | grep -A20 titan-rabbitmq

# Verify MQTT protocol is available in credentials
```

### Apps can't reach internal routes
```bash
# Verify network policies
cf network-policies

# Add missing policies
cf add-network-policy titan-orchestrator titan-sensor-agent --protocol tcp --port 8080
```

---

## Files Reference

| File | Purpose |
|------|---------|
| `cf-manifests/manifest.yml` | Main deployment manifest |
| `cf-manifests/vars.yml` | Variable substitutions |
| `cf-manifests/create-services.sh` | Service creation script |
| `cf-manifests/build-apps.sh` | Application build script |
| `titan-dashboard/Staticfile` | Static buildpack config for SPA |
