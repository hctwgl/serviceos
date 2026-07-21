---
title: M388 履约配置结构化 Draft DTO
status: Implemented
milestone: M388
lastUpdated: 2026-07-20
relatedMilestones: [M378, M383, M385]
openapiVersion: "1.0.63"
flywayVersion: "138"
---

# M388 履约配置结构化 Draft DTO

## 已实现

1. OpenAPI **1.0.63**：`ProjectFulfillmentDocument` / `ProjectFulfillmentStageDraft`；
2. Draft GET/PUT 以结构化 `document` 为主契约；
3. 服务端 `ProjectFulfillmentDocumentMapper` 唯一映射持久化 JSON；
4. Admin 编辑器读写 `document.stages`；`documentJson` 仅诊断抽屉；
5. 产品守卫单测禁止 `documentJson` 主写路径；Playwright 覆盖编辑器加载。

## 明确未实现

- Workflow 可视化设计器（M386）；
- Task 模板中心与实体选择器目录（M387）；
- 使用中工单计数读模型（项目级摘要见 **M422**）；
- 产品负责人人工视觉批准（保持 `READY_FOR_REVIEW`）。
