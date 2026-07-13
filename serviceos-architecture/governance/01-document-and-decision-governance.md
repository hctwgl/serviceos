---
title: 文档与架构决策治理
version: 0.1.0
status: Proposed
owner: Architecture Board
reviewers:
  - Product Architecture
  - Engineering Architecture
approved_by: []
approved_at:
supersedes: []
related_adrs: []
---

# 文档与架构决策治理

## 1. 目的

本规范定义 ServiceOS 文档、ADR、机器契约和实现证据的权威顺序、状态流转、审批责任与冲突处理方式。未经治理的文档不得被视为正式研发承诺。

## 2. 权威顺序

发生冲突时依次以以下内容为准：

1. Accepted ADR；
2. Accepted 领域与平台架构规范；
3. 已发布且通过兼容校验的机器契约；
4. Accepted 逻辑与物理数据模型；
5. Accepted 产品规格；
6. Accepted 验收矩阵；
7. 参考实现说明；
8. 代码注释和临时说明。

同级冲突以更新且显式声明 `supersedes` 的版本为准；无法裁决时提交 Architecture Board。

## 3. 状态

- `Draft`：内容不完整；
- `Proposed`：内容完整，可评审；
- `Accepted`：已批准，可作为研发权威输入；
- `Implemented`：对应实现与验证证据存在；
- `Superseded`：已被替代；
- `Rejected`：被否决，仅保留历史原因。

## 4. Accepted Gate

必须同时满足：

- 术语和领域模型一致；
- 范围、非范围和关键不变量明确；
- 映射 API、事件、数据和验收；
- 重大技术选择有 ADR；
- 依赖的业务事实已确认；
- 产品架构与技术架构至少各一人批准；
- 涉及车企项目规则时有业务负责人批准。

## 5. Implemented Gate

- 关联代码或配置存在；
- CI 通过；
- 验收矩阵有可追溯证据；
- 故障恢复和运行约束已验证；
- 实现没有改变 Accepted 语义。

## 6. 研发准入问题

开发任务进入迭代前必须回答：

1. 对应哪个 Accepted 文档？
2. 对应哪个已确认业务事实或样本？
3. 是否优先通过配置实现？
4. 是否需要配置版本锁定？
5. 是否影响历史实例？
6. API、事件、数据和验收是否同步？

任一项不清楚，不得进入核心业务开发。

## 7. CI 校验

CI 至少检查：

- YAML 元数据完整；
- 内部链接有效；
- Accepted 文档不强依赖 Draft；
- OpenAPI 和 JSON Schema 兼容；
- 追踪矩阵不存在孤立对象；
- Superseded 文档指向替代文档；
- Published 配置资产不可原地修改。
