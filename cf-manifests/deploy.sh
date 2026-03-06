#!/bin/bash
# =============================================================================
# TITAN MANUFACTURING — Full Cloud Foundry Deploy Script
# =============================================================================
#
# Runs the complete deployment pipeline:
#   1. Pre-flight checks (CF login, required tools)
#   2. Create platform services (idempotent)
#   3. Wait for GemFire to provision
#   4. Build all Java applications and React dashboard
#   5. Push all applications to CF
#   6. Create network policies
#   7. Verify deployment health
#
# Usage:
#   ./cf-manifests/deploy.sh [OPTIONS]
#
# Options:
#   --skip-services    Skip service creation (already exist)
#   --skip-build       Skip Maven/npm build (artifacts already built)
#   --skip-network     Skip network policy creation
#   --skip-verify      Skip post-deploy health checks
#   --help             Show this help
#
# Configuration (edit before running):
#   See the CONFIGURATION section below, or set env vars:
#     APPS_DOMAIN          (optional — auto-detected from foundation if not set)
#     (Greenplum UPS replaced by titan-pg Tanzu Postgres until GP is available)
#     GEMFIRE_SERVICE, GEMFIRE_PLAN,
#     RABBITMQ_SERVICE, RABBITMQ_PLAN,
#     GENAI_SERVICE, GENAI_PLAN
# =============================================================================

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

# ─────────────────────────────────────────────────────────────────────────────
# CONFIGURATION — Update these or set as environment variables
# ─────────────────────────────────────────────────────────────────────────────

CF_ORG="${CF_ORG:-tech-marketing}"
CF_SPACE="${CF_SPACE:-titan}"

# APPS_DOMAIN is auto-detected from the foundation after CF login.
# Override by setting this env var if auto-detection picks the wrong domain.
APPS_DOMAIN="${APPS_DOMAIN:-}"

# Service plan names — check `cf marketplace` for available plans on your foundation
GEMFIRE_SERVICE="${GEMFIRE_SERVICE:-p-cloudcache}"
GEMFIRE_PLAN="${GEMFIRE_PLAN:-extra-small}"

RABBITMQ_SERVICE="${RABBITMQ_SERVICE:-p.rabbitmq}"
RABBITMQ_PLAN="${RABBITMQ_PLAN:-on-demand-plan}"

GENAI_SERVICE="${GENAI_SERVICE:-genai}"
GENAI_PLAN="${GENAI_PLAN:-tanzu-gpt-oss-120b-v1025}"

# OpenAI API key — still needed as the credential for the GenAI proxy
OPENAI_API_KEY="${OPENAI_API_KEY:-}"

# ─────────────────────────────────────────────────────────────────────────────
# FLAGS
# ─────────────────────────────────────────────────────────────────────────────

SKIP_SERVICES=false
SKIP_BUILD=false
SKIP_NETWORK=false
SKIP_VERIFY=false

for arg in "$@"; do
    case $arg in
        --skip-services) SKIP_SERVICES=true ;;
        --skip-build)    SKIP_BUILD=true ;;
        --skip-network)  SKIP_NETWORK=true ;;
        --skip-verify)   SKIP_VERIFY=true ;;
        --help)
            sed -n '/^# Usage:/,/^# ====/p' "$0" | grep -v "^# ====" | sed 's/^# //'
            exit 0
            ;;
        *)
            echo "Unknown option: $arg. Use --help for usage."
            exit 1
            ;;
    esac
done

# ─────────────────────────────────────────────────────────────────────────────
# HELPERS
# ─────────────────────────────────────────────────────────────────────────────

STEP=0
ERRORS=0

step() {
    STEP=$((STEP + 1))
    echo ""
    echo "══════════════════════════════════════════════"
    echo "  Step $STEP: $1"
    echo "══════════════════════════════════════════════"
}

ok()   { echo "  ✓ $1"; }
info() { echo "  → $1"; }
warn() { echo "  ⚠ $1"; }
fail() { echo "  ✗ $1"; ERRORS=$((ERRORS + 1)); }

# ─────────────────────────────────────────────────────────────────────────────
# BANNER
# ─────────────────────────────────────────────────────────────────────────────

