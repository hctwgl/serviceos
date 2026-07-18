---
title: M216 Network Portal 工作区当前师傅 fan-in
status: Implemented
milestone: M216
lastUpdated: 2026-07-17
relatedMilestones: [M213, M214, M215, M194, M212]
---

# M216 Network Portal 工作区当前师傅 fan-in

## 目标

在限定工单工作区上，客户端 fan-in 本网点师傅列表，解析当前师傅 displayName，并可选展示
既有预约窗口（非地址）字段，补齐 product/03 §6.1「当前师傅和预约」。

## 范围与非目标

- 范围：ADR-054；工作区师傅摘要 + membership/list 深链；未指派 → tasks?taskId=；
  预约行可选 window 只读 enrichment；缺 technician.readOwnNetwork 省略师傅区块；
  OpenAPI 仍 1.0.0；catalog 仍 v16；Flyway 仍 100/102；E2E。
- 明确不做：新 HTTP/字段、SLA/Visit/表单 DTO、Admin workspace 复用、addressRef/PII、
  Portal ACK、写控件。

## 事实源

- `decisions/ADR-054-network-portal-workspace-technician-fanin.md`
- `api/06-application-query-preference-http-api.md` §10（M194 technicians / M213 workspace）
- Core OpenAPI `GET /network-portal/technicians`、`Appointment.revisions[].window`

## 设计要点

- 一次拉取 technicians 列表建 `technicianProfileId → item` 映射；
- 头/`tasks[].technicianId` 与 profileId 对齐（与指派 body 一致）；
- 预约窗口取 `revisions` 中 `revisionNo === currentRevisionNo` 的 `window`；忽略地址。

## 已实现

- [x] ADR-054
- [x] Workspace technician fan-in + deeplinks
- [x] Appointment window read-only enrichment
- [x] E2E

## 明确未实现

- SLA/Visit/表单摘要（无 NP 读 API；需另接受字段切片）
- Admin workspace 复用、客户 PII、notifications

## 工程证据

- OpenAPI 仍 1.0.0；Flyway 仍 100/102；catalog 仍 `page-registry-v16`
- Admin Web E2E：`network-portal-workspace-technician-fanin.spec.ts`

## 验证命令

```bash
bash scripts/verify-milestone-preflight.sh
bash scripts/agent-verify.sh docs
cd serviceos-admin-web && npm ci && npm run build
bash scripts/verify-local.sh
```
