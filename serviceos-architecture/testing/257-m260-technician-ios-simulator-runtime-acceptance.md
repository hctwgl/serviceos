---
title: M260 Technician iOS Simulator 运行验收矩阵
status: Implemented
milestone: M260
lastUpdated: 2026-07-18
---

# M260 Technician iOS Simulator 运行验收矩阵

| 编号 | 场景 | 预期 | 证据 |
|---|---|---|---|
| M260-01 | Simulator 安装启动 | Test App 可安装、启动并显示未登录师傅端，无白屏/启动崩溃 | `simctl install/launch` + iPhone 17 Pro Simulator 实际画面 |
| M260-02 | Keychain entitlement | XCTest 在本地签名 App 宿主中访问 Keychain，不接受禁签 bundle 的 `errSecMissingEntitlement` | hosted XCTest + shared scheme |
| M260-03 | Token 恢复 | Access Token 过期不提前删除 Refresh Token；刷新拒绝/注销后才清理 | `KeychainAccessTokenVaultSimulatorTests` |
| M260-04 | Page 失败关闭 | 未知 Page ID 不渲染 | `TechnicianNavigationSimulatorTests` |
| M260-05 | Capability 失败关闭 | 页面任一 required Capability 缺失均不渲染 | `TechnicianNavigationSimulatorTests` |
| M260-06 | Dynamic Type | Accessibility XXXL 下未登录壳仍可访问登录入口 | `TechnicianIOSUITests` 实跑 |
| M260-07 | 静态包边界 | AppTests 不重复链接 Foundation，避免宿主与测试 bundle 重复 Swift 类型 | hosted target graph + test log |
| M260-08 | 构建回归 | Test/Production Simulator、generic iPhoneOS arm64 和 test bundle 均通过 | `agent-verify.sh technician-ios-app` |
| M260-09 | 安全静态门禁 | 无 secret/密码/直接输出/普通偏好 Token；Keychain 为 ThisDeviceOnly | iOS App source gates |
| M260-10 | 契约/迁移 | OpenAPI 1.0.21、Flyway 100/102 不变 | existing gates |

## 明确未验收

真实 IdP/API、OIDC 回调、Context 切换、Token 撤销、开发团队签名、真机安装、VoiceOver 人工听读、
崩溃采集和 TestFlight 安装/升级/回滚不在本矩阵已完成范围。Simulator 本地签名不得外推为真机或分发证据。