echo ""
echo "╔══════════════════════════════════════════════╗"
echo "║   TITAN MANUFACTURING 5.0 — CF Deploy        ║"
echo "║   Forging the future with intelligent mfg    ║"
echo "╚══════════════════════════════════════════════╝"
echo ""
echo "  Project root : $PROJECT_ROOT"
echo "  CF Org       : $CF_ORG"
echo "  CF Space     : $CF_SPACE"
echo "  Apps domain  : ${APPS_DOMAIN:-auto-detect from foundation}"
echo "  Skip services: $SKIP_SERVICES"
echo "  Skip build   : $SKIP_BUILD"
echo "  Skip network : $SKIP_NETWORK"
echo "  Skip verify  : $SKIP_VERIFY"

# ─────────────────────────────────────────────────────────────────────────────
# STEP 1: PRE-FLIGHT CHECKS
# ─────────────────────────────────────────────────────────────────────────────

step "Pre-flight Checks"

# Check required tools
for tool in cf mvn npm curl; do
    if command -v "$tool" > /dev/null 2>&1; then
        ok "$tool found ($(command -v $tool))"
    else
        fail "$tool not found — please install it"
    fi
done

# Abort now if missing tools
if [ $ERRORS -gt 0 ]; then
    echo ""
    echo "ERROR: Missing required tools. Aborting."
    exit 1
fi

# Check CF login
if ! cf target > /dev/null 2>&1; then
    echo ""
    echo "ERROR: Not logged into Cloud Foundry. Run 'cf login' first."
    exit 1
fi

# Target the correct org and space
info "Targeting org: $CF_ORG / space: $CF_SPACE"
if ! cf target -o "$CF_ORG" -s "$CF_SPACE" > /dev/null 2>&1; then
    echo ""
    echo "ERROR: Could not target org '$CF_ORG' / space '$CF_SPACE'."
    echo "       Verify the org and space exist and you have access:"
    echo "         cf orgs"
    echo "         cf spaces"
    exit 1
fi
ok "Targeted org: $CF_ORG / space: $CF_SPACE"

echo ""
cf target | grep -E 'api endpoint|org:|space:' | sed 's/^/  /'

# ── Auto-detect apps domain from the foundation ───────────────────────────
if [ -z "$APPS_DOMAIN" ]; then
    info "Auto-detecting apps domain from foundation..."
    # `cf domains` lists shared domains; pick the first shared domain that
    # contains "apps." but is NOT the internal-only "apps.internal" domain
    DETECTED=$(cf domains 2>/dev/null \
        | grep -v "^Getting\|^name\|^---\|private\|apps\.internal" \
        | awk '{print $1}' \
        | grep "^apps\." \
        | head -1)

    if [ -z "$DETECTED" ]; then
        # Fallback: first shared domain that is not apps.internal
        DETECTED=$(cf domains 2>/dev/null \
            | grep -v "^Getting\|^name\|^---\|private\|apps\.internal" \
            | awk '{print $1}' \
            | head -1)
    fi

    if [ -z "$DETECTED" ]; then
        echo ""
        echo "ERROR: Could not auto-detect apps domain. Set APPS_DOMAIN env var and retry."
        echo "       Example: APPS_DOMAIN=apps.your-foundation.com ./cf-manifests/deploy.sh"
        exit 1
    fi

    APPS_DOMAIN="$DETECTED"
    ok "Auto-detected apps domain: $APPS_DOMAIN"
else
    ok "Using provided apps domain: $APPS_DOMAIN"
fi


# Check OpenAI API key is set (used as credential for the GenAI proxy)
if [ -z "$OPENAI_API_KEY" ]; then
    warn "OPENAI_API_KEY is not set — the GenAI service binding may require it"
fi

ok "Pre-flight complete"

# ─────────────────────────────────────────────────────────────────────────────
# STEP 2: CREATE SERVICES
# ─────────────────────────────────────────────────────────────────────────────

if [ "$SKIP_SERVICES" = true ]; then
    step "Create Services (SKIPPED)"
    info "Using --skip-services, assuming titan-gemfire, titan-rabbitmq, titan-ai, titan-pg exist"
