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

cd "${repo_root}"
docker compose -f "${compose_file}" up -d postgres keycloak
wait_http "http://127.0.0.1:8081/realms/serviceos/.well-known/openid-configuration" "Keycloak"

if ! curl --fail --silent "http://127.0.0.1:8080/livez" >/dev/null; then
  ./mvnw --batch-mode --no-transfer-progress -pl serviceos-backend -am -DskipTests package
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

if ! curl --fail --silent "http://127.0.0.1:5173" >/dev/null; then
  (
    cd serviceos-admin-web
    VITE_DEV_OIDC_ENABLED=true npm run dev -- --host 127.0.0.1
  ) >"${admin_log}" 2>&1 &
  admin_pid="$!"
fi
wait_http "http://127.0.0.1:5173" "ServiceOS Admin Web"

cd serviceos-admin-web
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

successful_audit_count="$(query_db "
  SELECT count(*)
    FROM aud_audit_record
   WHERE target_id = '70000000-0000-4000-8000-000000000001'
     AND action_name IN ('TASK_HUMAN_CLAIM', 'TASK_HUMAN_RELEASE')
     AND result_code = 'SUCCEEDED'
")"
[[ "${successful_audit_count}" -ge 2 ]] || {
  echo "Admin 试点 Task 成功审计记录不足: ${successful_audit_count}" >&2
  exit 1
}

echo "Admin 试点冒烟通过：真实 Keycloak、Backend、PostgreSQL 与浏览器链路均已验证"
