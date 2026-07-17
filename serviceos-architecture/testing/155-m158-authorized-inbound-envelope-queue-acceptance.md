---
title: M158 授权入站 Envelope 队列验收
status: Implemented
milestone: M158
lastUpdated: 2026-07-17
---

# M158 授权入站 Envelope 队列验收

| ID | 场景 | 期望 | 自动化入口 |
|---|---|---|---|
| M158-01 | OpenAPI 暴露列表 | Core 0.73.0 `listAuthorizedInboundEnvelopes` | `serviceos-core-v1.yaml` |
| M158-02 | projectId + readInbound | 默认 RECEIVED；仅返回已绑定项目内 Envelope | `InboundEnvelopeQueuePostgresIT` |
| M158-03 | 缺省 projectId | 实时授权项目集合范围化 SQL | `InboundEnvelopeQueuePostgresIT` |
| M158-04 | 游标绑定 | 范围或筛选变化后 VALIDATION_FAILED | `InboundEnvelopeQueuePostgresIT` |
| M158-05 | 安全字段 | 不含 digest / 对象引用 | IT + MVC |
| M158-06 | 匿名拒绝 | GET 队列 401 | `InboundMessageControllerSecurityTest` |
| M158-07 | Admin 队列筛选与深链 | COMPLETED+projectId → 详情 GET 200 | `admin-pilot-smoke.spec.ts` |
| M158-08 | 试点验收登记 | `ADMIN-PILOT-08IQ` | `verify-admin-smoke.sh` |

## 明确不做

- null-project 可见性、原文下载、SavedView、Visit 详情。
