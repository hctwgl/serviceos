---
title: M73 工单核心执行时间线投影
status: Implemented
milestone: M73
---

# M73 工单核心执行时间线投影

## 1. 目标

按 ARCH-19 已定义的 `readmodel` 模块建立首个可重建跨模块查询投影，并实现
`GET /api/v1/work-orders/{workOrderId}/timeline`。M73 覆盖从工单接收、流程/阶段推进到 Task
创建、领取、开始、释放、完成、取消及工单履约完成的核心执行链，不查询 Outbox、审计或其他模块
内部表拼接页面。

## 2. 模块与事实边界

- `readmodel` 只消费已发布事件、调用模块公开最小查询端口并写自己的 `rdm_*` 投影；
- WorkOrder、Workflow、Stage、Task 仍是权威事实源，时间线不得反向写领域表或成为状态机依据；
- WorkOrder/Workflow/Stage 事件载荷直接给出 workOrderId；缺少上下文的 Task 生命周期事件通过
  `task::api` 最小 `TaskTimelineContextQuery` 解析，不跨模块读取 `tsk_*`；
- 每个来源事件按 `eventId + consumerName + payloadDigest` 使用 Inbox 去重，投影写入和 Inbox complete
  同事务；同 eventId 不同 digest 失败关闭；
- 工单范围通过 `workorder::api` 权威解析，事件 tenant、aggregate/resource 身份或 Project 不一致时失败关闭。

## 3. 支持的核心事件

M73 仅接受已发布版本：

- `workorder.received/activated/fulfilled@v1`；
- `workflow.started/completed@v1`；
- `stage.activated/completed@v1`；
- `task.created/claimed/started/released/cancelled@v1`；
- `task.completed@v1/v2`。

每条投影保存来源事件、发生/接收时间、category、资源类型/ID/版本、稳定 resourceCode/outcomeCode、
actorId、correlationId 和显示模板版本。不得保存自由文本、payload、客户 PII、reason 正文、resultRef、
error body、签名、凭据或外部私有响应。

## 4. 查询、授权与分页

- 查询先复用 M68 WorkOrder 详情，完成 tenant 隔离和实时 `workOrder.read` Project Scope 鉴权；
- 按 `(occurredAt DESC, timelineEntryId DESC)` 稳定游标分页，cursor 绑定 workOrderId；
- 每页返回 WorkOrder `resourceVersion`、items、nextCursor、`asOf`、`lastProjectedAt` 和
  `freshnessStatus=UNKNOWN`；UNKNOWN 是对尚无端到端 broker checkpoint 的显式事实，不伪造 FRESH；
- RoleGrant 撤销后下一页立即 403 并记录拒绝审计；跨 tenant WorkOrder 返回 404；
- 本地投影允许事件乱序到达，展示顺序始终使用权威 `occurredAt`，`receivedAt` 暴露投影延迟。

## 5. 数据库

V071 新增 `rdm_work_order_timeline_entry`、来源事件唯一约束和
`(tenant_id, work_order_id, occurred_at DESC, timeline_entry_id DESC)` 查询索引。投影可清空重建，
不为它增加不可变触发器，也不建立跨模块外键。

## 6. 明确未实现

Appointment、Visit、Evidence/Review、Delivery、SLA、OperationalException、试算/结算事件合并，
correlation 展开、敏感字段二次授权、投影重建作业、Broker checkpoint、搜索、导出和 Portal 不在 M73。
自动 Task 技术 Attempt 继续由 M72 独立查询且在用户时间线默认折叠。
