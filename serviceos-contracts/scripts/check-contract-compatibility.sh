#!/usr/bin/env bash
set -euo pipefail

script_directory="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repository_root="${CONTRACT_REPOSITORY_ROOT:-$(cd "${script_directory}/../.." && pwd)}"
openapi_path="serviceos-contracts/src/main/resources/openapi/serviceos-core-v1.yaml"
event_schema_directory="serviceos-contracts/src/main/resources/events"

base_ref="${1:-${CONTRACT_BASE_REF:-}}"
if [[ -z "${base_ref}" || "${base_ref}" =~ ^0+$ ]]; then
  if git -C "${repository_root}" rev-parse --verify HEAD^ >/dev/null 2>&1; then
    base_ref="HEAD^"
  else
    echo "Contract compatibility bootstrap: no parent revision exists; current contract validation still runs in Maven."
    exit 0
  fi
fi

if ! git -C "${repository_root}" cat-file -e "${base_ref}^{commit}" 2>/dev/null; then
  echo "Contract base ref is not available locally: ${base_ref}. Use checkout fetch-depth 0 in CI." >&2
  exit 2
fi

temporary_directory="$(mktemp -d "${TMPDIR:-/tmp}/serviceos-contract-base.XXXXXX")"
trap 'rm -rf "${temporary_directory}"' EXIT

if git -C "${repository_root}" cat-file -e "${base_ref}:${openapi_path}" 2>/dev/null; then
  oasdiff_binary="${OASDIFF_BIN:-}"
  if [[ -z "${oasdiff_binary}" ]]; then
    oasdiff_binary="$(command -v oasdiff || true)"
  fi
  if [[ -z "${oasdiff_binary}" || ! -x "${oasdiff_binary}" ]]; then
    echo "oasdiff is required. Run serviceos-contracts/scripts/install-oasdiff.sh and set OASDIFF_BIN." >&2
    exit 2
  fi

  git -C "${repository_root}" show "${base_ref}:${openapi_path}" > "${temporary_directory}/base-openapi.yaml"
  "${oasdiff_binary}" breaking \
    --fail-on WARN \
    --format text \
    --color never \
    "${temporary_directory}/base-openapi.yaml" \
    "${repository_root}/${openapi_path}"
else
  echo "OpenAPI compatibility bootstrap: ${openapi_path} did not exist at ${base_ref}."
fi

# 已发布事件版本是不可变资产：删除或原地修改都必须失败；兼容演进只能新增 vN 文件。
event_schema_failure=0
while IFS= read -r published_schema; do
  [[ -z "${published_schema}" ]] && continue
  current_schema="${repository_root}/${published_schema}"
  if [[ ! -f "${current_schema}" ]]; then
    echo "Published event schema was deleted: ${published_schema}" >&2
    event_schema_failure=1
    continue
  fi

  git -C "${repository_root}" show "${base_ref}:${published_schema}" > "${temporary_directory}/published.schema.json"
  if ! cmp -s "${temporary_directory}/published.schema.json" "${current_schema}"; then
    echo "Published event schema changed in place: ${published_schema}. Add a new -vN.schema.json instead." >&2
    event_schema_failure=1
  fi
done < <(git -C "${repository_root}" ls-tree -r --name-only "${base_ref}" -- "${event_schema_directory}" \
  | LC_ALL=C sort \
  | grep -- '-v[0-9][0-9]*\.schema\.json$' || true)

if [[ "${event_schema_failure}" -ne 0 ]]; then
  exit 1
fi

echo "Contract compatibility gate passed against ${base_ref}."
