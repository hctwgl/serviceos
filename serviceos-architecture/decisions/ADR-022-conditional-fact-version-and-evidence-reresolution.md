---
title: ADR-022：条件事实版本与资料槽位重解析
version: 0.1.0
status: Proposed
owner: Product Architecture
reviewers:
  - Engineering Architecture
  - Evidence Domain Owner
related_adrs:
  - decisions/ADR-002-versioned-configuration-bundle.md
  - decisions/ADR-008-immutable-evidence-and-review-decisions.md
  - decisions/ADR-014-local-transaction-outbox-inbox.md
  - decisions/ADR-018-configuration-schema-and-expression-runtime.md
  - decisions/ADR-021-domain-event-publication-versioning.md
---

# ADR-022：条件事实版本与资料槽位重解析

## 1. 状态与待确认项

本 ADR 是 M53 的**可执行提案**，尚未接受。它不授权修改生产状态机，也不代表 M53 已实施。
开始编码前必须由用户或产品/架构负责人明确确认以下三项：

1. 条件事实是否以同一 Task、锁定 FormVersion 的最新 `VALIDATED FormSubmission` 为权威版本；
2. 已提交资料在条件由 true 变为 false 后，是否进入 `REVIEW_REQUIRED`，且不得自动删除或作废；
3. 条件由 false 再变为 true 时，是否创建新的槽位世代，而不是恢复旧槽位。

推荐接受上述三项。若任一项不同，必须先修改本 ADR、实现文档和验收矩阵，再编码。

## 2. 上下文

M52 已在 `task.created@v1` 时根据锁定配置和 WorkOrder/Region/Task 权威事实完成条件资料的
初次解析，并冻结输入摘要与命中解释。M34 已提供只追加的 `FormSubmission`，但表单条件仍被
失败关闭。当前数据模型还存在以下边界：

- `evd_task_evidence_resolution` 对一个 Task 只允许一条记录；
- `EvidenceSlot` 定义不可变，且一个 requirement 只允许一个默认 occurrence；
- `EvidenceItem`、`EvidenceRevision`、`EvidenceSetSnapshot` 和 Review 决定均引用精确历史版本；
- `form.submitted@v1` 只携带标识、版本、摘要和校验状态，不暴露可能含个人信息的完整值文档；
- Task 与 FormSubmission Outbox 属于不同聚合，消费者不能假设跨聚合全局顺序。

因此，重解析不能通过更新或删除旧槽位实现，也不能把完整表单值复制进事件。

## 3. 推荐决策

### 3.1 权威条件事实

每次解析使用两个明确输入层：

1. Task 锁定配置对应的 WorkOrder、Region、Task 基础事实；
2. 同一 tenant、project、Task、锁定 FormVersion 的精确 `VALIDATED FormSubmission` 值文档。

表单事实引用至少冻结：`submissionId`、`submissionVersion`、`formVersionId`、`contentDigest`。
`INVALID` submission 是可审计提交事实，但不能替换条件事实版本，也不能触发槽位变更。
缺失字段、类型漂移、错误 FormVersion 或摘要不一致必须失败关闭，不能把缺失值解释为 false。

表达式通过 `formValues["<fieldKey>"]` 访问表单值。方括号内必须是字符串字面量；发布期必须
验证 fieldKey 存在、类型可比较且属于当前 FormVersion。这样可精确支持包含点号或连字符的
稳定 fieldKey，不把任意 JSON 路径开放给配置。

### 3.2 单调事实序号与乱序事件

Evidence 为每个 Task 维护单调 `conditionFactRevision`：

- `0` 表示 `task.created@v1` 的基础事实；
- 正整数使用已验证 submission 的 `submissionVersion`；
- 同一 Task、事实来源和事实版本唯一。

所有解析事务先锁定 Task 的 Evidence resolution stream，再比较当前最大事实序号：

- 新事实序号大于当前值时追加新 generation；
- 重复事实由 Inbox 幂等重放；
- 较旧事实记录为 `STALE_NO_CHANGE`，不得把槽位回退到旧条件；
- 若 `form.submitted` 先于迟到的 `task.created` 被消费，submission generation 成为首个权威
  generation，后续 revision 0 只完成 Inbox 和乱序审计，不产生倒退 generation。

