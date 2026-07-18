#!/usr/bin/env bash
set -euo pipefail

# M261/M262 发布就绪性门禁：在没有 Apple 账号材料的机器上也能证明 Production archive 结构、
# App Store 图标、隐私清单和 dSYM；真实签名仍必须走 archive-technician-ios-release.sh。

repository_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
app_root="${repository_root}/serviceos-technician-ios"
workspace="${app_root}/TechnicianIOS.xcworkspace"
archive_root="${repository_root}/target/technician-ios-distribution-validation"
archive_path="${archive_root}/TechnicianIOS.xcarchive"
derived_data="${repository_root}/target/technician-ios-distribution-derived-data"
log_directory="${repository_root}/target/verification-logs"

if [[ -n "${SERVICEOS_XCODE_DEVELOPER_DIR:-}" ]]; then
  developer_dir="${SERVICEOS_XCODE_DEVELOPER_DIR}"
else
  developer_dir="$(xcode-select -p 2>/dev/null || true)"
fi
xcodebuild_path="${developer_dir}/usr/bin/xcodebuild"
[[ -x "${xcodebuild_path}" ]] || { echo "未找到完整 Xcode。" >&2; exit 1; }
export DEVELOPER_DIR="${developer_dir}"

privacy_manifest="${app_root}/App/PrivacyInfo.xcprivacy"
icon_set="${app_root}/App/Assets.xcassets/AppIcon.appiconset"
app_icon="${icon_set}/AppIcon-1024.png"
plutil -lint "${privacy_manifest}" >/dev/null
jq -e '.images == [{"filename":"AppIcon-1024.png","idiom":"universal","platform":"ios","size":"1024x1024"}]' \
  "${icon_set}/Contents.json" >/dev/null || { echo "AppIcon Contents.json 不符合单一 1024 图标约束。" >&2; exit 1; }
[[ "$(plutil -extract NSPrivacyTracking raw -o - "${privacy_manifest}")" == "false" ]] || {
  echo "隐私清单不得声明 tracking。" >&2
  exit 1
}
plutil -convert json -o - "${privacy_manifest}" | jq -e '
  [.NSPrivacyCollectedDataTypes[] | {
    type: .NSPrivacyCollectedDataType,
    linked: .NSPrivacyCollectedDataTypeLinked,
    tracking: .NSPrivacyCollectedDataTypeTracking,
    purposes: .NSPrivacyCollectedDataTypePurposes
  }] == [
    {type:"NSPrivacyCollectedDataTypePreciseLocation",linked:true,tracking:false,
     purposes:["NSPrivacyCollectedDataTypePurposeAppFunctionality"]},
    {type:"NSPrivacyCollectedDataTypeDeviceID",linked:true,tracking:false,
     purposes:["NSPrivacyCollectedDataTypePurposeAppFunctionality"]},
    {type:"NSPrivacyCollectedDataTypePhotosorVideos",linked:true,tracking:false,
     purposes:["NSPrivacyCollectedDataTypePurposeAppFunctionality"]}
  ]' >/dev/null || {
  echo "隐私清单必须精确声明主动签到位置、设备标识和现场照片/视频，且仅用于 App 功能、不追踪。" >&2
  exit 1
}
rg -q 'ASSETCATALOG_COMPILER_APPICON_NAME[[:space:]]*=[[:space:]]*AppIcon' \
  "${app_root}/Configuration/Base.xcconfig" || { echo "Production App 未绑定 AppIcon。" >&2; exit 1; }
[[ "$(sips -g pixelWidth "${app_icon}" | awk '/pixelWidth/ {print $2}')" == "1024" ]] || {
  echo "App Store 图标宽度必须为 1024。" >&2
  exit 1
}
[[ "$(sips -g pixelHeight "${app_icon}" | awk '/pixelHeight/ {print $2}')" == "1024" ]] || {
  echo "App Store 图标高度必须为 1024。" >&2
  exit 1
}
[[ "$(sips -g hasAlpha "${app_icon}" | awk '/hasAlpha/ {print $2}')" == "no" ]] || {
  echo "App Store 图标不得包含 Alpha 通道。" >&2
  exit 1
}

