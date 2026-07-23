---
title: ADR-024：独立 network 网点与师傅目录模块
version: 1.0.0
status: Accepted
owner: Product Architecture
reviewers:
  - Engineering Architecture
  - Dispatch Domain Owner
related_adrs:
  - decisions/ADR-001-modular-monolith-first.md
  - decisions/ADR-023-organization-directory-module.md
---

# ADR-024：独立 network 网点与师傅目录模块

## 1. 状态与已接受决策

本 ADR 作为网点与师傅目录的 Modulith 依赖评审结论，正式接受：

1. 合作组织、ServiceNetwork、NetworkMembership、TechnicianProfile、
   NetworkTechnicianMembership 与 TechnicianQualification 由独立模块 `network` 拥有，表前缀 `net_`；
2. Partner Organization / ServiceNetwork **不得**进入内部 `organization` 的 OrgUnit closure；
3. `TechnicianProfile` 与 `Principal` 通过稳定 UUID 关联，生命周期独立；账号 ACTIVE 不代表可接单；
4. `network` 允许依赖 `shared`、`identity::api`、`reliability::api`、`audit::api`；
5. 清退影响统计通过 `network.api` 消费者端口由 task/appointment/fieldwork/dispatch 实现，禁止
   `network` 直接读取其他模块内部表。

## 2. 上下文

交付计划要求网点人员与师傅身份落在服务网络边界，不得由 identity 或 authorization 直接拥有。
工程蓝图已预留 `network` 模块；稳定 Principal 已存在，内部组织与合作组织边界已经划清。

## 3. 决策细节

### 3.1 不拥有

- 登录身份与 IdentityLink（identity）
- 内部 OrgUnit/closure（organization）
- 派单评分、ServiceAssignment 权威（dispatch）
- 完整离线工作包运行时（Technician App 后续任务）

### 3.2 可接单判定

可接单（`canAcceptAssignment`）要求在同一时刻全部成立：

1. Principal 状态 ACTIVE；
2. TechnicianProfile 状态 ACTIVE；
3. 指定网点的 NetworkTechnicianMembership 有效且 ACTIVE；
4. 所需资质均 APPROVED 且未过期（默认要求至少一条有效资质；无资质策略显式失败关闭）。

### 3.3 邀请与绑定

网点邀请只创建/维护 `NetworkMembership`（人员）或 `NetworkTechnicianMembership`（师傅服务关系），
不创建第二套用户表；Principal 必须已存在。IdentityLink 绑定仍走 identity 命令。

## 4. 后果

- ArchitectureTest 纳入 `network`；
- dispatch 后续候选过滤应消费 `network.api` 的可接单查询，不得复制资质规则；
- 授权治理与客户端可引用 networkId，但网点目录权威仍属 `network`。
