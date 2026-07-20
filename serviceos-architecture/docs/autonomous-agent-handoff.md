---
title: ServiceOS 自主 Agent 交接
lastUpdated: 2026-07-20
---

# ServiceOS 自主 Agent 交接

## 当前

- **状态**：**AWAITING_OWNER_SELECTION**（M369 已 Implemented；下一切片需选型）
- 已合并候选栈 tip：`cursor/m369-business-calendar-sla-6a78`（Draft PR #198）
- 工程基线：**M369**（OpenAPI 仍 1.0.59 / Flyway 135；ADR-090 Accepted：D1-R/D2-R/D4-R）

## 请选型下一切片（回复字母或改写）

| 选项 | 切片 | 说明 | 风险 |
|---|---|---|---|
| **A** | iOS 条件执行器全量硬阻断 | 与 M349/M350 H5 及 Catalog 对齐，翻转 iOS 条件能力登记并硬拒 | 本 Linux Cloud Agent 环境多为 **`BLOCKED_EXTERNAL`**（需 macOS/Xcode） |
| B | 吉利真实 Sandbox 联调 | 凭据/签名/脱敏报文到位后优先 | **`BLOCKED_EXTERNAL`**（缺 URL/AK·SK·IV） |
| C | AMOUNT / 加权比例 | 配置计价口径 | **硬门禁**：需业务确认口径后方可实施 |
| D | BUSINESS 暂停/恢复（ADR-090 D1-B） | 在 M369 截止切片上增加 Pause/Resume、PAUSED segment、暂停原因 | **硬门禁**：需另接受 D1-B + D3 暂停原因；ARCH-12 仍 Proposed |
| E | OpenAPI 放宽 `clockMode`/`CALENDAR` | 闭合 M369 刻意留下的 HTTP 契约缺口（oasdiff 阻断原地去 const） | 需批准**破坏性演进**或新 OpenAPI 主版本 |
| F | 暂停 / 其他（请写明） | 例如 CLIENT `reviewTaskId`、editableWhen/defaultExpression 等已登记未实现项 | 不得发明未接受规则 |

未选型前 **不得** 开 M370 实现或推进 `latestMilestone`。

## 已闭环（本序列）

- M356～M363：Technician 客户端能力门禁 — #183～#190
- M364：独立审核 handling Task — #191
- M365：REVIEW_TASK 工作流门闸（A5-B）— #192
- M366：派单级 supportedClientKinds 过滤（A1-R～A5-R）— #193
- M367：Manual/Network assign kind 硬拒绝（A1-B）— #195
- M368：Network Portal on-behalf NETWORK_WEB 能力门禁 — #196
- M369：BUSINESS 日历 SLA 截止时间（ADR-090 D1-R/D2-R/D4-R）— #198
