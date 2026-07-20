---
title: M377 Admin 产品化视觉可访问性与回归关闭
status: Implemented
milestone: M377
lastUpdated: 2026-07-20
relatedMilestones: [M370, M371, M372, M373, M374, M375, M376]
openapiVersion: unchanged
flywayVersion: unchanged
---

# M377 Admin 产品化视觉 / a11y / 回归关闭

## 已实现

- 产品化视觉 Playwright：`tests/e2e/admin-productization-visual.spec.ts`
- 截图基线：`tests/e2e/__screenshots__/admin-work-order-directory-productized.png`（人工审查：无完整 UUID / 车企中文 / AppShell+ScopeBar）
- Token 扫描、unit、vue-tsc/build 回归
- 06 实施映射保持 Proposed 不变
- 里程碑文档 M370～M377 收口

## 验证命令

```bash
cd serviceos-admin-web
npm run check:tokens
npm run test:unit
npm run build
npx playwright test tests/e2e/admin-productization-visual.spec.ts
```

## 明确未完成 / 环境依赖

- 真实 OIDC 冒烟（`verify-admin-smoke.sh`）依赖本机 Keycloak/Backend/PostgreSQL，本云环境未跑通时不得谎报通过
- 全量视觉清单（审核队列、整改、冲突、Shadow 等）仅部分覆盖；终审 8 态仍沿用 M360 基线
- 键盘/200% 缩放/读屏人工旅程记录见验收矩阵，自动化 axe 未全量接入
