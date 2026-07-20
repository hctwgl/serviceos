---
title: M365 REVIEW_TASK 工作流门闸推进验收矩阵
status: Implemented
milestone: M365
lastUpdated: 2026-07-20
---

# M365 REVIEW_TASK 工作流门闸推进验收矩阵

| ID | 级别 | 场景 | 期望 | 证据 |
|---|---|---|---|---|
| M365-WF-001 | P0 | REVIEW_TASK 解析为 WAITING 门闸 | 无 taskType/taskKind；固定 waitEventType | WorkflowDefinitionParserTest |
| M365-WF-002 | P0 | INSTALL 后进入 REVIEW_TASK WAITING | 无 workflow HUMAN evidence.review Task | HomeChargingSurveyInstallTemplatePostgresIT |
| M365-WF-003 | P0 | 门闸信号后进入 WAIT_OEM | REVIEW_TASK COMPLETED | HomeChargingSurveyInstallTemplatePostgresIT |
| M365-WF-004 | P0 | 早期 APPROVED 在门闸激活时自动消费 | 直达 WAIT_OEM；early signal consumed | HomeChargingSurveyInstallTemplatePostgresIT |
| M365-WF-005 | P0 | 保持 A2-R/A5-R | reviewTaskId 仍为 handling Task | M364 回归 + 本切片不改 decide |
| M365-WF-006 | P0 | 模块边界 | ArchitectureTest | arch |
