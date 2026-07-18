---
title: M210 Network Portal 运营异常详情只读 UI
status: Implemented
milestone: M210
lastUpdated: 2026-07-17
relatedMilestones: [M203, M209]
---

# M210 Network Portal 运营异常详情只读 UI

## 目标

在 M203 已交付的
`GET /api/v1/network-portal/operational-exceptions/{exceptionId}` 之上，交付只读详情页。

## 范围与非目标

- 范围：ADR-048；Admin Web `/network-portal/exceptions/:id`；列表深链；任务深链；
  catalog 仍 v15；OpenAPI 仍 0.99.0；Flyway 仍 100/102；E2E。
- 明确不做：Portal ACK/resolve、新 HTTP/capability/pageId。

## 事实源

- ADR-048；ADR-041；API-06 §10；Core OpenAPI `getNetworkPortalOperationalException`

## 已实现

- [x] ADR-048
- [x] NetworkPortalExceptionDetailPage + 路由
- [x] 列表深链；无 ACK 控件
- [x] E2E

## 明确未实现

- Portal ACK/resolve。

## 验证命令

```bash
bash scripts/verify-milestone-preflight.sh
bash scripts/agent-verify.sh docs
cd serviceos-admin-web && npm ci && npm run build
bash scripts/verify-local.sh
```
