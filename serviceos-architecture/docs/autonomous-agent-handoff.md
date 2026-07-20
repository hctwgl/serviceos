---
title: ServiceOS 自主 Agent 交接
lastUpdated: 2026-07-20
---

# ServiceOS 自主 Agent 交接

## 当前

- **状态**：**IN_PROGRESS** — M378～M383 项目工单类型与履约配置产品化
- **最新里程碑**：M378 Implemented（领域基础）
- OpenAPI **1.0.60** / Flyway **137** / ADR-091 Accepted
- 分支：`cursor/bc-63192e98-f1e1-4e49-a3e3-c33a0e8b88da-4023`

## 下一步（按计划继续，无需再选型）

| 里程碑 | 内容 |
|---|---|
| M379 | 只读查询 UI + 项目详情履约入口（去空壳） |
| M380 | 草稿阶段编排工作区 |
| M381 | 校验 / 编译预览 / 四步发布 |
| M382 | Resolver 接入建单 + 工单冻结 + 快照 + 阻塞原因 |
| M383 | 产品化/a11y/E2E/文档收口 |

## 名称映射

- UI「工单类型」= `serviceProductCode`
- `ProjectFulfillmentProfile` = 新编排层（复用 Bundle/资产，不建第二引擎）
- 历史工单 = `LEGACY_BUNDLE`
