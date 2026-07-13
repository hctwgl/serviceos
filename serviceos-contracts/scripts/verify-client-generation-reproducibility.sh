#!/usr/bin/env bash
set -euo pipefail

script_directory="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
manifest="${script_directory}/../target/client-artifacts/typescript-fetch/manifest.json"

"${script_directory}/generate-client-artifact.sh" --use-existing
first_digest="$(jq -r '.generatedTreeSha256' "${manifest}")"

"${script_directory}/generate-client-artifact.sh"
second_digest="$(jq -r '.generatedTreeSha256' "${manifest}")"

if [[ -z "${first_digest}" || "${first_digest}" != "${second_digest}" ]]; then
  echo "Generated client is not reproducible: first=${first_digest} second=${second_digest}" >&2
  exit 1
fi

echo "Generated client reproducibility gate passed: ${second_digest}"
