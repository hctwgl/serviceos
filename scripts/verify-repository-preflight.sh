#!/usr/bin/env bash
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${root}"

fail() {
  echo "仓库预检失败: $1" >&2
  exit 1
}

command -v rg >/dev/null 2>&1 || fail "需要 ripgrep (rg)"
command -v node >/dev/null 2>&1 || fail "需要 Node.js 22.18.0 或更高版本"

required_files=(
  "pom.xml"
  "serviceos-backend/pom.xml"
  "serviceos-contracts/pom.xml"
  "serviceos-frontend/package.json"
  "serviceos-frontend/pnpm-workspace.yaml"
  "serviceos-ios-core/Package.swift"
  "serviceos-technician-ios/Package.swift"
  "serviceos-deploy/compose.yaml"
  "serviceos-deploy/compose.staging.yaml"
)
for required_file in "${required_files[@]}"; do
  [[ -f "${required_file}" ]] || fail "当前工程入口缺失: ${required_file}"
done

required_directories=(
  "serviceos-backend/src/main/java/com/serviceos"
  "serviceos-contracts/src/main/resources/openapi"
  "serviceos-contracts/src/main/resources/events"
  "serviceos-frontend/apps/admin"
  "serviceos-frontend/apps/network"
  "serviceos-frontend/apps/technician"
  "serviceos-frontend/packages/api-client"
  "serviceos-frontend/packages/auth-context"
  "serviceos-frontend/packages/design-system"
  "serviceos-frontend/packages/product-language"
  "serviceos-ios-core/Sources/ServiceOSIOSCore"
  "serviceos-technician-ios/Sources/TechnicianIOSFoundation"
)
for required_directory in "${required_directories[@]}"; do
  [[ -d "${required_directory}" ]] || fail "当前工程目录缺失: ${required_directory}"
done

# 这些根目录已被当前统一工程替代。只检查 Git 跟踪内容，避免本地工具留下的空目录造成误报。
removed_roots=(
  "serviceos-admin-web"
  "serviceos-network-web"
  "serviceos-technician-web"
  "serviceos-web-core"
  "serviceos-deploy/admin-pilot"
  "serviceos-deploy/demo"
)
for removed_root in "${removed_roots[@]}"; do
  if [[ -n "$(git ls-files -- "${removed_root}")" ]]; then
    fail "已删除工程根目录被重新引入: ${removed_root}"
  fi
done

read -r flyway_version migration_count < <(bash scripts/migration-baseline.sh)

while IFS= read -r -d '' script; do
  bash -n "${script}"
done < <(
  find scripts serviceos-contracts/scripts serviceos-deploy \
    -type f -name '*.sh' -print0
)

while IFS= read -r -d '' script; do
  node --check "${script}" >/dev/null
done < <(
  find serviceos-contracts/scripts serviceos-frontend/scripts \
    serviceos-deploy/product-development -type f -name '*.mjs' -print0
)

bash scripts/check-migration-baseline-references.sh

if rg -n '^SERVICEOS_EXPECTED_MIGRATION_(VERSION|COUNT)=[0-9]+$' \
  serviceos-deploy/staging; then
  fail "staging 不得保存会漂移的硬编码迁移终点；请使用 generate-local-env.sh"
fi

generated_env="$(mktemp)"
trap 'rm -f "${generated_env}"' EXIT
serviceos-deploy/staging/generate-local-env.sh \
  "${generated_env}" "serviceos-backend:preflight" >/dev/null

rg -qx "SERVICEOS_EXPECTED_MIGRATION_VERSION=${flyway_version}" "${generated_env}" \
  || fail "staging 迁移版本未从当前迁移目录生成"
rg -qx "SERVICEOS_EXPECTED_MIGRATION_COUNT=${migration_count}" "${generated_env}" \
  || fail "staging 迁移数量未从当前迁移目录生成"

if docker compose version >/dev/null 2>&1; then
  docker compose -f serviceos-deploy/compose.yaml config --quiet
  docker compose --env-file "${generated_env}" \
    -f serviceos-deploy/compose.staging.yaml config --quiet
fi

git diff --check

printf '仓库预检通过: Flyway %s / %s migrations\n' \
  "${flyway_version}" "${migration_count}"
