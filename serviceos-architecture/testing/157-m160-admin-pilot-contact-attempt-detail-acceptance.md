---
title: M160 Admin 联系尝试 ContactAttempt 详情页验收
status: Implemented
milestone: M160
lastUpdated: 2026-07-17
---

# M160 Admin 联系尝试 ContactAttempt 详情页验收

| ID | 场景 | 期望 | 自动化入口 |
|---|---|---|---|
| M160-01 | OpenAPI 暴露 getContactAttempt | Core 0.75.0 `/contact-attempts/{contactAttemptId}` | `ContractValidationTest` |
| M160-02 | 获权读取 | `appointment.read` 返回 ContactAttempt（无 ETag） | `AppointmentPostgresIT` / MVC |
| M160-03 | 缺权 / 不存在 | 403 / 404 | `AppointmentPostgresIT` |
| M160-04 | 匿名拒绝 | GET 401 | `AppointmentControllerSecurityTest` |
| M160-05 | Admin 工作区深链 | 记录联系后 AV → 联系详情 GET 200 | `admin-pilot-smoke.spec.ts` |
| M160-06 | 试点验收登记 | `ADMIN-PILOT-08CA` | `verify-admin-smoke.sh` |

## 明确不做

- FieldOperation 详情、SavedView、ServiceNetwork。
