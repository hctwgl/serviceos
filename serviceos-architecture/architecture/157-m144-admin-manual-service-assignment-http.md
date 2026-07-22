---
title: M144 Admin 人工初派 ServiceAssignment HTTP
status: Implemented
milestone: M144
lastUpdated: 2026-07-17
---

# M144 Admin 人工初派 ServiceAssignment HTTP

> 历史实现说明：本里程碑证明的 Admin 双责任 HTTP 入口已被 M453 的正式责任链替代并删除。
> 内部双责任事务编排仍由 Network Portal 指派师傅用例复用；当前外部产品契约只允许平台派网点、网点派师傅。

## 1. 范围

承接 M143，当时将 Visit 所需 ServiceAssignment 从 SPI 种子改为 **Admin HTTP 人工初派**：

```text
POST /api/v1/tasks/{taskId}/service-assignments:manual-assign
→ 确保容量 → prepare → confirmTaskPrepared → activate → complete
→ ACTIVE NETWORK + TECHNICIAN（同事务）
```

覆盖 field-ops 与 CPIM 入站 Playwright 路径。入站同单证明：

```text
接单 → Admin 派单(HTTP) → 预约 → 上门 → 表单/资料 → 审核/整改 → 外发 → 完结
```

即 `ADMIN-PILOT-09`（派单为窄化 Manual Assign，非完整评分/硬过滤引擎）。

## 2. Accepted 窄化决策（相对 Proposed）

相对仍为 Proposed 的 `api/04`、`architecture/11`、`product/07`、`ADR-009`，本里程碑**仅接受**下列试点表面：

| 项 | Accepted 选择 |
|---|---|
| 路径 | `POST /tasks/{taskId}/service-assignments:manual-assign`（非 dispatch-requests） |
| 能力 | 复用 Flyway 已有 `dispatch.assignment.manage` + `dispatch.capacity.configure`（非 product `dispatch.assignNetwork`） |
| 语义 | 初派 NETWORK+TECHNICIAN；不跑评分/硬过滤；合成 Task 握手与 authority/fence |
| 改派 | 不做；已有不同 ACTIVE 责任则冲突失败关闭 |
| UI | 工单工作区最小人工初派控件；无 `/dispatch` 队列页 |

该试点表面不再是当前可用契约；M453 删除了双责任 HTTP 路径和 Admin 控件，并用权威候选、提交时重校验和两阶段责任链替代。

## 3. 实现要点

1. `ManualServiceAssignmentService` 编排已 Implemented SPI；Controller 只做协议适配；
2. 当时的 Core OpenAPI 为 **0.72.0**；该路径已从当前 OpenAPI 删除；
3. 删除 M143 SPI 种子入口；冒烟 SQL 断言 `created_by` 为 Admin 主体。

## 4. 明确未实现

- 评分、硬过滤重跑、DispatchDecision、ServiceNetwork 目录生命周期；
- `dispatch.assignNetwork` 产品能力别名迁移；
- 改派 HTTP、专用入站队列页、真实 sandbox / 生产对象存储/专业扫描。
