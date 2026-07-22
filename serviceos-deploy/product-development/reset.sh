#!/usr/bin/env bash
set -euo pipefail

repository_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
compose_file="${repository_root}/serviceos-deploy/compose.yaml"
runtime_directory="${repository_root}/target/product-development"
backend_pid_file="${runtime_directory}/backend.pid"
backend_log="${runtime_directory}/backend.log"

if [[ "${SERVICEOS_ENVIRONMENT:-local}" != "local" ]]; then
  echo "拒绝执行：product-data:reset 只允许 SERVICEOS_ENVIRONMENT=local。" >&2
  exit 1
fi

mkdir -p "${runtime_directory}"

if [[ -f "${backend_pid_file}" ]]; then
  previous_pid="$(tr -d '[:space:]' < "${backend_pid_file}")"
  if [[ "${previous_pid}" =~ ^[0-9]+$ ]] && kill -0 "${previous_pid}" 2>/dev/null; then
    kill "${previous_pid}"
    for _ in {1..30}; do
      kill -0 "${previous_pid}" 2>/dev/null || break
      sleep 0.2
    done
  fi
fi

if lsof -nP -iTCP:8080 -sTCP:LISTEN >/dev/null 2>&1; then
  echo "拒绝执行：8080 端口已有非产品场景进程，请先停止该后端，避免重置正在使用的数据库。" >&2
  exit 1
fi

echo "重建本地 PostgreSQL 与 Keycloak…"
docker compose -f "${compose_file}" down --volumes --remove-orphans
docker compose -f "${compose_file}" up -d postgres keycloak

for _ in {1..90}; do
  if docker compose -f "${compose_file}" exec -T postgres \
      pg_isready -U serviceos_app -d serviceos >/dev/null 2>&1 \
      && curl --fail --silent "http://localhost:8081/realms/serviceos/.well-known/openid-configuration" \
        >/dev/null 2>&1; then
    break
  fi
  sleep 1
done

if ! docker compose -f "${compose_file}" exec -T postgres \
    pg_isready -U serviceos_app -d serviceos >/dev/null 2>&1; then
  echo "本地 PostgreSQL 未在预期时间内就绪。" >&2
  exit 1
fi
if ! curl --fail --silent "http://localhost:8081/realms/serviceos/.well-known/openid-configuration" \
    >/dev/null; then
  echo "本地 Keycloak 未在预期时间内就绪。" >&2
  exit 1
fi

echo "启动后端并执行正式 Flyway…"
(
  cd "${repository_root}"
  nohup env \
    SERVICEOS_BYD_CPIM_TENANT_ID=tenant-local \
    SERVICEOS_BYD_CPIM_PROJECT_CODE=BYD-OCEAN-SD-PILOT \
    SERVICEOS_OUTBOX_SCHEDULING_ENABLED=true \
    SERVICEOS_TASK_SCHEDULING_ENABLED=true \
    SERVICEOS_SLA_SCHEDULING_ENABLED=true \
    ./mvnw --no-transfer-progress -pl serviceos-backend spring-boot:run \
      -Dspring-boot.run.profiles=local >"${backend_log}" 2>&1 &
  echo $! >"${backend_pid_file}"
)

for _ in {1..120}; do
  if curl --fail --silent "http://localhost:8080/actuator/health/readiness" >/dev/null 2>&1; then
    break
  fi
  if ! kill -0 "$(cat "${backend_pid_file}")" 2>/dev/null; then
    tail -80 "${backend_log}" >&2
    echo "后端在准备产品场景时异常退出。" >&2
    exit 1
  fi
  sleep 1
done

if ! curl --fail --silent "http://localhost:8080/actuator/health/readiness" >/dev/null; then
  tail -80 "${backend_log}" >&2
  echo "后端未在预期时间内就绪。" >&2
  exit 1
fi

# OIDC 主体首次进入业务系统前必须存在权威 Principal/Persona/RoleGrant。
# 这是本地身份与授权引导，不写入任何项目、工单或履约核心业务表。
docker compose -f "${compose_file}" exec -T postgres \
  psql -v ON_ERROR_STOP=1 -U serviceos_app -d serviceos \
  < "${repository_root}/serviceos-deploy/keycloak/grant-local-project-admin.sql" >/dev/null
docker compose -f "${compose_file}" exec -T postgres \
  psql -v ON_ERROR_STOP=1 -U serviceos_app -d serviceos \
  < "${repository_root}/serviceos-deploy/keycloak/grant-local-product-users.sql" >/dev/null

admin_token="$(curl --fail --silent --show-error \
  -d client_id=admin-cli \
  -d username=admin \
  -d password=local-admin-change-me \
  -d grant_type=password \
  "http://localhost:8081/realms/master/protocol/openid-connect/token" | jq -r '.access_token')"
client_id="$(curl --fail --silent --show-error \
  -H "Authorization: Bearer ${admin_token}" \
  "http://localhost:8081/admin/realms/serviceos/clients?clientId=serviceos-local-cli" | jq -r '.[0].id')"

restore_pkce_client() {
  if [[ -n "${admin_token:-}" && -n "${client_id:-}" ]]; then
    representation="$(curl --fail --silent --show-error \
      -H "Authorization: Bearer ${admin_token}" \
      "http://localhost:8081/admin/realms/serviceos/clients/${client_id}")"
    curl --fail --silent --show-error -X PUT \
      -H "Authorization: Bearer ${admin_token}" \
      -H 'Content-Type: application/json' \
      --data "$(jq '.directAccessGrantsEnabled = false' <<<"${representation}")" \
      "http://localhost:8081/admin/realms/serviceos/clients/${client_id}" >/dev/null || true
  fi
}
trap restore_pkce_client EXIT

client_representation="$(curl --fail --silent --show-error \
  -H "Authorization: Bearer ${admin_token}" \
  "http://localhost:8081/admin/realms/serviceos/clients/${client_id}")"
curl --fail --silent --show-error -X PUT \
  -H "Authorization: Bearer ${admin_token}" \
  -H 'Content-Type: application/json' \
  --data "$(jq '.directAccessGrantsEnabled = true' <<<"${client_representation}")" \
  "http://localhost:8081/admin/realms/serviceos/clients/${client_id}" >/dev/null

access_token="$(curl --fail --silent --show-error \
  -d client_id=serviceos-local-cli \
  -d username=developer \
  -d password=local-dev-change-me \
  -d grant_type=password \
  "http://localhost:8081/realms/serviceos/protocol/openid-connect/token" | jq -r '.access_token')"

SERVICEOS_PRODUCT_ACCESS_TOKEN="${access_token}" \
  node "${repository_root}/serviceos-deploy/product-development/seed.mjs"

restore_pkce_client
trap - EXIT

echo
echo "产品场景已重建。"
echo "Admin：http://localhost:5173"
echo "账号：developer"
echo "密码：local-dev-change-me"
echo "其他角色：viewer / operator / dispatcher / reviewer / project-manager / project-assistant / network-manager / network-dispatcher"
echo "对应密码：local-<账号>-change-me"
echo "后端日志：${backend_log}"
