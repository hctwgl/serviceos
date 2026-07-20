---
title: ADR-090：BUSINESS 日历 SLA 首切片边界（待接受）
version: 0.1.0
status: Proposed
owner: Product Architecture
reviewers:
  - Engineering Architecture
  - SLA Owner
related_adrs: []
---

# ADR-090：BUSINESS 日历 SLA 首切片边界（待接受）

## 1. 状态

**Proposed**。负责人已选型「D = BUSINESS 日历 SLA」，但 ARCH-12 整体仍为 Proposed，
M1-04 未填试点日历/时长，暂停原因亦未项目确认。本 ADR 仅固化**首切片可接受边界**，
接受前不得开 M369 实现或推进 `latestMilestone`。

## 2. 上下文

- M61～M66 已交付 Task `ELAPSED` 自然时长时钟与授权查询；`sla-v1.schema.json` 当前
  `clockMode` 常量仅为 `ELAPSED`；
- ARCH-12 §6～§8 描述 BUSINESS 日历与暂停/恢复，但未 Accepted，且明确「时长与日历
  需按试点填写 M1-04」「暂停原因必须以真实项目确认后启用」；
- 实施状态将 BUSINESS 日历 SLA 标为 R3 硬门禁。

## 3. 待负责人选择（回复组合）

### D1 — 首切片范围

| 选项 | 范围 | 说明 |
|---|---|---|
| **D1-R（推荐）** | 仅 BUSINESS 截止时间计算 | 配置 `clockMode=BUSINESS` + 锁定 `BusinessCalendarVersion`；纯函数算 `deadlineAt`；保留 ELAPSED；**不含**暂停/恢复/预警/升级/重算 |
| D1-B | BUSINESS + 暂停/恢复 | 在 D1-R 上增加 `PauseSla`/`ResumeSla`、PAUSED segment、`SlaPauseRecord`；需同时接受暂停原因枚举 |
| D1-C | 完整 ARCH-12 MVP | 日历 + 暂停 + 预警 + 升级 + 通知；过大，不推荐作单里程碑 |

### D2 — 日历权威与样例

| 选项 | 说明 |
|---|---|
| **D2-R（推荐）** | 配置资产 `BusinessCalendar` 版本化进 Bundle；首切片用**显式样例日历**（固定时区 + 周一～周五工作时段 + 若干节假日/调休日 JSON），不接外部节假日 API |
| D2-B | 租户级独立日历目录 + SLA 仅引用；需额外数据模型与治理 |
| D2-C | 运行时调用外部法定节假日服务；**禁止**（外部依赖 + 不可重放） |

### D3 — 暂停原因（仅当选 D1-B）

| 选项 | 说明 |
|---|---|
| D3-R | 启用研究候选五类：用户延期、物业阻塞、电力报装、物料缺货、不可抗力；无需审批/证明（对齐 confirmed facts） |
| D3-B | 仅配置化 reasonCode 白名单，首切片空名单 = 禁止暂停 |
| D3-C | 暂不接受暂停（与 D1-R 一致） |

### D4 — Subject / 事件面

| 选项 | 说明 |
|---|---|
| **D4-R（推荐）** | 仍限 Task + `TASK_CREATED`/`TASK_COMPLETED`（与 M61 同构），仅扩展 clockMode |
| D4-B | 同时扩展 WorkOrder/Dispatch 等 subject（超首切片） |

## 4. 推荐接受语句

```text
Accept ADR-090 with: D1-R, D2-R, D4-R
(M369 = BUSINESS clockMode + Bundle-locked sample BusinessCalendarVersion;
deadline pure function; no pause/resume/warning/escalation/recalc; keep ELAPSED)
```

若同时要暂停：

```text
Accept ADR-090 with: D1-B, D2-R, D3-R, D4-R
```

## 5. 明确不做（接受推荐组合时）

- 结算落账 / 考核处罚金额；
- 外部节假日 API；
- 预警、升级、通知、OperationalException 联动；
- 受控重算与免责；
- 未经确认的默认时长或项目兜底策略。

## 6. 后果（接受后）

- 开 Draft PR **M369**：ADR-090 Accepted + sla schema 扩展 + 日历资产/计算 + IT；
- 未接受前保持 `AWAITING_OWNER_ACCEPT`，不得编码。
