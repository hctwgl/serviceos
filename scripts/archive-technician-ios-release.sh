#!/usr/bin/env bash
set -euo pipefail

# Technician iOS 真实签名与 App Store Connect 导出入口。
# 生产地址、Team、证书或 build number 任一缺失时必须失败关闭；脚本不会补默认生产值，也不会上传制品。

repository_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
app_root="${repository_root}/serviceos-technician-ios"
workspace="${app_root}/TechnicianIOS.xcworkspace"
scheme="TechnicianIOS"

fail() {
  echo "Technician iOS 发布预检失败：$1" >&2
  exit 1
}

require_value() {
  local name="$1"
  [[ -n "${!name:-}" ]] || fail "必须显式设置 ${name}。"
}

require_real_https_url() {
  local name="$1"
  local value="${!name}"
  [[ "${value}" == https://* ]] || fail "${name} 必须使用 HTTPS。"
  [[ "${value}" != *".invalid"* && "${value}" != *"localhost"* && "${value}" != *"127.0.0.1"* ]] || {
    fail "${name} 不得使用保留域名或本机地址。"
  }
}

if [[ -n "${SERVICEOS_XCODE_DEVELOPER_DIR:-}" ]]; then
  developer_dir="${SERVICEOS_XCODE_DEVELOPER_DIR}"
else
  developer_dir="$(xcode-select -p 2>/dev/null || true)"
fi
xcodebuild_path="${developer_dir}/usr/bin/xcodebuild"
[[ -x "${xcodebuild_path}" ]] || fail "未找到完整 Xcode；可通过 SERVICEOS_XCODE_DEVELOPER_DIR 指定。"
export DEVELOPER_DIR="${developer_dir}"

require_value SERVICEOS_IOS_DEVELOPMENT_TEAM
require_value SERVICEOS_IOS_BUILD_NUMBER
require_value SERVICEOS_PRODUCTION_API_BASE_URL
require_value SERVICEOS_PRODUCTION_OIDC_ISSUER

[[ "${SERVICEOS_IOS_DEVELOPMENT_TEAM}" =~ ^[A-Z0-9]{10}$ ]] || fail "Team ID 必须是 10 位大写字母或数字。"
[[ "${SERVICEOS_IOS_BUILD_NUMBER}" =~ ^[1-9][0-9]*$ ]] || fail "build number 必须是正整数。"
require_real_https_url SERVICEOS_PRODUCTION_API_BASE_URL
require_real_https_url SERVICEOS_PRODUCTION_OIDC_ISSUER

identity_output="$(security find-identity -v -p codesigning 2>/dev/null || true)"
if ! rg -q '"Apple (Development|Distribution):' <<<"${identity_output}"; then
  fail "本机 Keychain 没有有效 Apple Development/Distribution 签名身份。"
fi

privacy_manifest="${app_root}/App/PrivacyInfo.xcprivacy"
app_icon="${app_root}/App/Assets.xcassets/AppIcon.appiconset/AppIcon-1024.png"
[[ -f "${privacy_manifest}" ]] || fail "缺少 PrivacyInfo.xcprivacy。"
[[ -f "${app_icon}" ]] || fail "缺少 1024x1024 App Store 图标。"

release_root="${SERVICEOS_IOS_RELEASE_ROOT:-${repository_root}/target/technician-ios-release}"
archive_path="${release_root}/TechnicianIOS-${SERVICEOS_IOS_BUILD_NUMBER}.xcarchive"
export_path="${release_root}/export-${SERVICEOS_IOS_BUILD_NUMBER}"
[[ ! -e "${archive_path}" ]] || fail "archive 已存在：${archive_path}；请使用新的 build number 或显式 release root。"
mkdir -p "${release_root}"

provisioning_arguments=()
if [[ "${SERVICEOS_IOS_ALLOW_PROVISIONING_UPDATES:-false}" == "true" ]]; then
  provisioning_arguments+=(-allowProvisioningUpdates)
fi

"${xcodebuild_path}" \
  -workspace "${workspace}" \
  -scheme "${scheme}" \
  -configuration Production \
  -destination 'generic/platform=iOS' \
  -archivePath "${archive_path}" \
  DEVELOPMENT_TEAM="${SERVICEOS_IOS_DEVELOPMENT_TEAM}" \
  CURRENT_PROJECT_VERSION="${SERVICEOS_IOS_BUILD_NUMBER}" \
  SERVICEOS_PRODUCTION_API_BASE_URL="${SERVICEOS_PRODUCTION_API_BASE_URL}" \
  SERVICEOS_PRODUCTION_OIDC_ISSUER="${SERVICEOS_PRODUCTION_OIDC_ISSUER}" \
  "${provisioning_arguments[@]}" \
  archive

archive_info="${archive_path}/Info.plist"
archive_app="${archive_path}/Products/Applications/TechnicianIOS.app"
[[ -f "${archive_info}" && -d "${archive_app}" ]] || fail "签名 archive 结构不完整。"
[[ "$(plutil -extract ApplicationProperties.Team raw -o - "${archive_info}")" == "${SERVICEOS_IOS_DEVELOPMENT_TEAM}" ]] || {
  fail "archive Team 与显式 Team ID 不一致。"
}
codesign --verify --deep --strict "${archive_app}" || fail "archive App 签名校验失败。"

if [[ "${SERVICEOS_IOS_EXPORT_APP_STORE_CONNECT:-false}" == "true" ]]; then
  export_options="$(mktemp)"
  trap 'rm -f "${export_options}"' EXIT
  plutil -create xml1 "${export_options}"
  plutil -insert method -string app-store-connect "${export_options}"
  plutil -insert destination -string export "${export_options}"
  plutil -insert signingStyle -string automatic "${export_options}"
  plutil -insert teamID -string "${SERVICEOS_IOS_DEVELOPMENT_TEAM}" "${export_options}"
  plutil -insert stripSwiftSymbols -bool true "${export_options}"
  plutil -insert uploadSymbols -bool true "${export_options}"
  "${xcodebuild_path}" \
    -exportArchive \
    -archivePath "${archive_path}" \
    -exportPath "${export_path}" \
    -exportOptionsPlist "${export_options}" \
    "${provisioning_arguments[@]}"
  echo "Technician iOS App Store Connect IPA 已导出：${export_path}"
else
  echo "Technician iOS 签名 archive 已生成：${archive_path}"
fi

