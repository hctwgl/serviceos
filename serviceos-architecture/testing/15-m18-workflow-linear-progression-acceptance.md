---
title: M18 TaskCompleted 与工作流线性推进验收矩阵
version: 0.1.0
status: Proposed
owner: Fulfillment Platform QA
---

# M18 TaskCompleted 与工作流线性推进验收矩阵

| ID | 优先级 | 场景 | 预期 |
|---|---|---|---|
| M18-TASK-001 | P0 | Workflow 自动 Task 执行成功 | Task/Attempt 终态与 `task.completed@v1` 同事务提交 |
| M18-TASK-002 | P0 | 普通后台 Task 执行成功 | 只产生技术执行事件，不产生 Workflow TaskCompleted |
| M18-EVT-001 | P0 | TaskCompleted resultRef 被替换但摘要未变 | 消费失败，节点与下一 Task 不变 |
| M18-CFG-001 | P0 | definition version ID 或 digest 与冻结运行时不一致 | 失败关闭，不读取最新资产 |
| M18-WFL-001 | P0 | 当前任务有唯一无条件同阶段任务后继 | 当前节点 COMPLETED，下一节点 ACTIVE，创建一个下一 Task |
| M18-WFL-002 | P0 | 条件出边、无出边或多出边 | 失败关闭，不猜测下一节点 |
| M18-WFL-003 | P0 | 目标为跨阶段或 END | M18 明确拒绝，当前节点仍 ACTIVE，不产生部分推进 |
| M18-CTX-001 | P0 | 事件中的 Task/WorkOrder/Workflow/Stage/Node 上下文任一漂移 | 消费失败，冻结事实不变 |
| M18-IDEM-001 | P0 | 同 eventId、同摘要重放 | Inbox REPLAY，NodeInstance、Task、TaskCreated 数量不增加 |
| M18-IDEM-002 | P0 | 同 eventId、不同摘要重放 | `EVENT_PAYLOAD_MISMATCH`，原推进结果不变 |
| M18-CON-001 | P0 | 两个不同完成事件并发推进同一节点 | 最多一个成功；另一个因状态/唯一约束失败 |
| M18-TX-001 | P0 | 下一 Task 或 NodeInstance 创建失败 | 当前节点更新、Inbox 与派生 Outbox 全回滚 |
| M18-OUT-001 | P0 | 本地模式缺少 TaskCompleted 消费者 | 源 Outbox 不标记 Published，进入失败/退避策略 |
| M18-MIG-001 | P0 | PostgreSQL 18 空库迁移 | current=`018`，applied=`20`，重复 migrate=0 |
| M18-MIG-002 | P0 | V018 前存在终态 Workflow Task | 迁移失败，要求显式修复，禁止伪造 completion event |
| M18-DEP-001 | P0 | staging 发布 | migration Gate 校验 `018/20` 后才启动 backend |

## 验收纪律

- P0 PostgreSQL 用例必须在 Docker 或兼容容器运行时真实执行，`skipped` 不等于通过；
- M18 只证明同阶段唯一无条件任务推进，不得宣称跨阶段、结束、网关、并行或完整勘安闭环；
- 人工任务完成命令与权限、负责人、SLA 仍需独立里程碑验收；
- 真实 BYD sandbox、脱敏样本和业务负责人签署仍是外部未决证据。
