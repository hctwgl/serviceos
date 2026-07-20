---
title: ServiceOS 自主 Agent 交接
lastUpdated: 2026-07-20
---

# ServiceOS 自主 Agent 交接

## 当前

- **状态**：**PARTIAL** — M378～M382 主链路已落地；M383 收口未完全闭合
- **最新里程碑**：M383（部分）
- OpenAPI **1.0.60** / Flyway **137** / ADR-091 Accepted
- PR：https://github.com/hctwgl/serviceos/pull/204
- 分支：`cursor/bc-63192e98-f1e1-4e49-a3e3-c33a0e8b88da-4023`

## 已交付

| 里程碑 | Commit | 内容 |
|---|---|---|
| M378 | `4606e917` | 领域/Flyway/OpenAPI/Resolver/IT |
| M379 | `cd1c9469` | Admin 只读总览去空壳 |
| M380–M382 | `4bb70880` | 编辑器、四步发布、建单冻结、快照 |
| M383 | （本提交） | Playwright mock 冒烟 + 诚实缺口登记 |

## 后续优先

1. 试点项目种子 Profile，收紧「无 Profile 建单失败关闭」
2. Playwright 真实发布→建单 A/B 冻结隔离 E2E
3. allowed-actions 阻塞原因结构化
4. Admin 接入 `@serviceos/core-client`
5. a11y/视觉/OIDC smoke

## 名称映射

- UI「工单类型」= `serviceProductCode`
- 不新建第二套配置引擎
