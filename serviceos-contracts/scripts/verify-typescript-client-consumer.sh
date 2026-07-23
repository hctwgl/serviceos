#!/usr/bin/env bash
set -euo pipefail

script_directory="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repository_root="$(cd "${script_directory}/../.." && pwd)"
generated_directory="${repository_root}/serviceos-contracts/target/generated-clients/typescript-fetch"
artifact_directory="${repository_root}/serviceos-contracts/target/client-artifacts/typescript-fetch"
typescript_bin="${SERVICEOS_TYPESCRIPT_BIN:-${repository_root}/serviceos-admin-web/node_modules/.bin/tsc}"

if [[ ! -x "${typescript_bin}" ]]; then
  echo "未找到仓库锁定的 TypeScript 编译器：${typescript_bin}" >&2
  echo "请先在 serviceos-admin-web 执行 npm ci，或设置 SERVICEOS_TYPESCRIPT_BIN。" >&2
  exit 1
fi

# 客户端编译由本门禁显式执行；生成包不得通过 prepare 在 file: 依赖安装期隐式编译。
# 将仓库锁定的 .bin 置于 PATH，避免依赖全局 tsc。
export PATH="$(dirname "${typescript_bin}"):${PATH}"

if [[ ! -f "${generated_directory}/package.json" ]]; then
  "${script_directory}/generate-client-artifact.sh"
fi

# 生成代码只有被真实编译、打包、安装和导入后，才能证明它是可消费的共享契约，
# 不能把“文件生成成功”误当作 Web 客户端已经可用。
"${typescript_bin}" -p "${generated_directory}/tsconfig.json"
"${typescript_bin}" -p "${generated_directory}/tsconfig.esm.json"

mkdir -p "${artifact_directory}"
package_file="$(npm pack --silent --ignore-scripts \
  --pack-destination "${artifact_directory}" "${generated_directory}")"
package_path="${artifact_directory}/${package_file##*/}"

consumer_directory="$(mktemp -d "${TMPDIR:-/tmp}/serviceos-ts-client-consumer.XXXXXX")"
cleanup() {
  rm -rf "${consumer_directory}"
}
trap cleanup EXIT

(
  cd "${consumer_directory}"
  npm init --yes --silent >/dev/null
  npm install --ignore-scripts --no-audit --no-fund --silent "${package_path}"
)

cat > "${consumer_directory}/consumer.ts" <<'EOF'
import { Configuration, DefaultApi } from '@serviceos/core-client';

const configuration = new Configuration({ basePath: 'https://serviceos.invalid' });
const api: DefaultApi = new DefaultApi(configuration);

if (!(api instanceof DefaultApi)) {
  throw new Error('Generated DefaultApi cannot be instantiated');
}
EOF

"${typescript_bin}" \
  --noEmit \
  --strict \
  --target es2020 \
  --module node16 \
  --moduleResolution node16 \
  "${consumer_directory}/consumer.ts"

node -e \
  "const client = require(process.argv[1]); const api = new client.DefaultApi(new client.Configuration({basePath: 'https://serviceos.invalid'})); if (!(api instanceof client.DefaultApi)) process.exit(1);" \
  "${consumer_directory}/node_modules/@serviceos/core-client"

echo "TypeScript Client 消费门禁通过：${package_path}"
