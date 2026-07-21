---
title: M399 项目列表履约方案聚合计数
status: Implemented
milestone: M399
lastUpdated: 2026-07-20
relatedMilestones: [M378, M398]
openapiVersion: "1.0.65"
flywayVersion: "138"
---

# M399 项目列表履约方案聚合计数

## 已实现

1. OpenAPI **1.0.65**：`Project.publishedSchemeCount` / `draftSchemeCount`（可 null）；
2. `ProjectFulfillmentProfileService.summarizeSchemeCounts`：批量聚合 + `project.fulfillment.read` soft-gate；
3. `DefaultProjectQueryService.list` 同页 enrichment；缺能力字段为 null；
4. Admin 项目列表展示「已发布方案 / 草稿方案」列；
5. ArchitectureTest + PostgresIT soft-gate + Playwright；关闭 M398 登记的方案聚合 UI_DATA_GAP。

## 明确未完成

- 车企/区域/网点实体选择器；
- 工作台关注项目读模型；
- 人工视觉批准。
