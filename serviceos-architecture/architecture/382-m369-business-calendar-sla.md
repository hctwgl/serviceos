---
title: M369 BUSINESS 日历 SLA 截止时间（ADR-090 D1-R/D2-R/D4-R）
status: Implemented
milestone: M369
lastUpdated: 2026-07-20
relatedMilestones: [M61, M62, M75]
openapiVersion: "1.0.59"
flywayVersion: "135"
---

# M369 BUSINESS 日历 SLA 截止时间（ADR-090 D1-R/D2-R/D4-R）

## 状态

**Implemented**。承接已接受 **ADR-090**（负责人选型：`D1-R, D2-R, D4-R`）。

## 目标

在保留 Task `ELAPSED` 时钟的前提下，交付首个 **BUSINESS** 日历截止切片：配置
`clockMode=BUSINESS` + Bundle 锁定 `CALENDAR` 版本，用纯函数预计算 `deadlineAt`，
并在停止时按业务已用时长记账。

## 已实现范围

1. ADR-090 Accepted：D1-R / D2-R / D4-R；
2. 配置资产类型 `CALENDAR` + `calendar-v1.schema.json`；SLA schema 扩展
   `clockMode∈{ELAPSED,BUSINESS}`，BUSINESS 强制 `calendarRef`；
3. Flyway **V135**：资产类型 CHECK、`sla_instance` 日历冻结列与 clock_mode/calendar 互锁、
   范围/身份校验函数；
4. `BusinessCalendar` / `BusinessCalendarDeadlineCalculator`：跳过周末/节假日，调休日复用周一窗；
5. `JooqSlaClockService`：start 锁定日历版本并预计算 deadline；stop 对 BUSINESS 记业务秒；
   发出 `sla.started@v2`（含日历冻结字段；ELAPSED 日历字段为 null）；
6. OpenAPI 仍 **1.0.59**：`clockMode` 保持 `const: ELAPSED`（oasdiff 禁止原地去掉 const）；BUSINESS/CALENDAR 的 HTTP 枚举扩展留待显式主版本或已批准的破坏性演进；运行时与 `sla.started@v2` 已表达 BUSINESS；
7. 工单时间线消费 `sla.started@v2`；设计器可起草 `CALENDAR`；
8. 单元测试 + `SlaClockPostgresIT` BUSINESS 路径 + 契约探针。

## 明确未实现

- 暂停/恢复、预警、升级、通知、受控重算与免责（D1-B / ARCH-12 其余）；
- 外部节假日 API、租户级独立日历目录；
- WorkOrder/Dispatch 等非 Task subject；
- SLA 查询 DTO 暴露日历冻结字段；
- OpenAPI `clockMode`/`assetType` 枚举放宽（需新主版本或已批准破坏性演进；oasdiff 阻断原地去 const）；
- 结算落账 / 考核处罚金额。

## 验证

```bash
bash scripts/agent-verify.sh test BusinessCalendarDeadlineCalculatorTest,ConfigurationAssetSchemaValidatorTest
bash scripts/agent-verify.sh it SlaClockPostgresIT
bash scripts/agent-verify.sh arch
bash scripts/agent-verify.sh contracts  # OpenAPI 相对 master 无破坏；事件新增 v2
bash scripts/agent-verify.sh docs
```
