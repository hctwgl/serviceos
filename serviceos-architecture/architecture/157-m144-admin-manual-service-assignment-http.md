---
title: M144 Admin 人工初派 ServiceAssignment HTTP
status: Implemented
milestone: M144
lastUpdated: 2026-07-17
---

# M144 Admin 人工初派 ServiceAssignment HTTP

## 1. 范围

承接 M143，将 Visit 所需 ServiceAssignment 从 SPI 种子改为 **Admin HTTP 人工初派**：

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

完整 DispatchRequest/Decision/ServiceNetwork 生命周期仍属未实现。

## 3. 实现要点

1. `ManualServiceAssignmentService` 编排已 Implemented SPI；Controller 只做协议适配；
2. Core OpenAPI **0.72.0**；本地 RoleGrant 增加上述两能力；
3. 删除 M143 SPI 种子入口；冒烟 SQL 断言 `created_by` 为 Admin 主体。

## 4. 明确未实现

- 评分、硬过滤重跑、DispatchDecision、ServiceNetwork 目录生命周期；
- `dispatch.assignNetwork` 产品能力别名迁移；
- 改派 HTTP、专用入站队列页、真实 sandbox / 生产对象存储/专业扫描。
