---
title: M136 Admin 预约 / 上门写链路 E2E
status: Implemented
milestone: M136
lastUpdated: 2026-07-16
---

# M136 Admin 预约 / 上门写链路 E2E

## 1. 批准边界

承接 M135 与已 Implemented 的 Appointment（M30～M31）/ Visit（M32）运行时，以及 Admin
`TaskFieldOpsPanel`（M122～M126）。在真实 Keycloak / Backend / PostgreSQL / Chrome PR
阻断门禁中证明：

```text
assign/claim/start
→ proposeAppointment → PROPOSED
→ confirm → CONFIRMED
→ check-in → Visit IN_PROGRESS（Appointment IN_PROGRESS）
→ check-out → Visit COMPLETED（Appointment COMPLETED）
```

不扩大派单 Admin HTTP、用户确认渠道或 GPS 权威策略。

## 2. 实现范围

1. 本地 RoleGrant 追加 `appointment.propose/manage/cancel/recordContact` 与
   `visit.checkIn/checkOut/interrupt`；
2. 第五套每轮新建 UUID 夹具，并注入 ACTIVE NETWORK/TECHNICIAN `ServiceAssignment`，
   使 Visit 责任与开发者 principal / Task RESPONSIBLE 对齐（派单 Admin 表面仍未建立）；
3. Playwright 覆盖提议/确认/签到/签退；
4. `verify-admin-smoke.sh` SQL 断言 Appointment/Visit `COMPLETED`、四类审计与四条事件 Inbox。

## 3. 明确未实现

- 用户确认渠道、完整日程、跨端协作；
- GPS 权威距离 / BLOCK 策略演练；
- ServiceAssignment Admin 写表面；
- BYD 外发 stub + 回调；
- 完整 `ADMIN-PILOT-09`。

## 4. 工程证据

- `serviceos-deploy/keycloak/grant-local-project-admin.sql`
- `serviceos-deploy/admin-pilot/seed-admin-field-ops-assignment.sql`
- `serviceos-deploy/admin-pilot/verify-admin-smoke.sh`
- `serviceos-admin-web/tests/e2e/admin-pilot-smoke.spec.ts`
- `testing/133-m136-admin-appointment-visit-acceptance.md`
