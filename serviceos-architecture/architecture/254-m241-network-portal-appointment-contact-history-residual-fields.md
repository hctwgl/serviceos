---
title: M241 Network Portal 预约/联系历史残余 Accepted 字段展示
status: Implemented
milestone: M241
lastUpdated: 2026-07-17
relatedMilestones: [M197, M199, M238, M240]
---

# M241 Network Portal 预约/联系历史残余 Accepted 字段展示

## 目标

补齐任务页预约/联系历史上已 Accepted 但未展示的非 PII 残余字段，与工作区摘要（M240）对齐。

## 范围与非目标

- 范围：ADR-079；`NetworkPortalTasksPage` 历史 enrichment；TS 类型对齐；OpenAPI 仍 1.0.16；
  catalog 仍 v16；Flyway 仍 100/102；E2E。
- 明确不做：新 HTTP、addressRef/note/party/recording、PII、Portal ACK、notifications。

## 事实源

- `product/03-network-portal-spec.md` §8
- `decisions/ADR-079-network-portal-appointment-contact-history-residual-fields.md`
- Core OpenAPI `Appointment` / `ContactAttempt`

## 设计要点

- UI-only：列表 API 已返回完整 DTO；补齐 TS 与展示。
- `allowedActions` 只读展示，不改变既有写按钮门禁逻辑。

## 已实现

- [x] ADR-079
- [x] 预约历史范围/时间/allowedActions/时长
- [x] 联系历史 project/workOrder/createdAt
- [x] E2E `network-portal-appointment-contact-history-residual-fields.spec.ts`

## 明确未实现

今日/明日预约计数、notifications、Portal ACK、客户 PII。

## 工程证据

- OpenAPI 仍 1.0.16；Flyway 仍 100/102；catalog 仍 `page-registry-v16`
- Admin Web E2E + `bash scripts/verify-local.sh`

## 验证命令

```bash
bash scripts/verify-milestone-preflight.sh
bash scripts/agent-verify.sh docs
cd serviceos-admin-web && npm ci && npm run build
bash scripts/verify-local.sh
```
