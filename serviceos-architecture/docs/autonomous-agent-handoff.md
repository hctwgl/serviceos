---
title: ServiceOS 自主 Agent 交接
lastUpdated: 2026-07-20
---

# ServiceOS 自主 Agent 交接

## 当前

- **状态**：**READY_FOR_REVIEW**（M369 实现完成，待负责人选型下一切片）
- 进行中 Draft PR：M369 BUSINESS 日历 SLA 截止时间（**Implemented**）
- 分支：`cursor/m369-business-calendar-sla-6a78`
  （base：`cursor/m368-onbehalf-network-web-capability-6a78`）
- 工程基线：**M369**（OpenAPI 1.0.60 / Flyway 135；ADR-090 Accepted：D1-R/D2-R/D4-R）

## 已接受并实现（本切片）

```text
Accept ADR-090 with: D1-R, D2-R, D4-R
(M369 = BUSINESS clockMode + Bundle-locked sample BusinessCalendarVersion;
deadline pure function; no pause/resume/warning/escalation/recalc; keep ELAPSED)
```

## 下一切片候选（需负责人选型，不得发明）

1. iOS 条件执行器全量硬阻断（本 Linux 环境多为 `BLOCKED_EXTERNAL`）；
2. 吉利联调 / AMOUNT/加权（硬门禁）；
3. BUSINESS 暂停/恢复/预警/升级（需另接受 D1-B 或 ARCH-12 子集）。

## 已闭环

- M356～M363：Technician 客户端能力门禁 — #183～#190
- M364：独立审核 handling Task — #191
- M365：REVIEW_TASK 工作流门闸（A5-B）— #192
- M366：派单级 supportedClientKinds 过滤（A1-R～A5-R）— #193
- M367：Manual/Network assign kind 硬拒绝（A1-B）— #195
- M368：Network Portal on-behalf NETWORK_WEB 能力门禁 — #196
- M369：BUSINESS 日历 SLA 截止时间 — 本 PR
