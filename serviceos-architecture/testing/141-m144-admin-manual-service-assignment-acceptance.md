---
title: M144 Admin 人工初派 ServiceAssignment 验收
status: Implemented
lastUpdated: 2026-07-17
---

# M144 Admin 人工初派 ServiceAssignment 验收

> 本矩阵记录 M144 当时的历史验收事实。M453 已删除 Admin 双责任 HTTP 入口，当前验收以
> `447-m453-admin-network-assignment-productization-acceptance.md` 的两阶段责任链为准。

| ID | 场景 | 证据 | 结果 |
|---|---|---|---|
| M144-01 | OpenAPI 暴露人工初派 | Core 0.72.0 `.../service-assignments:manual-assign` | PASS |
| M144-02 | HTTP 安全边界 | MVC：未认证 401；JWT 主体到达 SPI；忽略 X-Actor-Id | PASS |
| M144-03 | PostgreSQL 编排 | ManualServiceAssignmentPostgresIT：双 ACTIVE + CONFIRMED + COMPLETED；幂等回放 | PASS |
| M144-04 | Admin UI + field-ops | Playwright manual-assign → 预约上门 | PASS |
| M144-05 | 入站 ADMIN-PILOT-09 | Playwright 接单后 HTTP 派单→…→FULFILLED；SQL `1:1:2:2:2` | PASS |

不证明完整评分/硬过滤引擎、ServiceNetwork 生命周期或真实 sandbox。
