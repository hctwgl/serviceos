#!/usr/bin/env bash
set -euo pipefail

# M259 Technician iOS App 精准门禁。
#
# 默认要求用户已经手动启动一台 Simulator，并执行 XCTest/XCUITest。仅需验证工程能否编译时，
# 可显式设置 SERVICEOS_IOS_REQUIRE_BOOTED_SIMULATOR=false；该模式不能作为里程碑最终运行态证据。

repository_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
app_root="${repository_root}/serviceos-technician-ios"
workspace="${app_root}/TechnicianIOS.xcworkspace"
scheme="TechnicianIOS"
configuration="Test"
derived_data="${repository_root}/target/technician-ios-derived-data"
log_directory="${repository_root}/target/verification-logs"
require_booted_simulator="${SERVICEOS_IOS_REQUIRE_BOOTED_SIMULATOR:-true}"

if [[ -n "${SERVICEOS_XCODE_DEVELOPER_DIR:-}" ]]; then
  developer_dir="${SERVICEOS_XCODE_DEVELOPER_DIR}"
elif [[ -x "/Users/louis/Downloads/Xcode-beta.app/Contents/Developer/usr/bin/xcodebuild" ]]; then
  developer_dir="/Users/louis/Downloads/Xcode-beta.app/Contents/Developer"
else
  selected_developer_dir="$(xcode-select -p 2>/dev/null || true)"
  if [[ -x "${selected_developer_dir}/usr/bin/xcodebuild" ]]; then
    developer_dir="${selected_developer_dir}"
  else
    echo "未找到完整 Xcode；可通过 SERVICEOS_XCODE_DEVELOPER_DIR 显式指定 Developer 目录。" >&2
    exit 1
  fi
fi

export DEVELOPER_DIR="${developer_dir}"
mkdir -p "${derived_data}" "${log_directory}"

xcodebuild_path="${developer_dir}/usr/bin/xcodebuild"
if [[ ! -x "${xcodebuild_path}" ]] || ! xcrun --find simctl >/dev/null 2>&1; then
  echo "指定目录不包含可用的 Xcode iOS Simulator 工具链：${developer_dir}" >&2
  exit 1
fi

for required_path in \
  "${workspace}/contents.xcworkspacedata" \
  "${app_root}/TechnicianIOS.xcodeproj/project.pbxproj" \
  "${app_root}/TechnicianIOS.xcodeproj/xcshareddata/xcschemes/${scheme}.xcscheme" \
  "${app_root}/App/Info.plist" \
  "${app_root}/Configuration/TechnicianIOS.entitlements"; do
  [[ -f "${required_path}" ]] || { echo "Technician iOS 工程文件缺失：${required_path}" >&2; exit 1; }
done

for environment in Local Development Test Staging Production; do
  [[ -f "${app_root}/Configuration/${environment}.xcconfig" ]] || {
    echo "Technician iOS 缺少 ${environment} 环境配置。" >&2
    exit 1
  }
  rg -q "SERVICEOS_ENV[[:space:]]*=" "${app_root}/Configuration/${environment}.xcconfig" || {
    echo "${environment}.xcconfig 未声明 SERVICEOS_ENV。" >&2
    exit 1
  }
done

plutil -lint "${app_root}/App/Info.plist" "${app_root}/Configuration/TechnicianIOS.entitlements" >/dev/null

source_roots=("${app_root}/App" "${app_root}/Configuration" "${app_root}/Sources")
if rg -n -i 'client[_-]?secret|password[[:space:]]*=' "${source_roots[@]}"; then
  echo "Technician iOS App 禁止包含 client secret 或密码。" >&2
  exit 1
fi
if rg -n 'print\(|debugPrint\(|NSLog\(' "${source_roots[@]}"; then
  echo "Technician iOS App 禁止直接打印潜在敏感数据。" >&2
  exit 1
fi
if rg -n -i 'UserDefaults[^\n]*(access|refresh|id)?token|@AppStorage[^\n]*(access|refresh|id)?token' \
  "${source_roots[@]}"; then
  echo "Token 禁止进入 UserDefaults 或 AppStorage。" >&2
  exit 1
fi
rg -q 'kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly' \
  "${app_root}/Sources/TechnicianIOSFoundation/KeychainAccessTokenVault.swift" || {
  echo "Technician iOS 生产 Keychain 必须使用 ThisDeviceOnly 等级。" >&2
  exit 1
}
rg -q 'ASWebAuthenticationSession' "${app_root}/App/OIDCAuthorizer.swift" || {
  echo "Technician iOS 登录必须使用系统 Web Authentication Session。" >&2
  exit 1
}
rg -q 'accessibilityIdentifier' "${app_root}/App" "${app_root}/Tests/TechnicianIOSUITests" || {
  echo "Technician iOS App 缺少稳定的可访问性/UI 自动化标识。" >&2
  exit 1
}

