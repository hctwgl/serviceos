---
title: M383 项目履约产品化收口（部分）
status: Implemented
milestone: M383
lastUpdated: 2026-07-20
relatedMilestones: [M378, M379, M380, M381, M382]
openapiVersion: "1.0.60"
flywayVersion: "137"
---

# M383 项目履约产品化收口（部分）

## 已实现

1. Playwright mock 冒烟：项目详情去空壳 + 履约列表可见；
2. 文档与交接更新；诚实登记剩余缺口。

## 明确未实现 / BLOCKED

| 项 | 状态 |
|---|---|
| Playwright 全链路（发布→建单 A/B→快照） | 未完成（需后端 fixture + 真实登录） |
| a11y/视觉全量 | 未完成 |
| 真实 OIDC smoke | `BLOCKED_EXTERNAL`（依赖环境 Keycloak） |
| `@serviceos/core-client` 替换薄封装 | 未完成 |
| allowed-actions 结构化阻塞原因 | 未完成 |
| 无 Profile 强制失败（去掉 LEGACY 过渡） | 未完成（需试点种子） |

因此：**不得宣称「项目工单履约配置完整实施完成」**；M378～M382 主链路已可运行，M383 收口未闭合。
