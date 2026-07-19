---
title: M299 出站提审 Profile 注册表验收矩阵
status: Implemented
milestone: M299
lastUpdated: 2026-07-19
---

| 编号 | 场景 | 预期 | 证据 |
|---|---|---|---|
| M299-01 | Profile SPI 存在 | SPI + Registry 文件存在 | ArchitectureTest |
| M299-02 | BYD lineage 唯一命中 | requireForInboundLineage 返回 BYD | OutboundReviewSubmissionProfilesTest |
| M299-03 | 未知 lineage | RESOURCE_NOT_FOUND 失败关闭 | OutboundReviewSubmissionProfilesTest |
| M299-04 | 出站队列/Review 回归 | 行为保持 | OutboundDeliveryQueuePostgresIT / ReviewCasePostgresIT |
