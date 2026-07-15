---
title: M64 项目区域关系与 REGION SLA 队列验收矩阵
status: Implemented
lastUpdated: 2026-07-15
---

# M64 项目区域关系与 REGION SLA 队列验收矩阵

| ID | 优先级 | 场景 | 预期 |
|---|---|---|---|
| M64-PRJ-001 | P0 | 创建项目携带两个 REGION 引用 | 项目与两条有效期关系同事务提交，响应和事件为稳定排序 |
| M64-PRJ-002 | P0 | regionCodes 省略或空数组 | 不建立关系，不猜测默认区域 |
| M64-PRJ-003 | P0 | 空白、超长或重复 REGION 引用 | 命令失败，项目/关系/审计/Outbox/幂等均不产生部分结果 |
| M64-DB-001 | P0 | 关系 tenant 与 Project tenant 不一致 | 复合外键拒绝跨租户关系 |
| M64-AUTH-001 | P0 | REGION RoleGrant 命中当前有效项目关系 | 返回精确项目 UUID 集合并与 PROJECT grant 取并集 |
| M64-AUTH-002 | P0 | 同 REGION 的其他租户或尚未生效关系 | 不进入授权集合 |
| M64-AUTH-003 | P0 | REGION 无匹配项目 | 403 + `PROJECT_SCOPE_MISSING` 拒绝审计，不返回 tenant 全量 |
| M64-AUTH-004 | P0 | 只有 NETWORK RoleGrant | 403 + `PROJECT_SCOPE_UNRESOLVED`，不从地址或名称猜测 |
| M64-SLA-001 | P0 | REGION 用户省略 projectId 查询 SLA 队列 | 解析项目集合并复用一条范围化 SQL 返回匹配实例 |
| M64-CUR-001 | P0 | REGION 关系在翻页间变化 | 最终 scope digest 改变，旧游标失败关闭 |
| M64-CON-001 | P0 | OpenAPI 0.35.0 与 project.created@v2 | OpenAPI 为兼容扩展，事件新增版本且 v1 不变，契约门禁与客户端生成通过 |
| M64-MIG-001 | P0 | PostgreSQL 18 空库迁移 | 66 个迁移到达 V064，约束与范围索引存在 |
| M64-MOD-001 | P0 | authorization 调用 REGION 解析端口 | 只经 `authorization::api` 依赖倒置，ArchitectureTest 通过 |

## 未纳入本里程碑

NETWORK/组织关系、Region 层级后代、关系修订 API、缓存/导出、Portal，以及 BUSINESS SLA 与通知不在
M64 验收范围内，不能由本矩阵外推为已完成。
