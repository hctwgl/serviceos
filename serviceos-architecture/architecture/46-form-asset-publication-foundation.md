---
title: M33 表单资产发布基础
version: 0.1.0
status: Implemented
---

# M33 表单资产发布基础

本切片实现动态表单与配置资产元模型的发布期前置门禁，尚不宣称 FormSubmission 或
`SERVICEOS_EXPR_V1` 已实现。

- FORM 发布前按 Draft 2020-12 Schema 校验，未知 schemaVersion 失败关闭；
- `assetKey/version` 必须与定义内 `formKey/version` 一致；
- 运行时 Schema 与架构事实源由逐字节漂移测试约束；
- Bundle 可锁定多个同类型资产，使勘测与安装 FormVersion 共存；
- 单例读取仍要求恰好一个版本，多版本时明确报告歧义；
- V033 使用 `bundle + assetVersion` 标识 BundleItem，保留租户、摘要外键和不可变触发器。
- Workflow 解析首任务和后续任务时保留节点 `formRef`；
- V034 将该引用固化到 `tsk_task.form_ref`，Task 公开只读上下文同步暴露此快照；
- `formRef` 来自工单锁定的 WorkflowVersion，后续表单解析不得改读最新 Workflow 或最新 FORM。
- V035 同步冻结 Task 的 `configurationBundleId + manifestDigest`，并对存量 Workflow Task 从工单事实回填；
- `forms` 模块只依赖 `task.api + configuration.api` 完成精确解析，不跨模块读取 WorkOrder/Workflow 表；
- `GET /api/v1/tasks/{taskId}/forms` 按 `form.read` 和 Task Project Scope 实时授权，返回发布定义和摘要；
- Task 无 `formRef` 返回空数组，缺失或歧义匹配失败关闭，不回退到最新 FormVersion。

ADR-018 仍为 Proposed，因此本切片不猜测表达式语义。规则静态类型、样本回放、草稿、不可变提交、
预填冲突和服务端校验仍待后续实现；FORM 已发布或可查询都不等于表达式可执行。
