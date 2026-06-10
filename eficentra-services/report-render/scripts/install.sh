#!/usr/bin/env bash
set -e

APP_NAME="eficentra-report-render"
SRC_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
INSTALL_DIR="${INSTALL_DIR:-/opt/eficentra-report-render}"
SERVICE_USER="${SERVICE_USER:-thingsboard}"
SERVICE_GROUP="${SERVICE_GROUP:-thingsboard}"
PORT="${PORT:-3000}"

echo "Installing $APP_NAME..."
echo "Source: $SRC_DIR"
echo "Target: $INSTALL_DIR"
echo "User: $SERVICE_USER"
echo "Port: $PORT"

sudo mkdir -p "$INSTALL_DIR"
sudo rsync -a --delete "$SRC_DIR/" "$INSTALL_DIR/"

cd "$INSTALL_DIR"
sudo npm install --omit=dev

sudo chown -R "$SERVICE_USER:$SERVICE_GROUP" "$INSTALL_DIR"

sudo tee /etc/systemd/system/eficentra-report-render.service > /dev/null <<EOF
[Unit]
Description=Eficentra Report Render Service
After=network.target

[Service]
Type=simple
WorkingDirectory=$INSTALL_DIR
ExecStart=/usr/bin/node $INSTALL_DIR/src/server.js
Restart=always
RestartSec=5
User=$SERVICE_USER
Environment=PORT=$PORT

[Install]
WantedBy=multi-user.target
EOF

sudo systemctl daemon-reload
sudo systemctl enable eficentra-report-render
sudo systemctl restart eficentra-report-render

echo "Installed."
sudo systemctl status eficentra-report-render --no-pager