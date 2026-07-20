---
title: ServiceOS 自主 Agent 交接
lastUpdated: 2026-07-20
---

# ServiceOS 自主 Agent 交接

## 当前

- **状态**：**PARTIAL（已按用户指示停止推进）** — 履约主链路可运行；Admin Pilot OIDC 全套未全绿
- OpenAPI **1.0.61** / Flyway **138** / ADR-091
- PR：https://github.com/hctwgl/serviceos/pull/204
- 最新基线分支：`cursor/bc-63192e98-f1e1-4e49-a3e3-c33a0e8b88da-4023`

## 最新追加

- Admin 履约 API 经 `@serviceos/core-client`（`prebuild` 生成）
- 工单 A/B 冻结 IT；Playwright + a11y mock
- **表单/资料级 blockingReasons**：`HumanTaskCompletionValidator.explainBlockingReasons`
- Admin Pilot seed 补齐履约 Profile；`project.fulfillment.*` 授予 local-project-admin
- OIDC 入站 CREATE `ACCEPTED`；Playwright **tests 1–6** + final-review visual/workspace **绿**
- 工作区补齐「集成」「预约到场」页签；Task 详情 prepared-complete 刷新 allowed-actions
- `verify-admin-smoke` 最佳 **17 passed / 2 failed**（test 7 外发 CLIENT、test 8 入站长链路未闭合）

## 明确未闭合（勿再默认续跑全量 OIDC）

1. OIDC test 7：审核外发 + 厂端回调关闭 CLIENT Case（活动记录/回执侧链）
2. OIDC test 8：入站长链路（领取→预约→整改→外发）
3. Admin 其他页面迁移到 `@serviceos/core-client`
4. 资料快照「是否已创建」完成门禁投影（当前仅槽位 MISSING/条件变更）

后续若续作，请**只跑失败用例**，勿默认整套 19 项串行重跑。
