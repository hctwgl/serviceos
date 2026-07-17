---
title: M167 Admin Task 面板资源详情深链
status: Implemented
milestone: M167
lastUpdated: 2026-07-17
---

# M167 Admin Task 面板资源详情深链

## 1. 范围

承接 M155～M160 / M166，在 Task 现场与表单资料面板补齐已有详情页深链：

```text
TaskFieldOpsPanel → ContactAttempt / Appointment / Visit
TaskFormsEvidencePanel → FormSubmission / EvidenceItem
（Snapshot / Review 深链已由 M156/M112 覆盖）
```

不新增 OpenAPI。

## 2. 实现要点

1. 面板列表旁 `task / …` 前缀链接，避免与工作区深链严格模式冲突；
2. 表单/资料深链用新页签，避免冲掉 complete 双输入状态；
3. Playwright `ADMIN-PILOT-08TP`。

## 3. 事务 / 授权 / 幂等

只读 UI 深链；详情 GET 仍由后端 Capability / Scope 强制。

## 4. 明确未实现

- FieldOperation 详情、SavedView、企业 OIDC/BFF、真实 sandbox。

## 5. 证据入口

- `TaskFieldOpsPanel.vue` / `TaskFormsEvidencePanel.vue`
- `admin-pilot-smoke.spec.ts`
- `testing/164-m167-admin-pilot-task-panel-resource-deeplink-acceptance.md`
