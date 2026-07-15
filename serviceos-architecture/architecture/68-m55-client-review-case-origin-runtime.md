---
title: M55 CLIENT ReviewCase 来源与回执批次门禁
version: 1.0.0
status: Implemented
---

# M55 CLIENT ReviewCase 来源与回执批次门禁

## 1. 决策基线

本切片实现 [资料、审核与整改闭环设计](10-evidence-review-correction.md) 中“总部审核通过并回传后，
创建 `origin=CLIENT` ReviewCase”的 Accepted 语义。M55 连接 M44～M54 的内部审核、不可变 Snapshot
和外部回执运行时，但不把适配层声明冒充成完整 Connector 或 OutboundDelivery 权威。

## 2. 已实现范围

1. ReviewCase 显式区分 `origin=INTERNAL|CLIENT`；既有内部创建命令只创建 INTERNAL；
2. `POST /internal/client-review-cases` 仅接受 SERVICE 主体和
   `evidence.createClientReviewCase` capability；
3. sourceReviewCase 必须属于同 tenant/project、`origin=INTERNAL`，且已
   `APPROVED` 或 `FORCE_APPROVED`；
4. CLIENT Case 冻结源 Case 的 task、project、EvidenceSetSnapshot 和 contentDigest，并要求适配层
   显式提交 `externalSubmissionRef`、`callbackBatchRef`、`mappingVersionId`、`policyVersion`；
5. 同一 tenant 下 externalSubmissionRef 唯一；同一 Snapshot、origin 至多一个 OPEN Case；
6. INTERNAL decide/force-approve/reopen 不接受 CLIENT Case；CLIENT Case 只能由外部回执裁决；
7. 外部回执只接受 OPEN CLIENT Case，且 callbackBatchRef、mappingVersionId 必须精确匹配冻结值；
8. CLIENT Case、幂等结果、审计和 `evidence.client-review-case-created@v1` Outbox 同事务提交。

## 3. 契约与数据

- OpenAPI：**0.28.0**，新增内部创建端点和 CLIENT lineage 字段；
- 事件：新增不可变 `evidence.client-review-case-created@v1`，不修改既有事件版本；
- Flyway：**V054**，为 `evd_review_case` 增加 origin、source、外部提交、批次和映射版本字段；
- PostgreSQL 约束保证 INTERNAL 不携带外部 lineage，CLIENT 必须携带完整 lineage；
- ReviewCase identity trigger 覆盖新增字段，创建后禁止改写批次、映射版本或来源。

V054 的 `origin=INTERNAL` 默认值只服务当前新系统结构迁移，迁移后立即删除默认值；运行时所有
INSERT 都必须显式写 origin，不留下长期默认猜测。

## 4. 事务、授权与失败语义

创建 CLIENT Case 的顺序为：校验 SERVICE 主体 → tenant 内读取 source → project capability 授权 →
校验来源状态与显式引用 → 幂等判定 → 唯一性检查 → 写 Case/命令结果/审计/Outbox。任一步失败均
整体回滚，不生成无来源 CLIENT Case。

回执先核对 CLIENT origin、冻结批次、映射版本和 M54 SnapshotMember，再进入幂等与状态迁移。
同一成功命令或同一 inboundEnvelopeId 在 Case 已结束后仍返回首次不可变回执；不同内容不能借
重放绕过来源门禁。

## 5. 明确未实现

- 车企专属 Connector 验签、时间窗、防重放和原文留存；
- 通用 InboundEnvelope / CanonicalMessage / OutboundDelivery 运行时；
- callbackBatchRef、mappingVersionId 对 integration 域登记记录的跨模块权威校验；
- 自动监听交付成功事件创建 CLIENT Case；当前由已认证适配层显式登记；
- 字段、FormSubmission、报告等其他外部审核 targetType；
- 外部驳回到 CorrectionCase 的自动映射、Portal 和二级审批/MFA。

## 6. 自动化证据

证据见 [M55 验收矩阵](../testing/52-m55-client-review-case-origin-acceptance.md)。核心入口为
`ReviewCasePostgresIT`、`ReviewCaseControllerSecurityTest`、OpenAPI/事件契约测试、客户端生成、
兼容门禁、Flyway V054 和 `ArchitectureTest`。