# Foundation 门禁先证明生成 Client/Token、OIDC、Context、网络和安全基础仍可独立消费。
# 它是 macOS Command Line Tools 独立消费者，不应被本脚本为 iOS 构建选择的 DEVELOPER_DIR 污染。
env -u DEVELOPER_DIR "${repository_root}/scripts/verify-technician-ios-foundation.sh"

run_xcodebuild() {
  local label="$1"
  shift
  local log_file="${log_directory}/technician-ios-${label}.log"
  echo "Technician iOS ${label}：开始"
  if ! "${xcodebuild_path}" "$@" >"${log_file}" 2>&1; then
    echo "Technician iOS ${label}：失败；日志 ${log_file}" >&2
    tail -80 "${log_file}" >&2
    return 1
  fi
  tail -n 3 "${log_file}"
  echo "Technician iOS ${label}：通过；日志 ${log_file}"
}

common_xcodebuild_arguments=(
  -workspace "${workspace}"
  -scheme "${scheme}"
  -derivedDataPath "${derived_data}"
  CODE_SIGNING_ALLOWED=NO
)

run_xcodebuild simulator-build \
  "${common_xcodebuild_arguments[@]}" \
  -configuration "${configuration}" \
  -destination 'generic/platform=iOS Simulator' \
  build

# 无签名 device build 证明 arm64 iPhoneOS 编译链成立；它不替代开发证书、安装或真机登录证据。
run_xcodebuild device-build \
  "${common_xcodebuild_arguments[@]}" \
  -configuration "${configuration}" \
  -destination 'generic/platform=iOS' \
  build

built_info_plist="${derived_data}/Build/Products/Test-iphonesimulator/TechnicianIOS.app/Info.plist"
[[ -f "${built_info_plist}" ]] || { echo "Simulator App Info.plist 未生成。" >&2; exit 1; }
[[ "$(plutil -extract SERVICEOS_ENV raw -o - "${built_info_plist}")" == "test" ]] || {
  echo "Test App 环境标识错误。" >&2
  exit 1
}
[[ "$(plutil -extract SERVICEOS_API_BASE_URL raw -o - "${built_info_plist}")" == "https://api.test.invalid/" ]] || {
  echo "Test App API 地址未固定到保留域名。" >&2
  exit 1
}
[[ "$(plutil -extract SERVICEOS_OIDC_ISSUER raw -o - "${built_info_plist}")" == "https://identity.test.invalid/realms/serviceos/" ]] || {
  echo "Test App OIDC issuer 未固定到保留域名。" >&2
  exit 1
}

run_xcodebuild test-build \
  "${common_xcodebuild_arguments[@]}" \
  -configuration "${configuration}" \
  -destination 'generic/platform=iOS Simulator' \
  build-for-testing

# Production 使用保留域名注入构建设置，验证优化配置可编译且不会接触真实环境。
run_xcodebuild production-simulator-build \
  "${common_xcodebuild_arguments[@]}" \
  -configuration Production \
  -destination 'generic/platform=iOS Simulator' \
  SERVICEOS_PRODUCTION_API_BASE_URL=https://api.production.invalid/ \
  SERVICEOS_PRODUCTION_OIDC_ISSUER=https://identity.production.invalid/realms/serviceos/ \
  build

production_info_plist="${derived_data}/Build/Products/Production-iphonesimulator/TechnicianIOS.app/Info.plist"
[[ "$(plutil -extract SERVICEOS_ENV raw -o - "${production_info_plist}")" == "production" ]] || {
  echo "Production App 环境标识错误。" >&2
  exit 1
}
[[ "$(plutil -extract SERVICEOS_API_BASE_URL raw -o - "${production_info_plist}")" == "https://api.production.invalid/" ]] || {
  echo "Production App 未使用显式注入的 API 地址。" >&2
  exit 1
}

booted_simulator_id="$(xcrun simctl list devices booted | sed -n 's/.*(\([0-9A-Fa-f-]\{36\}\)) (Booted).*/\1/p' | head -1)"
if [[ -z "${booted_simulator_id}" ]]; then
  if [[ "${require_booted_simulator}" == "true" ]]; then
    echo "没有用户手动启动的 iOS Simulator；请在 Xcode 中启动一台设备后重跑。本脚本不会自动启动。" >&2
    exit 1
  fi
  echo "未执行运行态 XCTest/XCUITest：SERVICEOS_IOS_REQUIRE_BOOTED_SIMULATOR=false 仅允许编译验证。"
else
  run_xcodebuild simulator-tests \
    "${common_xcodebuild_arguments[@]}" \
    -configuration "${configuration}" \
    -destination "platform=iOS Simulator,id=${booted_simulator_id}" \
    test
fi

echo "Technician iOS App 门禁通过：$(${xcodebuild_path} -version | paste -s -d ' ' -)"
