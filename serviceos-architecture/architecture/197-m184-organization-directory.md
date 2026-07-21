---
title: M184 企业组织与任职目录
status: Implemented
milestone: M184
lastUpdated: 2026-07-17
---

# M184 企业组织与任职目录

## 目标

在稳定 Principal（M183）之上建立企业 `Organization` / `OrgUnit` / closure / `OrgMembership`
运行时，支持 LOCAL 与 EXTERNAL_AUTHORITATIVE 主数据模式、同步收据，以及离职时的主体失权、
RoleGrant 终止与待重分配清单。

## 范围与非目标

- 范围：
  - 独立 `organization` 模块与 `org_` 表；
  - 多层 OrgUnit closure、循环/跨租户父节点拒绝；
  - 主职/兼职/负责人任职、调动与终止（只追加历史）；
  - 目录同步批次幂等、乱序跳过与部分失败逐项收据；
  - EXTERNAL_AUTHORITATIVE 字段普通写失败关闭，显式 override 除外；
  - 离职联动 Principal 停用、RoleGrant 撤销与 `ReassignmentWorkItem`；
  - 组织树/成员/同步/待办查询 API、能力、审计、幂等与 If-Match。
- 明确不做：
  - Partner Organization、ServiceNetwork、TechnicianProfile（M185）；
  - RoleGrant 申请/审批/委托治理 UI 与职责分离（M186）；
  - Admin 统一用户中心页面与真实 OIDC 治理 E2E（M187）；
  - Portal `/me` contexts/navigation（M188）；
  - 正式 HR/企业微信 Connector、双向回写、组织 scope 的 RoleGrant 匹配器扩展。

## 事实源

- `roadmap/03-identity-organization-governance-delivery-plan.md` §6
- `roadmap/04-identity-organization-governance-agent-worklist.md` §4
- `testing/identity-organization-governance-program-acceptance.md` §4
- `decisions/ADR-023-organization-directory-module.md`
- `architecture/19-engineering-module-blueprint.md` organization 行
- `architecture/196-m183-unified-principal-directory.md`

## 设计要点

### 模块与依赖

见 ADR-023。`organization` 通过消费者端口拿授权与撤权，通过 `identity.api` 停用主体，避免
`organization → authorization.api` 依赖，并保持 authorization 可依赖 `organization::api`。

### 不变量

- closure 是授权/树查询唯一下级依据，禁止字符串路径猜测；
- OrgUnit 移动与同步改父在同一事务重建闭包边；
- Membership 调动/终止只追加：先终止旧行再插入新行，不 UPDATE 历史区间起点；
- 同一主体在同一时刻最多一条有效 `PRIMARY` 任职；
- 跨租户父子、自环、把后代设为父节点一律失败关闭；
- EXTERNAL_AUTHORITATIVE 组织的结构/任职普通命令返回明确错误，不静默覆盖来源。

### 事务、幂等与锁

命令顺序：授权 → 幂等抢占 → 组织/单元/任职行锁 → 版本迁移 → 联动停用/撤权/待办 → 审计 →
幂等完成。同步批次以 `(tenant, sourceSystem, externalBatchKey)` 幂等；条目按
`externalVersion` 乱序时 SKIPPED 且不回退已更高版本。

离职联动在同一事务内：

1. 终止任职；
2. 调用 `PrincipalEmploymentLifecyclePort.disableForEmploymentTermination`；
3. 调用 `OrganizationRoleGrantPort.terminateActiveGrants`；
4. 写入 `OPEN` 待重分配清单。

### 能力

| Capability | 用途 |
|---|---|
| `organization.read` | 组织树、单元、任职、同步收据、待办查询 |
| `organization.manageStructure` | 创建组织/单元、移动单元 |
| `organization.manageMembership` | 任职、调动、终止 |
| `organization.sync` | 提交外部同步批次 |
| `organization.overrideExternal` | 在 EXTERNAL_AUTHORITATIVE 下显式人工覆盖 |

## 已实现

- Flyway `V087`：`org_organization` / `org_unit` / `org_unit_closure` / `org_membership` /
  `org_directory_sync_batch` / `org_directory_sync_item` / `org_reassignment_work_item` /
  `org_structure_event` 与五项能力；
- `organization` Modulith 模块与 HTTP API（见 Core OpenAPI 0.77.0）；
- LOCAL/EXTERNAL_AUTHORITATIVE 写门禁、closure 创建/移动/同步重建；
- 任职创建/调动（只追加）/终止；离职联动停用、撤权与待办；
- 同步批次幂等、乱序 SKIPPED、部分失败 `COMPLETED_WITH_ERRORS`；
- `OrganizationDirectoryPostgresIT`、`OrganizationControllerSecurityTest`、`ArchitectureTest`。

## 明确未实现

- 网点/师傅身份、合作组织目录；
- RoleGrant 申请审批、Delegation、authorization:explain；
- Admin 用户中心与组织治理 UI；
- Portal 上下文；正式外部 Connector 与回写；
- ORGANIZATION DataScope 匹配器、组织投影异步重建平台；
- `asTree=true` 的树形 JSON 投影（当前返回扁平列表）。

## 工程证据

- `db/migration/organization/V087__create_organization_directory.sql`
- `organization/application/DefaultOrganizationCommandService.java`
- `organization/infrastructure/JdbcOrganizationDirectoryRepository.java`
- `organization/web/OrganizationController.java`
- `identity/api/PrincipalEmploymentLifecyclePort.java`
- `authorization/application/JooqOrganizationRoleGrantAdapter.java`
- `serviceos-core-v1.yaml` 0.77.0
- `OrganizationDirectoryPostgresIT`
- `OrganizationControllerSecurityTest`
- `ArchitectureTest`
- `decisions/ADR-023-organization-directory-module.md`

## 验证命令

```bash
bash scripts/agent-verify.sh compile
bash scripts/agent-verify.sh it OrganizationDirectoryPostgresIT
bash scripts/agent-verify.sh test OrganizationControllerSecurityTest
bash scripts/agent-verify.sh arch
```
