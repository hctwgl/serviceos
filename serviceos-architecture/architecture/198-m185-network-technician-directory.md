---
title: M185 网点人员与师傅身份目录
status: Draft
milestone: M185
lastUpdated: 2026-07-17
---

# M185 网点人员与师傅身份目录

## 目标

建立独立于内部组织树的合作组织与 ServiceNetwork 目录，以及 NetworkMembership、
TechnicianProfile、多网点师傅关系与资质审核运行时；可接单判定失败关闭。

## 范围与非目标

- 范围：
  - `network` 模块与 `net_` 表；
  - PartnerOrganization、ServiceNetwork（不进入 OrgUnit closure）；
  - NetworkMembership 邀请（复用既有 Principal）；
  - TechnicianProfile 与 NetworkTechnicianMembership；
  - TechnicianQualification 只追加提交/总部裁决；
  - 可接单查询；清退/停用影响摘要与 clearance 待办；
  - HTTP API、能力、幂等、If-Match、审计、PostgreSQL/MVC/Architecture 证据。
- 明确不做：
  - 派单评分、硬过滤重跑、ServiceAssignment 改派自动化（dispatch）；
  - 完整离线工作包处理与 Technician App；
  - Admin 用户中心页面与真实 OIDC 治理 E2E（M187）；
  - RoleGrant 申请审批（M186）；Portal `/me`（M188）；
  - Coverage/Capability 地理围栏、合同、停派策略引擎。

## 事实源

- `roadmap/03-identity-organization-governance-delivery-plan.md` §7
- `roadmap/04-identity-organization-governance-agent-worklist.md` §5
- `testing/identity-organization-governance-program-acceptance.md` §5
- `decisions/ADR-024-network-technician-directory-module.md`
- `architecture/11-service-network-dispatch.md`（Proposed 背景，不外推未接受评分规则）
- `architecture/196-m183-*`、`architecture/197-m184-*`

## 设计要点

见 ADR-024。写命令：授权 → 幂等 → 行锁/版本 → 结构事件 → 审计。停用/清退同事务写入
clearance 待办并汇总影响端口结果；新可接单立即失败关闭。

## 已实现

- `network` Modulith 模块与 `net_` Flyway V088；
- PartnerOrganization、ServiceNetwork、NetworkMembership、TechnicianProfile、
  NetworkTechnicianMembership、TechnicianQualification 命令/查询与 HTTP `/api/v1` 适配器；
- `PrincipalStatusQuery`、`TechnicianEligibilityQuery`、`NetworkAssignedWorkImpactPort` 公开端口；
- 清退/停用 clearance 待办与 dispatch ACTIVE ServiceAssignment 影响统计；
- `NetworkDirectoryPostgresIT`、`NetworkControllerSecurityTest`、ArchitectureTest 纳入 `network`。

## 明确未实现

- Coverage/Capability 地理与品牌硬过滤引擎；
- 离线工作包强制回收运行时；
- Admin/Network Portal 用户中心页面；
- 自动改派未完成 Task。

## 工程证据

- `serviceos-backend/src/test/java/com/serviceos/network/NetworkDirectoryPostgresIT.java`
- `serviceos-backend/src/test/java/com/serviceos/network/web/NetworkControllerSecurityTest.java`
- `serviceos-backend/src/test/java/com/serviceos/ArchitectureTest.java`
- Flyway：`db/migration/network/V088__create_network_technician_directory.sql`

## 验证命令

```bash
bash scripts/agent-verify.sh it NetworkDirectoryPostgresIT
bash scripts/agent-verify.sh test NetworkControllerSecurityTest
bash scripts/agent-verify.sh arch
```
