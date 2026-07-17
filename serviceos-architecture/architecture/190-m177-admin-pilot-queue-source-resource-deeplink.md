---
title: M177 Admin 外发/异常/入站队列源资源深链
status: Implemented
milestone: M177
lastUpdated: 2026-07-17
---

# M177 Admin 外发/异常/入站队列源资源深链

## 1. 范围

承接 M176，补齐其余专项队列上 Accepted 投影字段的关联资源深链：

```text
外发队列 → project / sourceReview / sourceTask / sourceSnapshot / executionTask / clientReview
异常队列 → project / task / handlingTask
入站队列 → project / canonical / resultId（仅 resultType=WORK_ORDER）
```

`reviewRouteId` 与多态 `sourceId` 保持不链。不改 QueueTable 单元格渲染；不新增 OpenAPI。

## 2. 实现要点

1. 前端队列类型补齐 OpenAPI 已有字段；
2. `outbound-queue-cross-links` / `exception-queue-cross-links` / `inbound-queue-cross-links`；
3. Playwright `ADMIN-PILOT-08QO`。

## 3. 事务 / 授权 / 幂等

只读 UI 深链；目标 GET 仍由后端 Capability / Scope 强制。

## 4. 明确未实现

- QueueTable 行内单元格链接、SavedView、FieldOperation、企业 OIDC/BFF。

## 5. 证据入口

- `OutboundQueuePage.vue` / `ExceptionQueuePage.vue` / `InboundEnvelopeQueuePage.vue`
- `queues.ts`
- `admin-pilot-smoke.spec.ts`
- `testing/174-m177-admin-pilot-queue-source-resource-deeplink-acceptance.md`
