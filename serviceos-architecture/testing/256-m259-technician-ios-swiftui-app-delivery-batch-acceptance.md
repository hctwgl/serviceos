---
title: M259 Technician iOS SwiftUI App 与 Xcode 交付批次验收矩阵
status: Implemented
milestone: M259
lastUpdated: 2026-07-18
---

# M259 Technician iOS SwiftUI App 与 Xcode 交付批次验收矩阵

| 编号 | 场景 | 预期 | 证据 |
|---|---|---|---|
| M259-01 | 工程边界 | App、Foundation、iOS Core、生成 Client/Token 可由共享 scheme 构建 | Xcode target graph + generic builds |
| M259-02 | 五环境 | scheme/config 显式；Test 固定 `.invalid`，Production 必须显式注入 | xcconfig + built Info.plist assertions |
| M259-03 | 系统登录 | App 仅通过 `ASWebAuthenticationSession` 发起 Code+PKCE，不处理密码/secret | source gate + iOS SDK compile |
| M259-04 | Token 恢复 | 过期 Access Token 不提前删除 Refresh Token；刷新失败/注销才清理 | Foundation regression + Keychain XCTest build |
| M259-05 | Context/导航 | 仅 TECHNICIAN Context；导航还需满足服务端 Capability | Foundation smoke + App compile/source review |
| M259-06 | 原生页壳 | Feed/Schedule/Sync/Me 为 SwiftUI；未接入写链路时不伪造完成 | source review + Simulator build |
| M259-07 | Simulator 编译 | Test 与 Production App 可链接 iOS Simulator SDK | `xcodebuild build` |
| M259-08 | 设备编译 | arm64 iPhoneOS 无签名 App 可构建 | generic iOS `CODE_SIGNING_ALLOWED=NO` build |
| M259-09 | 测试产物 | Keychain XCTest 与 Dynamic Type XCUITest 可编译链接 | `xcodebuild build-for-testing` |
| M259-10 | 安全静态门禁 | 无 client secret/密码/直接打印/普通偏好 Token；Keychain 为 ThisDeviceOnly | `agent-verify.sh technician-ios-app` source gates |
| M259-11 | 契约/迁移 | OpenAPI 1.0.21、Flyway 100/102 不变 | existing gates |

## 明确未验收

没有 Booted Simulator 时不声明 XCTest/XCUITest 已运行。签名、真机安装、真实 OIDC 登录和上下文切换、
Token 撤销、VoiceOver 人工走查、崩溃采集与 TestFlight 安装/升级/回滚也不在本矩阵已完成范围。
