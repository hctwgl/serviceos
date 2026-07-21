---
title: M419 Admin 主体授权拒绝安全活动流验收矩阵
version: 0.1.0
status: Implemented
milestone: M419
lastUpdated: 2026-07-21
---

# M419 验收矩阵

| ID | 场景 | 期望 | 证据 |
|---|---|---|---|
| A1 | 真实拒绝写入后可读 | `require` 触发 AUTHORIZATION_DENIED 后列表含 capability/errorCode | `IdentityDirectoryPostgresIT` |
| A2 | soft-omit | 缺 `authorization.read` → `omitted=true` 且 items 空 | 同上 |
| A3 | 不并入变更时间线 | change-timeline source 枚举不含 AUTHORIZATION_DENIED | OpenAPI + 既有 Contributor |
| A4 | 模块边界 | ArchitectureTest | ArchitectureTest |
| A5 | Admin UI | 登录与安全 Tab 展示授权拒绝区块 | Playwright |

产品状态：`READY_FOR_REVIEW`。
