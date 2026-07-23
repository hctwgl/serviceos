---
title: ADR-088：派单级 supportedClientKinds 硬过滤
status: Accepted
date: 2026-07-20
owner: Product Architecture
reviewers:
  - Engineering Architecture
  - Dispatch Owner
  - Technician Portal Owner
related_adrs:
  - decisions/ADR-009-dispatch-filter-score-reserve.md
accepted:
  - A1-R
  - A2-R
  - A3-R
  - A4-R
  - A5-R
acceptedAt: 2026-07-20
amendments:
  - id: A1-B
    acceptedAt: 2026-07-20
    note: Manual + Network Portal assign/reassign 硬拒绝不兼容师傅（422 + DENY 审计）
---

# ADR-088：派单级 supportedClientKinds 硬过滤

## 1. 状态

**Accepted**（负责人确认：`A1-R, A2-R, A3-R, A4-R, A5-R`）。
背景：FORM/EVIDENCE 资产级 `supportedClientKinds` 定向发布、运行时目标外拒单和客户端预检已经存在。
决策提出时，派单（自动 TECHNICIAN 池、人工/网点指派）**仍不**读取
`supportedClientKinds`，可能把工单指给目标外端师傅，再在执行时 422。

## 2. 问题

需要在**指派时刻**按冻结 Bundle 上 FORM/EVIDENCE 的 `supportedClientKinds` 硬过滤师傅候选，
使“不可在该端履约”的师傅不会进入有效 TECHNICIAN 责任，与现有运行时失败关闭语义对齐。

工程缺口（事实）：

- `TechnicianProfile` / 网点师傅目录**无**权威 `clientKind` 字段；
- `DefaultDispatchRuntime` 硬过滤轴不含 clientKind；
- Manual / Network Portal 指派路径不重跑配置定向目标硬过滤。

## 3. 必须由负责人确认的决策点（A1～A5）

### A1 — 生效路径

| 选项 | 含义 |
|---|---|
| **A1-R（推荐）** | **仅自动 TECHNICIAN 派单池**（`activateTechnicianFromFrozenDispatchPolicy` 候选）硬过滤；Manual / Network Portal 指派仍允许指定，但须返回可解释警告或同步预检（见 A3）。 |
| A1-B | 自动 + Manual + Network assign/reassign **全部**硬拒绝不兼容师傅。 |
| A1-C | 仅 Network Portal；Admin 人工豁免。 |

**推荐 A1-R**：先堵住自动池“静默指错端”的主风险；人工路径保留运营覆盖，失败关闭放在现有执行门禁与可选预检。

### A2 — 师傅 clientKind 权威来源

| 选项 | 含义 |
|---|---|
| **A2-R（推荐）** | 新增师傅侧**声明能力**（如 `TechnicianProfile.supportedClientKinds` 或独立只追加声明），派单只读该声明；未声明 = 仅兼容“资产目标未定向（null）”的任务，定向发布任务失败关闭剔除。 |
| A2-B | 用最近一次成功 Portal 请求头 `X-ServiceOS-Client-Kind` 作为推断（弱、可伪造客户端视角，不推荐作唯一权威）。 |
| A2-C | 假设师傅同时具备 H5+iOS，直到显式声明（与定向发布“缩小目标”冲突，不推荐）。 |

**推荐 A2-R**：派单硬过滤需要可审计的服务端权威；请求头只证明“当前会话”，不能证明“可被派往该端”。

### A3 — 失败关闭结果

| 选项 | 含义 |
|---|---|
| **A3-R（推荐）** | 自动池过滤后为空 → 落入 **TECHNICIAN MANUAL**（可解释原因码，如 `CLIENT_KIND_TARGET_EMPTY`）；若接受 A1-B，Manual 不兼容 → **422** + 拒绝审计。 |
| A3-B | 自动池为空时跨区/放宽目标（禁止：破坏定向发布语义）。 |
| A3-C | 仅软警告，仍激活 TECHNICIAN（强制关闭：与失败关闭原则冲突）。 |

**推荐 A3-R**。

### A4 — 与定向发布目标的求交规则

| 选项 | 含义 |
|---|---|
| **A4-R（推荐）** | 取冻结 Bundle 内**本任务相关** FORM∩EVIDENCE 资产的 `supportedClientKinds`：**任一资产非空则取交集**；全 null = 不施加派单级 kind 过滤（仍受客户端能力并集门禁约束于执行时）。师傅声明集合必须与该有效目标有非空交集才可通过。 |
| A4-B | 只看 EVIDENCE；忽略 FORM。 |
| A4-C | 取并集（过宽，可能派给只能做一端的师傅去跑两端缺口任务）。 |

**推荐 A4-R**：与“定向子集发布”收紧语义一致。

### A5 — 与执行门禁的关系

| 选项 | 含义 |
|---|---|
| **A5-R（推荐）** | 派单过滤是**前置硬过滤**；Feed/详情与执行拒单仍保留（防声明变更、Bundle 重绑、人工覆盖）。不删除执行门禁。 |
| A5-B | 派单过滤通过后执行路径跳过 kind 检查（禁止：声明可变时不安全）。 |

**推荐 A5-R**。

## 4. 明确非目标（接受后首切片仍可不做）

- Network Portal on-behalf / `NETWORK_WEB` 代师傅语义（handoff 选项 B，另案）；
- iOS 条件执行器全量硬阻断；
- clientVersion 下限策略表；
- 改写 ADR-009 评分模型（本 ADR 只把 kind 目标纳入**硬过滤轴**）；
- 自动跨区回退。

## 5. 建议实施切片

1. A2-R：师傅声明能力持久化（Flyway + API/目录投影，最小读写）；
2. 冻结 Bundle 目标求交工具（复用现有侧表/Probe）；
3. 自动 TECHNICIAN 候选硬过滤 + MANUAL 回退原因；
4. 若接受 A1-B：Manual/Network 422 路径；
5. 审计/派单解释字段；IT + ArchitectureTest；文档 Implemented。

## 6. 接受方式

负责人在本 ADR 或回复中明确：

```text
Accept ADR-088 with: A1-R, A2-R, A3-R, A4-R, A5-R
```

或给出替代组合。接受后将 `status` 改为 `Accepted`，再按完整用例实现。

## 7. 后果（接受后）

- 定向发布任务不再被自动派给声明端不匹配的师傅；
- 执行门禁与派单硬过滤分层，声明变更仍失败关闭；
- 需新增师傅 clientKind 声明的权威数据面（A2-R）。
