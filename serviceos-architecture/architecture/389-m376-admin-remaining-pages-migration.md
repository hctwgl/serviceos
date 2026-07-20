---
title: M376 Admin 主导航其余页面迁移
status: Implemented
milestone: M376
lastUpdated: 2026-07-20
relatedMilestones: [M375]
openapiVersion: unchanged
flywayVersion: unchanged
---

# M376 Admin 主导航其余页面迁移

## 已实现

- 工作台迁移至 WorkbenchPageLayout（待办优先 / 风险 / 今日 / 最近）
- 审核/整改/任务/异常/SLA 队列接入 PageContainer
- 用户/组织/网点/师傅/角色/授权/项目目录/搜索接入 PageContainer
- 保留原有查询、QueueTable 与 deep link 能力

## 明确未完成深化

部分队列仍使用原生筛选控件与 QueueTable，待后续切片继续 Ant Table 化；不删除真实功能。
