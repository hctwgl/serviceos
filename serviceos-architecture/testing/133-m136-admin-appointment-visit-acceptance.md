---
title: M136 Admin 预约 / 上门写链路验收
status: Implemented
lastUpdated: 2026-07-16
---

# M136 Admin 预约 / 上门写链路验收

| ID | 场景 | 证据 | 结果 |
|---|---|---|---|
| M136-01 | 本地写能力授权 | RoleGrant 含 propose/manage/checkIn/checkOut | PASS |
| M136-02 | 责任对齐 | 夹具 ACTIVE NETWORK/TECHNICIAN ServiceAssignment 与开发者 principal 对齐 | PASS |
| M136-03 | 提议预约 | Admin proposeAppointment → PROPOSED | PASS |
| M136-04 | 确认预约 | confirm → CONFIRMED（If-Match） | PASS |
| M136-05 | 签到 | check-in → Visit IN_PROGRESS | PASS |
| M136-06 | 签退 | check-out → Visit/Appointment COMPLETED；审计与 Inbox | PASS |
| M136-07 | PR 阻断冒烟 | `verify-admin-smoke.sh` 含第五套夹具 | PASS |

本矩阵不证明用户确认渠道、GPS BLOCK、派单 Admin HTTP、外部提审或完整 `ADMIN-PILOT-09`。
