---
title: M277 SUB_PROCESS 运行时
status: Implemented
milestone: M277
lastUpdated: 2026-07-18
relatedMilestones: [M17, M270, M275, M276]
---

# M277 SUB_PROCESS 运行时

## 目标

支持父流程 `SUB_PROCESS` 节点：按 Bundle 内 `subProcessRef` 启动子 Workflow 实例，子流程 END 后幂等恢复父节点并继续推进。

## 已实现

- schema：`subProcessRef`；发布期 Bundle 引用校验；根 WORKFLOW 解析（未被引用者）
- Flyway V104：`instance_role` + `wfl_subprocess_link`；根 work_order 唯一索引
- 父节点 WAITING + 子 ROOT/SUBPROCESS 实例启动首任务
- 仅根流程 `fulfill`；子完结恢复父流程
- PostgreSQL IT

## 明确未实现

嵌套多级子流程压力测试、子流程取消传播、多实例调用同一子流程模板。
