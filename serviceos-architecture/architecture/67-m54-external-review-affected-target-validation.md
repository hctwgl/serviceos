---
title: M54 车企回执影响对象权威校验
version: 1.0.0
status: Implemented
---

# M54 车企回执影响对象权威校验

## 1. 决策基线

本切片实现 [资料、审核与整改闭环设计](10-evidence-review-correction.md) 中“回执精确引用受影响
资料版本”的既有 Accepted 语义，并关闭 M49 明确记录的 `affectedTargets` 强校验缺口。M54 不创建
CLIENT origin ReviewCase，不实现完整 OEM Connector，也不改变 M53 EvidenceSlot 重解析主线。

## 2. 已实现范围

1. `affectedTargets` 从任意 JSON object 收紧为机器契约 `ExternalReviewAffectedTarget`；
2. M54 只接受 `targetType=EVIDENCE_REVISION`，并要求同时提交 `evidenceSlotId`、
   `evidenceItemId` 和 `evidenceRevisionId`；
3. 服务端按 tenant 读取 ReviewCase 绑定的不可变 EvidenceSetSnapshot，三个标识必须共同命中
   同一个 SnapshotMember；
4. 跨 Snapshot、跨租户、未知版本、错配三元组、重复 revision 和超过 100 项全部失败关闭；
5. 精确目标进入幂等请求摘要与不可变 ExternalReviewReceipt，读取时仍返回同一强类型结构；
6. 既有回执、EXTERNAL ReviewDecision、驳回客服协调 Task、审计与 Outbox 保持同事务。

## 3. 契约与兼容处理

OpenAPI 提升到 **0.27.0**，`affectedTargets.items` 禁止额外字段并使用四个必填字段。该变更会让
0.26.0 中提交任意 object 的客户端不再兼容，项目负责人已于 **2026-07-15** 明确批准将其作为
版本化破坏性契约变更直接发布。ServiceOS 是新系统，因此只保留唯一权威结构，不保留旧字段、
宽松解析或兼容分支，也不修改兼容门禁来掩盖破坏性事实。

本切片不新增事件版本：既有 `evidence.external-review-receipt-recorded@v1` 只携带回执标识与摘要，
不携带目标详情或文件 URL。

## 4. 数据与事务

M54 不新增 Flyway。权威关系已由以下不可变数据提供：

- `evd_review_case.evidence_set_snapshot_id` 冻结审核范围；
- `evd_evidence_set_member` 冻结 slot/item/revision 三元组；
- `evd_external_review_receipt.affected_targets` 保存校验后的精确目标，回执表禁止 UPDATE/DELETE。

目标校验发生在任何 ReviewCase 状态迁移、ReviewDecision、协调 Task、审计或 Outbox 写入之前。
后续任一写入失败时，Spring 事务整体回滚，不留下部分决定或伪成功。

## 5. 授权与失败语义

- 仍仅允许具备 `evidence.recordExternalReceipt` 的 SERVICE 主体；
- tenant 来自受信 principal，不接受请求体 tenant；
- Snapshot 在当前 tenant 下不存在属于内部不变量破坏，失败关闭；
- 目标不属于权威 Snapshot 返回 `VALIDATION_FAILED`，不猜测或自动改写目标；
- 空目标仍表示回执未声明单项影响范围；M54 只保证“声明的每一项目标都权威有效”，不替客服
  自动推断整改对象。

## 6. 明确未实现

- 完整车企 Connector 验签与标准化入站表；
- CLIENT origin ReviewCase 自动创建；
- callbackBatchRef / mappingVersionId 的外部权威登记与批次校验；
- 字段、FormSubmission、报告等其他 targetType；
- 外部驳回到 CorrectionCase 的自动映射；
- OCR/CV、GPS 权威距离、多候选人评分与自动 claim。

## 7. 自动化证据

证据见 [M54 验收矩阵](../testing/51-m54-external-review-affected-target-validation-acceptance.md)。
核心入口为 `ReviewCasePostgresIT`、`ExternalReviewReceiptControllerSecurityTest`、OpenAPI 契约与
TypeScript 客户端生成测试、`ArchitectureTest` 及全仓 `verify`。
