---
title: M385 项目履约配置中心验收矩阵（切片 A）
status: Accepted
milestone: M385
lastUpdated: 2026-07-20
---

# M385 项目履约配置中心验收矩阵（切片 A）

| # | 场景 | 期望 | 证据 |
|---|---|---|---|
| 1 | Token/主题 | 企业蓝 `#1677FF`、浅色画布 | `tokens.css` / `app-theme.ts` / unit |
| 2 | 导航分组 | 使用服务端 section，无正则猜测 | AppShell + Page Registry v18 |
| 3 | 配置中心母版 | SummaryStrip + 子导航 + 表 + 右轨 | Playwright + 截图 |
| 4 | 新建向导 | 标准/复制/空白，无硬编码一键创建 | Create 页 + guard unit |
| 5 | Runbook | 服务端 DTO，页面不渲染 Manifest JSON | compile-preview + Publish e2e |
| 6 | Compare/Impact | 真实差异，非固定文案 | compare-impact IT + 面板 |
| 7 | allowed-actions | 编辑/预览/发布受服务端动作约束 | Detail/Editor/Publish |
| 8 | 只读/冲突 | SUSPENDED 提示；发布冲突中文说明 | Detail/Publish |
| 9 | 视觉 | 1440/1280 截图提交审查 | `__screenshots__/fulfillment-*` |
| 10 | 产品状态 | 最高 `READY_FOR_REVIEW` | implementation-status |

## 状态

| 维度 | 状态 |
|---|---|
| 技术 | `API_AVAILABLE` / `RUNTIME_CONNECTED` |
| 前端 | `FRONTEND_COMPLETE`（本切片声明范围） |
| 产品 | `READY_FOR_REVIEW` |
| 测试 | `TEST_PASSED`（本切片适用命令） |
| 视觉 | `VISUAL_NOT_REVIEWED` |
| 可访问性 | `A11Y_NOT_REVIEWED`（列表键盘聚焦 e2e 已覆盖） |
