#!/usr/bin/env bash
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
test_root="${1:-${root}/serviceos-backend/src/test/java}"
staging_generator="${2:-${root}/serviceos-deploy/staging/generate-local-env.sh}"

fail() {
  echo "迁移基线引用检查失败: $1" >&2
  exit 1
}

command -v rg >/dev/null 2>&1 || fail "需要 ripgrep (rg)"
[[ -d "${test_root}" ]] || fail "测试目录不存在: ${test_root}"
[[ -f "${staging_generator}" ]] || fail "staging 环境生成器不存在: ${staging_generator}"

read -r flyway_version migration_count < <(bash "${root}/scripts/migration-baseline.sh")

version_assertions="$(rg -n --glob '*.java' \
  'flyway\.info\(\)\.current\(\).*isEqualTo\("[0-9]+"\)' \
  "${test_root}" || true)"
stale_versions="$(printf '%s\n' "${version_assertions}" \
  | rg -v "isEqualTo\\(\\\"${flyway_version}\\\"\\)" || true)"
if [[ -n "${stale_versions}" ]]; then
  printf '%s\n' "${stale_versions}" >&2
  fail "测试仍断言旧 Flyway 版本；当前版本为 ${flyway_version}"
fi

count_assertions="$(rg -n --glob '*.java' \
  'flyway\.info\(\)\.applied\(\)\)\.hasSize\([0-9]+\)' \
  "${test_root}" || true)"
stale_counts="$(printf '%s\n' "${count_assertions}" \
  | rg -v "hasSize\\(${migration_count}\\)" || true)"
if [[ -n "${stale_counts}" ]]; then
  printf '%s\n' "${stale_counts}" >&2
  fail "测试仍断言旧迁移数量；当前数量为 ${migration_count}"
fi

if rg -n '^SERVICEOS_EXPECTED_MIGRATION_(VERSION|COUNT)=[0-9]+$' "${staging_generator}"; then
  fail "staging 环境生成器不得硬编码迁移终点"
fi

printf '迁移基线引用检查通过: Flyway %s / %s migrations\n' \
  "${flyway_version}" "${migration_count}"
