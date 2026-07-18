---
title: M259 Technician iOS SwiftUI App 与 Xcode 交付批次
status: Implemented
milestone: M259
lastUpdated: 2026-07-18
relatedMilestones: [M251, M252, M253, M257, M258]
---

# M259 Technician iOS SwiftUI App 与 Xcode 交付批次

## 1. 交付范围

M259 在 M258 仓库内安全基础上建立首个可由完整 Xcode 构建的原生 App 交付批次：

- 新增共享 `TechnicianIOS` scheme、workspace、Xcode project，以及 App、Foundation、生成 Client、
  Design Token、iOS Core 的本地 Swift Package 边界；
- 用 SwiftUI Observation 状态机承载启动恢复、未登录、系统登录、已登录和失败恢复，不以 WebView
  替代原生 AppShell；
- 用 `ASWebAuthenticationSession` 接入 OIDC Authorization Code + PKCE；App 不接触密码，不持有
  client secret，Access/Refresh Token 仍只进入 ThisDeviceOnly Keychain；
- 冷启动先检查 Access Token，过期时保留 Refresh Token 并尝试刷新；刷新被拒绝或注销后清理凭据；
- 只接受服务端返回的 TECHNICIAN Context、Capability 和导航，页面所需 Capability 不满足时失败关闭；
- 交付任务、日程、同步、我的四个原生 SwiftUI 导航页壳和上下文切换；Track E 写命令尚未接入，
  页面明确展示边界，不伪造业务数据、离线状态或业务完成；
- 用 `.xcconfig` 明确 Local/Development/Test/Staging/Production；Test 构建固定使用 `.invalid`
  保留域名，防止自动化误连真实环境；
- 新增 Keychain XCTest、Dynamic Type XCUITest 和 `agent-verify.sh technician-ios-app` 精准门禁。

## 2. 验证证据

在 `/Users/louis/Downloads/Xcode-beta.app`（Xcode 27.0，iOS 27 SDK）下已证明：

```bash
SERVICEOS_IOS_REQUIRE_BOOTED_SIMULATOR=false \
  bash scripts/agent-verify.sh technician-ios-app
```

- Test 配置的 generic iOS Simulator App 构建通过；
- Test 配置的 generic iPhoneOS arm64 无签名构建通过；该结果只证明设备编译链，不声明已安装或签名；
- App XCTest/XCUITest bundle 的 `build-for-testing` 通过；
- Production 配置以保留域名显式注入后完成 generic Simulator 优化构建；
- 构建产物的 Test 环境、API、OIDC issuer、client ID 和回调 scheme 与配置一致；
- M258 Foundation 独立消费者门禁继续通过，并新增过期 Access Token 不提前删除 Refresh Token 的回归；
- source gate 阻断 client secret、密码、直接日志输出、普通偏好 Token 存储和非 ThisDeviceOnly Keychain。

本里程碑未修改 HTTP/事件契约或数据库；Core OpenAPI 仍为 1.0.21，Flyway 仍为 100/102。

## 3. 明确未实现

当前没有用户手动启动的 Simulator，因此 XCTest/XCUITest **已编译但尚未运行**；M259 不把
`build-for-testing` 误报为运行态通过。下一 Track D 批次必须补齐 Simulator App 启动、Keychain XCTest、
Dynamic Type/XCUITest 和可访问性走查。

开发证书签名、真机安装、真实 IdP 登录、真机上下文切换、Token 撤销、VoiceOver 人工走查、崩溃采集、
TestFlight 安装/升级/回滚仍未验收。Track D 因此仍未全部完成；Track E 在线写闭环、Track F 离线与
后台上传也未开始。
