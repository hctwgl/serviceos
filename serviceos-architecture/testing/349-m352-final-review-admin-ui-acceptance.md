---
title: M352 Admin 终审只读 UI 验收矩阵
status: Implemented
milestone: M352
lastUpdated: 2026-07-19
---

# M352 Admin 终审只读 UI 验收矩阵

| ID | 级别 | 场景 | 证据 |
|---|---|---|---|
| M352-01 | P0 | 安装 ant-design-vue / icons 并锁文件 | `package.json` / `package-lock.json` |
| M352-02 | P0 | ConfigProvider + zhCN + Token | `App.vue` / `app/app-theme.ts` / `styles/tokens.css` |
| M352-03 | P0 | 工单详情出现「平台终审」标签 | `WorkOrderWorkspacePage.vue` |
| M352-04 | P0 | 三栏布局与 data-testid | `FinalReviewWorkspace.vue` |
| M352-05 | P0 | 本地草稿文案不含“已保存到服务器” | draft composable + 单元测试 |
| M352-06 | P0 | 正式提交按钮禁用并说明只读 | AllowedActionButton reason |
| M352-07 | P1 | 生产构建通过 | `npm run build` |
