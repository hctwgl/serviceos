---
title: M364 独立审核 REVIEW_TASK 模板分离验收矩阵
status: Implemented
milestone: M364
lastUpdated: 2026-07-20
---

# M364 独立审核 REVIEW_TASK 模板分离验收矩阵

## 设计门禁

| ID | 级别 | 场景 | 期望 | 证据 |
|---|---|---|---|---|
| M364-DES-001 | P0 | A1～A5 决策包成文并接受 | ADR-087 Accepted：A1-R～A5-R | ADR-087 |
| M364-DES-002 | P0 | 与 Correction 对称性 | `taskId` + `reviewTaskId` | ADR-087 / 实现 |
| M364-DES-003 | P0 | 整改复审不篡改旧决定 | CLOSED→新 Case+Task | IT |
| M364-DES-004 | P0 | 接受后推进 latestMilestone | implementation-status = M364 | 状态文档 |

## 工程门禁

| ID | 级别 | 场景 | 期望 | 证据 |
|---|---|---|---|---|
| M364-ENG-001 | P0 | create OPEN 绑定 reviewTaskId | 同事务 READY HUMAN Task | ReviewCasePostgresIT |
| M364-ENG-002 | P0 | APPROVED 只 complete reviewTaskId | 源提交 Task 状态不变 | ReviewCasePostgresIT |
| M364-ENG-003 | P0 | 试点模板含 REVIEW_TASK | 种子可发布且双目录同步 | workflow.json + ConfigurationSchemaDriftTest |
| M364-ENG-004 | P0 | 整改 CLOSED→新 Case+Task | 旧决定只读；新 Task READY | CorrectionCasePostgresIT |
| M364-ENG-005 | P0 | OpenAPI 1.0.57 / Flyway 132 | 契约与迁移一致 | contracts + V132 |
| M364-ENG-006 | P0 | 模块边界 | ArchitectureTest | arch |
