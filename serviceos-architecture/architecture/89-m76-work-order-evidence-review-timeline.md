---
title: M76 工单资料审核时间线事件合并
status: Implemented
milestone: M76
---

# M76 工单资料审核时间线事件合并

## 1. 目标

在 M73～M75 工单时间线投影上，合并已发布且带 `taskId` 的资料/表单/审核/整改公开事件，
覆盖用户可见的提交→快照→审核→整改闭环。不含 revision 技术校验噪声、外部 receipt 仅有
reviewCaseId 的事件，以及 Delivery/异常。

## 2. 模块与事实边界

- 同一 Inbox 消费者 `readmodel.work-order-core-timeline.v1`；
- 全部事件通过 `TaskTimelineContextQuery` 解析 workOrder；载荷 `projectId` 必须与 Task 一致；
- 非工单 Task 事件 Inbox 完成但不投影；
- 不保存 reason 正文、reasonCodes 数组、digest、note、payload 或 PII。

## 3. 支持的事件

| 事件 | category | resourceType | outcome 来源 |
|---|---|---|---|
| `form.submitted@v1` | `FORM` | `FormSubmission` | `validationStatus` |
| `evidence.set-snapshotted@v1` | `EVIDENCE` | `EvidenceSetSnapshot` | `purpose` |
| `evidence.review-case-created@v1` | `REVIEW` | `ReviewCase` | `CREATED` |
| `evidence.client-review-case-created@v1` | `REVIEW` | `ReviewCase` | `CLIENT_CREATED` |
| `evidence.review-decided@v1` | `REVIEW` | `ReviewCase` | `decision` |
| `evidence.review-case-reopened@v1` | `REVIEW` | `ReviewCase` | `REOPENED` |
| `evidence.correction-case-created@v1` | `CORRECTION` | `CorrectionCase` | `CREATED` |
| `evidence.correction-resubmitted@v1` | `CORRECTION` | `CorrectionCase` | `RESUBMITTED` |
| `evidence.correction-closed@v1` | `CORRECTION` | `CorrectionCase` | `CLOSED` |
| `evidence.correction-waived@v1` | `CORRECTION` | `CorrectionCase` | `WAIVED` |

## 4. 数据库与契约

- V074 expand category：`FORM` / `EVIDENCE` / `REVIEW` / `CORRECTION`；
- Core OpenAPI 0.47.0 扩展 timeline `x-extensible-enum`。

## 5. 明确未实现

revision 创建/作废/校验、slots 解析、condition disposition、external receipt、Delivery、异常、
试算/结算、checkpoint/重建、Portal。
