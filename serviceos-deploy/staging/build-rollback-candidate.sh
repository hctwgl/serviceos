#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "${script_dir}/../.." && pwd)"
git_ref="${1:-}"
image_ref="${2:-}"
if [[ -z "${git_ref}" || -z "${image_ref}" ]]; then
  echo "usage: $0 <git-ref> <image-ref>" >&2
  exit 64
fi
git -C "${repo_root}" cat-file -e "${git_ref}^{commit}"

work_dir="$(mktemp -d)"
trap 'rm -rf "${work_dir}"' EXIT
mkdir -p "${work_dir}/source" "${work_dir}/context"
git -C "${repo_root}" archive "${git_ref}" | tar -x -C "${work_dir}/source"
(
  cd "${work_dir}/source"
  ./mvnw --batch-mode --no-transfer-progress -pl serviceos-backend -am clean package -DskipTests
)
cp "${work_dir}/source"/serviceos-backend/target/serviceos-backend-*.jar "${work_dir}/context/app.jar"
cp "${script_dir}/entrypoint.sh" "${work_dir}/context/entrypoint.sh"

revision="$(git -C "${repo_root}" rev-parse "${git_ref}^{commit}")"
docker build \
  --file "${script_dir}/runtime.Dockerfile" \
  --build-arg "VCS_REF=${revision}" \
  --build-arg "BUILD_CREATED=$(date -u +%Y-%m-%dT%H:%M:%SZ)" \
  --tag "${image_ref}" \
  "${work_dir}/context"
echo "built rollback candidate: ${image_ref} ${revision}"
