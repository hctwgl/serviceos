#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=common.sh
source "${script_dir}/common.sh"

env_file="${1:-}"
require_env_file "${env_file}"
load_env_file "${env_file}"
require_immutable_image_or_local_override
: "${SERVICEOS_EXPECTED_MIGRATION_VERSION:?SERVICEOS_EXPECTED_MIGRATION_VERSION is required}"
: "${SERVICEOS_EXPECTED_MIGRATION_COUNT:?SERVICEOS_EXPECTED_MIGRATION_COUNT is required}"

# 数据库必须达到 healthy 后才能启动一次性迁移；--exit-code-from 确保迁移失败直接中止发布。
compose "${env_file}" up -d --wait --wait-timeout 60 database
compose "${env_file}" up --no-deps --abort-on-container-exit --exit-code-from migrate migrate

actual_migration_version="$(compose "${env_file}" exec -T database \
  psql -U serviceos_bootstrap -d serviceos -Atc \
  "SELECT version FROM flyway_schema_history WHERE success AND version IS NOT NULL ORDER BY installed_rank DESC LIMIT 1")"
if [[ "${actual_migration_version}" != "${SERVICEOS_EXPECTED_MIGRATION_VERSION}" ]]; then
  echo "migration version mismatch: expected=${SERVICEOS_EXPECTED_MIGRATION_VERSION} actual=${actual_migration_version}" >&2
  exit 1
fi
actual_migration_count="$(compose "${env_file}" exec -T database \
  psql -U serviceos_bootstrap -d serviceos -Atc \
  "SELECT count(*) FROM flyway_schema_history WHERE success")"
if [[ "${actual_migration_count}" != "${SERVICEOS_EXPECTED_MIGRATION_COUNT}" ]]; then
  echo "migration count mismatch: expected=${SERVICEOS_EXPECTED_MIGRATION_COUNT} actual=${actual_migration_count}" >&2
  exit 1
fi

compose "${env_file}" up -d --no-deps backend
wait_for_url "http://127.0.0.1:${SERVICEOS_STAGING_PORT:-18080}/readyz" 45

echo "staging deployment ready: ${SERVICEOS_IMAGE}"
