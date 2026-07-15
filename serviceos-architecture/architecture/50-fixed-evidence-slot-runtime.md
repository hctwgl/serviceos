---
title: M37 固定 EvidenceSlot 运行时
version: 0.1.0
status: Implemented
---

# M37 固定 EvidenceSlot 运行时

M37 实现 [资料、审核与整改闭环设计](10-evidence-review-correction.md) 中不依赖表达式引擎的
EvidenceSlot 首个运行时切片。工作流 Task 创建后，evidence 模块从 Task 已冻结的
ConfigurationBundle 和 Stage 解析固定资料要求，保存不可变解析事实，并提供租户、项目范围内的只读查询。

本里程碑实现既有领域设计，没有改变模块职责或接受新的表达式语义，因此不新增 ADR。

## 1. Task 冻结上下文

工作流创建 Task 时新增冻结 `stageCode`。它与现有的 `stageInstanceId`、Bundle ID 和 Bundle Digest
一起持久化，后续推进创建的新 Task 同样冻结其权威 Stage。迁移 V037 从已有 StageInstance 回填在途
工作流 Task，并以约束保证工作流 Task 的冻结上下文完整；非工作流 Task 不伪造 Stage。

evidence 不读取 workflow 内部表，也不从可变流程定义重新推断 Stage，而是通过 task 公开 API 获取
`TaskFulfillmentContext`。因此运行时解析不会跨模块访问 Repository，也不会随新配置发布而漂移。

## 2. 固定槽位解析

`task.created@v1` 的本地可靠消费者执行以下步骤：

1. 通过 Inbox 锁定 eventId，校验租户、Task 和 payload 归属；
2. 获取 Task 冻结上下文和 Bundle 中所有 EVIDENCE 资产；
3. 只选择 `EvidenceTemplate.stage == task.stageCode` 的模板；
4. 为不含 `requiredWhen` 的每个 requirement 创建一个不可变 EvidenceSlot；
5. 在同一事务保存解析批次、槽位、审计、`evidence.slots-resolved@v1` Outbox 和 Inbox 完成事实。

固定数量规则为：显式 `capture.minCount/maxCount` 优先；未给 minCount 时 required 默认 1、optional
默认 0；未给 maxCount 表示未设置上限。发布门禁禁止 `required=true` 与显式 `minCount=0` 的矛盾配置。
初始投影在 minCount 大于 0 时为 `MISSING`，否则为 `SATISFIED`；它只是当前数量投影，不替代后续
EvidenceItem/Revision 和机器校验事实。

即使当前 Stage 没有匹配模板，也保存 slotCount=0 的权威解析批次。这样查询空数组与消费者尚未处理
可以严格区分；后者返回冲突，不伪造“没有资料要求”。重复事件由 Inbox 和数据库唯一约束共同去重。

## 3. 失败关闭边界

M37 实施时 ADR-018 尚未接受，因此不解释 `requiredWhen`。只要当前 Stage 的任一 requirement 带条件表达式，
整个解析事务失败并回滚，不能静默省略条件槽位，也不能把它当作固定必填。缺失 Stage、Stage 格式非法、
Bundle/Digest 不一致或模板解析失败同样失败关闭。

解析事实保存 resolverVersion、源事件 ID/摘要、模板版本/摘要、requirement 原文/摘要、条件输入摘要和
命中解释，使后续引入获批表达式引擎时可以审计和迁移。M37 的固定解析不支持字段变化后的重新解析。

## 4. 数据、授权与契约

- V038 创建 `evd_task_evidence_resolution` 与 `evd_evidence_slot`，通过租户外键、唯一约束和不可变触发器
  保证 Task/Bundle/模板版本归属和解析事实不可覆盖；
- 普通持久化通过 evidence Repository 端口与 MyBatis XML 适配器完成；
- `GET /api/v1/tasks/{taskId}/evidence-slots` 要求 `evidence.read` 和实时 Project Scope，且不信任客户端
  tenant；
- OpenAPI 0.12.0 描述只读槽位投影；事件 JSON Schema 不复制敏感业务字段或资料正文；
- evidence 只依赖 task/configuration/authorization/audit/reliability 的公开 API 或 SPI。

## 5. 明确未实现

M37 只覆盖 EVD-001 中“固定槽位始终存在”的运行时基础，不宣称 EVD-001 条件切换或 EVD-002～009
完成。后续至少需要：

1. 条件初次解析已由 M52 实现；字段版本冻结和条件变化后的可审计重解析仍未实现；
2. M38 已对接安全文件 Begin/Finalize/隔离/扫描链路，创建 EvidenceItem 与不可变 EvidenceRevision；
3. OCR/业务机器校验、代办上传、作废命令和 Task 完成完整性门禁；
4. EvidenceSetSnapshot、审核决定、整改与多轮补传闭环。
