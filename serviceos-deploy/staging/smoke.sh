#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=common.sh
source "${script_dir}/common.sh"

env_file="${1:-}"
output_dir="${2:-${repo_root}/target/staging-evidence}"
require_env_file "${env_file}"
load_env_file "${env_file}"
require_immutable_image_or_local_override
: "${SERVICEOS_EXPECTED_MIGRATION_VERSION:?SERVICEOS_EXPECTED_MIGRATION_VERSION is required}"
: "${SERVICEOS_EXPECTED_MIGRATION_COUNT:?SERVICEOS_EXPECTED_MIGRATION_COUNT is required}"

port="${SERVICEOS_STAGING_PORT:-18080}"
mkdir -p "${output_dir}"

live_headers="${output_dir}/live.headers"
curl --fail --silent --show-error -D "${live_headers}" \
  -H 'X-Correlation-Id: staging-smoke-live-1' \
  "http://127.0.0.1:${port}/livez" | jq -e '.status == "UP"' >/dev/null
rg -q '^X-Correlation-Id: staging-smoke-live-1\r?$' "${live_headers}"
curl --fail --silent --show-error "http://127.0.0.1:${port}/readyz" \
  | jq -e '.status == "UP"' >/dev/null

metrics_status="$(curl --silent --output /dev/null --write-out '%{http_code}' \
  "http://127.0.0.1:${port}/actuator/prometheus")"
[[ "${metrics_status}" == "401" ]]

migration_count="$(compose "${env_file}" exec -T database \
  psql -U serviceos_bootstrap -d serviceos -Atc \
  "SELECT count(*) FROM flyway_schema_history WHERE success")"
[[ "${migration_count}" == "${SERVICEOS_EXPECTED_MIGRATION_COUNT}" ]]
migration_version="$(compose "${env_file}" exec -T database \
  psql -U serviceos_bootstrap -d serviceos -Atc \
  "SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank DESC LIMIT 1")"
[[ "${migration_version}" == "${SERVICEOS_EXPECTED_MIGRATION_VERSION}" ]]

# 运行账号必须能读取业务表，但不能执行 DDL。
compose "${env_file}" exec -T database sh -ceu '
  PGPASSWORD="$SERVICEOS_RUNTIME_DB_PASSWORD" \
    psql -h 127.0.0.1 -U serviceos_runtime -d serviceos -Atc \
      "SELECT count(*) FROM rel_outbox_event" >/dev/null
'
if compose "${env_file}" exec -T database sh -ceu '
  PGPASSWORD="$SERVICEOS_RUNTIME_DB_PASSWORD" \
    psql -h 127.0.0.1 -U serviceos_runtime -d serviceos -c \
      "CREATE TABLE runtime_must_not_create(id integer)"
' >"${output_dir}/ddl-negative.log" 2>&1; then
  compose "${env_file}" exec -T database \
    psql -U serviceos_bootstrap -d serviceos -c 'DROP TABLE IF EXISTS runtime_must_not_create' >/dev/null
  echo "runtime database role unexpectedly acquired DDL permission" >&2
  exit 1
fi

backend_container="$(compose "${env_file}" ps -q backend)"
migrate_container="$(compose "${env_file}" ps -aq migrate | head -n 1)"
[[ -n "${backend_container}" && -n "${migrate_container}" ]]
backend_image_id="$(docker inspect --format '{{.Image}}' "${backend_container}")"
migrate_image_id="$(docker inspect --format '{{.Image}}' "${migrate_container}")"
[[ "${backend_image_id}" == "${migrate_image_id}" ]]

[[ "$(docker inspect --format '{{.Config.User}}' "${backend_container}")" == "10001:10001" ]]
[[ "$(docker inspect --format '{{.HostConfig.ReadonlyRootfs}}' "${backend_container}")" == "true" ]]
docker inspect --format '{{json .HostConfig.CapDrop}}' "${backend_container}" | jq -e 'index("ALL") != null' >/dev/null
docker inspect --format '{{json .HostConfig.SecurityOpt}}' "${backend_container}" \
  | jq -e 'map(select(startswith("no-new-privileges"))) | length > 0' >/dev/null

revision="$(docker image inspect --format '{{index .Config.Labels "org.opencontainers.image.revision"}}' "${SERVICEOS_IMAGE}")"
if docker image inspect --format '{{range .Config.Env}}{{println .}}{{end}}' "${SERVICEOS_IMAGE}" \
    | rg -q 'SERVICEOS_(DB_PASSWORD|BOOTSTRAP_DB_PASSWORD|MIGRATOR_DB_PASSWORD|RUNTIME_DB_PASSWORD|FILE_SIGNING_KEY)='; then
  echo "deployment secret was baked into the image environment" >&2
  exit 1
fi
jq -n \
  --arg commit "${revision}" \
  --arg imageReference "${SERVICEOS_IMAGE}" \
  --arg imageId "${backend_image_id}" \
  --arg migrationVersion "${migration_version}" \
  --arg environment "staging-rehearsal" \
  '{commit: $commit, imageReference: $imageReference, imageId: $imageId,
    migrationVersion: $migrationVersion, environment: $environment,
    checks: ["live", "ready", "metrics-protected", "runtime-no-ddl", "same-image", "non-root", "read-only", "no-baked-secret"]}' \
  > "${output_dir}/release-manifest.json"

echo "staging smoke passed: ${output_dir}/release-manifest.json"
