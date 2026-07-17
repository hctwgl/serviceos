---
title: M182 Admin 外发执行任务与快照成员深链
status: Implemented
milestone: M182
lastUpdated: 2026-07-17
---

# M182 Admin 外发执行任务与快照成员深链

## 1. 范围

承接 M171 / M179 / M181，补齐两处 Accepted OpenAPI 字段的详情深链：

```text
外发交付 → executionTaskId → /tasks/{id}
资料快照 members[].evidenceItemId → /evidence-items/{id}
```

前端 `OutboundDelivery` 类型对齐 Core OpenAPI；不新增契约。

## 2. 实现要点

1. `outbound.ts` 增加 `executionTaskId`；详情事实格与交叉链接；
2. `EvidenceSetSnapshotDetailPage` 成员资料项链接条；
3. Playwright `ADMIN-PILOT-08EM`：执行任务 / 成员资料项 GET 200。

## 3. 事务 / 授权 / 幂等

只读 UI 深链；目标 GET 仍由后端 Capability / Scope 强制。

## 4. 明确未实现

- FieldOperation / ReviewRoute / SavedView、企业 OIDC/BFF。

## 5. 证据入口

- `outbound.ts` / `OutboundDeliveryDetailPage.vue` / `EvidenceSetSnapshotDetailPage.vue`
- `admin-pilot-smoke.spec.ts`
- `testing/179-m182-admin-pilot-outbound-execution-snapshot-member-deeplink-acceptance.md`