# 没有生产参数时发布入口必须在调用 xcodebuild 前失败；不能静默使用测试或本机地址。
negative_log="${log_directory}/technician-ios-release-negative.log"
mkdir -p "${archive_root}" "${derived_data}" "${log_directory}"
if env -u SERVICEOS_IOS_DEVELOPMENT_TEAM \
  -u SERVICEOS_IOS_BUILD_NUMBER \
  -u SERVICEOS_PRODUCTION_API_BASE_URL \
  -u SERVICEOS_PRODUCTION_OIDC_ISSUER \
  "${repository_root}/scripts/archive-technician-ios-release.sh" >"${negative_log}" 2>&1; then
  echo "缺少发布参数时脚本不应成功。" >&2
  exit 1
fi
rg -q '必须显式设置 SERVICEOS_IOS_DEVELOPMENT_TEAM' "${negative_log}" || {
  echo "发布入口没有对缺失 Team ID 失败关闭。" >&2
  exit 1
}

# target 下的固定验证产物可安全重建；它不包含签名身份或可分发 IPA。
rm -rf "${archive_path}"
archive_log="${log_directory}/technician-ios-unsigned-production-archive.log"
if ! "${xcodebuild_path}" \
  -workspace "${workspace}" \
  -scheme TechnicianIOS \
  -configuration Production \
  -destination 'generic/platform=iOS' \
  -derivedDataPath "${derived_data}" \
  -archivePath "${archive_path}" \
  SERVICEOS_PRODUCTION_API_BASE_URL=https://api.production.invalid/ \
  SERVICEOS_PRODUCTION_OIDC_ISSUER=https://identity.production.invalid/realms/serviceos/ \
  CODE_SIGNING_ALLOWED=NO \
  archive >"${archive_log}" 2>&1; then
  echo "Technician iOS 无签名 Production archive 失败；日志 ${archive_log}" >&2
  tail -80 "${archive_log}" >&2
  exit 1
fi

archive_info="${archive_path}/Info.plist"
archive_app="${archive_path}/Products/Applications/TechnicianIOS.app"
app_info="${archive_app}/Info.plist"
[[ -d "${archive_app}" ]] || { echo "archive 缺少 TechnicianIOS.app。" >&2; exit 1; }
[[ -d "${archive_path}/dSYMs/TechnicianIOS.app.dSYM" ]] || { echo "archive 缺少 dSYM。" >&2; exit 1; }
[[ -f "${archive_app}/PrivacyInfo.xcprivacy" ]] || { echo "archive 未打包隐私清单。" >&2; exit 1; }
[[ "$(plutil -extract ApplicationProperties.Architectures.0 raw -o - "${archive_info}")" == "arm64" ]] || {
  echo "archive 不是 arm64 iPhoneOS 产物。" >&2
  exit 1
}
[[ -z "$(plutil -extract ApplicationProperties.SigningIdentity raw -o - "${archive_info}")" ]] || {
  echo "仓库门禁 archive 不得伪装为已签名制品。" >&2
  exit 1
}
[[ "$(plutil -extract SERVICEOS_ENV raw -o - "${app_info}")" == "production" ]] || {
  echo "archive 环境标识不是 production。" >&2
  exit 1
}
[[ "$(plutil -extract SERVICEOS_API_BASE_URL raw -o - "${app_info}")" == "https://api.production.invalid/" ]] || {
  echo "验证 archive 未使用隔离的 Production 地址。" >&2
  exit 1
}

echo "Technician iOS 发布就绪性门禁通过：Production arm64 archive、dSYM、隐私清单、AppIcon 与失败关闭。"
echo "当前产物刻意未签名；真实 archive/IPA 必须提供 Team、证书、build number 和生产 HTTPS 地址。"
