#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
compose_file="${repo_root}/serviceos-deploy/compose.yaml"
backend_log="${TMPDIR:-/tmp}/serviceos-admin-pilot-backend.log"
admin_log="${TMPDIR:-/tmp}/serviceos-admin-pilot-web.log"
backend_pid=""
admin_pid=""

cleanup() {
  [[ -z "${admin_pid}" ]] || kill "${admin_pid}" 2>/dev/null || true
  [[ -z "${backend_pid}" ]] || kill "${backend_pid}" 2>/dev/null || true
}
trap cleanup EXIT

wait_http() {
  local url="$1"
  local label="$2"
  for _ in $(seq 1 90); do
    if curl --fail --silent "${url}" >/dev/null; then
      return 0
    fi
    sleep 1
  done
  echo "${label} 未在超时内就绪" >&2
  return 1
}

query_db() {
  local sql="$1"
  docker compose -f "${compose_file}" exec -T postgres \
    psql -U serviceos_app -d serviceos -Atc "${sql}"
}

new_uuid() {
  uuidgen | tr '[:upper:]' '[:lower:]'
}

cd "${repo_root}"
docker compose -f "${compose_file}" up -d postgres keycloak
wait_http "http://127.0.0.1:8081/realms/serviceos/.well-known/openid-configuration" "Keycloak"

if ! curl --fail --silent "http://127.0.0.1:8080/livez" >/dev/null; then
  ./mvnw --batch-mode --no-transfer-progress -pl serviceos-backend -am -DskipTests package
  # complete 只在命令事务内追加 task.completed；必须启用真实 Outbox worker，
  # 由 Inbox 去重消费者继续推进 Node/Stage/Workflow/WorkOrder，不能用 SQL 模拟异步结果。
  SERVICEOS_OUTBOX_SCHEDULING_ENABLED=true \
    java -jar serviceos-backend/target/serviceos-backend-0.1.0-SNAPSHOT.jar >"${backend_log}" 2>&1 &
  backend_pid="$!"
fi
wait_http "http://127.0.0.1:8080/readyz" "ServiceOS Backend"

docker compose -f "${compose_file}" exec -T postgres \
  psql -U serviceos_app -d serviceos \
  < serviceos-deploy/keycloak/grant-local-project-admin.sql
docker compose -f "${compose_file}" exec -T postgres \
  psql -U serviceos_app -d serviceos \
  < serviceos-deploy/admin-pilot/seed-admin-pilot.sql

# 终态验证每轮创建全新的 WorkOrder/Workflow/Stage/Node/Task，绝不通过 SQL 回退终态事实。
completion_work_order_id="$(new_uuid)"
completion_workflow_id="$(new_uuid)"
completion_stage_id="$(new_uuid)"
completion_node_id="$(new_uuid)"
completion_task_id="$(new_uuid)"
completion_start_event_id="$(new_uuid)"
completion_stage_event_id="$(new_uuid)"
completion_node_event_id="$(new_uuid)"
completion_external_code="ADMIN-PILOT-COMPLETE-${completion_work_order_id%%-*}"
completion_correlation_id="admin-pilot-complete-${completion_task_id}"

docker compose -f "${compose_file}" exec -T postgres \
  psql -U serviceos_app -d serviceos \
  -v completion_work_order_id="${completion_work_order_id}" \
  -v completion_workflow_id="${completion_workflow_id}" \
  -v completion_stage_id="${completion_stage_id}" \
  -v completion_node_id="${completion_node_id}" \
  -v completion_task_id="${completion_task_id}" \
  -v completion_start_event_id="${completion_start_event_id}" \
  -v completion_stage_event_id="${completion_stage_event_id}" \
  -v completion_node_event_id="${completion_node_event_id}" \
  -v completion_external_code="${completion_external_code}" \
  -v completion_correlation_id="${completion_correlation_id}" \
  < serviceos-deploy/admin-pilot/seed-admin-completion.sql

if ! curl --fail --silent "http://127.0.0.1:5173" >/dev/null; then
  (
    cd serviceos-admin-web
    VITE_DEV_OIDC_ENABLED=true npm run dev -- --host 127.0.0.1
  ) >"${admin_log}" 2>&1 &
  admin_pid="$!"
fi
wait_http "http://127.0.0.1:5173" "ServiceOS Admin Web"

cd serviceos-admin-web
export ADMIN_PILOT_COMPLETION_WORK_ORDER_CODE="${completion_external_code}"
export ADMIN_PILOT_COMPLETION_TASK_ID="${completion_task_id}"
npm run test:e2e

task_state="$(query_db "
  SELECT status || ':' || version || ':' || COALESCE(claimed_by, '')
    FROM tsk_task
   WHERE task_id = '70000000-0000-4000-8000-000000000001'
")"
[[ "${task_state}" =~ ^READY:[0-9]+:$ ]] || {
  echo "Admin 试点 Task 最终状态非法: ${task_state}" >&2
  exit 1
}

active_candidate_count="$(query_db "
  SELECT count(*)
    FROM tsk_task_assignment
   WHERE task_id = '70000000-0000-4000-8000-000000000001'
     AND assignment_kind = 'CANDIDATE'
     AND principal_id = '06b612f3-a901-4b0e-bd90-86b4259cc087'
     AND status = 'ACTIVE'
