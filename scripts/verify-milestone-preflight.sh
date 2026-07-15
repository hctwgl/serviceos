#!/usr/bin/env bash
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${root}"

fail() {
  echo "里程碑预检失败: $1" >&2
  exit 1
}

command -v rg >/dev/null 2>&1 || fail "需要 ripgrep (rg)"

read -r flyway_version migration_count < <(bash scripts/migration-baseline.sh)
status_file="serviceos-architecture/docs/implementation-status.md"
latest_milestone="$(sed -n 's/^latestMilestone:[[:space:]]*//p' "${status_file}" | head -1)"
[[ "${latest_milestone}" =~ ^M[0-9]+$ ]] || fail "implementation-status latestMilestone 缺失或非法"

# 先运行无需启动 JVM 或数据库的机械一致性检查，避免在全量 verify 末尾才发现基线漂移。
bash scripts/check-milestone-consistency.sh \
  "${latest_milestone}" "${flyway_version}" "${migration_count}"

bash -n scripts/*.sh
bash -n serviceos-deploy/staging/*.sh serviceos-deploy/staging/init/*.sh
bash -n serviceos-deploy/observability/*.sh

generated_env="$(mktemp)"
trap 'rm -f "${generated_env}"' EXIT
serviceos-deploy/staging/generate-local-env.sh \
  "${generated_env}" "serviceos-backend:preflight" >/dev/null

rg -qx "SERVICEOS_EXPECTED_MIGRATION_VERSION=${flyway_version}" "${generated_env}" \
  || fail "staging 迁移版本未从当前迁移目录生成"
rg -qx "SERVICEOS_EXPECTED_MIGRATION_COUNT=${migration_count}" "${generated_env}" \
  || fail "staging 迁移数量未从当前迁移目录生成"

# 当前迁移终点只能由测试辅助类从 classpath 迁移资源推导，禁止测试重新引入数字常量。
hardcoded_assertions="$(rg -n --glob '*.java' \
  'flyway\.info\(\)\.current\(\).*isEqualTo\("[0-9]+"\)|flyway\.info\(\)\.applied\(\)\)\.hasSize\([0-9]+\)' \
  serviceos-backend/src/test/java || true)"
if [[ -n "${hardcoded_assertions}" ]]; then
  printf '%s\n' "${hardcoded_assertions}" >&2
  fail "测试中存在 Flyway 当前版本或迁移数量硬编码；请使用 MigrationBaselineAssertions"
fi

if rg -n '^SERVICEOS_EXPECTED_MIGRATION_(VERSION|COUNT)=[0-9]+$' \
  serviceos-deploy/staging/generate-local-env.sh; then
  fail "staging 环境生成器不得硬编码迁移终点"
fi

git diff --check

printf '里程碑预检通过: %s / Flyway %s / %s migrations\n' \
  "${latest_milestone}" "${flyway_version}" "${migration_count}"
