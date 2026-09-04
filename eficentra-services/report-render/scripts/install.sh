#!/usr/bin/env bash
#
# Copyright © 2016-2026 The Thingsboard Authors
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

set -e

APP_NAME="eficentra-report-render"
SRC_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
INSTALL_DIR="${INSTALL_DIR:-/opt/eficentra-report-render}"
SERVICE_USER="${SERVICE_USER:-thingsboard}"
SERVICE_GROUP="${SERVICE_GROUP:-thingsboard}"
PORT="${PORT:-3000}"
RENDER_WORKERS="${RENDER_WORKERS:-2}"
RENDER_CONCURRENCY="${RENDER_CONCURRENCY:-1}"
RENDER_MAX_QUEUE="${RENDER_MAX_QUEUE:-20}"
RENDER_QUEUE_TIMEOUT_MS="${RENDER_QUEUE_TIMEOUT_MS:-300000}"
RENDER_MAX_PAYLOAD_MB="${RENDER_MAX_PAYLOAD_MB:-50}"

echo "Installing $APP_NAME..."
echo "Source: $SRC_DIR"
echo "Target: $INSTALL_DIR"
echo "User: $SERVICE_USER"
echo "Port: $PORT"
echo "Workers: $RENDER_WORKERS"
echo "Concurrency per worker: $RENDER_CONCURRENCY"
echo "Maximum queue per worker: $RENDER_MAX_QUEUE"
echo "Queue timeout: $RENDER_QUEUE_TIMEOUT_MS ms"
echo "Maximum payload: $RENDER_MAX_PAYLOAD_MB MB"

sudo mkdir -p "$INSTALL_DIR"
sudo rsync -a --delete "$SRC_DIR/" "$INSTALL_DIR/"

cd "$INSTALL_DIR"
sudo npm ci --omit=dev --ignore-scripts

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
Environment=RENDER_WORKERS=$RENDER_WORKERS
Environment=RENDER_CONCURRENCY=$RENDER_CONCURRENCY
Environment=RENDER_MAX_QUEUE=$RENDER_MAX_QUEUE
Environment=RENDER_QUEUE_TIMEOUT_MS=$RENDER_QUEUE_TIMEOUT_MS
Environment=RENDER_MAX_PAYLOAD_MB=$RENDER_MAX_PAYLOAD_MB
TimeoutStopSec=15

[Install]
WantedBy=multi-user.target
EOF

sudo systemctl daemon-reload
sudo systemctl enable eficentra-report-render
sudo systemctl restart eficentra-report-render

echo "Installed."
sudo systemctl status eficentra-report-render --no-pager
