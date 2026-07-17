---
title: M160 Admin 联系尝试 ContactAttempt 详情页
status: Implemented
milestone: M160
lastUpdated: 2026-07-17
---

# M160 Admin 联系尝试 ContactAttempt 详情页

## 1. 范围

承接 M159，补齐已有 `ContactAttempt` Schema 与 `listTaskContactAttempts` 之上缺失的按 ID 读取：

```text
GET /api/v1/contact-attempts/{contactAttemptId} → ContactAttempt（无 ETag）
Admin /contact-attempts/{id} 只读详情
工作区 APPOINTMENTS_VISITS.contactAttempts[] → 联系详情深链
```

复用已 Implemented `appointment.read`、不可变联系事实与安全摘要字段；不新增写命令或 PII 语义。

## 2. 实现要点

1. Core OpenAPI **0.75.0** `getContactAttempt`；
2. `AppointmentService.getContactAttempt` + Controller GET；租户 404 / 缺权 403；不可变故无 ETag；
3. Admin `ContactAttemptDetailPage`；工作区「打开联系详情」；
4. PostgreSQL IT、MVC 安全测试、Playwright `ADMIN-PILOT-08CA`。

## 3. 事务 / 授权 / 幂等

只读查询；授权与任务联系历史列表一致使用 `appointment.read` + 实时 project/network scope。

## 4. 明确未实现

- FieldOperation 提交详情、GPS 策略增强、离线合并；
- SavedView、企业 OIDC/BFF、ServiceNetwork。

## 5. 证据入口

- `AppointmentController` / `DefaultAppointmentService#getContactAttempt`
- `AppointmentPostgresIT` / `AppointmentControllerSecurityTest`
- `ContactAttemptDetailPage.vue` + `admin-pilot-smoke.spec.ts`
- `testing/157-m160-admin-pilot-contact-attempt-detail-acceptance.md`
