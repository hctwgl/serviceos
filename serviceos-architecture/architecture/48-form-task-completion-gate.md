---
title: M35 表单任务完成引用门禁
version: 0.1.0
status: Implemented
---

# M35 表单任务完成引用门禁

## 1. 实现边界

M35 实现动态表单规范中“提交与完成分离”的第二个动作：

- `SubmitForm` 仍只创建不可变提交和权威验证快照，不隐式完成 Task；
- `formRef` 非空的 HUMAN Task 完成时，`resultRef` 必须使用
  `form-submission://{submissionId}`；
- `resultDigest` 必须等于该 submission 的规范化值文档 `contentDigest`；
- submission 必须属于同一 tenant、Task、Project、formKey 和 Task 冻结 Bundle 中的精确
  FormVersion，且 `validationStatus=VALIDATED`；
- `INVALID`、跨 Task、跨版本、错误摘要、伪造或不存在的引用均以
  `FORM_SUBMISSION_NOT_VALIDATED` 失败关闭；
- 非表单 Task 不改变既有通用结果引用语义。

`VALIDATED` 只代表提交时结构和已批准基础规则通过，不等于资料审核或车企审核通过。

## 2. 模块边界

task 模块在公开 API 定义 `HumanTaskCompletionValidator` 扩展端口，forms 模块实现表单结果校验。
task 不依赖 forms、不访问 `frm_*` 表，也不把表单 Repository 暴露给其他模块。后续资料、审核等结果
可以使用同一模式增加所属模块校验器。

## 3. 事务与失败语义

校验发生在 `task.human.complete` 幂等抢占后、Task 条件更新前，并处于原有完成事务内：

- 校验失败会回滚本次幂等抢占，不更新 Task、不关闭 Assignment、不写审计或 Outbox；
- 校验成功后仍由 task 内核统一检查当前负责人、RUNNING、expectedVersion、ACTIVE guard 和
  workflow node；
- Task 状态、Assignment 关闭、审计、`task.completed@v1` 和冻结幂等响应保持同事务提交；
- 幂等重放返回首次冻结结果，不因后续新 submission 改写历史完成事实。

## 4. 未实现范围

M35 不实现 ADR-018 表达式、草稿/预填冲突、submission supersede/整改审核、EvidenceSet 完成条件、
审核通过门禁或 FulfillmentFact 提取。这些能力仍按 M3 后续切片交付。
