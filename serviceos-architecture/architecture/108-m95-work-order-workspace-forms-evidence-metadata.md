---
title: M95 工单工作区表单提交与资料项安全元数据
status: Implemented
milestone: M95
---

# M95 工单工作区表单提交与资料项安全元数据

## 1. 目标

补齐 M89 `FORMS_EVIDENCE` 的运行时进度摘要，增加 `formSubmissions` 与
`evidenceItems`，但不扩散业务值、文件和采集明细。

## 2. 接受范围

- Forms 使用独立 `FormSubmissionQueryService`，SQL 只读取提交元数据与校验计数；
- Evidence 使用独立 `EvidenceItemQueryService`，SQL 只聚合 Revision 数量与最新状态；
- 分别复用 `form.read` / `evidence.read` 与实时 Project Scope；
- 未完成可靠 EvidenceSlot 解析的 Task 跳过 slots/items，不伪造“无需资料”；
- 四个子集独立排序/limit；任一有数据为 AVAILABLE，无数据但有缺权为 UNAVAILABLE，
  全部有权且空为 EMPTY。

## 3. 敏感字段边界

- FormSubmission 不返回 values、校验消息、prefillVersion、submittedBy；
- EvidenceItem 不返回 Revision 图、fileObjectId、mimeType、sizeBytes、captureMetadata、
  validation 明细或 createdBy。

## 4. 契约

Core OpenAPI **0.65.0**。无新 Flyway，保持 V080 / 82 migrations。

## 5. 明确未实现

表单值与资料版本详情页、跨 Task cursor、FACTS_CALCULATIONS、customer/location、
队列/SavedView、Portal。
