---
title: M258 Technician iOS 仓库内安全基础
status: Implemented
milestone: M258
lastUpdated: 2026-07-18
relatedMilestones: [M248, M249, M251, M252, M253, M257]
---

# M258 Technician iOS 仓库内安全基础

## 1. 交付范围

M258 交付 Track D 在当前无完整 Xcode 环境下可真实验证的仓库内安全基础：

- Local/Development/Test/Staging/Production 配置模型，非 Local API/issuer 强制 HTTPS；
- OIDC Authorization Code + PKCE、回调 state 校验、Token exchange/refresh/logout，公共客户端不含 secret；
- `kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly` Keychain Vault，Access/Refresh Token 不写文件或日志；
- HTTPS URLSession transport、Problem Details 固定安全文案和 correlation/trace 诊断；
- `/me/contexts` 只接受服务端返回 TECHNICIAN Context，并按同一 contextVersion 加载 Capability/导航；
- 实际编译链接同源生成 `ServiceOSCoreClient`、`ServiceOSDesignTokens` 和 `ServiceOSIOSCore`；
- Token、联系人、地址、VIN、照片路径、文件路径、表单值和 payload 的日志脱敏；
- 修正 `ServiceRequestBuilder` 的相对 URL 解析，使带 query 的 `/me` 请求不被错误编码为 path。

## 2. 验证证据

- `bash scripts/agent-verify.sh technician-ios` 在 Swift 6 严格模式编译 Core、生成 Client、Design Token 和
  `TechnicianIOSFoundation` 四个模块，并实跑 Keychain、PKCE、exchange/refresh/logout、Context 正负与脱敏 smoke；
- `bash scripts/agent-verify.sh ios-core` 回归通过；
- source gate 禁止 client secret、密码赋值和直接打印潜在敏感数据；
- Core OpenAPI 1.0.21、Flyway 100/102 不变，本批次不修改服务端契约或数据库。

## 3. 明确未实现与环境阻断

本机 `xcode-select` 指向 `/Library/Developer/CommandLineTools`，没有 `Xcode.app`、`xcodebuild` iOS SDK、
Simulator 或 `simctl`。因此本里程碑不声明 SwiftUI App、`.xcodeproj`、模拟器、开发真机、签名、
Keychain entitlement、VoiceOver/Dynamic Type、崩溃采集或 TestFlight 已完成。

Track D 下一批次必须在安装完整 Xcode 后建立并验证 App 工程；在此之前不得进入宣称 iOS 可运行的状态。
Track E 在线履约写闭环和 Track F 离线/后台上传仍未实施。
