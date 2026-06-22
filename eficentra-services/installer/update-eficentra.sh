#!/usr/bin/env bash
set -euo pipefail

EFICENTRA_ROOT="${EFICENTRA_ROOT:-/home/vboxuser/Eficentra}"
DEPLOY_SCRIPT="$EFICENTRA_ROOT/eficentra-services/installer/deploy-eficentra.sh"

echo ""
echo "============================================================"
echo " Eficentra update"
echo "============================================================"

cd "$EFICENTRA_ROOT"

echo ""
echo "Pulling latest changes..."
git pull

echo ""
echo "Building Eficentra..."
mvn clean install -DskipTests -rf :ui-ngx

echo ""
echo "Deploying Eficentra..."
chmod +x "$DEPLOY_SCRIPT"
"$DEPLOY_SCRIPT"

echo ""
echo "Eficentra update completed."