---
title: ServiceOS 自主 Agent 交接
lastUpdated: 2026-07-20
---

# ServiceOS 自主 Agent 交接

## 当前

- **状态**：**AWAITING_OWNER_ACCEPT**（负责人选型 **D = BUSINESS 日历 SLA**；设计未接受，不得实现）
- 栈 tip：`cursor/m368-onbehalf-network-web-capability-6a78`（M368 / #196）
- 选型文档分支：`cursor/m369-awaiting-owner-selection-6a78`（#197 及本更新）
- 工程基线：**M368**（OpenAPI 1.0.59 / Flyway 134）

## 为何不能直接开 M369

1. ARCH-12（SLA 时钟/日历/暂停）状态仍为 **Proposed**；
2. M1-04 试点日历/时长未填写；暂停原因未项目确认；
3. 现网 `sla-v1` 仅允许 `clockMode=ELAPSED`；
4. 实施状态将 BUSINESS 日历 SLA 标为 **R3 硬门禁**。

## 请接受 ADR-090 组合（回复 Accept 语句或改写）

事实源：`serviceos-architecture/decisions/ADR-090-business-calendar-sla-slice.md`

| 推荐 | 含义 |
|---|---|
| **D1-R** | 仅 BUSINESS 截止计算（不含暂停） |
| **D2-R** | Bundle 内版本化样例日历（无外部节假日 API） |
| **D4-R** | 仍限 Task + TASK_CREATED/TASK_COMPLETED |

### 推荐接受语句

```text
Accept ADR-090 with: D1-R, D2-R, D4-R
(M369 = BUSINESS clockMode + Bundle-locked sample BusinessCalendarVersion;
deadline pure function; no pause/resume/warning/escalation/recalc; keep ELAPSED)
```

若要坚持暂停/恢复同切片，改用：

```text
Accept ADR-090 with: D1-B, D2-R, D3-R, D4-R
```

未收到上述 Accept（或等价书面批准）前 **不得** 实现 M369 / 推进 `latestMilestone`。

## 已闭环

- M356～M363：Technician 客户端能力门禁 — #183～#190
- M364～M365：审核 Task / REVIEW_TASK 门闸 — #191～#192
- M366～M367：派单 kind 过滤 / Manual 硬拒绝 — #193、#195
- M368：Network Portal on-behalf NETWORK_WEB 门禁 — #196
