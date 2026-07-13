#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "${script_dir}/../.." && pwd)"
image_ref="${1:-}"
if [[ -z "${image_ref}" ]]; then
  echo "usage: $0 <image-ref>" >&2
  exit 64
fi

revision="$(git -C "${repo_root}" rev-parse HEAD)"
created="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
docker build \
  --build-arg "VCS_REF=${revision}" \
  --build-arg "BUILD_CREATED=${created}" \
  --tag "${image_ref}" \
  "${repo_root}"

image_id="$(docker image inspect --format '{{.Id}}' "${image_ref}")"
echo "built ServiceOS image: ${image_ref} ${image_id}"
