#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(git rev-parse --show-toplevel 2>/dev/null || true)"
if [[ -z "${ROOT_DIR}" ]]; then
  echo "错误：必须在 ServiceOS Git 仓库中运行。" >&2
  exit 1
fi
cd "${ROOT_DIR}"

CONTEXT_ID="${1:-}"
if [[ -z "${CONTEXT_ID}" ]]; then
  echo "用法：bash scripts/plan-context.sh <Context-ID>" >&2
  exit 2
fi

PACK="serviceos-architecture/context/milestones/${CONTEXT_ID}.md"
BASELINE="serviceos-architecture/context/current-baseline.md"
CATALOG="serviceos-architecture/context/modules/catalog.tsv"

for required in "${PACK}" "${BASELINE}" "${CATALOG}"; do
  if [[ ! -f "${required}" ]]; then
    echo "错误：缺少上下文文件 ${required}" >&2
    exit 3
  fi
done

frontmatter_value() {
  local key="$1"
  local file="$2"
  awk -v key="${key}" '
    $0 == "---" { marker++ ; next }
    marker == 1 && index($0, key ":") == 1 {
      sub("^[^:]+:[[:space:]]*", "")
      print
      exit
    }
    marker >= 2 { exit }
  ' "${file}"
}

print_section() {
  local title="$1"
  local file="$2"
  awk -v heading="## ${title}" '
    $0 == heading { printing=1; next }
    printing && /^## / { exit }
    printing { print }
  ' "${file}"
}

extract_paths() {
  local title="$1"
  local file="$2"
  print_section "${title}" "${file}" \
    | sed -n 's/^[[:space:]]*- `\([^`]*\)`.*/\1/p'
}

trim() {
  local value="$1"
  value="${value#"${value%%[![:space:]]*}"}"
  value="${value%"${value##*[![:space:]]}"}"
  printf '%s' "${value}"
}

TITLE="$(frontmatter_value title "${PACK}")"
RISK="$(frontmatter_value riskLevel "${PACK}")"
BUDGET="$(frontmatter_value initialReadBudget "${PACK}")"
MODULES="$(frontmatter_value affectedModules "${PACK}")"
STATUS="$(frontmatter_value status "${PACK}")"
LATEST="$(frontmatter_value latestBusinessMilestone "${BASELINE}")"
BASELINE_COMMIT="$(frontmatter_value baselineCommit "${BASELINE}")"

printf 'Context Pack: %s — %s\n' "${CONTEXT_ID}" "${TITLE:-未命名}"
printf '状态: %s\n' "${STATUS:-unknown}"
printf '风险等级: %s\n' "${RISK:-未声明}"
printf '初始阅读预算: %s tokens\n' "${BUDGET:-未声明}"
printf '当前业务基线: %s @ %s\n\n' "${LATEST:-unknown}" "${BASELINE_COMMIT:-unknown}"

printf '目标\n----\n'
print_section "目标" "${PACK}"
printf '\n明确非目标\n----------\n'
print_section "明确非目标" "${PACK}"

printf '\n必须阅读\n--------\n'
missing=0
while IFS= read -r path; do
  [[ -z "${path}" ]] && continue
  printf '  %s\n' "${path}"
  if [[ ! -e "${path}" ]]; then
    printf '    [缺失]\n' >&2
    missing=1
  fi
done < <(extract_paths "必须阅读" "${PACK}")

printf '\n按需阅读\n--------\n'
print_section "按需阅读" "${PACK}"

printf '\n模块路由\n--------\n'
IFS=',' read -r -a module_list <<< "${MODULES}"
for raw_module in "${module_list[@]}"; do
  module="$(trim "${raw_module}")"
  [[ -z "${module}" ]] && continue
  row="$(awk -F'|' -v module="${module}" '$1 == module { print; exit }' "${CATALOG}")"
  if [[ -z "${row}" ]]; then
    printf '  %s: [目录中未登记]\n' "${module}" >&2
    missing=1
    continue
  fi
  IFS='|' read -r _ card authority test_hint neighbors <<< "${row}"
  printf '  %s\n' "${module}"
  printf '    权威入口: %s\n' "${authority}"
  [[ "${card}" != "-" ]] && printf '    模块卡片: %s\n' "${card}"
  printf '    测试检索: %s\n' "${test_hint}"
  printf '    相邻模块: %s\n' "${neighbors}"
  if [[ "${card}" != "-" && ! -f "${card}" ]]; then
    printf '    [模块卡片缺失]\n' >&2
    missing=1
  fi
  if [[ "${authority}" != "-" && ! -f "${authority}" ]]; then
    printf '    [权威入口缺失]\n' >&2
    missing=1
  fi
done

printf '\n必须验证\n--------\n'
print_section "必须验证" "${PACK}"

printf '\n扩大检索前先检查：是否出现契约冲突、状态机/事务/授权变化、跨模块依赖、破坏性迁移或历史兼容。\n'

if [[ "${missing}" -ne 0 ]]; then
  echo "错误：Context Pack 存在缺失引用或未登记模块。" >&2
  exit 4
fi
