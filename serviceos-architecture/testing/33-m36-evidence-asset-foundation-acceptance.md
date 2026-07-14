---
title: M36 Evidence 资产发布基础验收
version: 0.1.0
status: Implemented
---

# M36 Evidence 资产发布基础验收

| ID | 优先级 | 验收点 | 自动化证据 |
|---|---|---|---|
| M36-CFG-001 | P0 | 合法 EVIDENCE 按精确版本发布且身份一致 | `ConfigurationPublicationPostgresIT` |
| M36-CFG-002 | P0 | 非法 Schema、未知版本和身份漂移在落库前拒绝 | `ConfigurationPublicationPostgresIT` |
| M36-CFG-003 | P0 | 重复 evidenceKey 与反向数量区间拒绝且无部分持久化 | `ConfigurationPublicationPostgresIT` |
| M36-CFG-004 | P0 | 多个 EvidenceTemplate 可被同一 Bundle 精确锁定并稳定解析 | `ConfigurationPublicationPostgresIT` |
| M36-CFG-005 | P0 | 多版本误用单例读取时失败关闭 | `ConfigurationPublicationPostgresIT` |
| M36-CFG-006 | P1 | 架构 Schema 与运行时资源逐字节无漂移 | `ConfigurationSchemaDriftTest` |

本矩阵只证明发布期配置基础，不替代 M3 EVD-001～EVD-009。条件表达式、资料槽位、上传、
Revision、Snapshot、审核和整改仍未实现。
