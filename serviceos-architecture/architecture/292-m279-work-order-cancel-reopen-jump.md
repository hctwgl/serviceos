---
title: M279 工单取消/重开与流程人工跳转
status: Draft
milestone: M279
lastUpdated: 2026-07-18
relatedMilestones: [M16, M19, M277]
---

# M279 工单取消/重开与流程人工跳转

## 目标

交付最小可靠切片：ACTIVE 工单可取消并级联关闭运行时；CANCELLED 可授权重开并重新启动根流程；ACTIVE 根流程可人工跳转到定义内任务节点。

## 设计要点

1. `CancelWorkOrder`：ACTIVE→CANCELLED；Outbox `workorder.cancelled`；Workflow Inbox 级联取消。
2. `ReopenWorkOrder`：CANCELLED→ACTIVE；`workorder.reopened`；Workflow 按冻结 Bundle 新建根实例。
3. `JumpWorkflow`：取消当前 ACTIVE/WAITING 运行节点后激活目标任务节点（需审批引用）。
4. 失败关闭：版本不匹配、非法状态、未知跳转目标。
