---
title: ADR-090：BUSINESS 日历 SLA 首切片边界
version: 1.0.0
status: Accepted
owner: Product Architecture
reviewers:
  - Engineering Architecture
  - SLA Owner
related_adrs: []
---

# ADR-090：BUSINESS 日历 SLA 首切片边界

## 1. 状态与已接受决策

负责人确认（选型原文：`D1-R, D2-R, D4-R`）后正式接受：

1. **D1-R**：首切片仅 BUSINESS 截止时间计算——配置 `clockMode=BUSINESS` + Bundle 锁定
   `BusinessCalendarVersion`；纯函数预计算 `deadlineAt`；保留 ELAPSED；**不含**暂停/恢复/
   预警/升级/重算；
2. **D2-R**：日历权威为版本化配置资产 `CALENDAR`，进入 Configuration Bundle；首切片使用显式
   样例日历（固定时区 + 周一～周五工作时段 + 可选节假日/调休日 JSON），**不接**外部节假日 API；
3. **D4-R**：仍限 Task + `TASK_CREATED` / `TASK_COMPLETED`，仅扩展 `clockMode`；
4. 调休日样例语义：复用周一工作窗（实现文档固定，避免运行时猜测）。

## 2. 上下文

- Task `ELAPSED` 自然时长时钟与授权查询已经存在；
- ARCH-12 §6～§8 描述 BUSINESS 日历与暂停/恢复，但整体仍为 Proposed，且时长/暂停原因需试点确认；
- 本 ADR 仅固化**首切片可接受边界**，不把 ARCH-12 全体标为 Accepted。

## 3. 后果

- 当前实现包含 CALENDAR 资产 + SLA schema + V135 冻结列 + 纯函数截止/业务已用时长 +
  `sla.started@v2` + IT；
- 不接受：外部节假日 API、暂停/预警/升级、非 Task subject、结算落账、无期限双轨 clockMode。

## 4. 明确不做

- 结算落账 / 考核处罚金额；
- 外部节假日 API；
- 预警、升级、通知、OperationalException 联动；
- 受控重算与免责；
- 未经确认的默认时长或项目兜底策略。
