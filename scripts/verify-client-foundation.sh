#!/usr/bin/env bash
set -euo pipefail

repository_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${repository_root}"

output_directory="${repository_root}/target/client-foundation-gate"
log_directory="${output_directory}/logs"
rm -rf "${output_directory}"
mkdir -p "${log_directory}"

passed=()
run_gate() {
  local name="$1"
  shift
  local log_file="${log_directory}/${name}.log"
  if "$@" >"${log_file}" 2>&1; then
    passed+=("${name}")
    echo "PASS ${name}"
    return
  fi
  echo "FAIL ${name}；日志：${log_file}" >&2
  tail -n 80 "${log_file}" >&2
  exit 1
}

# 每个生成物都必须经过自己的独立消费者，而不是只证明文件能生成。
run_gate typescript-client bash scripts/agent-verify.sh client-ts
run_gate swift-client bash scripts/agent-verify.sh client-swift
run_gate design-tokens bash scripts/agent-verify.sh design-tokens
run_gate client-identities bash scripts/agent-verify.sh client-identities
run_gate web-core bash scripts/agent-verify.sh web-core
run_gate ios-core bash scripts/agent-verify.sh ios-core

# 元数据专项在总门禁中只重跑协议/服务端测试；Web/iOS 请求探针已由上面两个独立 Core 门禁覆盖。
run_gate client-metadata-backend ./mvnw --no-transfer-progress \
  -pl serviceos-backend -Dtest=CorrelationContextFilterTest test
run_gate client-metadata-contract ./mvnw --no-transfer-progress \
  -pl serviceos-contracts -Dtest=ClientMetadataContractTest test
run_gate contract-compatibility bash scripts/agent-verify.sh contracts

git_head="$(git rev-parse HEAD)"
openapi_version="$(sed -n 's/^  version: //p' serviceos-contracts/src/main/resources/openapi/serviceos-core-v1.yaml | head -1)"
jq -n \
  --arg gateVersion "client-foundation-gate-v1" \
  --arg gitHead "${git_head}" \
  --arg openApiVersion "${openapi_version}" \
  --argjson passed "$(printf '%s\n' "${passed[@]}" | jq -R . | jq -s .)" \
  '{gateVersion: $gateVersion, gitHead: $gitHead, openApiVersion: $openApiVersion, passed: $passed}' \
  > "${output_directory}/manifest.json"

echo "Client Foundation 总门禁通过：${#passed[@]} gates / Core OpenAPI ${openapi_version}"
echo "证据清单：${output_directory}/manifest.json"
