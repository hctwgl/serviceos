#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(git rev-parse --show-toplevel 2>/dev/null || true)"
if [[ -z "${ROOT_DIR}" ]]; then
  echo "错误：必须在 ServiceOS Git 仓库中运行。" >&2
  exit 1
fi
cd "${ROOT_DIR}"

REQUESTED_BASE="${1:-master}"
if git rev-parse --verify "${REQUESTED_BASE}^{commit}" >/dev/null 2>&1; then
  BASE_REF="${REQUESTED_BASE}"
elif git rev-parse --verify "origin/${REQUESTED_BASE}^{commit}" >/dev/null 2>&1; then
  BASE_REF="origin/${REQUESTED_BASE}"
elif git rev-parse --verify 'HEAD^' >/dev/null 2>&1; then
  BASE_REF="HEAD^"
else
  echo "错误：无法解析比较基线 ${REQUESTED_BASE}。" >&2
  exit 2
fi

CATALOG="serviceos-architecture/context/modules/catalog.tsv"
if [[ ! -f "${CATALOG}" ]]; then
  echo "错误：缺少模块目录 ${CATALOG}" >&2
  exit 3
fi

changed_files="$({
  git diff --name-only "${BASE_REF}"...HEAD 2>/dev/null || true
  git diff --name-only 2>/dev/null || true
  git diff --cached --name-only 2>/dev/null || true
} | awk 'NF && !seen[$0]++')"

if [[ -z "${changed_files}" ]]; then
  printf '比较基线: %s\n未发现变更。\n' "${BASE_REF}"
  exit 0
fi

risk_rank=0
risk_reason=()
modules=()

add_module() {
  local candidate="$1"
  local item
  [[ -z "${candidate}" ]] && return
  for item in "${modules[@]:-}"; do
    [[ "${item}" == "${candidate}" ]] && return
  done
  modules+=("${candidate}")
}

raise_risk() {
  local rank="$1"
  local reason="$2"
  if (( rank > risk_rank )); then
    risk_rank="${rank}"
  fi
  risk_reason+=("${reason}")
}

while IFS= read -r path; do
  [[ -z "${path}" ]] && continue
  case "${path}" in
    serviceos-backend/src/main/java/com/serviceos/*/*)
      module="$(printf '%s' "${path}" | sed -E 's#serviceos-backend/src/main/java/com/serviceos/([^/]+)/.*#\1#')"
      add_module "${module}"
      raise_risk 1 "生产代码：${path}"
      ;;
    serviceos-backend/src/test/java/com/serviceos/*/*)
      module="$(printf '%s' "${path}" | sed -E 's#serviceos-backend/src/test/java/com/serviceos/([^/]+)/.*#\1#')"
      add_module "${module}"
      raise_risk 1 "测试代码：${path}"
      ;;
    serviceos-backend/src/main/resources/db/migration/*/*)
      module="$(printf '%s' "${path}" | sed -E 's#serviceos-backend/src/main/resources/db/migration/([^/]+)/.*#\1#')"
      add_module "${module}"
      raise_risk 2 "Flyway/SQL：${path}"
      ;;
    serviceos-contracts/*)
      add_module contracts
      raise_risk 2 "机器契约：${path}"
      ;;
    AGENTS.md|agent-rules/*|serviceos-architecture/context/*|scripts/plan-context.sh|scripts/plan-impact.sh|scripts/init-agent-session.sh|scripts/test-context-tools.sh)
      add_module agent-context
      raise_risk 1 "Agent 路由规则：${path}"
      ;;
    serviceos-architecture/decisions/*|serviceos-architecture/domain/*)
      add_module documentation
      raise_risk 3 "ADR/领域语义：${path}"
      ;;
    serviceos-architecture/*)
      add_module documentation
      raise_risk 0 "架构或产品文档：${path}"
      ;;
    .github/workflows/*|pom.xml|*/pom.xml|mvnw|.mvn/*)
      add_module build
      raise_risk 2 "构建/CI：${path}"
      ;;
    .gitignore)
      add_module build
      raise_risk 0 "仓库忽略规则：${path}"
      ;;
    *)
      raise_risk 0 "其他文件：${path}"
      ;;
  esac

  case "${path}" in
    */package-info.java)
      add_module build
      raise_risk 2 "Spring Modulith 模块声明：${path}"
      ;;
  esac
done <<< "${changed_files}"

case "${risk_rank}" in
  0) risk="R0" ;;
  1) risk="R1" ;;
  2) risk="R2" ;;
  *) risk="R3" ;;
esac

printf '比较基线: %s\n' "${BASE_REF}"
printf '建议风险下限: %s\n\n' "${risk}"
printf '变更文件\n--------\n%s\n' "${changed_files}"

printf '\n影响模块\n--------\n'
for module in "${modules[@]:-}"; do
  row="$(awk -F'|' -v module="${module}" '$1 == module { print; exit }' "${CATALOG}")"
  if [[ -z "${row}" ]]; then
    printf '  %s: [模块目录未登记，需人工补充]\n' "${module}"
    continue
  fi
  IFS='|' read -r _ card authority test_hint neighbors <<< "${row}"
  printf '  %s\n' "${module}"
  printf '    权威入口: %s\n' "${authority}"
  [[ "${card}" != "-" ]] && printf '    模块卡片: %s\n' "${card}"
  printf '    测试检索: rg --files serviceos-backend/src/test | rg %q\n' "${test_hint}"
  printf '    相邻模块: %s\n' "${neighbors}"
done

printf '\n升级原因\n--------\n'
printf '  %s\n' "${risk_reason[@]}"

cat <<'NOTICE'

注意：该结果只给出最低风险下限。出现授权、租户范围、状态机、事务边界、外部副作用、破坏性数据或契约变化时，应人工升级到更高等级。
NOTICE