")"
[[ "${active_candidate_count}" == "1" ]] || {
  echo "Admin 试点 ACTIVE 候选事实数量非法: ${active_candidate_count}" >&2
  exit 1
}

latest_assignment_batch="$(query_db "
  SELECT source_type || ':' || source_id || ':' || candidate_count
    FROM tsk_task_assignment_batch
   WHERE task_id = '70000000-0000-4000-8000-000000000001'
   ORDER BY assigned_at DESC
   LIMIT 1
")"
[[ "${latest_assignment_batch}" == "MANUAL:admin-pilot-e2e:1" ]] || {
  echo "Admin 试点最新候选批次不是页面 MANUAL 分配结果: ${latest_assignment_batch}" >&2
  exit 1
}

active_responsible_count="$(query_db "
  SELECT count(*)
    FROM tsk_task_assignment
   WHERE task_id = '70000000-0000-4000-8000-000000000001'
     AND assignment_kind = 'RESPONSIBLE'
     AND status = 'ACTIVE'
")"
[[ "${active_responsible_count}" == "0" ]] || {
  echo "Admin 试点 release 后仍存在 ACTIVE 责任事实: ${active_responsible_count}" >&2
  exit 1
}

successful_audit_action_count="$(query_db "
  SELECT count(DISTINCT action_name)
    FROM aud_audit_record
   WHERE target_id = '70000000-0000-4000-8000-000000000001'
     AND action_name IN (
       'TASK_ASSIGN_CANDIDATES', 'TASK_HUMAN_CLAIM', 'TASK_HUMAN_RELEASE'
     )
     AND result_code = 'SUCCEEDED'
")"
[[ "${successful_audit_action_count}" == "3" ]] || {
  echo "Admin 试点候选分配/领取/释放成功审计不完整: ${successful_audit_action_count}" >&2
  exit 1
}

completion_state=""
for _ in $(seq 1 60); do
  completion_state="$(query_db "
    SELECT task.status || ':' || node.status || ':' || stage.status || ':' ||
           workflow.status || ':' || work_order.status
      FROM tsk_task task
      JOIN wfl_node_instance node
        ON node.tenant_id = task.tenant_id
       AND node.workflow_node_instance_id = task.workflow_node_instance_id
      JOIN wfl_stage_instance stage
        ON stage.tenant_id = task.tenant_id
       AND stage.stage_instance_id = task.stage_instance_id
      JOIN wfl_workflow_instance workflow
        ON workflow.tenant_id = task.tenant_id
       AND workflow.workflow_instance_id = task.workflow_instance_id
      JOIN wo_work_order work_order
        ON work_order.tenant_id = task.tenant_id
       AND work_order.id = task.work_order_id
     WHERE task.task_id = '${completion_task_id}'
  ")"
  [[ "${completion_state}" == "COMPLETED:COMPLETED:COMPLETED:COMPLETED:FULFILLED" ]] && break
  sleep 1
done
[[ "${completion_state}" == "COMPLETED:COMPLETED:COMPLETED:COMPLETED:FULFILLED" ]] || {
  echo "Admin 试点终态推进未闭环: ${completion_state}" >&2
  exit 1
}

expired_assignment_count="$(query_db "
  SELECT count(*)
    FROM tsk_task_assignment
   WHERE task_id = '${completion_task_id}'
     AND assignment_kind IN ('CANDIDATE', 'RESPONSIBLE')
     AND status = 'EXPIRED'
")"
[[ "${expired_assignment_count}" == "2" ]] || {
  echo "Admin 试点 complete 后候选/责任未全部过期: ${expired_assignment_count}" >&2
  exit 1
}

completion_audit_count="$(query_db "
  SELECT count(DISTINCT action_name)
    FROM aud_audit_record
   WHERE target_id = '${completion_task_id}'
     AND action_name IN (
       'TASK_ASSIGN_CANDIDATES', 'TASK_HUMAN_CLAIM',
       'TASK_HUMAN_START', 'TASK_HUMAN_COMPLETE'
     )
     AND result_code = 'SUCCEEDED'
")"
[[ "${completion_audit_count}" == "4" ]] || {
  echo "Admin 试点终态命令成功审计不完整: ${completion_audit_count}" >&2
  exit 1
}

progression_inbox_count="$(query_db "
  SELECT count(*)
    FROM rel_inbox_record
   WHERE consumer_name = 'workflow.task-completed.v1'
     AND status = 'SUCCEEDED'
     AND event_id = (
       SELECT event_id
         FROM rel_outbox_event
        WHERE aggregate_id = '${completion_task_id}'
          AND event_type = 'task.completed'
     )
")"
[[ "${progression_inbox_count}" == "1" ]] || {
  echo "Admin 试点 task.completed 未被 Workflow Inbox 成功消费: ${progression_inbox_count}" >&2
  exit 1
}

echo "Admin 试点冒烟通过：真实 Keycloak、Backend、PostgreSQL 与浏览器链路均已验证"
