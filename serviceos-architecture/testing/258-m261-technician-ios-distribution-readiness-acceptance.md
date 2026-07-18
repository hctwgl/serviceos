---
title: M261 Technician iOS 签名与分发就绪基础验收矩阵
status: Implemented
milestone: M261
lastUpdated: 2026-07-18
---

# M261 Technician iOS 签名与分发就绪基础验收矩阵

| 编号 | 场景 | 预期 | 证据 |
|---|---|---|---|
| M261-01 | AppIcon | 1024x1024、无 Alpha，并由 Production App 显式绑定 | `sips` + xcconfig + Xcode asset compile |
| M261-02 | 隐私清单 | manifest 可解析且当前不声明 tracking | `plutil` + archive packaged resource |
| M261-03 | Production archive | 优化 arm64 iPhoneOS App 可形成 `.xcarchive` | unsigned validation archive + Xcode store validation |
| M261-04 | 符号产物 | archive 包含匹配 App 的 dSYM | archive structure gate |
| M261-05 | 环境隔离 | 验证 archive 为 production，但只能使用 `.invalid` 注入地址 | built Info.plist assertions |
| M261-06 | 无签名诚实边界 | 仓库门禁 archive 的 SigningIdentity/Team 必须为空 | archive Info.plist assertions |
| M261-07 | 发布参数 | Team、build number、API、issuer 均必须显式提供 | release script negative probe |
| M261-08 | 生产地址 | API/issuer 必须 HTTPS，拒绝 `.invalid`、localhost 和 127.0.0.1 | release preflight source/negative review |
| M261-09 | 签名身份 | 本机无有效 Apple 身份时真实 archive 必须拒绝 | Keychain identity preflight；当前 0 identities |
| M261-10 | IPA 边界 | 只有真实签名 archive 且显式开关时才导出 App Store Connect IPA；不自动上传 | release script |
| M261-11 | 契约/迁移 | OpenAPI 1.0.21、Flyway 100/102 不变 | existing gates |

## 明确未验收

真实签名、provisioning、开发真机、真实 IdP、IPA 实际导出、App Store Connect 上传、TestFlight 安装/升级/
回滚、崩溃采集符号化和 VoiceOver 人工听读不在本矩阵已完成范围。无签名 archive 只证明结构和编译链。
