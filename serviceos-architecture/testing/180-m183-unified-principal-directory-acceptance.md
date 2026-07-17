---
title: M183 统一主体目录验收矩阵
status: Implemented
milestone: M183
lastUpdated: 2026-07-17
---

# M183 统一主体目录验收矩阵

| ID | 场景 | 期望 | 自动化入口 |
|---|---|---|---|
| M183-01 | 允许 tenant/client 的 OIDC USER 首次并发登录 | 只创建一个 Principal、Profile、IdentityLink 和注册事实，所有请求返回同一稳定 ID | `IdentityDirectoryPostgresIT.concurrentFirstLoginCreatesOneStablePrincipal` |
| M183-02 | 未允许 client、未知 SERVICE 或未登记身份登录 | 默认失败关闭，不创建主体 | `IdentityDirectoryPostgresIT.unknownContextAndServicePrincipalFailClosed` |
| M183-03 | 同一主体追加第二个 issuer/subject | 第二身份解析为同一 Principal；唯一冲突返回 409 | `IdentityDirectoryPostgresIT.secondIdentityResolvesSamePrincipalAndDisableRejectsOldJwtImmediately` |
| M183-04 | 停用主体后使用旧 JWT 对应身份请求 | 实时目录状态拒绝，返回 ACCESS_DENIED | 同上 |
| M183-05 | 普通目录与敏感身份查询 | 普通响应无 issuer/subject；敏感接口要求独立 capability | `SecurityPrincipalControllerSecurityTest`、`IdentityDirectoryPostgresIT` |
| M183-06 | Profile 员工号并发/唯一冲突 | 显式 409，失败事务不推进聚合版本 | `IdentityDirectoryPostgresIT.profileEmployeeNumberConflictReturnsExplicitConflictAndRollsBackVersion` |
| M183-07 | JWT 到稳定 Principal 映射 | tenant/issuer/client 缺失失败；外部 subject 不再直接成为内部 ID | `SecurityContextCurrentPrincipalProviderTest` |
| M183-08 | 模块边界 | identity 通过自有授权端口由 authorization 适配，无模块环 | `ArchitectureTest` |
| M183-09 | Core OpenAPI 与客户端生成 | 0.76.0 可解析，生成客户端可复现，新增路径/模型完整 | `serviceos-contracts` tests、客户端生成门禁 |
| M183-10 | Flyway 与全仓门禁 | 原生 PostgreSQL 完成 88 个迁移至 V086；L3 verify 全绿 | `IdentityDirectoryPostgresIT`、`scripts/verify-local.sh` |

## 完成门禁

- 不保存密码或 Secret；IdentityLink 与生命周期事实不可变；
- tenant 只来自受信 Principal，RoleGrant 实时校验并记录允许/拒绝审计；
- JIT 默认关闭且并发安全，未知 SERVICE 失败关闭；
- 命令具备幂等、If-Match、事务回滚和 409 冲突语义；
- PostgreSQL、MVC 安全、OpenAPI、客户端生成、ArchitectureTest 与 L3 均通过后才能改为 Implemented。

## 明确不做

- M184～M188 的组织、网点/师傅、授权治理、Admin 用户中心和 Portal context；
- 身份解绑、密码管理、身份缓存和跨服务 Broker 事件。
