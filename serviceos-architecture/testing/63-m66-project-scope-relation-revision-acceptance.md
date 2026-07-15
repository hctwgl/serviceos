---
title: M66 项目范围关系整组修订验收矩阵
status: Implemented
lastUpdated: 2026-07-15
---

# M66 项目范围关系整组修订验收矩阵

| ID | 优先级 | 场景 | 预期 |
|---|---|---|---|
| M66-PRJ-001 | P0 | 显式替换 REGION/NETWORK 整组关系 | Project 版本递增；删除关系结束、新增关系追加、未变关系保持 |
| M66-PRJ-002 | P0 | 某集合为空数组 | 明确终止该类型全部开放关系，不猜测默认关系 |
| M66-PRJ-003 | P0 | 缺少任一集合、reason 或引用非法 | 400，项目/关系/审计/Outbox/幂等均无新增 |
| M66-CON-001 | P0 | 陈旧 If-Match 或并发修订 | 仅一个条件更新成功；失败命令不产生部分关系变化 |
| M66-NOP-001 | P0 | 新旧两个集合完全相同 | 明确冲突，不递增版本、不伪造审计或事件 |
| M66-IDM-001 | P0 | 相同幂等键与相同载荷重放 | 返回首次冻结结果；不同载荷返回 `IDEMPOTENCY_KEY_REUSED` |
| M66-TX-001 | P0 | 关系写入、Audit 或 Outbox 失败 | Project 版本和全部关系变更一并回滚 |
| M66-AUTH-001 | P0 | 无 HIGH capability 或跨租户项目 | 403/404 且拒绝审计；无业务副作用 |
| M66-SCOPE-001 | P0 | 修订后解析 REGION/NETWORK grant | 删除立即失效，新增立即生效，tenant/有效期精确匹配 |
| M66-CUR-001 | P0 | 修订发生在 SLA 队列翻页间 | scope digest 改变，旧游标失败关闭 |
| M66-EVT-001 | P0 | 修订成功 | `project.scope-relations-revised@v1` 冻结完整集合、差异、原因和新版本 |
| M66-MIG-001 | P0 | PostgreSQL 18 空库迁移 | V066 capability、开放关系唯一索引与结束审计约束存在 |
| M66-MOD-001 | P0 | 模块边界验证 | Project 仅经公开 Authorization/Audit/Reliability API，ArchitectureTest 通过 |

## 未纳入本里程碑

ServiceNetwork/Region/Organization 目录与生命周期、计划生效和审批工作流、项目生命周期、Portal、
派单策略、BUSINESS SLA、通知和结算不在 M66 验收范围内。

## 自动化证据

- `ProjectCommandPostgresIT` 18 项通过；
- `AuthorizationPolicyPostgresIT` 8 项、`SlaClockPostgresIT` 11 项通过；
- `ProjectControllerSecurityTest`、Project 领域与应用测试共 11 项通过；
- `ArchitectureTest` 2 项通过；
- PostgreSQL 18.4 空库执行 68 个迁移到 V066；
- Contracts 36 项、OpenAPI 兼容和 TypeScript 客户端可重复生成通过；
- `bash scripts/verify-local.sh` 全量 L3 通过。
