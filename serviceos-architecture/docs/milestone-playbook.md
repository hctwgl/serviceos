---
title: ServiceOS 里程碑标准执行手册
---

# ServiceOS 里程碑标准执行手册

本手册把「一个里程碑从开工到合并」的循环固化为可复制的命令块，避免每个里程碑重新推导流程。
硬约束（模块边界、事务、验证阶梯、文档同步范围）以根 `AGENTS.md` 为准；本手册不放宽任何门禁。

## 0. 开工（约 5 分钟）

```bash
git status --short --branch
```

1. 按 `serviceos-architecture/docs/agent-navigation.md` 路由表确定最小阅读集（3～6 个文件），用 `bash scripts/find-milestone.sh <Mxx|关键词>` 定位最近一次同模块里程碑；
2. 写下本次范围与**明确不做的边界**；
3. 按根 `AGENTS.md` §3 选择 R0～R3 与对应验证命令；
4. 里程碑号默认取 `serviceos-architecture/docs/implementation-status.md` 的 `latestMilestone` +1；涉及已批准程序计划、撞号或被占用编号时，必须先按事实源完成重编号裁决，不得自行复用（编号冲突见 status §2.1）。

## 1. 文档骨架先行

```bash
cp serviceos-architecture/docs/templates/milestone-implementation-template.md \
   serviceos-architecture/architecture/<NNN>-m<xxx>-<slug>.md
cp serviceos-architecture/docs/templates/milestone-acceptance-template.md \
   serviceos-architecture/testing/<NNN>-m<xxx>-<slug>-acceptance.md
```

- 填 frontmatter（`milestone: Mxxx`、`status: Draft`）；先写「目标、范围与非目标、明确未实现」，再写设计；
- 架构与验收目录序号分别取各目录现有**数值最大值** +1，禁止用 `ls | tail` 的字典序结果。可用：

```bash
find serviceos-architecture/architecture -maxdepth 1 -type f -name '*.md' -exec basename {} \; \
  | sed -n 's/^\([0-9][0-9]*\)-.*/\1/p' | sort -n | tail -1
find serviceos-architecture/testing -maxdepth 1 -type f -name '*.md' -exec basename {} \; \
  | sed -n 's/^\([0-9][0-9]*\)-.*/\1/p' | sort -n | tail -1
```

  当前两个目录独立编号，且目录序号与里程碑号不强制一致；
- `README.md` 与 `testing/README.md` 的登记放到第 4 步文档同步一起做，不边写边登记。

## 2. 实施顺序与精准验证

按「契约 → 迁移 → 领域/应用/适配器 → 测试」推进，每个逻辑小步立即运行对应精准验证，不攒到最后：

| 步骤 | 内容 | 立即验证 |
|---|---|---|
| 契约 | `serviceos-contracts` 的 OpenAPI / 事件 Schema（已发布契约只新增版本，不原地破坏） | `bash scripts/agent-verify.sh contracts`（默认比较当前分支与 `origin/master` 的 merge-base） |
| 迁移 | `serviceos-backend/src/main/resources/db/migration/<module>/V<nnn>__*.sql`，版本号取全局最大 +1 | 对应 PostgreSQL IT |
| 代码 | 模块内小步提交；跨模块只走 `api`/`spi` 端口 | `bash scripts/agent-verify.sh compile` |
| 测试 | 领域单元测试 + `*PostgresIT`（SQL/锁/唯一约束/迁移语义必须真实 PostgreSQL） | `bash scripts/agent-verify.sh test <Class>` / `it <Class>` |
| 边界 | 模块声明、公开 API、跨模块调用/事件变化 | `bash scripts/agent-verify.sh arch` |
| Admin Web | 页面切片 | `npm run build`（在 `serviceos-admin-web/`） |

失败时先重跑直接失败的测试，不重复执行无关全量测试。

## 3. 里程碑门禁（标记 Implemented 前）

冻结最终候选 HEAD 后执行一次 L3，不要在中间迭代反复跑：

```bash
bash scripts/verify-local.sh
```

