#!/usr/bin/env bash
set -euo pipefail

# ADR-091 P0 一致性门禁（供 CI 调用）：重新生成 jOOQ 代码，并校验生成物与
# Flyway 迁移基线一致——任何差异（修改/新增/删除/未跟踪文件）都会失败。

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${root}"

bash scripts/generate-jooq.sh

# git diff --exit-code 覆盖已跟踪内容的修改；porcelain 额外覆盖未跟踪文件
#（新表首次生成时尚未跟踪，仅靠 git diff 会漏判）。
git diff --exit-code -- serviceos-backend/src/generated
untracked_or_stale="$(git status --porcelain -- serviceos-backend/src/generated)"
if [[ -n "${untracked_or_stale}" ]]; then
  echo "jOOQ 生成物与 Flyway 迁移基线不一致：" >&2
  echo "${untracked_or_stale}" >&2
  echo "请运行 bash scripts/generate-jooq.sh 并提交更新后的生成物。" >&2
  exit 1
fi
echo "jOOQ 生成物与 Flyway 迁移基线一致。"
