#!/usr/bin/env bash

staging_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "${staging_dir}/../.." && pwd)"
compose_file="${repo_root}/serviceos-deploy/compose.staging.yaml"

require_env_file() {
  local env_file="${1:-}"
  if [[ -z "${env_file}" || ! -r "${env_file}" ]]; then
    echo "readable staging env file is required" >&2
    exit 64
  fi
}

load_env_file() {
  local env_file="$1"
  # 文件由本仓库生成器创建或由运维控制，内容只允许简单 KEY=VALUE；禁止提交到 Git。
  set -a
  # shellcheck disable=SC1090
  source "${env_file}"
  set +a
  : "${SERVICEOS_IMAGE:?SERVICEOS_IMAGE is required}"
}

require_immutable_image_or_local_override() {
  if [[ "${SERVICEOS_IMAGE}" == *@sha256:* ]]; then
    return
  fi
  if [[ "${SERVICEOS_ALLOW_MUTABLE_IMAGE:-false}" != "true" ]]; then
    echo "staging image must be pinned by digest: ${SERVICEOS_IMAGE}" >&2
    exit 65
  fi
  echo "warning: mutable image accepted only for local/CI rehearsal: ${SERVICEOS_IMAGE}" >&2
}

compose() {
  docker compose --env-file "$1" -f "${compose_file}" "${@:2}"
}

wait_for_url() {
  local url="$1"
  local attempts="${2:-30}"
  for ((attempt = 1; attempt <= attempts; attempt++)); do
    if curl --fail --silent --show-error --max-time 3 "${url}" >/dev/null; then
      return 0
    fi
    sleep 1
  done
  echo "timed out waiting for ${url}" >&2
  return 1
}
