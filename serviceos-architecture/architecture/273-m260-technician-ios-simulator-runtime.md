---
title: M260 Technician iOS Simulator 运行验收批次
status: Implemented
milestone: M260
lastUpdated: 2026-07-18
relatedMilestones: [M251, M252, M258, M259]
---

# M260 Technician iOS Simulator 运行验收批次

## 1. 交付范围

M260 将 M259 的“可构建 App 与测试产物”推进为在真实 Booted Simulator 中执行的运行证据：

- App XCTest 改为由 `TechnicianIOS` 宿主运行，使 Simulator 使用本地签名 App 的 Keychain entitlement，
  不再用全局 `CODE_SIGNING_ALLOWED=NO` 破坏运行态安全能力；
- XCTest bundle 复用 App 已链接的静态 Foundation，避免宿主与测试 bundle 重复装载同一 Swift 类型；
- 在 Simulator 的 ThisDeviceOnly Keychain 中验证 Access Token 过期后 Refresh Token 继续保留，只有刷新拒绝
  或注销路径才允许清理；
- 新增导航失败关闭测试：服务端 Page ID 必须是客户端已知页面，且每项 required Capability 均满足后才渲染；
- 在 iPhone 17 Pro Simulator 上安装、启动 Test App，确认未登录师傅端能够真实渲染，无白屏或启动崩溃；
- 运行 Accessibility XXXL XCUITest，确认未登录壳在最大动态字体下仍可滚动并触达企业账号登录入口；
- 精准门禁继续覆盖 Foundation、Test/Production Simulator 构建、generic iPhoneOS arm64 无签名构建和
  `build-for-testing`，并在存在 Booted Simulator 时执行 XCTest/XCUITest。

## 2. 运行证据

本批次使用 `/Applications/Xcode-beta.app`（Xcode 27.0，iOS 27 SDK）和用户已启动的 iPhone 17 Pro
Simulator 执行 `bash scripts/agent-verify.sh technician-ios-app`：

- 3 项 App XCTest 通过：1 项 Keychain 生命周期、2 项导航/Capability 失败关闭；
- 1 项 XCUITest 通过：Accessibility XXXL 未登录 AppShell；
- Test Simulator、Production Simulator、generic iPhoneOS arm64 与 Test `build-for-testing` 均通过；
- Test App 已经 `simctl install` 与 `simctl launch`，未登录页面真实显示中文说明、企业账号登录入口和
  `test` 环境标识；
- source gate 继续阻断 client secret、密码、直接日志输出、普通偏好 Token 存储和非 ThisDeviceOnly
  Keychain。

本里程碑未修改 HTTP/事件契约或数据库；Core OpenAPI 仍为 1.0.21，Flyway 仍为 100/102。

## 3. 安全与边界

Simulator 测试必须使用本地签名宿主获得 Keychain entitlement；generic iPhoneOS 编译仍由 Test
配置中的设备 SDK 条件保持无签名。两者不得用同一个全局禁签参数混为一谈。

导航测试同时验证客户端已知 Page ID 和服务端 Capability。未知页面或任一 Capability 缺失时不渲染，
不能由前端猜测角色或补默认入口。

## 4. 明确未实现

Test 配置使用 `.invalid` 地址，本批次没有连接真实 IdP/API，因此没有声明真实 OIDC 回调、Context 切换、
Token 撤销或业务数据加载已通过。Simulator 本地签名也不能替代开发团队签名、真机安装和证书治理。

VoiceOver 人工听读顺序、运行态日志/崩溃采集、真实设备 Dynamic Type、TestFlight 安装/升级/回滚仍未验收。
Track D 尚需后续签名与分发批次；Track E 在线写闭环和 Track F 离线工作包也未开始。
