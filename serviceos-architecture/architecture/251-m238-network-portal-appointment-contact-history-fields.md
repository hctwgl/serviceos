---
title: M238 Network Portal 预约/联系历史 Accepted 字段展示
status: Implemented
milestone: M238
lastUpdated: 2026-07-17
relatedMilestones: [M197, M199, M216, M220, M237]
---

# M238 Network Portal 预约/联系历史 Accepted 字段展示

## 目标

闭合 product/03 §8：任务页预约/联系历史展示操作者与渠道，避免「网点代约」被误读为师傅预约。

## 范围与非目标

- 范围：ADR-076；`NetworkPortalTasksPage` 历史行渲染既有非 PII 字段；补齐 TS 类型
  `createdBy` / revision `confirmationChannel`/`confirmedPartyType`；OpenAPI 仍 1.0.16；
  catalog 仍 v16；Flyway 仍 100/102；E2E。
- 明确不做：新 HTTP、工作区/目录摘要扩 actor、addressRef/note/party/recording、
  今日/明日预约计数、notifications、Portal ACK、PII。

## 事实源

- `product/03-network-portal-spec.md` §8
- `decisions/ADR-076-network-portal-appointment-contact-history-fields.md`
- Core OpenAPI `Appointment` / `AppointmentRevision` / `ContactAttempt`

## 设计要点

- UI-only：列表 API 已返回完整 DTO；仅补类型与展示。
- 渠道取当前 `currentRevisionNo` 对应 revision 的 `confirmationChannel`；操作者取
  `Appointment.createdBy` / `ContactAttempt.actorId`。
- 继续禁止渲染 addressRef/note（ADR-054）。

## 已实现

- [x] ADR-076
- [x] 预约历史：操作者 / 渠道 / 确认方类型 / 窗口
- [x] 联系历史：操作者 / 渠道
- [x] E2E `network-portal-appointment-contact-history-fields.spec.ts`

## 明确未实现

工作区/目录摘要 actor、今日/明日预约计数、notifications、Portal ACK、客户 PII。

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
