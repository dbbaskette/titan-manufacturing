#!/bin/bash
# =============================================================================
# TITAN MANUFACTURING — Build Script for Cloud Foundry Deployment
# =============================================================================
# Builds all Java applications and the React dashboard
# =============================================================================

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

echo "=============================================="
echo "  TITAN MANUFACTURING — Build for CF"
echo "=============================================="
echo "Project root: $PROJECT_ROOT"
echo ""

cd "$PROJECT_ROOT"

# ─────────────────────────────────────────────────────────────────────────────
# Build Java Applications
# ─────────────────────────────────────────────────────────────────────────────
echo "[1/2] Building Java applications with Maven..."
mvn clean package -DskipTests -q

echo "      ✓ titan-orchestrator"
echo "      ✓ sensor-mcp-server"
echo "      ✓ maintenance-mcp-server"
echo "      ✓ inventory-mcp-server"
echo "      ✓ logistics-mcp-server"
echo "      ✓ sensor-data-generator"
echo ""

# ─────────────────────────────────────────────────────────────────────────────
# Build React Dashboard
# ─────────────────────────────────────────────────────────────────────────────
echo "[2/2] Building React dashboard..."
cd titan-dashboard

# Install dependencies if needed
if [ ! -d "node_modules" ]; then
    echo "      Installing npm dependencies..."
    npm ci --legacy-peer-deps
fi

# Build production bundle
npm run build

# Copy Staticfile to dist for CF deployment
cp Staticfile dist/

echo "      ✓ titan-dashboard (dist/)"
echo ""

cd "$PROJECT_ROOT"

# ─────────────────────────────────────────────────────────────────────────────
# Verify Build Artifacts
# ─────────────────────────────────────────────────────────────────────────────
echo "=============================================="
echo "  Build Complete — Verifying Artifacts"
echo "=============================================="

MISSING=0

check_artifact() {
    if [ -f "$1" ]; then
        SIZE=$(du -h "$1" | cut -f1)
        echo "  ✓ $2 ($SIZE)"
    else
        echo "  ✗ $2 — NOT FOUND: $1"
        MISSING=$((MISSING + 1))
    fi
}

check_artifact "titan-orchestrator/target/titan-orchestrator.jar" "titan-orchestrator"
check_artifact "sensor-mcp-server/target/sensor-mcp-server.jar" "sensor-mcp-server"
check_artifact "maintenance-mcp-server/target/maintenance-mcp-server.jar" "maintenance-mcp-server"
check_artifact "inventory-mcp-server/target/inventory-mcp-server.jar" "inventory-mcp-server"
check_artifact "logistics-mcp-server/target/logistics-mcp-server.jar" "logistics-mcp-server"
check_artifact "sensor-data-generator/target/sensor-data-generator.jar" "sensor-data-generator"

if [ -d "titan-dashboard/dist" ] && [ -f "titan-dashboard/dist/index.html" ]; then
    echo "  ✓ titan-dashboard (dist/)"
else
    echo "  ✗ titan-dashboard — dist/ not found"
    MISSING=$((MISSING + 1))
fi

echo ""

if [ $MISSING -gt 0 ]; then
    echo "ERROR: $MISSING artifact(s) missing. Check build output above."
    exit 1
fi

echo "=============================================="
echo "  Ready for Deployment"
echo "=============================================="
echo ""
echo "Next steps:"
echo "  1. Ensure services are created: ./cf-manifests/create-services.sh"
echo "  2. Deploy: cf push -f cf-manifests/manifest.yml --vars-file cf-manifests/vars.yml"
echo "  3. Create network policies (see SERVICES.md)"
echo ""
