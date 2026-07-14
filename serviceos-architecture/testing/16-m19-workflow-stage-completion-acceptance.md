---
title: M19 跨阶段、END 与工单履约完成验收矩阵
version: 0.1.0
status: Proposed
owner: Fulfillment Platform QA
---

# M19 跨阶段、END 与工单履约完成验收矩阵

| ID | 优先级 | 场景 | 预期 |
|---|---|---|---|
| M19-STAGE-001 | P0 | 唯一无条件边进入新 stageCode | 原 Stage COMPLETED，新 Stage/Node/Task ACTIVE/PENDING |
| M19-STAGE-002 | P0 | 新 Stage 或 Task 创建失败 | 原 Node/Stage、Inbox 与派生事件全部回滚 |
| M19-END-001 | P0 | 唯一无条件边到达 END | Node、Stage、Workflow COMPLETED，WorkOrder FULFILLED |
| M19-END-002 | P0 | END 事件重放 | 不重复完成状态，不重复生成完成事件 |
| M19-EVT-001 | P0 | 跨阶段 | `stage.completed` 与新 `stage.activated` 同事务追加 |
| M19-EVT-002 | P0 | 到达 END | Stage、Workflow、WorkOrder 三类完成事件同事务追加 |
| M19-CFG-001 | P0 | 条件边、多出边、网关或并行 | 失败关闭，不产生部分推进 |
| M19-MIG-001 | P0 | PostgreSQL 18 空库迁移 | current=`019`，applied=`21`，重复 migrate=0 |
| M19-DEP-001 | P0 | staging 发布 | migration Gate 校验 `019/21` 后才启动 backend |

验收必须在可访问 Docker 的环境执行 PostgreSQL IT，`skipped` 不计通过。
