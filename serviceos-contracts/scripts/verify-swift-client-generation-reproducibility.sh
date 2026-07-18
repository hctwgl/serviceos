#!/usr/bin/env bash
set -euo pipefail

script_directory="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
manifest="${script_directory}/../target/client-artifacts/swift6/manifest.json"

"${script_directory}/generate-swift-client-artifact.sh"
first_digest="$(jq -r '.generatedTreeSha256' "${manifest}")"

"${script_directory}/generate-swift-client-artifact.sh"
second_digest="$(jq -r '.generatedTreeSha256' "${manifest}")"

if [[ -z "${first_digest}" || "${first_digest}" != "${second_digest}" ]]; then
  echo "Generated Swift client is not reproducible: first=${first_digest} second=${second_digest}" >&2
  exit 1
fi

echo "Generated Swift client reproducibility gate passed: ${second_digest}"
