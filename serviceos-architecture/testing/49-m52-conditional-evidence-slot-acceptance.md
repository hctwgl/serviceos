---
title: M52 条件 EvidenceSlot requiredWhen 验收
version: 0.1.0
status: Implemented
---

| ID | 优先级 | 场景 | 证据 |
|---|---|---|---|
| M52-EXPR-001 | P0 | SERVICEOS_EXPR_V1 白名单路径求值 | `ServiceOsExprV1EvaluatorTest` |
| M52-EXPR-002 | P0 | 未知路径/非法语法失败关闭 | `ServiceOsExprV1EvaluatorTest` |
| M52-EXPR-003 | P0 | 长度/嵌套/操作符复杂度超限失败关闭 | `ServiceOsExprV1EvaluatorTest` |
| M52-CFG-001 | P0 | 非法 requiredWhen 与条件必填 minCount=0 在发布前拒绝 | `ConfigurationPublicationPostgresIT` |
| M52-EVD-001 | P0 | brand 命中创建条件槽位 | `EvidenceSlotPostgresIT` |
| M52-EVD-002 | P0 | brand 未命中省略条件槽位 | `EvidenceSlotPostgresIT` |
| M52-EVD-002A | P0 | false 决策仍固化输入摘要与解析级解释 | `EvidenceSlotPostgresIT` |
| M52-EVD-003 | P0 | 权威上下文缺值时整笔回滚 | `EvidenceSlotPostgresIT` |
| M52-EVD-004 | P0 | 固定槽位回归不变 | `JsonEvidenceTemplateResolverTest`、`EvidenceSlotPostgresIT` |
| M52-EVD-005 | P0 | 条件命中且显式 minCount=0 失败关闭 | `JsonEvidenceTemplateResolverTest` |
| M52-MOD-001 | P0 | WorkOrder JDBC 留在 infrastructure；跨模块仅依赖公开 API | `ArchitectureTest` |
| M52-DEP-001 | P0 | PostgreSQL 正向迁移至 052 / 54 | staging rehearsal、`EvidenceSlotPostgresIT` |
