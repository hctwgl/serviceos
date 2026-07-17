---
title: M212 Network Portal 师傅关系详情只读 UI
status: Implemented
milestone: M212
lastUpdated: 2026-07-17
relatedMilestones: [M206, M211]
---

# M212 Network Portal 师傅关系详情只读 UI

## 目标

在 M206 GET technician-memberships/{id} 之上交付只读详情页（含真实 version）。

## 范围与非目标

- 范围：ADR-050；`/network-portal/technicians/memberships/:id`；列表深链；catalog 仍 v15；
  OpenAPI 仍 0.99.0；Flyway 仍 100/102；E2E。
- 明确不做：新 HTTP/capability/pageId；改变 terminate 写语义。

## 已实现

- [x] ADR-050
- [x] NetworkPortalMembershipDetailPage + 路由
- [x] 师傅列表关系 ID 深链
- [x] E2E

## 验证命令

```bash
bash scripts/verify-milestone-preflight.sh
bash scripts/agent-verify.sh docs
cd serviceos-admin-web && npm ci && npm run build
bash scripts/verify-local.sh
```
