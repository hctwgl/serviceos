---
title: M69 授权工单执行工作区投影
status: Accepted
milestone: M69
---

# M69 授权工单执行工作区投影

## 1. 目标

在 M68 工单概要上补齐管理端工作区所需的只读执行骨架：

- `GET /api/v1/work-orders/{workOrderId}/stages`；
- `GET /api/v1/work-orders/{workOrderId}/tasks`。

两条查询先调用公开 `WorkOrderQueryService.get` 完成 tenant 隔离与实时 `workOrder.read` 项目范围鉴权，
随后由 workflow、task 模块分别读取自己的表，禁止跨模块查表。

## 2. 确定性语义

- Stage 按 `sequenceNo/stageInstanceId` 正序，返回 Workflow 冻结定义引用和当前 Stage 权威状态；
- Task 按 `createdAt/taskId` 正序稳定游标分页，cursor 绑定 workOrderId；
- Task 只返回执行摘要，不返回 payload、resultRef、inputVersionRefs、客户信息或错误正文；
- 尚未异步初始化 Workflow 的已授权工单返回 `workflow=null, stages=[]`，这是可观察的真实状态，不伪造初始化成功；
- 非法 cursor、越权与跨 tenant 继续失败关闭。

## 3. 非目标

完整时间线、Node/Attempt 历史、允许动作、Task 独立队列/详情、客户 PII、派单/SLA 风险聚合、
新生命周期状态和 Portal 前端不在 M69 范围。

## 4. 工程变化

Core OpenAPI 升至 0.40.0；V069 为 Stage/Task 工作区稳定读取补齐组合索引。命令事务、状态机、
Inbox/Outbox 和 Evidence 主线不变。
