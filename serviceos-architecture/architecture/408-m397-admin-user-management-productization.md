---
title: M397 Admin 用户管理母版产品化
status: Implemented
milestone: M397
lastUpdated: 2026-07-20
relatedMilestones: [M187, M188, M385]
openapiVersion: "1.0.64"
flywayVersion: "138"
---

# M397 Admin 用户管理母版产品化

## 已实现

1. 用户目录：ListPageLayout、SummaryStrip、中文筛选/表格、分页；
2. 用户详情：DetailPageLayout + Tabs（基本信息/组织归属/角色权限/登录安全/变更）；
3. 新建用户入口诚实禁用并登记 UI_DATA_GAP；
4. Playwright 1440 截图；产品状态 `READY_FOR_REVIEW`。

## 明确未实现

- 新建用户写流程；
- 组织归属树、角色摘要、最近登录专用列表读模型；
- 人工视觉批准。
