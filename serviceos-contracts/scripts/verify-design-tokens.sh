#!/usr/bin/env bash
set -euo pipefail

script_directory="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
module_directory="$(cd "${script_directory}/.." && pwd)"
manifest="${module_directory}/target/generated-design-tokens/manifest.json"

node "${script_directory}/generate-design-tokens.mjs"
first_digest="$(jq -r '.generatedTreeSha256' "${manifest}")"
node "${script_directory}/generate-design-tokens.mjs"
second_digest="$(jq -r '.generatedTreeSha256' "${manifest}")"

if [[ -z "${first_digest}" || "${first_digest}" != "${second_digest}" ]]; then
  echo "Design Token 生成不稳定: first=${first_digest} second=${second_digest}" >&2
  exit 1
fi

build_directory="$(mktemp -d "${TMPDIR:-/tmp}/serviceos-design-token-build.XXXXXX")"
cleanup() {
  rm -rf "${build_directory}"
}
trap cleanup EXIT

swiftc \
  -swift-version 6 \
  -parse-as-library \
  -emit-module \
  -module-name ServiceOSDesignTokens \
  -emit-module-path "${build_directory}/ServiceOSDesignTokens.swiftmodule" \
  "${module_directory}/target/generated-design-tokens/swift/ServiceOSDesignTokens.swift"

css_file="${module_directory}/target/generated-design-tokens/web/serviceos-design-tokens.css"
rg -q --fixed-strings -- '--serviceos-color-action-primary: #243B53;' "${css_file}"
rg -q --fixed-strings -- '--serviceos-spacing-lg: 16px;' "${css_file}"

echo "Design Token 门禁通过：${second_digest}"
