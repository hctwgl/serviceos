#!/usr/bin/env bash
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${root}"

output="$(bash scripts/find-milestone.sh M183)"
printf '%s\n' "${output}" | rg -q '^\| M183 \|'
printf '%s\n' "${output}" | rg -q '^serviceos-architecture/architecture/196-m183-'
printf '%s\n' "${output}" | rg -q '^serviceos-architecture/testing/180-m183-'

set +e
bash scripts/find-milestone.sh '不存在的里程碑查询值' >/dev/null 2>&1
status=$?
set -e
[[ "${status}" -eq 1 ]] || {
  echo "不存在的里程碑查询应返回 1，实际为 ${status}" >&2
  exit 1
}

echo "里程碑精确定位脚本回归通过。"
