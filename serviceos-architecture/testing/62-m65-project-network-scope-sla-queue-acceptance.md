---
title: M65 项目网点关系与 NETWORK SLA 队列验收矩阵
status: Implemented
lastUpdated: 2026-07-15
---

# M65 项目网点关系与 NETWORK SLA 队列验收矩阵

| ID | 优先级 | 场景 | 预期 |
|---|---|---|---|
| M65-PRJ-001 | P0 | 创建项目携带两个 NETWORK 引用 | 项目与两条有效期关系同事务提交，响应和事件稳定排序 |
| M65-PRJ-002 | P0 | networkIds 省略或空数组 | 不建立关系，不猜测默认网点 |
| M65-PRJ-003 | P0 | 空白、超长或重复 NETWORK 引用 | 项目/关系/审计/Outbox/幂等全部回滚 |
| M65-DB-001 | P0 | 关系 tenant 与 Project tenant 不一致 | 复合外键拒绝跨租户关系 |
| M65-AUTH-001 | P0 | NETWORK RoleGrant 命中当前有效项目关系 | 返回精确项目 UUID 集合，并与 PROJECT/REGION grant 取并集 |
| M65-AUTH-002 | P0 | 同 networkId 的其他租户或尚未生效关系 | 不进入授权集合 |
| M65-AUTH-003 | P0 | NETWORK 无匹配项目 | 403 + `PROJECT_SCOPE_MISSING` 拒绝审计，不扩大权限 |
| M65-SLA-001 | P0 | NETWORK 用户省略 projectId 查询 SLA 队列 | 单次解析授权项目集合并执行一条范围化 SQL |
| M65-CUR-001 | P0 | NETWORK 关系在翻页间变化 | scope digest 改变，旧游标失败关闭 |
| M65-CON-001 | P0 | OpenAPI 0.36.0 与 project.created@v3 | OpenAPI 兼容扩展；新增事件版本且 v1/v2 不变 |
| M65-MIG-001 | P0 | PostgreSQL 18 空库迁移 | 67 个迁移到达 V065，约束和范围索引存在 |
| M65-MOD-001 | P0 | authorization 调用 NETWORK 解析端口 | 只经 `authorization::api` 依赖倒置，ArchitectureTest 通过 |

## 未纳入本里程碑

ServiceNetwork 生命周期、覆盖/能力/资质/停派、关系修订 API、组织/区域层级、派单策略、Portal、
BUSINESS SLA 和通知不在 M65 验收范围内。
