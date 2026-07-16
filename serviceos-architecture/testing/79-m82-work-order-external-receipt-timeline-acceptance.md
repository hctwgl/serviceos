---
title: M82 工单外部审核回执时间线事件合并验收矩阵
status: Accepted
milestone: M82
---

# M82 工单外部审核回执时间线事件合并验收矩阵

| ID | 场景 | 期望 |
|---|---|---|
| M82-01 | receipt recorded | 经 ReviewTimelineContextQuery 写入 REVIEW / ExternalReviewReceipt 投影 |
| M82-02 | ReviewCase 不存在 | 失败关闭 |
| M82-03 | projectId 与 Case 不一致 | 失败关闭 |
| M82-04 | 信息最小化 | 不保存 externalKey / envelope / canonical |
| M82-05 | Modulith | readmodel → evidence::api 通过 ArchitectureTest |
| M82-06 | 工程门禁 | OpenAPI 0.53.0、无新 Flyway、PostgreSQL/Inbox/MVC/Contract/Client/ArchitectureTest 与 L3 verify |
