# ServiceOS Technician iOS Foundation

Technician iOS 的仓库内安全基础，当前提供：

- Local/Development/Test/Staging/Production 环境失败关闭；
- `TECHNICIAN_IOS` 固定客户端元数据；
- OIDC Authorization Code + PKCE 请求、回调 state 校验、Token exchange/refresh/logout；
- `ThisDeviceOnly` Keychain Token Vault；
- HTTPS URLSession transport、Problem Details 安全文案与 trace/correlation 诊断；
- `/me` TECHNICIAN Context/Capability/导航加载及伪造 Context 拒绝；
- 同源生成 `ServiceOSCoreClient` 与 `ServiceOSDesignTokens` 的真实编译链接；
- 当前责任任务 Feed/详情、用户主动触发的一次性 CoreLocation 签到与无法施工中断；
- Token、联系人、地址、VIN、照片路径和表单值的日志脱敏。

```bash
bash scripts/agent-verify.sh technician-ios
bash scripts/agent-verify.sh technician-ios-app
bash scripts/agent-verify.sh technician-ios-distribution
```

完整 Xcode 已用于 Simulator App、XCTest/XCUITest 和无签名 Production arm64 archive 门禁。发布就绪门禁还会
校验 1024x1024 无 Alpha AppIcon、`PrivacyInfo.xcprivacy`（精确位置/Device ID、关联用户、App Functionality、
不 tracking）、dSYM 与生产配置失败关闭；其 archive 刻意不签名，
不能当作可安装或可上传制品。

真实签名 archive/IPA 使用：

```bash
SERVICEOS_IOS_DEVELOPMENT_TEAM=<10 位 Team ID> \
SERVICEOS_IOS_BUILD_NUMBER=<递增正整数> \
SERVICEOS_PRODUCTION_API_BASE_URL=https://api.example.com/ \
SERVICEOS_PRODUCTION_OIDC_ISSUER=https://identity.example.com/realms/serviceos/ \
bash scripts/archive-technician-ios-release.sh
```

设置 `SERVICEOS_IOS_EXPORT_APP_STORE_CONNECT=true` 才会从已签名 archive 导出 App Store Connect IPA；脚本不会
自动上传。真实执行还要求 Keychain 中已有有效 Apple Development/Distribution 身份和匹配 provisioning；缺少
Team、证书、生产 HTTPS 地址或 build number 时立即失败。当前仍未声明开发真机、真实 IdP、VoiceOver 人工走查
或 TestFlight 安装/升级/回滚已通过。

M262 仍是纯在线切片：定位只在签到按钮触发时采集一次，不启用持续或后台定位。没有真实现场操作
`operationRefs` 时不会开放签退；联系人/预约写、动态表单、Evidence、整改和离线工作包仍未实现。
