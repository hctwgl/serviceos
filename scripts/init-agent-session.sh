#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(git rev-parse --show-toplevel 2>/dev/null || true)"
if [[ -z "${ROOT_DIR}" ]]; then
  echo "错误：必须在 ServiceOS Git 仓库中运行。" >&2
  exit 1
fi
cd "${ROOT_DIR}"

CONTEXT_ID="${1:-}"
FORCE="${2:-}"
if [[ -z "${CONTEXT_ID}" ]]; then
  echo "用法：bash scripts/init-agent-session.sh <Context-ID> [--force]" >&2
  exit 2
fi

PACK="serviceos-architecture/context/milestones/${CONTEXT_ID}.md"
SESSION_FILE=".agent/session-state.md"
if [[ ! -f "${PACK}" ]]; then
  echo "错误：Context Pack 不存在：${PACK}" >&2
  exit 3
fi
if [[ -f "${SESSION_FILE}" && "${FORCE}" != "--force" ]]; then
  echo "错误：${SESSION_FILE} 已存在；确认覆盖时追加 --force。" >&2
  exit 4
fi

mkdir -p .agent
HEAD_SHA="$(git rev-parse HEAD)"
BRANCH="$(git branch --show-current 2>/dev/null || true)"
TIMESTAMP="$(date -u '+%Y-%m-%dT%H:%M:%SZ')"
TITLE="$(awk '
  $0 == "---" { marker++; next }
  marker == 1 && /^title:/ { sub("^title:[[:space:]]*", ""); print; exit }
' "${PACK}")"

cat > "${SESSION_FILE}" <<EOF
---
contextId: ${CONTEXT_ID}
contextTitle: ${TITLE}
branch: ${BRANCH:-detached}
baselineCommit: ${HEAD_SHA}
lastUpdated: ${TIMESTAMP}
---

# Agent 本地会话状态

本文件只用于本机跨会话续作，已被 Git 忽略，不是架构事实源。继续任务前先比较 baselineCommit、当前 HEAD 和已读取文件 SHA；发生变化时只重新读取变化部分。

## 已确认事实

- 待填写。

## 已读取文件与版本

- \`${PACK}\` @ 当前工作树

## 当前代码入口

- 待填写。

## 已完成

- 待填写。

## 待完成

- 待填写。

## 未决事项

- 无。

## 已执行验证

- 未执行。
EOF

printf '已生成 %s\n' "${SESSION_FILE}"
