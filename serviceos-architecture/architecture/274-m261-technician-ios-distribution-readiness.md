---
title: M261 Technician iOS 签名与分发就绪基础
status: Implemented
milestone: M261
lastUpdated: 2026-07-18
relatedMilestones: [M258, M259, M260]
---

# M261 Technician iOS 签名与分发就绪基础

## 1. 交付范围

M261 在 M260 Simulator 运行证据之后补齐无需 Apple 账号即可审查的分发基础，并为真实签名建立失败关闭入口：

- 新增 1024x1024、无 Alpha 的 App Store 图标并显式绑定 `AppIcon`；
- 新增 `PrivacyInfo.xcprivacy`，当前 App 不声明 tracking、收集数据或 required-reason API；后续接入 GPS、
  相机、文件或三方 SDK 时必须同步更新，不能把本清单外推到未来能力；
- 以 Production 优化配置生成无签名 arm64 iPhoneOS `.xcarchive`，验证 App、dSYM、隐私清单、环境元数据和
  Xcode store validation 路径；
- 新增统一真实发布脚本，强制显式 Team ID、递增 build number、生产 API/OIDC HTTPS 地址和本机有效 Apple
  签名身份；缺任一条件在调用 archive 前失败；
- 支持从真实签名 archive 导出 App Store Connect IPA，但默认只生成 archive，且脚本不会自动上传制品；
- 发布产物位于 `target/`，不把证书、provisioning、Team 私有配置或生产地址写入仓库。

## 2. 工程证据

在 `/Applications/Xcode-beta.app`（Xcode 27.0，iOS 27 SDK）执行
`bash scripts/agent-verify.sh technician-ios-distribution` 已证明：

- Production arm64 iPhoneOS archive 成功，`CFBundleIdentifier=com.serviceos.technician`；
- archive 同时包含 `TechnicianIOS.app`、`TechnicianIOS.app.dSYM` 与 `PrivacyInfo.xcprivacy`；
- AppIcon 为 1024x1024 且不含 Alpha；
- archive 内环境为 `production`，验证地址固定为 `.invalid`，不会误连真实环境；
- archive 的 SigningIdentity/Team 为空，门禁不会将无签名产物冒充为可分发包；
- 真实发布入口在缺少 Team ID 时按预期失败关闭。

本机 `security find-identity -v -p codesigning` 返回 `0 valid identities found`，且没有物理 iPhone。因此本批
只能证明分发结构与发布入口，不声明实际签名、安装或上传成功。

本里程碑未修改 HTTP/事件契约或数据库；Core OpenAPI 仍为 1.0.21，Flyway 仍为 100/102。

## 3. 明确未实现

Apple Developer Team、证书、provisioning profile、真实生产地址和 App Store Connect 权限不属于仓库内容，
当前机器也未具备。开发真机安装、真实 IdP 登录/Context 切换、签名 archive、IPA 导出、TestFlight 上传、
安装/升级/回滚和小 cohort 均未验收。

dSYM 已生成，但尚未接入正式崩溃采集与符号化；VoiceOver 人工听读也未完成。Track D/Track G 的这些外部运行
证据仍需在材料可用后补齐；Track E 在线写闭环与 Track F 离线工作包尚未开始。
