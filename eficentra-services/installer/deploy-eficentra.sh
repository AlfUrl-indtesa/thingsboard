#!/usr/bin/env bash
set -euo pipefail

# ==========================================================
# Eficentra Deployment Script
# Installs/updates:
# - Eficentra ThingsBoard JAR
# - Eficentra report render service
# - Report storage directories
# - System permissions
# ==========================================================

EFICENTRA_ROOT="${EFICENTRA_ROOT:-/home/vboxuser/Eficentra}"

TB_SERVICE="${TB_SERVICE:-thingsboard}"
TB_USER="${TB_USER:-thingsboard}"
TB_GROUP="${TB_GROUP:-thingsboard}"

TB_JAR_SOURCE="${TB_JAR_SOURCE:-$EFICENTRA_ROOT/application/target/thingsboard-4.4.0-SNAPSHOT-boot.jar}"
TB_JAR_TARGET="${TB_JAR_TARGET:-/usr/share/thingsboard/bin/thingsboard.jar}"
TB_CONFIG_FILE="${TB_CONFIG_FILE:-/etc/thingsboard/conf/thingsboard.yml}"

REPORT_STORAGE_DIR="${REPORT_STORAGE_DIR:-/opt/thingsboard/data/reports}"
RENDER_SERVICE_DIR="${RENDER_SERVICE_DIR:-$EFICENTRA_ROOT/eficentra-services/report-render}"
RENDER_SERVICE_NAME="${RENDER_SERVICE_NAME:-eficentra-report-render}"
RENDER_HEALTH_URL="${RENDER_HEALTH_URL:-http://127.0.0.1:3000/health}"

BACKUP_DIR="${BACKUP_DIR:-/opt/eficentra-backups}"
BUILD_BEFORE_DEPLOY="${BUILD_BEFORE_DEPLOY:-false}"

timestamp() {
  date +"%Y%m%d%H%M%S"
}

log() {
  echo ""
  echo "============================================================"
  echo "$1"
  echo "============================================================"
}

fail() {
  echo ""
  echo "ERROR: $1"
  exit 1
}

require_file() {
  local file="$1"
  local message="$2"

  if [ ! -f "$file" ]; then
    fail "$message: $file"
  fi
}

require_dir() {
  local dir="$1"
  local message="$2"

  if [ ! -d "$dir" ]; then
    fail "$message: $dir"
  fi
}

run_optional_status() {
  local service="$1"

  if systemctl list-unit-files | grep -q "^${service}.service"; then
    sudo systemctl status "$service" --no-pager || true
  else
    echo "Service not found: $service"
  fi
}

log "Eficentra deployment configuration"

echo "EFICENTRA_ROOT=$EFICENTRA_ROOT"
echo "TB_SERVICE=$TB_SERVICE"
echo "TB_USER=$TB_USER"
echo "TB_GROUP=$TB_GROUP"
echo "TB_JAR_SOURCE=$TB_JAR_SOURCE"
echo "TB_JAR_TARGET=$TB_JAR_TARGET"
echo "TB_CONFIG_FILE=$TB_CONFIG_FILE"
echo "REPORT_STORAGE_DIR=$REPORT_STORAGE_DIR"
echo "RENDER_SERVICE_DIR=$RENDER_SERVICE_DIR"
echo "RENDER_SERVICE_NAME=$RENDER_SERVICE_NAME"
echo "RENDER_HEALTH_URL=$RENDER_HEALTH_URL"
echo "BACKUP_DIR=$BACKUP_DIR"
echo "BUILD_BEFORE_DEPLOY=$BUILD_BEFORE_DEPLOY"

log "Pre-flight checks"

require_dir "$EFICENTRA_ROOT" "Eficentra root directory not found"
require_dir "$RENDER_SERVICE_DIR" "Report render service directory not found"
require_file "$RENDER_SERVICE_DIR/scripts/install.sh" "Report render install script not found"

if ! command -v java >/dev/null 2>&1; then
  fail "Java is not installed or not available in PATH"
fi

if ! command -v node >/dev/null 2>&1; then
  fail "Node.js is not installed or not available in PATH"
fi

if ! command -v npm >/dev/null 2>&1; then
  fail "npm is not installed or not available in PATH"
fi

if ! command -v systemctl >/dev/null 2>&1; then
  fail "systemctl is not available"
fi

if ! id "$TB_USER" >/dev/null 2>&1; then
  fail "System user does not exist: $TB_USER"
fi

if [ "$BUILD_BEFORE_DEPLOY" = "true" ]; then
  log "Building Eficentra before deployment"

  cd "$EFICENTRA_ROOT"
  mvn clean install -DskipTests -rf :ui-ngx
fi

require_file "$TB_JAR_SOURCE" "Eficentra ThingsBoard boot JAR not found"

log "Creating backup directory"

sudo mkdir -p "$BACKUP_DIR"

log "Stopping ThingsBoard"

if systemctl is-active --quiet "$TB_SERVICE"; then
  sudo systemctl stop "$TB_SERVICE"
else
  echo "$TB_SERVICE is not active. Continuing."
fi

log "Backing up current ThingsBoard JAR"

if [ -f "$TB_JAR_TARGET" ]; then
  sudo cp "$TB_JAR_TARGET" "$BACKUP_DIR/thingsboard.jar.bak.$(timestamp)"
  echo "Backup created in $BACKUP_DIR"
else
  echo "No existing ThingsBoard JAR found at $TB_JAR_TARGET"
fi

log "Installing Eficentra ThingsBoard JAR"

sudo cp "$TB_JAR_SOURCE" "$TB_JAR_TARGET"
sudo chown "$TB_USER:$TB_GROUP" "$TB_JAR_TARGET"
sudo chmod 644 "$TB_JAR_TARGET"

log "Preparing report storage directory"

sudo mkdir -p "$REPORT_STORAGE_DIR"
sudo chown -R "$TB_USER:$TB_GROUP" "$REPORT_STORAGE_DIR"
sudo chmod -R 750 "$REPORT_STORAGE_DIR"

log "Checking ThingsBoard report configuration"

if [ -f "$TB_CONFIG_FILE" ]; then
  if grep -q "report:" "$TB_CONFIG_FILE"; then
    echo "Report configuration block exists in $TB_CONFIG_FILE"
  else
    echo "WARNING: report configuration block was not found in $TB_CONFIG_FILE"
    echo "Make sure thingsboard.yml contains report.storage and report.render settings."
  fi
else
  echo "WARNING: ThingsBoard config file not found: $TB_CONFIG_FILE"
fi

log "Installing Eficentra report render service"

cd "$RENDER_SERVICE_DIR"

chmod +x scripts/install.sh
./scripts/install.sh

log "Starting ThingsBoard"

sudo systemctl start "$TB_SERVICE"

log "Waiting for services"

sleep 8

log "Checking service status"

run_optional_status "$TB_SERVICE"
run_optional_status "$RENDER_SERVICE_NAME"

log "Checking render health endpoint"

if curl -fsS "$RENDER_HEALTH_URL"; then
  echo ""
  echo "Render service health check OK"
else
  echo ""
  echo "WARNING: Render service health check failed: $RENDER_HEALTH_URL"
fi

log "Deployment completed"

echo "Eficentra was deployed successfully."
echo ""
echo "Useful checks:"
echo "  sudo systemctl status $TB_SERVICE --no-pager"
echo "  sudo systemctl status $RENDER_SERVICE_NAME --no-pager"
echo "  curl $RENDER_HEALTH_URL"