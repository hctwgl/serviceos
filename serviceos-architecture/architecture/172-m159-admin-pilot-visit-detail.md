---
title: M159 Admin 上门 Visit 详情页
status: Implemented
milestone: M159
lastUpdated: 2026-07-17
---

# M159 Admin 上门 Visit 详情页

## 1. 范围

承接 M158，补齐已有 `Visit` Schema 与 `listWorkOrderVisits` 之上缺失的按 ID 读取：

```text
GET /api/v1/visits/{visitId} → Visit + ETag
Admin /visits/{id} 只读详情
工作区 APPOINTMENTS_VISITS.visits[] → 上门详情深链
```

复用已 Implemented `visit.read`、Visit 聚合与安全摘要字段；不新增写命令或 PII 语义。

## 2. 实现要点

1. Core OpenAPI **0.74.0** `getVisit`；
2. `VisitService.get` + Controller GET；租户 404 / 缺权 403；
3. Admin `VisitDetailPage`；工作区「打开上门详情」；
4. PostgreSQL IT、MVC 安全测试、Playwright `ADMIN-PILOT-08VD`。

## 3. 事务 / 授权 / 幂等

只读查询；授权与列表一致使用 `visit.read` + 实时 project/network scope。

## 4. 明确未实现

- ContactAttempt 独立详情页；
- FieldOperation 提交详情、GPS 策略增强、离线合并；
- SavedView、企业 OIDC/BFF、ServiceNetwork。

## 5. 证据入口

- `VisitController` / `DefaultVisitService#get`
- `VisitPostgresIT` / `VisitControllerSecurityTest`
- `VisitDetailPage.vue` + `admin-pilot-smoke.spec.ts`
- `testing/156-m159-admin-pilot-visit-detail-acceptance.md`
