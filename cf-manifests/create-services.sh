#!/bin/bash
# =============================================================================
# TITAN MANUFACTURING — Cloud Foundry Service Creation Script
# =============================================================================
# Usage: ./create-services.sh
#
# Before running:
#   1. Update GREENPLUM_* variables below with your actual values
#   2. Verify service plan names match your foundation (cf marketplace)
# =============================================================================

set -e

# ─────────────────────────────────────────────────────────────────────────────
# CONFIGURATION — Update these values for your environment
# ─────────────────────────────────────────────────────────────────────────────

CF_ORG="${CF_ORG:-tech-marketing}"
CF_SPACE="${CF_SPACE:-titan}"

# Greenplum (off-platform) connection details
GREENPLUM_HOST="greenplum.example.com"
GREENPLUM_PORT="5432"
GREENPLUM_DATABASE="titan-manufacturing"
GREENPLUM_USER="gpadmin"
GREENPLUM_PASSWORD="CHANGE_ME"

# Service plan names (check `cf marketplace` for available plans)
GEMFIRE_SERVICE="p-cloudcache"
GEMFIRE_PLAN="extra-small"

RABBITMQ_SERVICE="p.rabbitmq"
RABBITMQ_PLAN="on-demand-plan"

GENAI_SERVICE="genai"
GENAI_PLAN="tanzu-gpt-oss-120b-v1025"

# ─────────────────────────────────────────────────────────────────────────────
# SERVICE CREATION
# ─────────────────────────────────────────────────────────────────────────────

echo "=============================================="
echo "  TITAN MANUFACTURING — Service Setup"
echo "=============================================="
echo ""

# Check if logged in
if ! cf target > /dev/null 2>&1; then
    echo "ERROR: Not logged into Cloud Foundry. Run 'cf login' first."
    exit 1
fi

# Target org and space
echo "Targeting org: $CF_ORG / space: $CF_SPACE"
if ! cf target -o "$CF_ORG" -s "$CF_SPACE" > /dev/null 2>&1; then
    echo "ERROR: Could not target org '$CF_ORG' / space '$CF_SPACE'."
    exit 1
fi

echo "Target: $(cf target | grep -E 'org:|space:')"
echo ""

# ─────────────────────────────────────────────────────────────────────────────
# 1. GemFire Service
# ─────────────────────────────────────────────────────────────────────────────
echo "[1/3] Creating GemFire service: titan-gemfire"
if cf service titan-gemfire > /dev/null 2>&1; then
    echo "      → Already exists, skipping"
else
    cf create-service "$GEMFIRE_SERVICE" "$GEMFIRE_PLAN" titan-gemfire
    echo "      → Created (may take several minutes to provision)"
fi
echo ""

# ─────────────────────────────────────────────────────────────────────────────
# 2. RabbitMQ Service
# ─────────────────────────────────────────────────────────────────────────────
echo "[2/4] Creating RabbitMQ service: titan-rabbitmq"
if cf service titan-rabbitmq > /dev/null 2>&1; then
    echo "      → Already exists, skipping"
else
    cf create-service "$RABBITMQ_SERVICE" "$RABBITMQ_PLAN" titan-rabbitmq
    echo "      → Created"
fi
echo ""

# ─────────────────────────────────────────────────────────────────────────────
# 3. GenAI Service
# ─────────────────────────────────────────────────────────────────────────────
echo "[3/4] Creating GenAI service: titan-ai"
if cf service titan-ai > /dev/null 2>&1; then
    echo "      → Already exists, skipping"
else
    cf create-service "$GENAI_SERVICE" "$GENAI_PLAN" titan-ai
    echo "      → Created"
fi
echo ""

# ─────────────────────────────────────────────────────────────────────────────
# 4. Greenplum User-Provided Service
# ─────────────────────────────────────────────────────────────────────────────
echo "[4/4] Creating Greenplum user-provided service: titan-greenplum-ups"
if cf service titan-greenplum-ups > /dev/null 2>&1; then
    echo "      → Already exists, skipping"
    echo "      → To update credentials, run:"
    echo "         cf update-user-provided-service titan-greenplum-ups -p '{...}'"
else
    cf create-user-provided-service titan-greenplum-ups -p "{
        \"uri\": \"jdbc:postgresql://${GREENPLUM_HOST}:${GREENPLUM_PORT}/${GREENPLUM_DATABASE}\",
        \"hostname\": \"${GREENPLUM_HOST}\",
        \"port\": \"${GREENPLUM_PORT}\",
        \"database\": \"${GREENPLUM_DATABASE}\",
        \"username\": \"${GREENPLUM_USER}\",
        \"password\": \"${GREENPLUM_PASSWORD}\"
    }"
    echo "      → Created"
fi
echo ""

# ─────────────────────────────────────────────────────────────────────────────
# SUMMARY
# ─────────────────────────────────────────────────────────────────────────────
echo "=============================================="
echo "  Service Creation Complete"
echo "=============================================="
echo ""
cf services
echo ""
echo "Next steps:"
echo "  1. Wait for titan-gemfire to finish provisioning (cf service titan-gemfire)"
echo "  2. Build applications: ./build-apps.sh"
echo "  3. Deploy applications: cf push -f cf-manifests/manifest.yml"
echo ""
