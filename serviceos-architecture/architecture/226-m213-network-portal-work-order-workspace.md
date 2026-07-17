---
title: M213 Network Portal 限定工单工作区
status: Implemented
milestone: M213
lastUpdated: 2026-07-17
relatedMilestones: [M194, M208, M212]
---

# M213 Network Portal 限定工单工作区

## 目标

交付 Network Portal 限定工单工作区只读适配：ACTIVE NETWORK 责任门禁下的薄工作区快照，
以及 Admin Web `/network-portal/work-orders/:id` 只读壳。

## 范围与非目标

- 范围：
  - ADR-051：`GET /network-portal/work-orders/{workOrderId}/workspace`；
  - 薄 DTO：工单头 + ACTIVE 任务摘要；改派后 ACCESS_DENIED；
  - Page Registry `NETWORK.WORKORDER.WORKSPACE` + `page-registry-v16`；
  - OpenAPI `1.0.0`；Flyway 仍 100/102；
  - Admin Web 列表深链 + 详情页；PortalContext IT / PostgresIT / Security / E2E。
- 明确不做：Admin workspace 复用、客户 PII、INTEGRATION、Portal ACK、notifications、
  FieldOperation、完整 §6.1 SLA/Visit/表单区块。

## 事实源

- ADR-051；product/03 §5～§6；ADR-032；API-06 §10

## 已实现

- [x] ADR-051
- [x] Query/Controller/OpenAPI + IT
- [x] Page Registry v16
- [x] Admin Web 详情壳 + E2E

## 明确未实现

- 完整 product/03 §6.1 区块；Admin workspace 等价面。

## 验证命令

```bash
bash scripts/verify-milestone-preflight.sh
bash scripts/agent-verify.sh docs contracts
./mvnw -pl serviceos-backend -am test -Dtest=ArchitectureTest,PortalContextPostgresIT,NetworkPortalReadPostgresIT,NetworkPortalControllerSecurityTest
cd serviceos-admin-web && npm ci && npm run build
bash scripts/verify-local.sh
```
