---
title: M33 表单资产发布基础验收
version: 0.1.0
status: Implemented
---

# M33 表单资产发布基础验收

| ID | 优先级 | 验收点 | 自动化证据 |
|---|---|---|---|
| M33-CFG-001 | P0 | 合法 FORM 按精确版本发布 | `ConfigurationPublicationPostgresIT` |
| M33-CFG-002 | P0 | 非法 Schema、未知版本、身份不一致在落库前拒绝 | `ConfigurationPublicationPostgresIT` |
| M33-CFG-003 | P0 | 勘测与安装 FormVersion 同 Bundle 锁定 | `ConfigurationPublicationPostgresIT` |
| M33-CFG-004 | P0 | 多版本误用单例读取时失败关闭 | `ConfigurationPublicationPostgresIT` |
| M33-CFG-005 | P1 | 架构 Schema 与运行时资源无漂移 | `ConfigurationSchemaDriftTest` |

本矩阵不替代 M3 FORM-001～FORM-005；表达式与 FormSubmission 完成后才能关闭这些场景。
