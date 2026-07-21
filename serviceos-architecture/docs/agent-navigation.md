---
title: ServiceOS Agent 任务导航
---

# ServiceOS Agent 任务导航

本文件回答一个问题：**面对一个任务，最少读哪几个文件就可以开工。**

所有路径相对仓库根 `/Users/louis/code/serviceos`。阅读顺序固定为：

```text
1. serviceos-architecture/docs/implementation-status.md（约 225 行：基线、能力总览、下一方向）
2. 本文件中匹配任务类型的行（确定最小必读集）
3. 用 `bash scripts/find-milestone.sh <Mxx|关键词>` 定位具体 Mxx 文档（一行一里程碑）
4. 只读最小必读集；Mxx 文档先读 frontmatter 与「已实现/明确未实现」两节再决定是否深读
```

检索纪律：

- 通过 `scripts/find-milestone.sh` 查询里程碑号、模块名或关键词，不整份读取 `milestone-index.md`，也不批量通读 `architecture/`、`testing/` 目录；
- 只读与任务直接相关的 Mxx 文档（通常是最近一次同模块切片）；历史摘要细节查 `implementation-status-archive.md`，不回读状态总览找；
- 冲突优先级按根 `AGENTS.md` §2.2；代码与文档冲突时不默认代码正确；
- 探索阶段的 token 预算是「3～6 个文件」，超过就说明路由选错了，回到本表重新定位。

## 任务路由表

| 任务类型 | 最小必读（按序） | 按需阅读 |
|---|---|---|
| 开工定位：下一个任务是什么 | `serviceos-architecture/docs/implementation-status.md` §2/§2.1/§3/§5 | 身份治理序列 M183～M188：`serviceos-architecture/roadmap/03-identity-organization-governance-delivery-plan.md`、`serviceos-architecture/roadmap/04-identity-organization-governance-agent-worklist.md`、`serviceos-architecture/testing/identity-organization-governance-program-acceptance.md` |
| 后端新里程碑（通用） | ① 若已有 Accepted 设计：对应 Mxx 实现文档 + 验收矩阵；② `serviceos-architecture/docs/implementation-traceability-matrix.md` §2 该模块行；③ `grep -i <模块或关键词> serviceos-architecture/docs/milestone-index.md` 找最近一次同模块里程碑的实现文档 + 验收矩阵；④ `serviceos-backend/AGENTS.md` 该模块行 | 领域总体设计（`serviceos-architecture/architecture/00`～`21` 编号文档）相关章节、相关 ADR（`serviceos-architecture/decisions/`） |
| readmodel 时间线/工作区切片 | `serviceos-architecture/architecture/86-m73-work-order-core-execution-timeline.md`（投影基线）、`serviceos-architecture/architecture/97-m84-work-order-timeline-projection-checkpoint-rebuild.md`（checkpoint/重建）、index Grep `时间线\|工作区` 取最近一次同类切片、`serviceos-architecture/api/06-application-query-preference-http-api.md` 对应 Accepted 章节 | `serviceos-architecture/data/06-application-projection-preference-logical-model.md` |
| 授权查询/队列切片 | `serviceos-architecture/architecture/80-m67-authorized-project-directory-query.md`、`serviceos-architecture/architecture/81-m68-authorized-work-order-query.md`、index Grep `队列` 取最近一次（如 M99/M158）、`serviceos-architecture/architecture/07-identity-authorization-audit.md` 授权章节 | `serviceos-architecture/data/02-authorization-audit-logical-model.md` |
| Admin Web 切片 | index Grep `Admin` 取最近一次里程碑实现文档（如 M182）、`serviceos-architecture/docs/admin-pilot-readiness-baseline.md`、`serviceos-architecture/product/02-admin-operations-portal-spec.md` 相关页面章节、`serviceos-admin-web/src` 相邻页面代码 | `serviceos-architecture/product/07-page-action-permission-matrix.md` |
| BYD/集成切片 | `serviceos-architecture/architecture/69-m56-inbound-envelope-canonical-message-runtime.md`（入站基线）、`serviceos-architecture/architecture/71-m58-byd-review-submission-outbound-delivery.md`（外发基线）、`serviceos-architecture/architecture/13-integration-reliability.md`、`serviceos-contracts/src/main/resources/openapi/byd-cpim-v731.yaml` | ADR-010/014、index Grep `BYD\|OutboundDelivery` 取最近切片 |
| Evidence/审核整改切片 | `serviceos-architecture/architecture/66-m53-form-condition-evidence-reresolution-proposal.md`、`serviceos-architecture/architecture/68-m55-client-review-case-origin-runtime.md`、`serviceos-architecture/architecture/10-evidence-review-correction.md` | ADR-008/018/022 |
| SLA 切片 | `serviceos-architecture/architecture/74-m61-task-elapsed-sla-clock.md`、`serviceos-architecture/architecture/75-m62-sla-authorized-query-projection.md`、`serviceos-architecture/architecture/12-sla-clock-escalation.md` | index Grep `SLA` 取最近切片 |
| 契约变更（OpenAPI/事件 Schema） | `serviceos-contracts/README.md`、目标 yaml/schema、根 `AGENTS.md` §7 | `serviceos-architecture/architecture/26-contract-ci-client-generation-implementation.md`（M12 门禁原理） |
| Flyway/数据迁移/数据访问 | `serviceos-architecture/decisions/ADR-091-jooq-unified-data-access.md`（jOOQ 统一数据访问与工程约束）、对应模块 `serviceos-backend/src/main/resources/db/migration/<module>/` 最新若干文件 | 相邻迁移的 PostgreSQL IT、`serviceos-architecture/architecture/36-persistence-engineering-guideline.md`（历史参考，选型条款已被 ADR-091 取代） |
| 纯文档/状态维护 | 当前文件及直接引用、`serviceos-architecture/docs/implementation-status.md` §7 维护规则、`serviceos-architecture/docs/milestone-playbook.md` §文档同步 | 根 `AGENTS.md` §11 |

## 明确不做

- 任何任务都不得因为「找上下文」而 `cat` 或全量 Read 整个 `architecture/`、`testing/`、`docs/` 目录；
- 身份治理序列已重编号为 M183～M188；不得再使用与 Admin Pilot 历史冲突的旧 M135～M140 编号（见 `implementation-status.md` §2.1）；
- 「候选下一方向」清单（status §5）中的条目在明确批准前不得当作任务直接实现。
