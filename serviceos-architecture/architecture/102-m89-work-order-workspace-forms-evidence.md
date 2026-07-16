---
title: M89 工单工作区表单与资料区块
status: Implemented
milestone: M89
---

# M89 工单工作区表单与资料区块

## 1. 目标

扩展 API-06 §5 `workspace/sections/{section}` Accepted 范围，增加
`FORMS_EVIDENCE` 实时组合按需加载。

## 2. 接受范围

- API-06 §5 新增 `FORMS_EVIDENCE`（仍不含其余 section / activity-summary）；
- 复用 `workOrder.read` 入口鉴权；Form / Evidence 分别经 `form.read` /
  `evidence.read` 按 Task 扇出装载，缺权时该半边为 null，不把整个工作区打成 403；
- 载荷不含表单 `definitionJson`、资料 `requirementDefinition` /
  `resolutionExplanation` 等大 JSON，也不含表单提交 values（无按 Task 列表端口）。

## 3. 组合事实

| 子集 | 来源 |
|---|---|
| forms | 工单 Task 列表后 `forms::api` `TaskFormQueryService.listForTask` 扇出 |
| evidenceSlots | 工单 Task 列表后 `evidence::api` `EvidenceSlotQueryService.listForTask` 扇出 |

顶层 `sectionAvailability.FORMS_EVIDENCE`：缺两边读权 → UNAVAILABLE；两边空 → EMPTY；
否则 AVAILABLE。

`evidence.read` 已授权但 Task 尚未完成可靠槽位解析（`TASK_STATE_CONFLICT`）时，
将该半边视为空列表，避免未解析资料把整个工作区顶层查询打成冲突失败。

limit 分别截断 forms / evidenceSlots；本切片不提供跨 Task 深分页 cursor（cursor 必须为空）。

## 4. 契约

Core OpenAPI **0.59.0**。无新 Flyway。

## 5. 明确未实现

其余 section、FormSubmission 列表、EvidenceItem/Revision 明细、队列/SavedView、Portal、
区块持久化投影。
