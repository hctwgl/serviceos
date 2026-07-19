#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
COMPOSE_FILE="${ROOT}/serviceos-deploy/compose.yaml"

if [[ "${SERVICEOS_DEMO_ALLOW_PROD:-}" == "true" ]]; then
  echo "拒绝：演示数据脚本不得在生产开启 SERVICEOS_DEMO_ALLOW_PROD" >&2
  exit 1
fi

echo "初始化演示数据（幂等）…"
# 先保证 admin-pilot 项目/Bundle 存在，再写入 WO-DEMO-* 工单
docker compose -f "${COMPOSE_FILE}" exec -T postgres \
  psql -U serviceos_app -d serviceos \
  < "${ROOT}/serviceos-deploy/admin-pilot/seed-admin-pilot.sql"
docker compose -f "${COMPOSE_FILE}" exec -T postgres \
  psql -U serviceos_app -d serviceos \
  < "${ROOT}/serviceos-deploy/demo/seed-demo-orders.sql"
echo "完成。可在管理端「演示数据管理」与「工单全流程演练」查看 WO-DEMO-* 工单。"
