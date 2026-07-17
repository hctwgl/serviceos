#!/usr/bin/env bash
# M143：经 Dispatch SPI 为指定工单/Task 注入 ACTIVE NETWORK + TECHNICIAN ServiceAssignment。
# 不暴露 Admin 派单 HTTP；删除条件见 AdminPilotLiveServiceAssignmentSeeder。
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
work_order_id="${1:-}"
task_id="${2:-}"
run_key="${3:-${task_id}}"

if [[ -z "${work_order_id}" || -z "${task_id}" ]]; then
  echo "用法: $0 <workOrderId> <taskId> [runKey]" >&2
  exit 1
fi

cd "${repo_root}"
./mvnw --batch-mode --no-transfer-progress -pl serviceos-backend \
  -Dtest=AdminPilotLiveServiceAssignmentSeeder \
  -Dserviceos.admin.pilot.seed=true \
  -Dserviceos.admin.pilot.workOrderId="${work_order_id}" \
  -Dserviceos.admin.pilot.taskId="${task_id}" \
  -Dserviceos.admin.pilot.runKey="${run_key}" \
  test
