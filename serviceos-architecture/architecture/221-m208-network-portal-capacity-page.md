---
title: M208 Network Portal 产能页
status: Implemented
milestone: M208
lastUpdated: 2026-07-17
relatedMilestones: [M194, M207]
---

# M208 Network Portal 产能页

## 目标

在 M194 capacity API 与 M207 工作台摘要之上，注册 `NETWORK.CAPACITY` 并交付独立只读产能页。

## 范围与非目标

- 范围：
  - ADR-046：Page Registry + Admin Web `/network-portal/capacity`；
  - 复用 `GET /network-portal/capacity`；展示含 `version`；
  - catalog `page-registry-v15`；OpenAPI 仍 0.99.0；Flyway 仍 100/102；
  - 工作台深链；PortalContext IT；E2E。
- 明确不做：产能写、CapacityAdjustmentRequest、字段发明。

## 事实源

- ADR-046；ADR-032；product/03 `NETWORK.CAPACITY`；M194 capacity

## 设计要点

- 无新后端路径；能力仍为 `networkTask.read`；
- UI 调用专用 listCapacity，不全量依赖 workbench 嵌入。

## 已实现

- [x] ADR-046
- [x] Page Registry NETWORK.CAPACITY + v15
- [x] listNetworkPortalCapacity + CapacityPage
- [x] 工作台深链
- [x] PortalContextPostgresIT / E2E

## 明确未实现

- 产能申请/写；停派原因等未 Accepted 字段。

## 工程证据

- Page Registry：`page-registry-v15`
- E2E：`network-portal-capacity-page.spec.ts`
- IT：`PortalContextPostgresIT`

## 验证命令

```bash
bash scripts/verify-milestone-preflight.sh
bash scripts/agent-verify.sh docs contracts arch
./mvnw -pl serviceos-backend -am test -Dtest=ArchitectureTest,PortalContextPostgresIT
cd serviceos-admin-web && npm ci && npm run build
bash scripts/verify-local.sh
```
