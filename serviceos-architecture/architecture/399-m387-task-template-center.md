---
title: M387 Admin 任务模板中心产品化
status: Implemented
milestone: M387
lastUpdated: 2026-07-20
relatedMilestones: [M385, M386, M388]
openapiVersion: "1.0.64"
flywayVersion: "138"
---

# M387 Admin 任务模板中心产品化

## 已实现

1. OpenAPI **1.0.64**：`GET /configuration/task-templates`；
2. 服务端从 WORKFLOW 已发布版本与在编草稿投影 `ConfigurationTaskTemplateItem`；
3. 产品页 `/configuration/task-templates`：分类树 + 表格 + 右栏详情 + SummaryStrip；
4. Page Registry v20：`ADMIN.TASK_TEMPLATE.CENTER`；
5. 履约配置中心「任务模板」导航跳转；
6. 诚实 `gaps` 字段说明分配策略/升级独立资产仍缺；
7. PostgresIT + Playwright + 截图。

## 明确未实现

- 独立 TaskTemplate 写聚合、复制、批量发布；
- 升级策略完整模型；
- 产品负责人视觉批准（`READY_FOR_REVIEW`）。
