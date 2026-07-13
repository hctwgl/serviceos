#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=common.sh
source "${script_dir}/common.sh"

env_file="${1:-}"
require_env_file "${env_file}"
compose "${env_file}" down --volumes --remove-orphans
echo "staging rehearsal resources removed"
