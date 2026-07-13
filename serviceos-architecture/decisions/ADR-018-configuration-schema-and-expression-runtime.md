---
title: ADR-018 配置 Schema 与表达式运行时
version: 0.1.0
status: Proposed
owner: Architecture Board
reviewers:
  - Product Architecture
  - Engineering Architecture
approved_by: []
approved_at:
supersedes: []
related_adrs:
  - decisions/ADR-002-versioned-configuration-bundle.md
  - decisions/ADR-005-flatten-inheritance-at-publish-time.md
---

# ADR-018：配置 Schema 与表达式运行时

## 上下文

ServiceOS 需要业务人员配置流程、表单、资料、SLA、派单和价格。任意脚本会引入安全、性能、可解释性和版本兼容风险；全部写死又无法支持多车企差异。

## 决策

采用分层配置执行模型：

1. 结构层：JSON/YAML + 版本化 JSON Schema；
2. 条件层：受限、强类型布尔表达式；
3. 决策层：多条件规则优先使用决策表；
4. 公式层：价格和指标使用受限数值表达式；
5. 脚本层：默认关闭，仅平台管理员在沙箱内使用；
6. 扩展层：高复杂逻辑通过审核后的插件扩展点实现。

配置禁止直接访问数据库、任意网络、文件系统、线程、反射和未授权敏感字段。

## 运行时要求

- 白名单函数和属性；
- 静态类型检查；
- 执行超时、复杂度和资源限制；
- 确定性和可重放；
- 输入输出审计；
- 命中解释；
- 配置版本锁定；
- 批量样本模拟；
- 不允许产生外部副作用。

表达式上下文由平台显式构造，可包含 `workOrder`、`customer`、`vehicle`、`asset`、`project`、`region`、`task`、`fulfillmentFacts`、`actor` 和 `clock`。配置不能访问未声明属性。

## 备选方案

- 任意 Groovy/JavaScript：灵活但不可控，拒绝作为默认方案；
- 全部自研 DSL：长期可能需要，但不作为 MVP 前置条件；
- 全部写代码：会复现旧系统问题，拒绝。

## 后果

正面：大多数车企差异可配置、可校验、可模拟和可解释。

负面：需要建设 Schema 注册表、验证器、模拟器和插件治理；部分复杂需求仍需要研发扩展。

## 复评触发条件

- 受限表达式无法覆盖超过 20% 的新增需求；
- 插件持续增长并出现重复模式；
- 批量派单或计价性能不达标；
- 需要 BPMN/DMN 标准互操作。