Agent 的非交互调用默认使用精简输出，完整日志保存在 `target/verification-logs/`；失败摘要包含
关键错误、日志尾部和完整路径。诊断时可用 `SERVICEOS_VERIFY_OUTPUT=full` 查看实时全量输出，
不得通过日志模式改变验证参数或接受失败结果。

`clean verify`（L4）只用于发布候选、怀疑缓存污染或明确要求。

## 4. 文档同步（按根 AGENTS.md §11 完整清单）

1. `architecture/Mxx` 实现文档与 `testing/Mxx` 验收矩阵定稿（`status: Implemented`）；
2. `serviceos-architecture/README.md` 与 `testing/README.md` 登记新文档；
3. `implementation-traceability-matrix.md` 增加里程碑行；
4. `implementation-status.md`：`lastUpdated`、`latestMilestone`、能力总览、下一方向（`baselineCommit` 允许合并后回填）；
5. `implementation-status-archive.md` 是冻结历史，不再追加新里程碑摘要；新里程碑范围只在实现文档、验收矩阵、追踪矩阵和状态总览中维护；
6. 从权威实现文档重新生成索引：

```bash
bash scripts/generate-milestone-index.sh
```

7. 根 `README.md` 当前可运行基线、被影响总体设计的已实现/未实现边界、适用契约与部署迁移清单。

## 5. 提交前门禁

```bash
bash scripts/agent-verify.sh docs              # git diff --check + 脚本语法 + 索引新鲜度
bash scripts/verify-milestone-preflight.sh     # 里程碑 PR 前必跑（一致性门禁 + staging 清单）
bash scripts/agent-verify.sh contracts         # 有契约变化时，最终候选再次对稳定分支基线检查
```

提交保持单一意图：实现与对应测试/文档同提交；`baselineCommit` 需回填时用紧随的独立文档提交。不混入无关格式化与用户已有修改。

## 6. CI 节奏（根 AGENTS.md §10.2）

- 中间 push 后不等待 GitHub Actions，继续用 L0～L2 本地精准验证推进；
- 同一 PR 旧运行被 concurrency 取消是预期收敛，不是失败；
- HEAD 冻结、本地门禁通过后，只通过 `workflow_dispatch` 等待一次完整远端验证；
- `container-staging` 只属于 master push、手工验证或发布候选。

## 7. 耗时陷阱（不要做）

- 不要对中间迭代跑全量 `verify` 或 `clean`（L3/L4 限定场景）；
- 不要机械 `cat` 大文件、整份读取 `milestone-index.md` 或全量 Read 目录找上下文，回到 `serviceos-architecture/docs/agent-navigation.md` 路由并使用 `scripts/find-milestone.sh`；
- 不要重拼 Maven 命令，统一用 `scripts/agent-verify.sh` 入口；
- 不要为「保险」重复运行已通过的无关测试；
- 可选本地加速（用户级，不写入仓库默认）：`~/.testcontainers.properties` 中开启 `testcontainers.reuse.enable=true` 可复用 PostgreSQL 容器；视为个人环境优化，不作为验证缩水理由。

## 8. 优化成效验收（先连续记录 3 个里程碑）

为避免只证明“流程被重新组织”，前 3 个使用本手册的里程碑在交付说明中记录以下数据；满 3 个后再决定是否保留记录：

| 指标 | 记录方式 | 目标 |
|---|---|---|
| 探索读取量 | 开工前实际打开的事实源文件数 | 常规任务 3～6 个；无目录级批量读取 |
| 探索耗时 | 从 `git status` 到写下范围/非目标的分钟数 | 能说明路由命中情况；异常时修路由表 |
| 精准验证耗时 | L1/L2 各命令耗时与重跑次数 | 失败只重跑直接相关项 |
| 全量验证次数 | L3/L4 实际执行次数 | 普通里程碑冻结候选后 L3 一次；无无效 clean |
| 文档维护面 | 实际修改的权威文档数量 | 不维护 archive 重复摘要；只按影响范围更新 |

流程优化本身的首个可核验基线：默认状态入口由 1,641 行降至 225 行（约减少 86%）；后续收益以真实里程碑记录为准，不以脚本存在代替耗时证据。
