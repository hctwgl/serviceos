---
title: M319 批量 ReplayRequest 验收矩阵
status: Implemented
milestone: M319
lastUpdated: 2026-07-19
---

# M319 批量 ReplayRequest 验收矩阵

| ID | 场景 | 期望 | 证据 |
|---|---|---|---|
| B319-01 | PREVIEW | ELIGIBLE + INELIGIBLE(NOT_UNKNOWN) | `BatchReplayPostgresIT#previewMarksEligibleAndIneligible` |
| B319-02 | SUBMIT→APPROVE | COMPLETED + SCHEDULED + 单笔 replay 落库 | `BatchReplayPostgresIT#submitApproveSchedulesSingleReplay` |
| B319-03 | 契约 | OpenAPI 1.0.43 兼容 | `agent-verify.sh contracts` |
