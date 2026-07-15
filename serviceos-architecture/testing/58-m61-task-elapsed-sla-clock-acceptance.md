---
title: M61 Task 自然时长 SLA 时钟验收矩阵
version: 1.0.0
status: Implemented
---

# M61 Task 自然时长 SLA 时钟验收矩阵

| ID | 优先级 | 场景 | 必须证明的结果 |
|---|---|---|---|
| M61-CFG-001 | P0 | 发布 SLA v1 | 只接受显式 TASK/ELAPSED/start/stop/时长和匹配资产身份 |
| M61-CFG-002 | P0 | 未知 Schema、缺时长或 Workflow 引用缺失/错配 | 发布失败关闭，不猜测默认 SLA |
| M61-TSK-001 | P0 | Workflow 创建首个/后续 Task | `slaRef` 与 Bundle/Workflow Version 一起冻结，幂等冲突拒绝 |
| M61-CLK-001 | P0 | task.created@v1 | 原子创建 RUNNING instance、segment、TARGET_DUE milestone 和 started@v1 |
| M61-CLK-002 | P0 | 截止时间当时完成 | 形成 MET、取消 milestone、关闭 segment，不形成 breach |
| M61-BRE-001 | P0 | 对账发现到期 | 锁定 instance/milestone/Task，只触发一次 BREACHED 和 breached@v1 |
| M61-BRE-002 | P0 | Task 已完成但 completion event 尚未消费 | 对账不误报；消费后按业务完成时间形成 MET/MET_LATE |
| M61-LATE-001 | P0 | 逾期完成 | 保留 deadline breach 历史并形成 MET_LATE，事件版本为 1/2/3 |
| M61-IDM-001 | P0 | 相同事件 id/digest 重放 | 不重复实例、milestone、segment 或 Outbox |
| M61-IDM-002 | P0 | 相同事件 id 不同 digest | 失败关闭，不接受事件改写 |
| M61-TX-001 | P0 | SLA Outbox 追加失败 | Inbox/instance/segment/milestone/Outbox 整体回滚 |
| M61-DB-001 | P0 | 跨租户/错误策略、非法迁移或终态改写 | PostgreSQL FK/CHECK/trigger 拒绝 |
| M61-CON-001 | P0 | started/breached/met 三个事件 | Schema、有效样例与事件版本治理通过 |
| M61-MOD-001 | P0 | SLA → Configuration/Task/Reliability 协作 | 仅使用公开 API/SPI，ArchitectureTest 通过 |
| M61-MIG-001 | P0 | 空库迁移 | PostgreSQL 18 原生镜像应用 63 个迁移并到达 v061 |

## 自动化证据映射

- `ConfigurationPublicationPostgresIT`：M61-CFG-001/002；
- `WorkflowDefinitionParserTest` 与既有 Workflow/Task PostgreSQL 回归：M61-TSK-001；
- `SlaClockPostgresIT`：M61-CLK/BRE/LATE/IDM/TX/DB/MIG；
- `ContractValidationTest`、`EventSchemaGovernanceTest`：M61-CON-001；
- `ArchitectureTest`：M61-MOD-001。

本矩阵不宣称 BUSINESS 日历、暂停/恢复、预警/升级/通知、其他 subject、HTTP/Portal 或考核结算完成。
