---
title: ServiceOS 当前基线轻量快照
status: Implemented
lastUpdated: 2026-07-15
latestBusinessMilestone: M61
baselineCommit: f0d4d31
activeContextPack: CTX-001
---

# ServiceOS 当前基线

本文件只回答“现在从哪里继续”，用于 Agent 冷启动。完整能力历史、逐里程碑证据和未实现明细仍以 `../docs/implementation-status.md`、对应实现文档和验收矩阵为准。

## 当前工程形态

| 项目 | 当前值 |
|---|---|
| 后端 | Java 21、Spring Boot、Spring Modulith 模块化单体 |
| 可构建工程 | `serviceos-backend`、`serviceos-contracts` |
| 数据库 | PostgreSQL + Flyway，版本 061 / 63 |
| HTTP 契约 | Core OpenAPI 0.32.0、BYD CPIM OpenAPI 0.3.0 |
| 事件契约 | JSON Schema，包含可靠消息、审核、交付恢复和 SLA v1 |
| 前端 | 尚无工程代码；已有 Admin、Network、Technician 产品规格 |

## 当前已稳定的基础能力

- OIDC/JWT、Capability、Tenant/Project Scope 和拒绝审计；
- Inbox、Outbox、Worker claim/lease/retry；
- WorkOrder 接收、配置包锁定、工作流启动和线性 Stage/Task 推进；
- 人工 Task claim/start/complete、责任分配与执行保护；
- ServiceAssignment、容量、改派 Saga 和异常恢复；
- 动态表单、Evidence、Review、Correction 和安全文件纵向切片；
- BYD CPIM 入站、提审、审核回调、UNKNOWN 人工重发和严格 ACK 恢复；
- Task 自然时长 SLA v1。

## 当前主要未闭环范围

- 工作流并行/汇聚网关、完整条件表达式和复杂补偿；
- SLA 业务日历、暂停恢复、预警升级和通知；
- 通用 Connector、更多车企消息、生产凭据和真实 sandbox；
- OCR/CV/GPS 权威校验；
- Admin、Network、Technician、External Portal 前端工程；
- 履约事实、双向试算和正式结算运行时。

## 最近三个业务里程碑

| 里程碑 | 摘要 |
|---|---|
| M59 | UNKNOWN 外部交付的高风险人工重发 |
| M60 | 严格 ACK 后恢复交付并闭环运营异常 |
| M61 | Task 自然时长 SLA 时钟与策略版本锁定 |

## 当前工程改进

`CTX-001` 建立分层上下文、模块路由、影响分析和本地会话缓存。完成后，新任务不得默认全文读取实施状态、追踪矩阵或全部 Architecture Book。

## 默认启动规则

1. 读取根 `AGENTS.md`；
2. 读取本文件；
3. 读取用户指定或当前分支声明的 Context Pack；
4. 运行 `bash scripts/plan-context.sh <ID>`；
5. 只读取输出中的必读文件；
6. 发现冲突或高风险触发条件后再扩大范围。

## 何时读取完整历史

只有以下情况才默认展开 `implementation-status.md` 的历史章节、完整追踪矩阵或旧 Mxx 文档：

- 回归定位需要确认旧行为；
- 当前事实源互相冲突；
- 数据迁移或已发布契约兼容；
- 需要证明某项能力何时、以何种边界实施；
- Context Pack 明确引用。

## 下一里程碑选择

业务下一里程碑不在本文件硬编码。应由用户目标、未完成边界和新建 Context Pack共同决定，避免 Agent 因历史文档中的旧“下一步”自动偏航。
