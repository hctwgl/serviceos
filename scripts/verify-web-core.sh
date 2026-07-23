#!/usr/bin/env bash
set -euo pipefail

repository_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
package_directory="${repository_root}/serviceos-web-core"
typescript_bin="${SERVICEOS_TYPESCRIPT_BIN:-${repository_root}/serviceos-frontend/node_modules/.bin/tsc}"

if [[ ! -x "${typescript_bin}" ]]; then
  echo "未找到仓库锁定的 TypeScript 编译器：${typescript_bin}" >&2
  exit 1
fi

if rg -n '\b(ADMIN|NETWORK|TECHNICIAN|CONSUMER)\b' "${package_directory}/src"; then
  echo "共享 Web Core 不得包含 Portal 或角色假设。" >&2
  exit 1
fi

"${typescript_bin}" -p "${package_directory}/tsconfig.json"
node --test "${package_directory}/tests/web-core.test.mjs"

artifact_directory="${repository_root}/target/web-core-artifacts"
mkdir -p "${artifact_directory}"
package_file="$(npm pack --silent --ignore-scripts --pack-destination "${artifact_directory}" "${package_directory}")"
package_path="${artifact_directory}/${package_file##*/}"
consumer_directory="$(mktemp -d "${TMPDIR:-/tmp}/serviceos-web-core-consumer.XXXXXX")"
cleanup() {
  rm -rf "${consumer_directory}"
}
trap cleanup EXIT

(
  cd "${consumer_directory}"
  npm init --yes --silent >/dev/null
  npm install --ignore-scripts --no-audit --no-fund --silent "${package_path}"
  node --input-type=module -e \
    "import {createMemoryAccessTokenStore} from '@serviceos/web-core'; const store=createMemoryAccessTokenStore(()=>0,0); store.set({accessToken:'x',expiresAtEpochMs:1}); if(!store.current()) process.exit(1)"
)

echo "Web Core 门禁通过：${package_path}"
