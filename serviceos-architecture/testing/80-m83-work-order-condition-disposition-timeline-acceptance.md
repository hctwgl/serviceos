---
title: M83 工单条件处置时间线事件合并验收矩阵
status: Accepted
milestone: M83
---

# M83 工单条件处置时间线事件合并验收矩阵

| ID | 场景 | 期望 |
|---|---|---|
| M83-01 | KEEP disposition | 经 TaskTimelineContextQuery 写入 EVIDENCE / EvidenceConditionDisposition，outcome=KEEP |
| M83-02 | 无工单链接 Task | Inbox 完成但不投影 |
| M83-03 | projectId 与 Task 不一致 | 失败关闭 |
| M83-04 | 信息最小化 | 不保存 reviewRef / reasonCode / slotId |
| M83-05 | 工程门禁 | OpenAPI 0.54.0、无新 Flyway、PostgreSQL/Inbox/MVC/Contract/Client/ArchitectureTest 与 L3 verify |
