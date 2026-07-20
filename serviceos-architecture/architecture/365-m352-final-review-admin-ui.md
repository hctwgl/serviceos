---
title: M352 Admin 终审工作台只读 UI 与 Ant Design 定型
status: Implemented
milestone: M352
lastUpdated: 2026-07-19
relatedMilestones: [M351]
openapiVersion: "1.0.48"
flywayVersion: "129"
---

# M352 Admin 终审工作台只读 UI 与 Ant Design 定型

## 目标

在现有工单工作区中接入平台终审三栏只读工作台，并完成 Admin Web 的 Ant Design Vue
基础设施定型（ConfigProvider、中文 locale、ServiceOS Token、AppShell 视觉）。

## 范围与非目标

- 范围：
  - `ant-design-vue` + `@ant-design/icons-vue`（npm 锁文件）
  - ConfigProvider + zhCN + Design Token
  - AppShell 侧栏按 Token 重绘（继续消费服务端导航）
  - 公共业务组件：StatusTag / SlaCountdown / AllowedActionButton / SensitiveText / AsyncContent / PermissionBoundary
  - `FinalReviewWorkspace` 三栏只读 UI + sessionStorage 草稿 + Revision 短时预览
  - 工单详情「平台终审」标签
- 明确不做：正式提交（M353）；批量重写无关页面；Vben / 第二套 UI 库

## 已实现

- `serviceos-admin-web` 依赖与主题
- `features/work-orders/components/final-review/FinalReviewWorkspace.vue`
- 草稿 composable；登出清理监听
- `npm run build` / `test:unit` / 视觉占位 spec

## 明确未实现

正式 `:decide`、冲突弹窗已由后续里程碑完成；完整 8 态视觉基线实拍见 **M360**。

## 验证命令

```bash
cd serviceos-admin-web && npm ci && npm run build && npm run test:unit
```
