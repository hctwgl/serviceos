---
title: M419 Admin 主体授权拒绝安全活动流
version: 0.1.0
status: Implemented
milestone: M419
lastUpdated: 2026-07-21
---

# M419 Admin 主体授权拒绝安全活动流

## 1. 目标

关闭用户详情「AUTHORIZATION_DENIED 作为主体活动流」UI_DATA_GAP：提供**独立**只读 API，
按被拒主体（actor）展示授权拒绝，明确不并入 change-timeline。

## 2. 已实现

| 层 | 内容 |
|---|---|
| Flyway | **V144** `ix_aud_audit_actor_authorization_denied` |
| OpenAPI | **1.0.85** `GET .../authorization-denials` |
| Audit | `AuditQueryService.listAuthorizationDenialsByActor` |
| Identity | hard `identity.read` + soft `authorization.read` → `omitted` |
| Admin Web | 「登录与安全」Tab 新增「授权拒绝」区块 |
| 证据 | `IdentityDirectoryPostgresIT` + ArchitectureTest + Playwright |

## 3. 权限

- 硬门禁：`identity.read`（缺权 403；主体不存在 404）
- soft-gate：`authorization.read`（缺权 200 + `omitted=true` + `items=[]`）

## 4. 明确未实现

- 失败登录 / 设备指纹
- 将 AUTHORIZATION_DENIED 混入 change-timeline（刻意禁止）
- 全国区县全量树 / 拼音 / 多级子品牌
- 产品负责人视觉金标（`READY_FOR_REVIEW`）