else
    step "Create Platform Services"

    # GemFire
    info "Creating titan-gemfire ($GEMFIRE_SERVICE / $GEMFIRE_PLAN)..."
    if cf service titan-gemfire > /dev/null 2>&1; then
        ok "titan-gemfire already exists"
    else
        cf create-service "$GEMFIRE_SERVICE" "$GEMFIRE_PLAN" titan-gemfire
        ok "titan-gemfire created (provisioning asynchronously)"
    fi

    # RabbitMQ
    info "Creating titan-rabbitmq ($RABBITMQ_SERVICE / $RABBITMQ_PLAN)..."
    if cf service titan-rabbitmq > /dev/null 2>&1; then
        ok "titan-rabbitmq already exists"
    else
        cf create-service "$RABBITMQ_SERVICE" "$RABBITMQ_PLAN" titan-rabbitmq
        ok "titan-rabbitmq created"
    fi

    # GenAI
    info "Creating titan-ai ($GENAI_SERVICE / $GENAI_PLAN)..."
    if cf service titan-ai > /dev/null 2>&1; then
        ok "titan-ai already exists"
    else
        cf create-service "$GENAI_SERVICE" "$GENAI_PLAN" titan-ai
        ok "titan-ai created"
    fi

    # Postgres (temporary until Greenplum is available)
    info "Creating titan-pg (postgres / on-demand-postgres-db)..."
    if cf service titan-pg > /dev/null 2>&1; then
        ok "titan-pg already exists"
    else
        cf create-service postgres on-demand-postgres-db titan-pg
        ok "titan-pg created (provisioning asynchronously)"
    fi

    # ── Wait for all async services to finish provisioning ────────────────────
    step "Wait for Services to Provision"

    # Returns the last-operation state for a service: "create succeeded",
    # "create in progress", "create failed", or empty if unknown.
    service_state() {
        cf service "$1" 2>/dev/null \
            | grep -E "^(status:|last operation)" \
            | head -1 \
            | sed 's/^status:[[:space:]]*//' \
            | sed 's/^last operation[[:space:]]*//' \
            | xargs
    }

    wait_for_service() {
        local SVC="$1"
        local TIMEOUT=900   # 15 minutes — RabbitMQ on-demand can be slow
        local ELAPSED=0

        info "Waiting for $SVC to finish provisioning..."
        while true; do
            local STATE
            STATE=$(service_state "$SVC")

            if echo "$STATE" | grep -qi "succeed"; then
                ok "$SVC is ready"
                return 0
            elif echo "$STATE" | grep -qi "failed"; then
                fail "$SVC provisioning failed (state: $STATE)"
                echo "  Check: cf service $SVC"
                return 1
            elif [ $ELAPSED -ge $TIMEOUT ]; then
                fail "Timed out waiting for $SVC after ${TIMEOUT}s (state: $STATE)"
                echo "  Check manually: cf service $SVC"
                echo "  Re-run with --skip-services --skip-build once ready"
                return 1
            else
                info "$SVC — $STATE (${ELAPSED}s elapsed, checking again in 20s...)"
                sleep 20
                ELAPSED=$((ELAPSED + 20))
            fi
        done
    }

    # Check each async service — skip if it was already present (idempotent run)
    PROVISION_FAILED=false
    for SVC in titan-gemfire titan-rabbitmq titan-ai titan-pg; do
        STATE=$(service_state "$SVC")
        if echo "$STATE" | grep -qi "in progress"; then
            wait_for_service "$SVC" || PROVISION_FAILED=true
        elif echo "$STATE" | grep -qi "succeeded"; then
            ok "$SVC already ready"
        elif echo "$STATE" | grep -qi "failed"; then
            fail "$SVC is in a failed state — check: cf service $SVC"
            PROVISION_FAILED=true
        else
            warn "$SVC state unknown ('$STATE') — proceeding anyway"
        fi
    done

    if [ "$PROVISION_FAILED" = true ]; then
        echo ""
        echo "ERROR: One or more services failed to provision. Aborting."
        exit 1
    fi

    ok "All services ready"
