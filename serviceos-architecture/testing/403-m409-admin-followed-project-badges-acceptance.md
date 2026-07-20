# M409 Admin 关注项目待办/SLA 角标 — 验收矩阵

Status: Implemented  
Date: 2026-07-20

| ID | 场景 | 期望 | 证据 |
|---|---|---|---|
| A1 | 仅有 `project.read` | 角标字段全部为 `null`，列表仍成功 | `FollowedProjectPostgresIT#followIsIdempotentAndListReturnsAuthorizedProjects` |
| A2 | 具备 workOrder/evidence/sla 读能力且无数据 | 角标为 `0`，`openTodoCount=0`，`*Truncated=false` | `FollowedProjectPostgresIT#listEnrichesZeroBadgesWhenReadCapabilitiesPresent` |
| A3 | `withoutBadges` 工厂 | 写路径响应不计数字段为 null | `FollowedProjectItemTest` |
| A4 | 工作台展示角标 | 关注项显示待办/SLA/工单角标 | `admin-project-workbench-product.spec.ts` |
| A5 | OpenAPI | `1.0.75` 含新字段 | 契约 diff |

## 明确未验收

- 距离角标（无坐标事实）
- 超过 100 条时的精确 COUNT
