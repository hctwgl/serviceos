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

- Admin 履约 API 经 `@serviceos/core-client`（`prebuild` 生成）
- 工单 A/B 冻结 IT；Playwright + a11y mock
- **表单/资料级 blockingReasons**：`HumanTaskCompletionValidator.explainBlockingReasons`（缺表单 VALIDATED、缺必传资料槽位名）
- Admin Pilot seed 补齐履约 Profile；入站 CREATE 已在真实 Keycloak/Postgres 下 `ACCEPTED`
- 完整 Admin OIDC Playwright 套件：本环境曾因 5173 被非 OIDC Vite 占用导致找不到登录按钮 → 记 **BLOCKED_EXTERNAL / env conflict**（见 `oidc-smoke-probe.md`）；种子修复后可在干净端口重跑

## 仍未闭合

1. 干净环境完整 `verify-admin-smoke.sh` 绿（OIDC UI 全套）
2. Admin 其他页面迁移到 core-client
3. 资料快照是否已创建的完成门禁投影（当前仅槽位 MISSING/条件变更）
