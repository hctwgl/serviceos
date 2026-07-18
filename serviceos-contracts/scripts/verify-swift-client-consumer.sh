#!/usr/bin/env bash
set -euo pipefail

script_directory="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repository_root="$(cd "${script_directory}/../.." && pwd)"
generated_directory="${repository_root}/serviceos-contracts/target/generated-clients/swift6"
sources_directory="${generated_directory}/Sources/ServiceOSCoreClient"

if ! command -v swiftc >/dev/null 2>&1; then
  echo "未找到 Swift 编译器，无法验证 Swift Client。" >&2
  exit 1
fi

if [[ ! -d "${sources_directory}" ]]; then
  "${script_directory}/generate-swift-client-artifact.sh"
fi

build_directory="$(mktemp -d "${TMPDIR:-/tmp}/serviceos-swift-client-build.XXXXXX")"
cleanup() {
  rm -rf "${build_directory}"
}
trap cleanup EXIT

# 直接以 Swift 6 严格模式编译全部生成源码，避免仅凭 Package.swift 存在就宣称客户端可用。
find "${sources_directory}" -name '*.swift' -print0 \
  | xargs -0 swiftc \
      -swift-version 6 \
      -parse-as-library \
      -module-name ServiceOSCoreClient \
      -emit-module \
      -emit-library \
      -emit-module-path "${build_directory}/ServiceOSCoreClient.swiftmodule" \
      -o "${build_directory}/libServiceOSCoreClient.dylib"

cat > "${build_directory}/Consumer.swift" <<'EOF'
import ServiceOSCoreClient

@main
struct Consumer {
    static func main() {
        let configuration = ServiceOSCoreClientAPIConfiguration(basePath: "https://serviceos.invalid/api/v1")
        precondition(configuration.basePath == "https://serviceos.invalid/api/v1")
        _ = DefaultAPI.self
    }
}
EOF

swiftc \
  -swift-version 6 \
  -parse-as-library \
  -I "${build_directory}" \
  -L "${build_directory}" \
  -lServiceOSCoreClient \
  "${build_directory}/Consumer.swift" \
  -o "${build_directory}/consumer"

DYLD_LIBRARY_PATH="${build_directory}" "${build_directory}/consumer"
echo "Swift Client 消费门禁通过：$(swiftc --version | head -1)"
