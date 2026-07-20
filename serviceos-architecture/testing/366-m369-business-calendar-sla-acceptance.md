---
title: M369 BUSINESS 日历 SLA 截止时间验收矩阵
status: Implemented
milestone: M369
lastUpdated: 2026-07-20
---

# M369 BUSINESS 日历 SLA 截止时间验收矩阵

| ID | 场景 | 期望 | 证据 |
|---|---|---|---|
| M369-01 | 工作窗内 BUSINESS 起算 | `deadlineAt` 仅累加业务秒，不按墙钟直线外推 | `BusinessCalendarDeadlineCalculatorTest` |
| M369-02 | 跨越周末/节假日 | 非工作日不计时；调休日复用周一窗 | 单元测试 + `SlaClockPostgresIT` |
| M369-03 | start 冻结日历版本 | `sla_instance.calendar_*` 与 Bundle item/身份一致；ELAPSED 日历列为空 | `SlaClockPostgresIT` + V135 CHECK |
| M369-04 | BUSINESS 按时完成 | `MET`，`elapsedSeconds` 为业务秒 | PostgresIT |
| M369-05 | BUSINESS 逾期完成 | `MET_LATE`，业务已用时长含截止后业务窗 | PostgresIT |
| M369-06 | ELAPSED 回归 | 既有自然时长时钟与查询仍成立；`sla.started@v2` | PostgresIT |
| M369-07 | BUSINESS 无/错 `calendarRef` | 资产 schema 或 Bundle 校验失败关闭 | `ConfigurationAssetSchemaValidatorTest` |
| M369-08 | ELAPSED 携带 `calendarRef` | 发布失败关闭 | Schema validator |
| M369-09 | 无效时区/窗 end≤start | CALENDAR 发布失败关闭 | Schema validator |
| M369-10 | OpenAPI 1.0.60 + `sla.started@v2` | 契约兼容与探针通过 | contracts |
| M369-11 | ArchitectureTest | 模块边界保持 | ArchitectureTest |

## 明确不验收

- 暂停/恢复、预警/升级/通知、外部节假日服务、非 Task subject、结算金额。