fi

# ─────────────────────────────────────────────────────────────────────────────
# STEP 3: BUILD APPLICATIONS
# ─────────────────────────────────────────────────────────────────────────────

if [ "$SKIP_BUILD" = true ]; then
    step "Build Applications (SKIPPED)"
    info "Using --skip-build, assuming artifacts are already built"
else
    step "Build Java Applications"
    cd "$PROJECT_ROOT"
    info "Running mvn clean package -DskipTests..."
    mvn clean package -DskipTests -q
    ok "titan-orchestrator"
    ok "sensor-mcp-server"
    ok "maintenance-mcp-server"
    ok "inventory-mcp-server"
    ok "logistics-mcp-server"
    ok "sensor-data-generator"

    step "Build React Dashboard"
    cd "$PROJECT_ROOT/titan-dashboard"
    if [ ! -d "node_modules" ]; then
        info "Installing npm dependencies..."
        npm ci --legacy-peer-deps -q
    fi
    npm run build --silent
    cp Staticfile dist/
    ok "titan-dashboard (dist/)"
    cd "$PROJECT_ROOT"

    # Verify all artifacts exist
    step "Verify Build Artifacts"
    MISSING=0
    check_artifact() {
        if [ -f "$PROJECT_ROOT/$1" ]; then
            SIZE=$(du -h "$PROJECT_ROOT/$1" | cut -f1)
            ok "$2 ($SIZE)"
        else
            fail "$2 — NOT FOUND at $1"
            MISSING=$((MISSING + 1))
        fi
    }

    check_artifact "titan-orchestrator/target/titan-orchestrator.jar"       "titan-orchestrator"
    check_artifact "sensor-mcp-server/target/sensor-mcp-server.jar"         "sensor-mcp-server"
    check_artifact "maintenance-mcp-server/target/maintenance-mcp-server.jar" "maintenance-mcp-server"
    check_artifact "inventory-mcp-server/target/inventory-mcp-server.jar"   "inventory-mcp-server"
    check_artifact "logistics-mcp-server/target/logistics-mcp-server.jar"   "logistics-mcp-server"
    check_artifact "sensor-data-generator/target/sensor-data-generator.jar" "sensor-data-generator"

    if [ -f "$PROJECT_ROOT/titan-dashboard/dist/index.html" ]; then
        ok "titan-dashboard (dist/)"
    else
        fail "titan-dashboard dist/ — not found"
        MISSING=$((MISSING + 1))
    fi

    if [ $MISSING -gt 0 ]; then
        echo ""
        echo "ERROR: $MISSING build artifact(s) missing. Aborting deploy."
        exit 1
    fi
fi

# ─────────────────────────────────────────────────────────────────────────────
# STEP 4: UPDATE VARS FILE
# ─────────────────────────────────────────────────────────────────────────────

step "Update vars.yml"
cd "$PROJECT_ROOT"

# Write vars.yml with all substitution values
cat > cf-manifests/vars.yml << EOF
# =============================================================================
# TITAN MANUFACTURING — Cloud Foundry Variables
# =============================================================================
# Auto-generated by deploy.sh on $(date)
# =============================================================================

apps-domain: ${APPS_DOMAIN}
EOF

ok "vars.yml written (apps-domain: ${APPS_DOMAIN})"

# ─────────────────────────────────────────────────────────────────────────────
# STEP 5: PUSH APPLICATIONS
# ─────────────────────────────────────────────────────────────────────────────

# ─────────────────────────────────────────────────────────────────────────────
# STEP 5: PUSH AGENTS + SUPPORTING APPS (before orchestrator)
# ─────────────────────────────────────────────────────────────────────────────
# The orchestrator connects to agents at startup via .apps.internal DNS.
# Agents must be running and network policies in place before the orchestrator
# starts, otherwise MCP client initialization will fail with UnresolvedAddressException.

step "Push Agents and Supporting Apps"
cd "$PROJECT_ROOT"
info "Pushing MCP agents, sensor generator, and dashboard first..."

