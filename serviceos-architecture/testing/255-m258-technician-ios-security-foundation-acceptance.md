---
title: M258 Technician iOS 仓库内安全基础验收矩阵
status: Implemented
milestone: M258
lastUpdated: 2026-07-18
---

# M258 Technician iOS 仓库内安全基础验收矩阵

| 编号 | 场景 | 预期 | 证据 |
|---|---|---|---|
| M258-01 | 环境 | 五环境显式选择；非 Local URL 强制 HTTPS | Swift smoke |
| M258-02 | 客户端身份 | 固定 `TECHNICIAN_IOS` 与语义版本 | compile/smoke |
| M258-03 | PKCE | 随机 verifier/state、S256 challenge、回调 state 失败关闭 | smoke |
| M258-04 | Token endpoint | exchange/refresh 使用公共 client，无 client secret | request assertion |
| M258-05 | Token 存储 | Access/Refresh Token 进入 ThisDeviceOnly Keychain | real Keychain smoke |
| M258-06 | 生命周期 | 过期失败关闭、refresh、logout 清理 | smoke |
| M258-07 | 网络 | HTTPS request、Problem Details、trace/correlation | strict compile + smoke |
| M258-08 | Context | 仅服务端 TECHNICIAN Context；伪造拒绝 | generated DTO + smoke |
| M258-09 | 生成物 | Swift Client/Design Token/Core 被 App Foundation 真实链接 | four-module compile |
| M258-10 | 日志 | Token/联系人/地址/VIN/照片/文件/表单/payload 脱敏 | source gate + smoke |
| M258-11 | Core 回归 | 带 query 的相对请求 URL 正确解析 | iOS Core + session smoke |
| M258-12 | 契约/迁移 | OpenAPI 1.0.21、Flyway 100/102 不变 | existing gates |

## 明确未验收

SwiftUI App、Xcode project、iOS SDK 编译、Simulator、真机、entitlement、签名、VoiceOver/Dynamic Type、
崩溃采集和 TestFlight。缺少完整 Xcode 是外部环境阻断，不得由 Command Line Tools 结果替代。
