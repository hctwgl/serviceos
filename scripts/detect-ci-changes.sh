#!/usr/bin/env bash
set -euo pipefail

output_file="${GITHUB_OUTPUT:-/dev/stdout}"

emit() {
  printf '%s=%s\n' "$1" "$2" >> "${output_file}"
}

if [[ "${1:-}" == "--full" ]]; then
  emit full true
  emit docs_only false
  emit admin true
  emit backend true
  emit contracts true
  emit deploy true
  emit pilot true
  exit 0
fi

admin=false
backend=false
contracts=false
deploy=false
pilot=false
docs_only=true
unknown=false
changed=false

while IFS= read -r path; do
  [[ -z "${path}" ]] && continue
  changed=true

  case "${path}" in
    *.md|LICENSE|LICENSE.*|serviceos-architecture/*)
      ;;
    serviceos-admin-web/*)
      docs_only=false
      admin=true
      ;;
    serviceos-backend/*|pom.xml|mvnw|mvnw.cmd|.mvn/*)
      docs_only=false
      backend=true
      ;;
    serviceos-contracts/*)
      docs_only=false
      contracts=true
      ;;
    serviceos-deploy/admin-pilot/*|serviceos-deploy/keycloak/*)
      docs_only=false
      deploy=true
      pilot=true
      ;;
    serviceos-deploy/*|Dockerfile)
      docs_only=false
      deploy=true
      ;;
    .github/*|scripts/*)
      docs_only=false
      unknown=true
      ;;
    *)
      docs_only=false
      unknown=true
      ;;
  esac
done

if [[ "${changed}" == "false" ]]; then
  docs_only=false
  unknown=true
fi

# 未识别路径和 CI 基础设施变化采用保守策略：完整 PR 验证，但仍不包含 staging。
if [[ "${unknown}" == "true" ]]; then
  admin=true
  backend=true
  contracts=true
  deploy=true
  pilot=true
fi

emit full false
emit docs_only "${docs_only}"
emit admin "${admin}"
emit backend "${backend}"
emit contracts "${contracts}"
emit deploy "${deploy}"
emit pilot "${pilot}"