for APP in titan-sensor-agent titan-maintenance-agent titan-inventory-agent titan-logistics-agent titan-sensor-generator titan-dashboard; do
    info "Pushing $APP..."
    cf push "$APP" -f cf-manifests/manifest.yml --vars-file cf-manifests/vars.yml
    ok "$APP pushed"
done

# ─────────────────────────────────────────────────────────────────────────────
# STEP 6: NETWORK POLICIES
# ─────────────────────────────────────────────────────────────────────────────

if [ "$SKIP_NETWORK" = true ]; then
    step "Network Policies (SKIPPED)"
    info "Using --skip-network"
else
    step "Create Network Policies"
    info "Allowing orchestrator to reach internal MCP agents..."

    add_policy() {
        local SRC=$1 DST=$2 PORT=$3
        if cf network-policies 2>/dev/null | grep -q "$SRC.*$DST.*$PORT"; then
            ok "Policy $SRC → $DST:$PORT already exists"
        else
            cf add-network-policy "$SRC" "$DST" --protocol tcp --port "$PORT"
            ok "Policy $SRC → $DST:$PORT created"
        fi
    }

    add_policy titan-orchestrator titan-sensor-agent      8080
    add_policy titan-orchestrator titan-maintenance-agent 8080
    add_policy titan-orchestrator titan-inventory-agent   8080
    add_policy titan-orchestrator titan-logistics-agent   8080
fi

# ─────────────────────────────────────────────────────────────────────────────
# STEP 7: PUSH ORCHESTRATOR (after agents are up and policies are set)
# ─────────────────────────────────────────────────────────────────────────────

step "Push Orchestrator"
cd "$PROJECT_ROOT"
info "Pushing titan-orchestrator (agents must be reachable now)..."
cf push titan-orchestrator -f cf-manifests/manifest.yml --vars-file cf-manifests/vars.yml
ok "titan-orchestrator pushed"

# ─────────────────────────────────────────────────────────────────────────────
# STEP 8: VERIFY DEPLOYMENT
# ─────────────────────────────────────────────────────────────────────────────

if [ "$SKIP_VERIFY" = true ]; then
    step "Health Checks (SKIPPED)"
    info "Using --skip-verify"
else
    step "Health Checks"
    info "Waiting 15s for apps to fully start..."
    sleep 15

    check_health() {
        local NAME=$1 URL=$2
        HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" --max-time 10 "$URL" 2>/dev/null || echo "000")
        if [ "$HTTP_CODE" = "200" ]; then
            ok "$NAME → HTTP $HTTP_CODE ($URL)"
        else
            warn "$NAME → HTTP $HTTP_CODE ($URL) — may still be starting"
        fi
    }

    check_health "titan-orchestrator health" "https://titan-orchestrator.${APPS_DOMAIN}/actuator/health"
    check_health "titan-dashboard"           "https://titan-dashboard.${APPS_DOMAIN}/"
    check_health "titan-sensor-generator"    "https://titan-sensor-generator.${APPS_DOMAIN}/actuator/health"
fi

# ─────────────────────────────────────────────────────────────────────────────
# FINAL SUMMARY
# ─────────────────────────────────────────────────────────────────────────────

echo ""
echo "╔══════════════════════════════════════════════╗"
echo "║   Deployment Complete                        ║"
echo "╚══════════════════════════════════════════════╝"
echo ""
echo "  App Status:"
cf apps | tail -n +3 | sed 's/^/    /'
echo ""
echo "  Access Points:"
echo "    Dashboard  → https://titan-dashboard.${APPS_DOMAIN}"
echo "    API        → https://titan-orchestrator.${APPS_DOMAIN}/api/chat"
echo "    Generator  → https://titan-sensor-generator.${APPS_DOMAIN}/api/generator/status"
echo ""
echo "  Verify services:"
echo "    cf services"
echo ""
echo "  Watch logs:"
echo "    cf logs titan-orchestrator --recent"
echo "    cf logs titan-maintenance-agent --recent"
echo ""

if [ $ERRORS -gt 0 ]; then
    echo "  ⚠ Completed with $ERRORS warning(s). Review output above."
    exit 1
fi

echo "  ✓ All steps completed successfully."
echo ""
