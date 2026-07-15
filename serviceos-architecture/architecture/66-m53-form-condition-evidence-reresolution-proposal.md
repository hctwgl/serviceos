---
title: M53 表单条件与 EvidenceSlot 重解析运行时
version: 1.0.0
status: Implemented
---

# M53 表单条件与 EvidenceSlot 重解析运行时

## 1. 决策基线

本文件把 [ADR-022](../decisions/ADR-022-conditional-fact-version-and-evidence-reresolution.md)
转换为已实现切片。ADR-022 已于 2026-07-15 接受；M53 严格采用“最新 VALIDATED 表单事实、
true→false 人工处置、false→true 新槽位世代”三项语义。

## 2. 已实现范围

1. FORM 发布期编译 `requiredWhen`、`visibleWhen` 和 validation rule 中的表单字段引用；
2. SubmitForm 使用相同 `SERVICEOS_EXPR_V1` 规范执行条件必填和已批准布尔断言；
3. `VALIDATED form.submitted@v1` 触发 Evidence 只追加重解析，`INVALID` 只完成消费审计；
4. 新增单调 resolution generation 与活动槽位 membership；
5. 处理重复、乱序、并发 submission，不回退权威事实版本；
6. true→false 的已提交槽位进入 `REVIEW_REQUIRED`，并由明确处置命令关闭；
7. 查询、Snapshot 创建和 Task 完成门禁统一读取最新 generation。

计算字段、任意函数、草稿/预填冲突、OCR/CV、客户端表达式实现和离线合并不属于本切片。

## 3. 模块与事务设计

### configuration

- 扩展表达式 AST/静态验证，支持精确 `formValues["fieldKey"]`；
- 根据锁定 FORM 定义建立字段类型表，不允许任意 JSON 路径；
- EVIDENCE requiredWhen 引用表单字段时，发布 Bundle 前验证对应 FORM 资产与依赖闭包。

### forms

- 提供按 tenant + submissionId 查询精确 `VALIDATED` submission facts 的公开只读端口；
- 服务端条件验证与 M52/M53 共享同一解释器，不复制 JavaScript 语义；
- 完整 values 不进入事件、日志、Trace 或审计动态文本。

### evidence

- 新 consumer 处理 `form.submitted@v1`，使用 Inbox 去重；
- 在 PostgreSQL 事务中串行化 Task resolution stream，追加 generation、membership、审计和 Outbox；
- `task.created` 与 `form.submitted` 共用同一个 generation 应用服务，禁止两套解析路径；
- 最新活动槽位查询、Snapshot 和完成门禁通过 Repository 端口读取同一投影。

### review/files

- true→false 本身不调用 files invalidate；
- 已提交槽位的保留/作废由显式处置命令完成，作废仍复用 M42/M46 的授权与文件联动；
- 历史 ReviewCase 与 Snapshot 不改写，新 Review/Disposition 精确引用条件变化 generation。

## 4. 数据迁移

连续 Flyway `V053__create_evidence_reresolution_generation.sql` 已实现：

- 把现有 M37～M52 resolution 解释为 generation 1、事实 revision 0；
- 解除一个 Task 只能有一条 resolution 的约束，改为 `tenant + task + generation_no` 唯一；
- 增加 `condition_fact_type/ref/revision/digest` 与前一 generation 引用；
- 增加不可变 generation membership，保存每个 requirement 的 decision、activeSlotId、transition、
  explanation 和 disposition；
- 为 false→true 新槽位增加世代/前驱引用，保留历史 occurrence 的唯一性；
- 使用触发器禁止修改或删除 generation、membership 和 slot lineage；
- 不给生产运行时留下默认值、兼容双写或旧查询回退。

迁移必须从空库和真实 V052 数据基线分别验证；回填后立即删除临时默认值。

## 5. API 与事件

- `GET /tasks/{taskId}/evidence-slots` 返回最新 generationId、factRef、活动状态和待处置摘要；
- 新增 `POST /tasks/{taskId}/evidence-slots/{slotId}:resolve-condition-change`，必须携带
  `expectedResolutionId`、decision、reasonCode 与 reviewRef；
- 新增 `evidence.slots-reresolved@v1`，只携带 generation、事实摘要和 transition 计数；
- 事件不得携带完整表单值或资料 URL；
- 破坏现有响应字段时必须提升 OpenAPI 版本并通过客户端可重复生成门禁。

## 6. 明确失败语义

- 表单字段缺失、类型不符、版本不匹配：整次 submission 验证或重解析失败关闭；
- 重复事件：返回首次 Inbox 结果，不追加 generation；
- 旧事实迟到：记录 `STALE_NO_CHANGE`，不回退当前 generation；
- 并发新 submission：按 submissionVersion 串行应用，较低版本不得覆盖较高版本；
- true→false 且已有资料：标记待处置并阻断 Snapshot/CompleteTask，不自动成功；
- 任一审计、Outbox 或 Inbox 写入失败：整个重解析事务回滚。

## 7. 明确未实现

- 任意函数、计算字段、脚本与决策表；
- 草稿/预填冲突、客户端表达式执行与离线合并；
- OCR/CV、GPS 权威距离；
- 自动替用户决定 KEEP/INVALIDATE；
- 历史资料物理删除与长期归档策略。

## 8. 自动化证据

证据见 [M53 验收矩阵](../testing/50-m53-form-condition-evidence-reresolution-acceptance-proposal.md)。
核心入口为 `ConfigurationPublicationPostgresIT`、`FormValueValidatorTest`、
`EvidenceSlotPostgresIT`、`EvidenceConditionDispositionControllerSecurityTest`、契约测试、
Spring Modulith/ArchUnit 与全仓 `./mvnw clean verify`。
