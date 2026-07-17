---
title: M159 Admin 上门 Visit 详情页验收
status: Implemented
milestone: M159
lastUpdated: 2026-07-17
---

# M159 Admin 上门 Visit 详情页验收

| ID | 场景 | 期望 | 自动化入口 |
|---|---|---|---|
| M159-01 | OpenAPI 暴露 getVisit | Core 0.74.0 `/visits/{visitId}` | `ContractValidationTest` |
| M159-02 | 获权读取 | `visit.read` 返回 Visit + ETag | `VisitPostgresIT` / MVC |
| M159-03 | 缺权 / 不存在 | 403 / 404 | `VisitPostgresIT` |
| M159-04 | 匿名拒绝 | GET 401 | `VisitControllerSecurityTest` |
| M159-05 | Admin 工作区深链 | 签退后 AV → 上门详情 GET 200 | `admin-pilot-smoke.spec.ts` |
| M159-06 | 试点验收登记 | `ADMIN-PILOT-08VD` | `verify-admin-smoke.sh` |

## 明确不做

- ContactAttempt 详情、FieldOperation 详情、SavedView、ServiceNetwork。
