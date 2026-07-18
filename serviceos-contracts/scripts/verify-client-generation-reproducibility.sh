#!/usr/bin/env bash
set -euo pipefail

script_directory="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
manifest="${script_directory}/../target/client-artifacts/typescript-fetch/manifest.json"

# 第一次也从干净输出目录生成，避免上一次消费门禁留下的 dist/ 被误计入源码树摘要。
# 复现门禁比较的是两次独立生成，而不是“当前工作目录”与一次生成结果。
"${script_directory}/generate-client-artifact.sh"
first_digest="$(jq -r '.generatedTreeSha256' "${manifest}")"

"${script_directory}/generate-client-artifact.sh"
second_digest="$(jq -r '.generatedTreeSha256' "${manifest}")"

if [[ -z "${first_digest}" || "${first_digest}" != "${second_digest}" ]]; then
  echo "Generated client is not reproducible: first=${first_digest} second=${second_digest}" >&2
  exit 1
fi

echo "Generated client reproducibility gate passed: ${second_digest}"
