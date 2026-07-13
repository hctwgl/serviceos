---
title: ServiceOS 领域语言与边界索引
version: 0.1.0
status: Proposed
---

# ServiceOS 领域语言与边界

本目录冻结 ServiceOS 的一级领域语言、限界上下文和聚合边界。它不是现有架构文档的替代品，而是跨产品、研发、测试、数据和集成团队共同使用的命名权威。

## 阅读顺序

1. [统一领域语言](00-ubiquitous-language.md)
2. [限界上下文地图](01-context-map.md)
3. [聚合目录](02-aggregate-catalog.md)
4. [ADR-020：核心领域命名与边界稳定策略](../decisions/ADR-020-canonical-domain-language.md)

## 权威关系

发生概念冲突时，按以下顺序裁决：

```text
Accepted ADR
> 本目录中 Accepted 的领域语言与边界文档
> architecture/ 下的领域与能力文档
> API、数据模型与产品规格
> 代码命名与注释
```

如果代码、数据库、OpenAPI 或页面术语与本目录冲突，必须先修正文档或通过新 ADR 变更领域语言，禁止在实现层自行创造同义词。

## 本批基线的关键结论

- `WorkOrder`（工单）保持为核心履约实例的统一名称，不改名为 `Fulfillment`；
- `Fulfillment` 只用于“履约行为、履约事实和履约结果”等语义，不作为工单聚合的替代名称；
- `Task` 是执行和待办外壳，流程运行时通过任务推进，但任务不吞并其引用的业务聚合；
- `Evidence` 统一表示照片、视频、文件、签字、OCR/GPS 等履约证据；
- `ReviewCase` 保持为审核案例的领域名称，自动校验能力称为 Validation，但不以 Validation 替换人工审核与车企审核的业务概念；
- `ConfigurationBundle` 是工单创建时锁定的精确配置资产集合；
- 聚合之间只通过稳定标识和不可变快照引用，不直接持有另一个聚合对象图。

## 变更纪律

以下变更必须新增 ADR：

- 一级领域名重命名；
- 聚合根调整；
- 限界上下文拆分或合并；
- 事实源所有权迁移；
- 已发布事件语义改变；
- 跨上下文同步事务边界扩大。
