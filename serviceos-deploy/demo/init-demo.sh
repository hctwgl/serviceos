#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
COMPOSE_FILE="${ROOT}/serviceos-deploy/compose.yaml"

if [[ "${SERVICEOS_DEMO_ALLOW_PROD:-}" == "true" ]]; then
  echo "拒绝：演示数据脚本不得在生产开启 SERVICEOS_DEMO_ALLOW_PROD" >&2
  exit 1
fi

echo "初始化演示数据（幂等）…"
# admin-pilot → 工单壳 → 网点 Portal → 多状态任务/责任/SLA
docker compose -f "${COMPOSE_FILE}" exec -T postgres \
  psql -U serviceos_app -d serviceos \
  < "${ROOT}/serviceos-deploy/admin-pilot/seed-admin-pilot.sql"
docker compose -f "${COMPOSE_FILE}" exec -T postgres \
  psql -U serviceos_app -d serviceos \
  < "${ROOT}/serviceos-deploy/demo/seed-demo-orders.sql"
docker compose -f "${COMPOSE_FILE}" exec -T postgres \
  psql -U serviceos_app -d serviceos \
  < "${ROOT}/serviceos-deploy/demo/seed-demo-network-portal.sql"
docker compose -f "${COMPOSE_FILE}" exec -T postgres \
  psql -U serviceos_app -d serviceos \
  < "${ROOT}/serviceos-deploy/demo/seed-demo-tasks.sql"
echo "完成。可在管理端「演示数据管理」查看 WO-DEMO-* 的 20 态场景任务。"
echo "网点/师傅门户本地账号：Keycloak developer / local-dev-change-me（需已执行 grant-local-project-admin.sql）。"
