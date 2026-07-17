---
title: M158 授权入站 Envelope 队列
status: Implemented
milestone: M158
lastUpdated: 2026-07-17
---

# M158 授权入站 Envelope 队列

## 1. 目标

窄接受 API-06 §6.1 `GET /inbound-envelopes`，建立跨工单、实时项目范围约束的入站
Envelope 队列，并在 Admin 提供筛选与详情深链。不是通用 `work-queues` 或 SavedView。

## 2. 查询语义

- 筛选：`projectId`、`processingStatus`（默认 `RECEIVED`）、`messageType`、
  `resultType`、`resultId`、`canonicalMessageId`；
- 排序：`receivedAt DESC, inboundEnvelopeId DESC`；
- limit 1～100；游标绑定 scopeDigest 与全部筛选；
- 始终排除 `projectId IS NULL`；null-project 可见性仍为草案；
- 能力：`integration.readInbound`。

## 3. 安全响应

队列返回 Envelope 身份、messageType、externalMessageId、signature/processing 状态、
mapping/canonical/result 引用与时间戳。禁止 digest、对象存储引用、签名原文、nonce、凭据。

## 4. 契约与数据库

- Core OpenAPI **0.73.0**；
- Flyway **V085** 增加 `(tenant_id, processing_status, received_at DESC, id DESC)` 游标索引；
- Admin：`/integration/inbound` 队列页 → 已有详情页。

## 5. 明确未实现

null-project 可见性、原文下载、replay/人工处置 UI、SavedView、通用 work-queues、Visit 详情、
ServiceNetwork。

## 6. 证据入口

- `InboundMessageController` / `DefaultInboundMessageQueryService`
- `InboundEnvelopeQueuePostgresIT` / `InboundMessageControllerSecurityTest`
- `InboundEnvelopeQueuePage.vue` + Playwright `ADMIN-PILOT-08IQ`
- `testing/155-m158-authorized-inbound-envelope-queue-acceptance.md`
