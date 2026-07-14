#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "${script_dir}/../.." && pwd)"
current_image="${1:-serviceos-backend:m17-rehearsal}"
rollback_ref="${2:-HEAD^}"
rollback_image="${3:-serviceos-backend:m17-rollback-rehearsal}"
env_file="$(mktemp)"
bad_env_file="$(mktemp)"
evidence_dir="${repo_root}/target/staging-rehearsal"

cleanup() {
  set +e
  if [[ -s "${env_file}" ]]; then
    "${script_dir}/cleanup.sh" "${env_file}" >/dev/null 2>&1
  fi
  rm -f "${env_file}" "${bad_env_file}"
}
trap cleanup EXIT

rm -rf "${evidence_dir}"
"${script_dir}/generate-local-env.sh" "${env_file}" "${current_image}"
# rehearsal 使用固定隔离 project 名；先清理上次中断留下的容器/volume，保证每次都从空库开始。
"${script_dir}/cleanup.sh" "${env_file}" >/dev/null 2>&1 || true

# 正式模式必须拒绝只带 tag 的可变镜像引用，不能静默降级为本地策略。
awk '
  /^SERVICEOS_ALLOW_MUTABLE_IMAGE=/ {
    print "SERVICEOS_ALLOW_MUTABLE_IMAGE=false"
    next
  }
  { print }
' "${env_file}" > "${bad_env_file}"
chmod 600 "${bad_env_file}"
mkdir -p "${evidence_dir}"
if "${script_dir}/deploy.sh" "${bad_env_file}" >"${evidence_dir}/mutable-image-negative.log" 2>&1; then
  echo "formal deployment unexpectedly accepted a tag-only image" >&2
  exit 1
fi
rg -q 'staging image must be pinned by digest' "${evidence_dir}/mutable-image-negative.log"

"${script_dir}/build-image.sh" "${current_image}"
"${script_dir}/deploy.sh" "${env_file}"
"${script_dir}/smoke.sh" "${env_file}" "${evidence_dir}/current"

"${script_dir}/build-rollback-candidate.sh" "${rollback_ref}" "${rollback_image}"
"${script_dir}/rollback.sh" "${env_file}" "${rollback_image}"
"${script_dir}/restore-current.sh" "${env_file}"
"${script_dir}/smoke.sh" "${env_file}" "${evidence_dir}/restored"

# 迁移版本不符合发布清单时必须失败关闭，且不能替换仍在服务的后端容器。
awk '
  /^SERVICEOS_EXPECTED_MIGRATION_VERSION=/ {
    print "SERVICEOS_EXPECTED_MIGRATION_VERSION=999"
    next
  }
  { print }
' "${env_file}" > "${bad_env_file}"
chmod 600 "${bad_env_file}"
before_container="$(docker compose --env-file "${env_file}" \
  -f "${repo_root}/serviceos-deploy/compose.staging.yaml" ps -q backend)"
if "${script_dir}/deploy.sh" "${bad_env_file}" >"${evidence_dir}/fail-closed.log" 2>&1; then
  echo "deployment unexpectedly accepted a mismatched migration version" >&2
  exit 1
fi
after_container="$(docker compose --env-file "${env_file}" \
  -f "${repo_root}/serviceos-deploy/compose.staging.yaml" ps -q backend)"
[[ "${before_container}" == "${after_container}" ]]
rg -q 'migration version mismatch: expected=999 actual=023' "${evidence_dir}/fail-closed.log"
curl --fail --silent --show-error "http://127.0.0.1:18080/readyz" \
  | jq -e '.status == "UP"' >/dev/null

echo "full staging deployment rehearsal passed: ${evidence_dir}"
