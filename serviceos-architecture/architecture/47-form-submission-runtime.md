---
title: M34 不可变表单提交运行时
version: 0.1.0
status: Implemented
---

# M34 不可变表单提交运行时

## 1. 实现边界

本切片实现 E3-03 中不依赖未批准表达式语义的提交内核：

- Task 已冻结的 `configurationBundleId + manifestDigest + formRef` 精确解析唯一 FormVersion；
- 客户端 `formVersionId` 必须与该版本一致，不读取最新配置；
- 仅当前责任人的 `RUNNING HUMAN` Task 可提交，ACTIVE TaskExecutionGuard 期间失败关闭；
- `form.submit` 按 Task Project Scope 实时授权，tenant、actor、submittedBy 均取自受信身份；
- `FormSubmission` 和 `SubmissionValidation` 只追加，数据库触发器禁止更新或删除；
- 同一 Task/FormVersion 的 submissionVersion 在锁定 Task 行后单调分配；
- 固定 required、基础数据类型、未知字段和永久 HTTP(S) 文件地址由服务端验证；
- `INVALID` 作为不可变提交事实保留，但不能作为完成 Task 的有效输入；
- 提交、验证、审计、`form.submitted@v1`、幂等结果在同一 PostgreSQL 事务提交；
- `POST /tasks/{taskId}/form-submissions` 与 `GET /form-submissions/{id}` 已纳入 OpenAPI 0.10.0。

## 2. 失败关闭边界

ADR-018 和动态表单字段引擎仍为 Proposed。本切片不定义或猜测：

- `SERVICEOS_EXPR_V1` 表达式求值；
- validator `parameters` 的类型和执行语义；
- 条件显隐、条件必填、跨字段规则和计算字段；
- prefillSnapshot 获取、权威版本冲突与人工确认策略。

锁定 FormVersion 含上述运行时要求，或客户端携带 `prefillVersion` 时，服务端返回
`FORM_RUNTIME_UNSUPPORTED`（HTTP 422），且不创建幂等抢占、提交、审计或 Outbox 污染。

## 3. 数据与事务

V036 创建：

- `frm_form_submission`：不可变值文档、精确 FormVersion、版本、摘要、验证状态和提交身份；
- `frm_submission_validation`：验证器版本、输入摘要、错误/警告和执行时间；
- `frm_form_command_result`：首次成功命令的冻结资源引用。

值文档在摘要计算、首次响应和幂等回放时使用相同规范化 JSON。事件只携带版本、摘要、状态和
错误数量，不复制可能包含个人信息的原始表单值。

## 4. 尚未关闭的 M3 范围

M34 仍不宣称 M3 FORM-001～FORM-005 完成。Task 完成引用门禁已由
[M35 表单任务完成引用门禁](48-form-task-completion-gate.md)补齐；后续至少需要：

- ADR-018 获批后的共享表达式规范、静态类型、样本回放和资源限制；
- FormDraft、prefillSnapshot 与离线冲突；
- supersede/整改链和审核引用；
- 受控领域映射与 FulfillmentFact 提取。
