#!/usr/bin/env bash
set -euo pipefail

script_directory="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
module_directory="$(cd "${script_directory}/.." && pwd)"
repository_root="$(cd "${module_directory}/.." && pwd)"
manifest="${module_directory}/target/generated-client-identities/manifest.json"
typescript_bin="${SERVICEOS_TYPESCRIPT_BIN:-${repository_root}/serviceos-frontend/node_modules/.bin/tsc}"

if [[ ! -x "${typescript_bin}" ]]; then
  echo "未找到仓库锁定的 TypeScript 编译器：${typescript_bin}" >&2
  exit 1
fi

node "${script_directory}/generate-client-identities.mjs"
first_digest="$(jq -r '.generatedTreeSha256' "${manifest}")"
node "${script_directory}/generate-client-identities.mjs"
second_digest="$(jq -r '.generatedTreeSha256' "${manifest}")"
if [[ -z "${first_digest}" || "${first_digest}" != "${second_digest}" ]]; then
  echo "Client Identity 生成不稳定: first=${first_digest} second=${second_digest}" >&2
  exit 1
fi

build_directory="$(mktemp -d "${TMPDIR:-/tmp}/serviceos-client-identity-build.XXXXXX")"
cleanup() {
  rm -rf "${build_directory}"
}
trap cleanup EXIT

typescript_source="${module_directory}/target/generated-client-identities/typescript/client-identities.ts"
typescript_probe="${build_directory}/client-identities.test.ts"
cp "${typescript_source}" "${build_directory}/client-identities.ts"
printf '%s\n' \
  "import { filterKnownActionCodes, isKnownActionCode } from './client-identities.js'" \
  "if (!isKnownActionCode('task.claim')) throw new Error('known action missing')" \
  "if (isKnownActionCode('task.guess')) throw new Error('unknown action accepted')" \
  "if (filterKnownActionCodes(['task.claim', 'task.guess']).join(',') !== 'task.claim') throw new Error('unknown action not hidden')" \
  > "${typescript_probe}"
"${typescript_bin}" --strict --target ES2022 --module NodeNext --moduleResolution NodeNext --outDir "${build_directory}/js" \
  "${build_directory}/client-identities.ts" "${typescript_probe}"
node "${build_directory}/js/client-identities.test.js"

swift_source="${module_directory}/target/generated-client-identities/swift/ServiceOSClientIdentities.swift"
swift_probe="${build_directory}/ClientIdentitiesSmoke.swift"
printf '%s\n' \
  '@main' \
  'struct ClientIdentitiesSmoke {' \
  '    static func main() {' \
  '        precondition(ServiceOSClientIdentities.isKnownActionCode("task.claim"))' \
  '        precondition(!ServiceOSClientIdentities.isKnownActionCode("task.guess"))' \
  '        precondition(ServiceOSClientIdentities.filterKnownActionCodes(["task.claim", "task.guess"]) == ["task.claim"])' \
  '    }' \
  '}' > "${swift_probe}"
swiftc -swift-version 6 -strict-concurrency=complete "${swift_source}" "${swift_probe}" -o "${build_directory}/client-identities-smoke"
"${build_directory}/client-identities-smoke"

echo "Client Identity 门禁通过：${second_digest}"
