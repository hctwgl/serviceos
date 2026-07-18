---
title: M248 Swift OpenAPI Client 消费基础验收矩阵
status: Implemented
milestone: M248
lastUpdated: 2026-07-18
---

# M248 Swift OpenAPI Client 消费基础验收矩阵

| 编号 | 场景 | 预期 | 证据 |
|---|---|---|---|
| M248-01 | 同一 Core OpenAPI 两次干净生成 | Swift 文件树摘要一致 | `verify-swift-client-generation-reproducibility.sh` |
| M248-02 | Swift 6 严格编译 | 全部 Models/APIs/Infrastructure 编译为模块和 dylib | `swiftc -swift-version 6` |
| M248-03 | 独立消费者 | 可导入模块、构造 APIConfiguration、引用 DefaultAPI | 临时 executable smoke |
| M248-04 | oneOf Dictionary | 生成合法、可 Codable 的索引 case，无 HTML 转义标识符 | GeneratedClientContractTest + Swift 编译 |
| M248-05 | 共享边界 | 与 TypeScript 同源；无角色/菜单/数据范围假设 | Maven config + diff review |

## 明确未验收

Xcode/iOS App、SwiftPM 远端依赖消费、真机/模拟器、认证/错误/Trace、设计 Token 与制品发布。