该规则不依赖 Worker 调度顺序，也不要求跨聚合 exactly-once。

### 3.3 只追加 resolution generation

一个 Task 可以拥有多个不可变 resolution generation。每代冻结：

- generationNo、条件事实引用与摘要；
- 锁定 Bundle、FormVersion、EvidenceVersion 和解释器版本；
- 每个 requirement 的 true/false 决策、绑定值和命中解释；
- 本代活动槽位集合、沿用/新建/停用关系；
- sourceEventId、correlationId、resolvedAt。

`GET /tasks/{taskId}/evidence-slots` 和 Task 完成门禁只读取最新 generation 的活动投影；历史
generation、旧槽位、旧 Snapshot 和旧 Review 仍可按精确引用读取，不被新一代改写。

### 3.4 槽位世代与已提交资料

推荐采用以下确定性规则：

| 前一代 | 新一代 | 没有 EvidenceItem | 已有 EvidenceItem/Revision |
|---|---|---|---|
| false | false | 不创建槽位 | 不适用 |
| false | true | 创建新槽位世代 | 创建新槽位世代，不恢复历史槽位 |
| true | true | 沿用当前活动槽位 | 沿用当前活动槽位及其资料 |
| true | false | 停用当前槽位 | 保留全部历史并标记 `REVIEW_REQUIRED` |

`REVIEW_REQUIRED` 时不得自动删除、自动作废 StoredFile、自动使历史 Snapshot 失效，也不得完成
Task。后续必须通过明确 Review/Disposition 命令决定保留或作废；该命令、权限和审核范围要在
本 ADR 接受后写入 M53 实现范围。普通重解析消费者无权替用户作出该决定。

### 3.5 表单事件与模块边界

forms 仍拥有完整值文档。evidence 消费 `form.submitted@v1` 后，只能通过 forms 公开的只读端口
按 tenant、submissionId 查询精确 `VALIDATED` submission；不得访问 `frm_*` 表。事件继续只
携带非敏感摘要，不为重解析扩散完整表单值。

重解析事务应原子提交：resolution generation、generation membership/transition、审计、
Evidence Outbox 和 Inbox 完成。跨模块只读发生在写入前；不得在持有数据库行锁时执行网络调用。

## 4. 拒绝的方案

### 4.1 原地更新或删除 EvidenceSlot

拒绝。它会破坏已有 EvidenceItem、Snapshot、Review、审计和外部回执的精确版本引用。

### 4.2 只在 CompleteTask 时临时重新计算

拒绝。Portal 无法提前展示权威资料要求，且无法保存条件变化、乱序和人工处置历史。

### 4.3 把完整 FormSubmission 值放入事件

拒绝。会扩大个人信息暴露面，使事件契约与表单 Schema 强耦合，并复制 forms 的权威事实。

### 4.4 条件 false 时自动作废已上传资料

拒绝作为默认规则。条件变化不等于资料虚假或不再具有审计价值，自动作废还会联动安全文件并
产生不可逆业务副作用。

## 5. 后果

正面：重解析可重放、可审计、抗乱序，不破坏历史资料与审核链；Portal 和完成门禁共享同一
最新 generation 事实。

负面：需要增加 generation/membership/transition 数据结构、Review disposition 和查询投影；
高频表单提交可能产生较多只追加记录，需要索引、归档和并发测试。

## 6. 接受后的工程门禁

- PostgreSQL Testcontainers 证明重复、乱序和并发 submission 只形成单调 generation；
- 模块测试证明 evidence 不访问 forms 内部包或表；
- 发布期验证 `formValues[...]` 字段存在与类型，运行时缺值失败关闭；
- 已提交槽位 true→false 不产生隐式文件作废，且完成门禁阻断到明确处置；
- 历史 Snapshot、Review 和外部回执在重解析后保持逐字节不变；
- Event Schema、OpenAPI、Flyway、追踪矩阵和实施状态在同一里程碑同步。
