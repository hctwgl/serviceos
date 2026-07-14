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

ADR-018 仍为 Proposed，因此本切片不猜测表达式语义。规则静态类型、样本回放、Task 表单解析、
草稿、不可变提交、预填冲突和服务端校验仍待后续实现；FORM 已发布不等于可执行。
