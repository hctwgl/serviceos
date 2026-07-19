#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
COMPOSE_FILE="${ROOT}/serviceos-deploy/compose.yaml"

echo "清空演示标记数据…"
docker compose -f "${COMPOSE_FILE}" exec -T postgres \
  psql -U serviceos_app -d serviceos \
  < "${ROOT}/serviceos-deploy/demo/clear-demo.sql"
echo "完成。"
