#!/usr/bin/env bash
set -euo pipefail

repository_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
core_sources="${repository_root}/serviceos-ios-core/Sources/ServiceOSIOSCore"
foundation_sources="${repository_root}/serviceos-technician-ios/Sources/TechnicianIOSFoundation"
generated_client="${repository_root}/serviceos-contracts/target/generated-clients/swift6/Sources/ServiceOSCoreClient"
generated_tokens="${repository_root}/serviceos-contracts/target/generated-design-tokens/swift/ServiceOSDesignTokens.swift"
build_directory="$(mktemp -d "${TMPDIR:-/tmp}/serviceos-technician-ios-foundation.XXXXXX")"
cleanup() { rm -rf "${build_directory}"; }
trap cleanup EXIT

command -v swiftc >/dev/null 2>&1 || { echo "未找到 Swift 6 编译器。" >&2; exit 1; }
if [[ ! -d "${generated_client}" ]]; then
  "${repository_root}/serviceos-contracts/scripts/generate-swift-client-artifact.sh"
fi
node "${repository_root}/serviceos-contracts/scripts/generate-design-tokens.mjs"

if rg -n -i 'client[_-]?secret|password[[:space:]]*=' "${foundation_sources}"; then
  echo "Technician iOS 禁止包含 client secret 或密码。" >&2
  exit 1
fi
if rg -n 'print\(|debugPrint\(|NSLog\(' "${foundation_sources}"; then
  echo "Technician iOS Foundation 禁止直接打印潜在敏感数据。" >&2
  exit 1
fi
rg -q 'kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly' \
  "${foundation_sources}/KeychainAccessTokenVault.swift" || {
  echo "Technician iOS 生产 Keychain 必须使用 ThisDeviceOnly 等级。" >&2
  exit 1
}

find "${core_sources}" -name '*.swift' -print0 | xargs -0 swiftc \
  -swift-version 6 -parse-as-library -module-name ServiceOSIOSCore \
  -emit-module -emit-library \
  -emit-module-path "${build_directory}/ServiceOSIOSCore.swiftmodule" \
  -o "${build_directory}/libServiceOSIOSCore.dylib"

find "${generated_client}" -name '*.swift' -print0 | xargs -0 swiftc \
  -swift-version 6 -parse-as-library -module-name ServiceOSCoreClient \
  -emit-module -emit-library \
  -emit-module-path "${build_directory}/ServiceOSCoreClient.swiftmodule" \
  -o "${build_directory}/libServiceOSCoreClient.dylib"

swiftc -swift-version 6 -parse-as-library -module-name ServiceOSDesignTokens \
  -emit-module -emit-library \
  -emit-module-path "${build_directory}/ServiceOSDesignTokens.swiftmodule" \
  -o "${build_directory}/libServiceOSDesignTokens.dylib" "${generated_tokens}"

find "${foundation_sources}" -name '*.swift' -print0 | xargs -0 swiftc \
  -swift-version 6 -parse-as-library -module-name TechnicianIOSFoundation \
  -I "${build_directory}" -L "${build_directory}" \
  -lServiceOSIOSCore -lServiceOSCoreClient -lServiceOSDesignTokens \
  -framework Security -framework CryptoKit \
  -emit-module -emit-library \
  -emit-module-path "${build_directory}/TechnicianIOSFoundation.swiftmodule" \
  -o "${build_directory}/libTechnicianIOSFoundation.dylib"

swiftc -swift-version 6 -parse-as-library \
  -I "${build_directory}" -L "${build_directory}" \
  -lTechnicianIOSFoundation -lServiceOSIOSCore -lServiceOSCoreClient -lServiceOSDesignTokens \
  -framework Security -framework CryptoKit \
  "${repository_root}/serviceos-technician-ios/Tests/FoundationSmoke.swift" \
  -o "${build_directory}/foundation-smoke"
DYLD_LIBRARY_PATH="${build_directory}" "${build_directory}/foundation-smoke"

if xcodebuild -version >/dev/null 2>&1; then
  echo "检测到完整 Xcode；后续 App 工程批次可启用 simulator/device 门禁。"
else
  echo "未安装完整 Xcode：本门禁只证明仓库内 Swift 6/Keychain/OIDC/Context/Client/Token 基础，不声明模拟器、真机或 TestFlight。"
fi
echo "Technician iOS Foundation 门禁通过：$(swiftc --version | head -1)"
