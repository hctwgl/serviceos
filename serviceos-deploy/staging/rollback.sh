#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=common.sh
source "${script_dir}/common.sh"

env_file="${1:-}"
rollback_image="${2:-}"
require_env_file "${env_file}"
load_env_file "${env_file}"
if [[ -z "${rollback_image}" ]]; then
  echo "usage: $0 <env-file> <rollback-image>" >&2
  exit 64
fi

current_container="$(compose "${env_file}" ps -q backend)"
current_image="$(docker inspect --format '{{.Config.Image}}' "${current_container}")"
port="${SERVICEOS_STAGING_PORT:-18080}"

restore_current() {
  echo "rollback candidate failed; restoring ${current_image}" >&2
  SERVICEOS_IMAGE="${current_image}" compose "${env_file}" up -d --no-deps --force-recreate backend
  wait_for_url "http://127.0.0.1:${port}/readyz" 45
}
trap restore_current ERR

SERVICEOS_IMAGE="${rollback_image}" compose "${env_file}" up -d --no-deps --force-recreate backend
wait_for_url "http://127.0.0.1:${port}/readyz" 45

rolled_container="$(SERVICEOS_IMAGE="${rollback_image}" compose "${env_file}" ps -q backend)"
rolled_image_id="$(docker inspect --format '{{.Image}}' "${rolled_container}")"
expected_image_id="$(docker image inspect --format '{{.Id}}' "${rollback_image}")"
[[ "${rolled_image_id}" == "${expected_image_id}" ]]
curl --fail --silent --show-error "http://127.0.0.1:${port}/livez" | jq -e '.status == "UP"' >/dev/null

trap - ERR
echo "application rollback smoke passed: ${current_image} -> ${rollback_image}"
