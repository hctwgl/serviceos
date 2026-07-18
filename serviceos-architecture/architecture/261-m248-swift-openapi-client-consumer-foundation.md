---
title: M248 Swift OpenAPI Client 消费基础
status: Implemented
milestone: M248
lastUpdated: 2026-07-18
relatedMilestones: [M12, M247]
---

# M248 Swift OpenAPI Client 消费基础

## 范围与证据

- 与 TypeScript Client 共享 Core OpenAPI `1.0.20` 和固定 OpenAPI Generator `7.22.0`，生成
  `ServiceOSCoreClient` Swift 6 模块；
- 使用 Foundation `URLSession`、Swift Concurrency `AsyncAwait` 和 SPM 目录结构，不引入第三方网络依赖；
- 两次干净生成的完整文件树摘要一致，并生成包含契约 SHA、生成器版本和模块名的来源清单；
- 修正上游 Swift 6 模板对 `oneOf` Dictionary 分支生成非法 case 名，以及 Package manifest 显式语言模式
  与部分 SwiftPM 版本不兼容的问题；修正位于受控生成模板，不手改生成源码；
- 以本机 Apple Swift 6.4、`-swift-version 6` 编译全部生成源码，再由独立 executable 导入模块、
  实例化 `ServiceOSCoreClientAPIConfiguration` 并引用 `DefaultAPI`；
- `bash scripts/agent-verify.sh client-swift` 是统一精准入口；Core OpenAPI 与 Flyway 100/102 均不变。

## 明确未实现

iOS App/Xcode 工程、Keychain/OIDC、网络适配与 Problem Details、设计 Token、制品仓库发布/签名/SBOM、
真机/模拟器验证，以及独立 Network Web/Technician H5。

## 失败关闭语义

缺少 Swift 编译器、生成漂移、任一生成源码无法在 Swift 6 严格模式编译、独立消费者无法链接或运行时，
门禁均非零退出。生成模块不包含 ADMIN/NETWORK/TECHNICIAN 菜单或数据范围假设。
