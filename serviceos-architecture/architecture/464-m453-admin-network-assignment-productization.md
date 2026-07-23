---
title: M453 Admin 责任网点候选与分配产品化
status: Implemented
milestone: M453
---

# M453 Admin 责任网点候选与分配产品化

## 目标

把 Admin 工单工作区中的责任网点分配从演示输入改为可解释、可授权、失败关闭的正式产品链路。用户只能从服务端依据工单冻结配置和当前运营事实计算出的合格候选中选择网点；提交时服务端再次执行硬规则校验，防止候选过期后绕过项目、区域、状态或容量约束。

## 范围与非目标

- 范围：
  - 增加 Task 维度的责任网点候选查询契约和 `dispatch.read` 项目范围授权；
  - 复用自动派单的项目网点、启用状态、服务覆盖、业务类型、容量和冻结派单策略计算；
  - 人工派网点提交前重新计算候选，并只允许当前仍合格的网点；
  - 删除 Admin 工作区的演示网点/师傅标识、自由业务类型输入和跳过网点接单的双责任操作；
  - 候选页面展示中文网点名称、覆盖摘要、剩余容量和推荐解释，不以技术标识代替产品名称。
- 明确不做：
  - 不改变 Network Portal 的接单与派师傅链路；
  - 不新增跨区兜底、无容量自动扩容或普通人工特批；
  - 不新增派单策略、容量或覆盖数据模型，也不修改数据库结构。

## 事实源

- `serviceos-architecture/architecture/11-service-network-dispatch.md` §4、§7、§10～§11；
- `serviceos-architecture/product-design/decisions/DEC-005-work-order-responsibility-chain-and-dispatch-command-boundary.md`；
- `serviceos-architecture/product/admin/12-classic-professional-visual-baseline.md`；
- `serviceos-architecture/product/07-page-action-permission-matrix.md`；
- M332/M338 冻结派单运行时与比例缺口实现；
- M370～M377 Admin 产品化基线。

## 设计要点

1. 候选计算与自动派单共享同一应用层评估器，不允许前端拼接目录和容量接口自行判断资格。
2. 查询先由 Task 权威事实确定 tenant/project，再以 `dispatch.read` 执行 PROJECT Scope 授权；客户端不能提交 projectId 或 tenantId。
3. 候选计算使用 Task 冻结的 Bundle 与 `dispatchPolicyRef`，并结合当前 ACTIVE 网点、服务覆盖和容量；缺少冻结配置、区域事实或候选时明确失败/返回空态，不猜测默认项目、区域或网点。
4. 人工派网点命令在同一事务中重新评估目标网点并读取最新容量版本，然后才进入容量预占和激活 saga；候选已失效时整个命令失败，不产生半成品责任关系。
5. 业务类型由工单权威 `serviceProductCode` 决定。为保持当前已发布请求结构，本里程碑只接受与权威值完全一致的 `businessType`，不允许客户端改变语义。
6. 原有 `ensureCapacity` 自动创建/扩容行为不再用于人工派网点；没有已配置且有剩余量的容量即为不合格候选。

## 已实现

- 已形成 `NetworkDispatchCandidateEvaluator` 唯一评估器，自动派单、Admin 候选查询和人工派网点命令共享项目网点、ACTIVE 状态、服务覆盖、业务类型、现有容量和冻结策略硬过滤。
- 已新增 Task 维度责任网点候选查询与 `dispatch.read` PROJECT Scope 授权，返回中文网点名称、覆盖摘要、剩余容量和可解释排序。
- 人工派网点命令会重新评估候选、校验权威业务类型并使用评估时容量版本预占；无容量不会创建或扩容，竞争变化由容量版本冲突失败关闭。
- Admin 工单工作区已删除演示网点/师傅标识、自由业务类型输入和双责任快捷入口，只允许从服务端当前候选中选择责任网点。
- 已删除 Admin 历史双责任 HTTP 接口及请求契约；平台只能派责任网点，师傅必须由具有可信 NETWORK 上下文的网点端另行指派。内部原子编排继续供网点端复用，不构成平台入口。
- Core OpenAPI 升级为 2.0.0，明确表达删除历史 Admin 双责任路径的破坏性产品契约边界；无数据库迁移。
- TECHNICIAN 服务责任统一保存师傅档案 ID；Technician Portal 通过权威目录把登录主体解析为唯一档案后查询责任，Task、预约和现场操作继续使用登录主体 ID。只读工作区允许展示尚未绑定有效主体的师傅档案，代表师傅执行的命令仍按有效主体映射失败关闭，不保留两种责任标识双读。
- Admin 真实 OIDC 长链路已按“平台派网点 → 网点派师傅”完成工单领取、预约上门、资料整改补传和复审；项目详情快速切换时固定本次请求项目，避免过期请求污染共享认证状态。

## 明确未实现

- 普通人工覆盖不可覆盖硬规则；
- 跨区、跨项目或无容量兜底；
- 候选历史快照与独立 `DispatchDecision` 查询页面。

## 工程证据

- HTTP 安全：`NetworkAssignmentCandidateControllerSecurityTest`、`ManualServiceAssignmentControllerSecurityTest`；
- PostgreSQL：`DispatchPolicyServiceAssignmentPostgresIT`、`ManualServiceAssignmentPostgresIT`、`NetworkPortalAcceptAssignmentPostgresIT`；
- 相邻身份与读模型回归：`AppointmentPostgresIT`、`VisitPostgresIT`、`TechnicianPortalFeedPostgresIT`、`WorkOrderWorkspacePostgresIT`、Network Portal 整改/异常/工作台 PostgreSQL IT；
- Admin：`network-assignment-productization.test.mjs`、`admin-productization-smoke.spec.ts`、生产构建；
- 真实浏览器：Admin Pilot 入站工单完整链路与预约/上门链路均通过真实 OIDC、Backend、PostgreSQL 和浏览器验证；
- 契约：OpenAPI 兼容门禁、TypeScript/Swift 6 客户端生成与消费门禁；
- 模块边界：`ArchitectureTest`；
- 完整 L3：`bash scripts/verify-local.sh` 通过，Backend 与 Contracts Reactor 均为 `SUCCESS`。

## 验证命令

```bash
bash scripts/agent-verify.sh contracts
bash scripts/agent-verify.sh test <M453相关单元测试>
bash scripts/agent-verify.sh it <M453相关PostgresIT>
bash scripts/agent-verify.sh arch
(cd serviceos-admin-web && npm run build)
bash scripts/verify-local.sh
```
