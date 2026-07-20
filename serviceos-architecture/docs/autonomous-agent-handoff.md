---
title: ServiceOS 自主 Agent 交接
lastUpdated: 2026-07-20
---

# ServiceOS 自主 Agent 交接

## 当前

- **状态**：**PARTIAL** — 履约主链路可运行；真实 OIDC 全链路与表单/资料级阻塞仍未闭合
- OpenAPI **1.0.61** / Flyway **138** / ADR-091
- PR：https://github.com/hctwgl/serviceos/pull/204

## 最新追加

- Admin 履约 API 经 `@serviceos/core-client`（`prebuild` 生成）；禁止页面手写 URL
- `ProjectFulfillmentWorkOrderFreezePostgresIT`：工单 A 冻 v1、B 冻 v2
- Playwright 列表/键盘聚焦 + axe a11y（mock）

## 仍未闭合

1. 真实 Keycloak OIDC smoke（可能 `BLOCKED_EXTERNAL`）
2. 真实后端驱动的发布→建单 Playwright（非 mock）
3. 表单字段/资料槽位级 `blockingReasons`
4. Admin 其他页面迁移到 core-client（仅履约模块已切换）
