#!/usr/bin/env bash
set -euo pipefail

if [[ $# -lt 3 || $# -gt 4 ]]; then
  echo "用法: $0 Mxx flywayVersion migrationCount [--allow-pending-baseline]" >&2
  exit 2
fi

milestone="$1"
flyway_version="$2"
expected_migration_count="$3"
allow_pending="${4:-}"
root="$(cd "$(dirname "$0")/.." && pwd)"
status="$root/serviceos-architecture/docs/implementation-status.md"
traceability="$root/serviceos-architecture/docs/implementation-traceability-matrix.md"

fail() {
  echo "里程碑一致性检查失败: $1" >&2
  exit 1
}

[[ "$milestone" =~ ^M[0-9]+$ ]] || fail "milestone 必须为 Mxx"
[[ "$flyway_version" =~ ^[0-9]{3}$ ]] || fail "flywayVersion 必须为三位数字"
[[ "$expected_migration_count" =~ ^[0-9]+$ ]] || fail "migrationCount 必须为正整数"

lower_milestone="$(printf '%s' "$milestone" | tr '[:upper:]' '[:lower:]')"
architecture_count="$(find "$root/serviceos-architecture/architecture" -maxdepth 1 -type f \
  -iname "*-${lower_milestone}-*.md" | wc -l | tr -d ' ')"
acceptance_count="$(find "$root/serviceos-architecture/testing" -maxdepth 1 -type f \
  -iname "*-${lower_milestone}-*.md" | wc -l | tr -d ' ')"
[[ "$architecture_count" == "1" ]] || fail "$milestone 必须恰好有一个实现文档"
[[ "$acceptance_count" == "1" ]] || fail "$milestone 必须恰好有一个验收矩阵"

rg -q "^latestMilestone: ${milestone}$" "$status" || fail "implementation-status latestMilestone 未同步"
rg -q "当前版本 ${flyway_version} / ${expected_migration_count}" "$status" \
  || fail "implementation-status Flyway 版本/数量未同步"
rg -q "\\| ${milestone} \\|" "$traceability" || fail "追踪矩阵缺少 $milestone"
rg -q "${milestone}" "$root/serviceos-architecture/README.md" || fail "Architecture Book 导航缺少 $milestone"
rg -q "${milestone}" "$root/serviceos-architecture/testing/README.md" || fail "测试导航缺少 $milestone"
rg -q "${milestone}" "$root/README.md" || fail "根 README 缺少 $milestone"

actual_migration_count="$(find "$root/serviceos-backend/src/main/resources/db/migration" -type f \
  \( -name 'V*.sql' -o -name 'R*.sql' \) | wc -l | tr -d ' ')"
[[ "$actual_migration_count" == "$expected_migration_count" ]] \
  || fail "迁移数量实际为 $actual_migration_count，期望 $expected_migration_count"

latest_version="$(find "$root/serviceos-backend/src/main/resources/db/migration" -type f -name 'V*.sql' \
  -exec basename {} \; | sed -E 's/^V([0-9]+)__.*/\1/' | sort -n | tail -1)"
[[ "$latest_version" == "$flyway_version" ]] \
  || fail "最高 Flyway 版本实际为 $latest_version，期望 $flyway_version"

if rg -q '^baselineCommit: PENDING_' "$status" && [[ "$allow_pending" != "--allow-pending-baseline" ]]; then
  fail "baselineCommit 仍待回填"
fi
if [[ -n "$allow_pending" && "$allow_pending" != "--allow-pending-baseline" ]]; then
  fail "未知参数: $allow_pending"
fi

index_file="$root/serviceos-architecture/docs/milestone-index.md"
[[ -f "$index_file" ]] || fail "milestone-index.md 缺失，请运行 bash scripts/generate-milestone-index.sh 生成"
if ! diff <(bash "$root/scripts/generate-milestone-index.sh" --stdout) "$index_file" >/dev/null; then
  fail "milestone-index.md 已过期，请运行 bash scripts/generate-milestone-index.sh 重新生成"
fi

echo "里程碑一致性检查通过: $milestone / Flyway $flyway_version / $expected_migration_count migrations"
