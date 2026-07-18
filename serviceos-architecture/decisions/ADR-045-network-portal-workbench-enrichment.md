---
title: ADR-045：Network Portal 工作台能力门控计数增强
version: 1.0.0
status: Accepted
owner: Product Architecture
reviewers:
  - Engineering Architecture
  - Network Portal Owner
related_adrs:
  - decisions/ADR-032-network-portal-read-apis.md
  - decisions/ADR-040-network-portal-correction-queue.md
  - decisions/ADR-041-network-portal-exception-queue.md
  - decisions/ADR-043-network-portal-qualification-list.md
---

# ADR-045：Network Portal 工作台能力门控计数增强

## 1. 状态与已接受决策

本 ADR 作为 M207 的边界与授权结论，正式接受：

1. 扩展既有 `GET /api/v1/network-portal/workbench`（**不**新增路径）；**不**新建 portal 模块；
2. HTTP（Core OpenAPI `0.99.0`）：在 `NetworkPortalWorkbenchView` 上**附加**可选计数字段；
   既有必填字段与门禁不变（ACTIVE NetworkMembership + NETWORK `networkTask.read`）；
3. **附加字段**（均非 required；缺能力时 JSON **省略**该属性，不得用 `null`/`0` 表示无权限）：
   - `unassignedTechnicianTaskCount`：ACTIVE NETWORK 责任且无 TECHNICIAN assignee 的任务数
     （同 `networkTask.read`，工作台成功时始终给出）；
   - `openCorrectionCaseCount`：本网点 ACTIVE 任务上 OPEN CorrectionCase 数
     （需 NETWORK `evidence.read`）；
   - `openOperationalExceptionCount`：本网点 ACTIVE 任务上 OPEN OperationalException 数
     （需 NETWORK `operations.exception.read`）；
   - `pendingQualificationCount`：本网点 ACTIVE 师傅的 PENDING 资质数
     （需 NETWORK `technician.readOwnNetwork`）；
4. **能力策略**：对 enrichment 能力使用 `authorize`（非 `require`）；缺能力仅省略字段，
   **不**导致整页 workbench 失败；有能力时返回精确计数（不受 list `limit` 截断）；
5. **编排**：`DefaultNetworkPortalQueryService.workbench` fan-in 既有
   assignments / corrections.listForTask / exceptions.listForTask / qualifications 端口；
6. Page Registry：catalog → `page-registry-v14`（`NETWORK.WORKBENCH` 能力列表仍为
   `networkTask.read`）；
7. Admin Web：渲染 capacity 与新计数，并深链到 tasks/corrections/exceptions/qualifications；
8. **不**接受：SLA 风险计数、产能申请、Portal ACK/decide、Workbench 大字段发明、
   新 capability 种子、即将到期时间窗策略。

## 2. 上下文

product/03 §4 工作台需要待分配/待补资料/异常等发现计数；M202/M203/M205 已交付对应队列。
缺能力时省略字段对齐 api/06 受控搜索「缺类型能力则省略」先例，避免把无权限伪装成零。

## 3. 后果

- OpenAPI `0.98.0` → `0.99.0`；**无**新 Flyway（仍 100/102）；
- 工作台 UI 展示计数与深链；
- SLA 计数 / 产能申请若需要，须另接受切片。
