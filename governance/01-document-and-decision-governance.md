---
title: 文档与架构决策治理
version: 0.1.0
status: Proposed
owner: Architecture Board
---

# 文档与架构决策治理

## 1. 目的

本规范定义 ServiceOS 文档、ADR、机器契约和实现证据的权威顺序、状态流转、审批责任与冲突处理方式。未经本规范治理的文档，不得被视为正式研发承诺。

## 2. 权威顺序

当内容冲突时，按以下顺序裁决：

1. 已接受的 ADR；
2. 已接受的领域与平台架构规范；
3. 已发布且通过兼容性校验的机器契约；
4. 已接受的逻辑和物理数据模型；
5. 已接受的产品规格；
6. 已接受的验收矩阵；
7. 参考实现说明；
8. 代码注释与临时说明。

同级文档冲突时，以版本更新且显式声明 supersedes 的文档为准；无法自动裁决时，由 Architecture Board 处理。

## 3. 文档状态

- `Draft`：内容不完整，不可进入正式评审；
- `Proposed`：内容完整，可评审，不可作为最终研发承诺；
- `Accepted`：已通过评审，可作为研发权威输入；
- `Implemented`：对应实现存在且已有验证证据；
- `Superseded`：已被新文档或 ADR 替代；
- `Rejected`：方案被否决，仅保留历史原因。

## 4. 必备元数据

所有正式 Markdown 文档必须包含：

```yaml
---
title: 文档标题
version: 0.1.0
status: Proposed
owner: 责任角色
reviewers:
  - Product Architecture
  - Engineering Architecture
approved_by: []
approved_at:
supersedes: []
related_adrs: []
---
```

ADR 还必须包含：上下文、决策、备选方案、后果、复评触发条件。

## 5. 评审 Gate

### 5.1 Accepted 条件

文档只有同时满足以下条件，才能标记为 `Accepted`：

- 术语与领域模型一致；
- 关键不变量明确；
- 已列出范围和非范围；
- 已映射到 API、数据和验收；
- 重大技术选择有 ADR；
- 业务事实已由业务负责人确认；
- 至少一名产品架构和一名技术架构评审通过。

### 5.2 Implemented 条件

- 关联代码或配置存在；
- CI 通过；
- 验收矩阵有证据；
- 运行约束与故障恢复已验证；
- 实现没有偷偷改变 Accepted 语义。

## 6. 变更分类

- **Patch**：措辞、示例、非语义性补充；
- **Minor**：向后兼容的能力增加；
- **Major**：破坏性语义变化、聚合边界变化、契约不兼容。

Major 变更必须新增 ADR，并提供迁移、双写或兼容期方案。

## 7. 研发纪律

开发任务进入迭代前必须回答：

1. 对应哪个 Accepted 文档？
2. 对应哪个业务事实或试点样本？
3. 是否通过配置实现？
4. 是否需要版本锁定？
5. 是否影响历史实例？
6. API、事件、数据和验收是否已同步更新？

任一项不清楚，不得进入核心业务开发。

## 8. CI 校验建议

CI 至少检查：

- 正式文档 YAML 元数据完整；
- 内部链接有效；
- Accepted 文档不得引用 Draft 作为强依赖；
- OpenAPI 与 JSON Schema 兼容；
- 追踪矩阵中不存在孤立命令、事件、实体和验收项；
- Superseded 文档必须指向替代文档。
