---
title: M292 WORKFLOW 配置干跑模拟
status: Implemented
milestone: M292
lastUpdated: 2026-07-18
relatedMilestones: [M269, M282, M291]
---

# M292 WORKFLOW 配置干跑模拟

## 目标

对 WORKFLOW 草稿或原始定义做**无副作用**干跑：给定 `SERVICEOS_EXPR_V1` 上下文，沿 START→任务→网关推进，产出步骤轨迹；失败关闭，不写运行时表。

## 范围

1. `POST /configuration/drafts/{id}:simulate`；
2. `POST /configuration/simulations:run`；
3. Admin 设计器「模拟」按钮与轨迹展示；
4. 支持线性推进与 EXCLUSIVE_GATEWAY；WAIT_EVENT/TIMER/PARALLEL/SUB_PROCESS 以暂停原因记录，不伪造外部信号。

## 明确未实现

历史订单回放、并行全路径展开 UI、真实任务调度、持久化模拟会话。
