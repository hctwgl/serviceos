---
title: M135 Admin 正常整改补传 / 关闭 / 复审写链路验收
status: Implemented
lastUpdated: 2026-07-16
---

# M135 Admin 正常整改补传 / 关闭 / 复审写链路验收

| ID | 场景 | 证据 | 结果 |
|---|---|---|---|
| M135-01 | 同 Item 补传 Revision | Admin 上传在已有 Item 时传 `evidenceItemId`；`maxCount=1` 槽位可追加第二 Revision | PASS |
| M135-02 | 驳回自动开整改 | 普通 REJECTED → CorrectionCase `IN_PROGRESS` + `evidence.correction` Task；授权队列可见 | PASS |
| M135-03 | 正常补传 | 源 Task 新 Snapshot → `resubmit` → `RESUBMITTED` + 补传历史；不得复用驳回 Snapshot | PASS |
| M135-04 | 关闭整改 | `close` → `CLOSED`；成功提示不遮蔽权威状态与补传历史 | PASS |
| M135-05 | 复审通过 | 对补传 Snapshot 新建 INTERNAL ReviewCase → 普通 APPROVED（close ≠ 审核通过） | PASS |
| M135-06 | 完结推进 | FormSubmission + 补传 Snapshot 双引用 complete → Inbox → WorkOrder `FULFILLED` | PASS |
| M135-07 | PR 阻断冒烟 | `bash serviceos-deploy/admin-pilot/verify-admin-smoke.sh` 含第四套夹具与 SQL 断言 | PASS |

本矩阵只证明正常整改补传/关闭/复审/完结的 Admin 浏览器写链路；不证明外部提审、预约上门、
ServiceAssignment 网点派单或从入站接单开始的完整 `ADMIN-PILOT-09`。
