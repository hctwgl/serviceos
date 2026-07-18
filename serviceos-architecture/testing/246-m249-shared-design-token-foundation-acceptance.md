---
title: M249 多端共享 Design Token 基础验收矩阵
status: Implemented
milestone: M249
lastUpdated: 2026-07-18
---

# M249 多端共享 Design Token 基础验收矩阵

| 编号 | 场景 | 预期 | 证据 |
|---|---|---|---|
| M249-01 | 单一版本化源 | 仅含 schemaVersion 与五个视觉分类 | DesignTokenContractTest |
| M249-02 | 重复生成 | CSS/Swift 输出树摘要一致 | `agent-verify.sh design-tokens` |
| M249-03 | Web 输出 | 语义 CSS variables 使用稳定前缀和单位 | CSS 精确探针 |
| M249-04 | Swift 输出 | Swift 6 常量模块完整编译 | `swiftc -swift-version 6` |
| M249-05 | 共享边界 | 无 Portal/角色/菜单/数据范围假设 | Java + Node negative validation |
| M249-06 | 契约边界 | OpenAPI/Flyway 不变 | contracts gate + preflight |

## 明确未验收

独立客户端实际导入、Admin 样式迁移、暗色/可访问性、视觉截图回归、图标文案和制品发布。
