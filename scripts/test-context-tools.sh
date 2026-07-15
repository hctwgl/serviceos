#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(git rev-parse --show-toplevel 2>/dev/null || true)"
if [[ -z "${ROOT_DIR}" ]]; then
  echo "错误：必须在 ServiceOS Git 仓库中运行。" >&2
  exit 1
fi
cd "${ROOT_DIR}"

scripts=(
  scripts/plan-context.sh
  scripts/plan-impact.sh
  scripts/init-agent-session.sh
  scripts/test-context-tools.sh
)

bash -n "${scripts[@]}"

context_output="$(bash scripts/plan-context.sh CTX-001)"
grep -q 'Context Pack: CTX-001' <<< "${context_output}"
grep -q '风险等级: R2' <<< "${context_output}"
grep -q 'agent-context' <<< "${context_output}"
grep -q 'bootstrap' <<< "${context_output}"
grep -q '必须验证' <<< "${context_output}"

catalog="serviceos-architecture/context/modules/catalog.tsv"
duplicate_modules="$(awk -F'|' '!/^#/ && seen[$1]++ { print $1 }' "${catalog}")"
if [[ -n "${duplicate_modules}" ]]; then
  echo "错误：模块目录存在重复键：${duplicate_modules}" >&2
  exit 2
fi
if grep -Eq '^[[:space:]]+[^#]' "${catalog}"; then
  echo "错误：模块目录键前存在空白字符。" >&2
  exit 3
fi

while IFS='|' read -r module card authority _ _; do
  [[ -z "${module}" || "${module}" == \#* ]] && continue
  if [[ "${card}" != "-" && ! -f "${card}" ]]; then
    echo "错误：模块 ${module} 的卡片不存在：${card}" >&2
    exit 4
  fi
  if [[ "${authority}" != "-" && ! -f "${authority}" ]]; then
    echo "错误：模块 ${module} 的权威入口不存在：${authority}" >&2
    exit 5
  fi
done < "${catalog}"

trap 'rm -f .agent/session-state.md' EXIT
bash scripts/init-agent-session.sh CTX-001 --force >/dev/null
test -f .agent/session-state.md
grep -q 'contextId: CTX-001' .agent/session-state.md
if ! git check-ignore -q .agent/session-state.md; then
  echo "错误：本地会话状态未被 .gitignore 排除。" >&2
  exit 6
fi

impact_output="$(bash scripts/plan-impact.sh master)"
grep -q '建议风险下限:' <<< "${impact_output}"
grep -q '影响模块' <<< "${impact_output}"

printf 'Agent context routing tools verified.\n'
