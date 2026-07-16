---
title: M76 工单资料审核时间线事件合并验收矩阵
status: Implemented
milestone: M76
---

# M76 工单资料审核时间线事件合并验收矩阵

| 编号 | 场景 | 预期证据 |
|---|---|---|
| M76-01 | form/snapshot/review/correction 已发布事件 | 写入同一工单投影，category/resource/eventType 正确 |
| M76-02 | 仅有 taskId | 只通过 TaskTimelineContextQuery 解析，projectId 错配失败关闭 |
| M76-03 | 非工单 Task | Inbox 完成但不投影 |
| M76-04 | 信息最小化 | 不保存 reason 正文、reasonCodes、digest、note、payload |
| M76-05 | 工程门禁 | V074、OpenAPI 0.47.0、PostgreSQL/Inbox/MVC/Contract/Client/ArchitectureTest 通过 |

本矩阵不验收 revision 技术校验噪声、external receipt、Delivery、异常、checkpoint/重建和 Portal。
