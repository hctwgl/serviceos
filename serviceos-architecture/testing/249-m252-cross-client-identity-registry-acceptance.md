---
title: M252 多端 Page/Feature/Action 机器注册表验收矩阵
status: Implemented
milestone: M252
lastUpdated: 2026-07-18
---

# M252 多端 Page/Feature/Action 机器注册表验收矩阵

| 编号 | 场景 | 预期 | 证据 |
|---|---|---|---|
| M252-01 | Page ID 对齐 | 机器注册表与后端 33 个代码注册 pageId 完全一致 | Backend alignment test |
| M252-02 | Action 对齐 | Task/Appointment/Visit/Exception 已发布 enum 全部登记且唯一 | Contract test + Core OpenAPI |
| M252-03 | Feature 边界 | `FORMAL_SETTLEMENT` 为 RESERVED/DISABLED，不被本切片启用 | JSON contract assertion |
| M252-04 | TypeScript 消费 | 生成常量/联合类型可 strict 编译；未知动作被过滤 | TS compile + negative runtime probe |
| M252-05 | Swift 消费 | Swift 6 strict 编译运行；未知动作被过滤 | Swift executable negative probe |
| M252-06 | 生成稳定性 | 两次干净生成完整树摘要一致 | `agent-verify.sh client-identities` |

## 明确未验收

独立应用实际导入、运行时 Feature API、clientKind/clientVersion、支持能力协商、制品发布、未知 schema、正式结算与新业务动作。
