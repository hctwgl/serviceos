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
  - AppShell+工单中心、Empty、Error、工单详情、项目详情、审核队列、用户目录、角色目录、工作台
- 截图基线：`tests/e2e/__screenshots__/admin-*.png`（人工审查：无完整 UUID；车企中文；产品壳）
- a11y：`@axe-core/playwright`（`npm run test:a11y`）；排除 Ant Select 内部 combobox 实现缺口并在文档登记
- mock 冒烟：`tests/e2e/admin-productization-smoke.spec.ts`
- SavedView E2E 适配 Modal + Ant Select
- 对比度修复：三级文本 Token、SemanticStatusTag 自有语义色、侧栏选中态
- Token 扫描、unit、vue-tsc/build 回归
- 06 实施映射保持 Proposed 不变

## 验证命令

```bash
cd serviceos-admin-web
npm run check:tokens
npm run test:unit
npm run build
npm run test:productization   # visual + a11y + mock smoke + workbench stability
```

## 明确未完成 / 环境依赖

- 真实 OIDC 冒烟（`verify-admin-smoke.sh`）：本环境 Docker daemon 不可用（`unix:///var/run/docker.sock` 不存在），**未通过、不得谎报**
- 终审 8 态视觉仍沿用 M360；Conflict/Shadow/Offline 独立产品化金标未全覆盖
- 200% 缩放 / 完整读屏人工旅程未做桌面录证
- Ant Design Vue Select 内部 `aria-expanded` 等由组件库实现，axe 排除 `.ant-select` 内部节点