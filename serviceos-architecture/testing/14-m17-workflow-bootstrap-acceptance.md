---
title: M17 工作流可靠启动验收矩阵
version: 0.1.0
status: Proposed
owner: Fulfillment Platform QA
---

# M17 工作流可靠启动验收矩阵

| ID | 优先级 | 场景 | 预期 |
|---|---|---|---|
| M17-WO-001 | P0 | 首次接收外部工单 | WorkOrder 与 `workorder.received` 同事务提交 |
| M17-WO-002 | P0 | 同业务订单同载荷重放 | 返回同一 WorkOrder，仅一个 Received 事件 |
| M17-CFG-001 | P0 | Bundle ID 正确但 manifest digest 被替换 | 消费失败，不启动 Workflow |
| M17-CFG-002 | P0 | Workflow semanticVersion 与资产版本不一致 | 失败关闭并回滚消费者事务 |
| M17-WFL-001 | P0 | 合法 START → SERVICE_TASK | Workflow/Stage ACTIVE，Task AUTOMATED/PENDING，WorkOrder ACTIVE |
| M17-WFL-002 | P0 | START 多条无条件出边 | 失败关闭，不生成 Workflow/Stage/Task |
| M17-WFL-003 | P0 | 首节点缺 stageCode/taskType | 失败关闭，不猜测默认值 |
| M17-IDEM-001 | P0 | 同 eventId、同摘要重放 | Inbox 返回 REPLAY，实例与事件数不增加 |
| M17-IDEM-002 | P0 | 同 eventId、不同摘要 | `EVENT_PAYLOAD_MISMATCH`，原结果不变 |
| M17-TX-001 | P0 | 创建 Task 或激活 WorkOrder 失败 | Workflow/Stage/Task/Inbox/派生 Outbox 全回滚 |
| M17-OUT-001 | P0 | 消费成功 | 发出 workflow.started、stage.activated、task.created、workorder.activated v1 |
| M17-OUT-002 | P0 | `workorder.received` 本地模式无匹配消费者 | 不标记 Published，进入既有失败/退避策略；纯通知事件允许零订阅者 |
| M17-MIG-001 | P0 | PostgreSQL 18 空库迁移 | current=`017`，applied=`19`，重复 migrate=0 |
| M17-MIG-002 | P0 | V015 前已有工单 | 迁移失败并要求显式 WorkOrderReceived 补录计划 |
| M17-DEP-001 | P0 | staging 发布 | migration Gate 校验 `017/19` 后才启动 backend |

## 验收纪律

- P0 PostgreSQL 用例必须在 Docker/兼容容器运行时真实执行，`skipped` 不等于通过；
- M17 只验收“可靠启动”，不得据此宣称后续节点推进或比亚迪勘安全流程完整闭环；
- 真实 BYD sandbox、业务样本和业务负责人签署仍是外部未决证据。
