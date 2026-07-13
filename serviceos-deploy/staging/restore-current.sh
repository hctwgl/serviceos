#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=common.sh
source "${script_dir}/common.sh"

env_file="${1:-}"
require_env_file "${env_file}"
load_env_file "${env_file}"
SERVICEOS_IMAGE="${SERVICEOS_IMAGE}" compose "${env_file}" up -d --no-deps --force-recreate backend
wait_for_url "http://127.0.0.1:${SERVICEOS_STAGING_PORT:-18080}/readyz" 45
echo "current image restored: ${SERVICEOS_IMAGE}"
