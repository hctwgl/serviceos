---
title: M81 工单 Task 指派与执行保护时间线事件合并验收矩阵
status: Accepted
milestone: M81
---

# M81 工单 Task 指派与执行保护时间线事件合并验收矩阵

| ID | 场景 | 期望 |
|---|---|---|
| M81-01 | assigned / prepared / guard / manual-intervention | 经 TaskTimelineContextQuery 写入 TASK 投影 |
| M81-02 | 无工单链接 Task | Inbox 完成但不投影 |
| M81-03 | 信封与 taskId 不一致 | 失败关闭 |
| M81-04 | 信息最小化 | 不保存候选人、preparationKey、guardKey、businessKey |
| M81-05 | 工程门禁 | OpenAPI 0.52.0、无新 Flyway、PostgreSQL/Inbox/MVC/Contract/Client/ArchitectureTest 与 L3 verify |
