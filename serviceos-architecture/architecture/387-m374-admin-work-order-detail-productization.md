---
title: M374 Admin 工单详情产品化
status: Implemented
milestone: M374
lastUpdated: 2026-07-20
relatedMilestones: [M373]
openapiVersion: unchanged
flywayVersion: unchanged
---

# M374 Admin 工单详情产品化

## 已实现

- DetailPageLayout：返回工单中心、业务编号、状态 Tag、主操作
- 摘要区产品字段；五等宽技术卡片移除
- Tabs：概览 / 履约任务 / 资料 / 审核整改 / 终审 / SLA / 活动 / 技术诊断
- 网点分配产品化（搜索选择 + 保留 smoke aria-label）
- 技术字段进入技术诊断 Tab / DeveloperDiagnosticsDrawer
- FinalReviewWorkspace 与 allowed-actions 保留

## UI_DATA_GAP

客户/手机/地址脱敏字段、网点推荐原因、师傅显示名（仅有 ID 时名称不可用）。

## 验证

```bash
cd serviceos-admin-web && npm run build
```
