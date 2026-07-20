---
title: ServiceOS 自主 Agent 交接
lastUpdated: 2026-07-20
---

# ServiceOS 自主 Agent 交接

## 当前

- **状态**：**IMPLEMENTING / Draft PR**
- 进行中：M365 REVIEW_TASK 工作流门闸（A5-B）
- 分支：`cursor/m365-review-task-workflow-gate-6a78`
  （base：`cursor/m364-review-task-separation-design-6a78`）
- 工程基线目标：**M365**（OpenAPI 1.0.57 / Flyway 133）
- 选型：**C**（A5-B）

## 已闭环

- M356～M363：Technician 客户端能力门禁
- M364：独立审核 handling Task（ADR-087 A1-R～A5-R）
- M365（进行中）：REVIEW_TASK WAITING 门闸 + review-decided 唤醒

## 下一候选（M365 完成后需再选型）

1. 派单级 `supportedClientKinds` 过滤
2. Network Portal on-behalf 能力门禁
3. iOS 条件执行器 / 吉利 / AMOUNT·加权 / BUSINESS SLA（硬门禁）
