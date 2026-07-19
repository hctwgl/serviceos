---
title: M355 平台终审真实端到端门禁
status: Implemented
milestone: M355
lastUpdated: 2026-07-19
relatedMilestones: [M351, M352, M353, M354]
openapiVersion: "1.0.49"
flywayVersion: "130"
---

# M355 平台终审真实端到端门禁

## 目标

用真实 PostgreSQL / Backend / 生成 Client / Playwright 覆盖终审主路径与安全约束；
完整 28 场景矩阵以自动化 IT + Admin 构建/单元 + 契约/客户端门禁为主证据，
真实 OIDC Chrome 冒烟沿用 `admin-pilot` 入口。

## 证据入口

| 类别 | 命令 / 产物 |
|---|---|
| 后端裁决与整改 | `ReviewCasePostgresIT` / `CorrectionCasePostgresIT` / `ReviewRuleGatePostgresIT` |
| 终审查询与脱敏 | `WorkOrderWorkspacePostgresIT` / SecurityTest / MaskedContactTest |
| 契约与 Client | OpenAPI 1.0.49；`bash scripts/agent-verify.sh client-ts`（decide 破坏性演进已记录） |
| Admin | `npm run build` / `npm run test:unit` / FinalReviewWorkspace |
| Playwright（Mock API） | `npx playwright test tests/e2e/final-review-workspace.spec.ts`：三栏加载、主操作禁用/驳回整改、只读无权限；截图 `tests/e2e/__screenshots__/final-review-*.png` |
| 冒烟 | `serviceos-deploy/admin-pilot/verify-admin-smoke.sh`（需 compose+Keycloak；本环境未完整实跑） |

## 明确未实现 / 限制

- OpenAPI decide 请求对旧客户端破坏性演进（新系统直接修正，oasdiff 相对 master 会报警）
- 完整 8 态视觉截图人工基线需在有稳定夹具的环境补采
- 结算/预结算仍禁止伪造

## 验证命令

```bash
bash scripts/agent-verify.sh it ReviewCasePostgresIT,CorrectionCasePostgresIT,ReviewRuleGatePostgresIT,WorkOrderWorkspacePostgresIT
bash scripts/agent-verify.sh client-ts
cd serviceos-admin-web && npm ci && npm run build && npm run test:unit
```
