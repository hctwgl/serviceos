---
title: Agent 实施流程优化交接评审文档
version: 1.1.0
status: Proposed
review_date: 2026-07-17
baseline: latestMilestone M182 / Flyway 085 / 87 migrations
---

# Agent 实施流程优化交接评审文档

本文档面向**零上下文的评审 Agent**，完整交代本次「Agent 实施流程优化」的背景、方案、变更、验证证据与建议评审要点。所有变更当前**未提交**，评审可直接基于 `git status` / `git diff` 进行。v1.1 已吸收首轮评审提出的导航路径、索引歧义、目录序号、稳定契约基线和重复 archive 摘要问题。

## 1. 交接目的

用户提出两个痛点：

1. **文档太多太杂**：Agent 探索下一个任务时要检索大量文件，浪费 token；
2. **里程碑循环耗时**：每次执行里程碑，编写代码、测试、提交过程非常耗时。

用户批准了「方案 A：完整优化」（另一候选「方案 B：保守增量、不拆分状态文件」被放弃）。本次变更全部为**文档 + bash 脚本**，不触碰生产代码、CI workflow、pom 和任何测试。

## 2. 问题诊断（变更前已核实的事实）

- `serviceos-architecture/` 当时有 149 个 architecture 文档（11,040 行）+ 134 个 testing 文档（3,549 行）；
- 根 `AGENTS.md` §2.1 规定的固定入口 `implementation-status.md` 已膨胀到 1,165 行，其中约 950 行（§4「最近里程碑」）是各 Mxx 实现文档的重复摘要——每个 Agent 会话开局即消耗；
- 全仓库只有根 `AGENTS.md`，后端 21 个模块没有模块地图，Agent 每次重新探索目录树；
- 没有「任务类型 → 最小必读文件」路由，只能靠 Grep 在文档海里捞针；
- 已有 `scripts/verify-local.sh`（架构修正）、`check-milestone-consistency.sh`（一致性门禁）、CI `verify.yml`（分层触发），但缺：标准执行手册、命名验证入口、里程碑文档模板、契约门禁本地快捷入口。

## 3. 已批准方案（方案 A）概览

四条主线：

1. **导航瘦身**：拆分状态文件，历史摘要移入 archive；新增生成式里程碑索引；新增任务路由表；新增后端模块地图；
2. **执行标准化**：新增里程碑执行手册 + 文档模板 + `agent-verify.sh` 命名验证入口；
3. **门禁联动**：`check-milestone-consistency.sh` 增加索引新鲜度检查（CI 经 `verify-milestone-preflight.sh` 自动继承）；
4. **入口登记**：根 `AGENTS.md`、两个 README 同步新入口与检索纪律。

明确不放宽根 `AGENTS.md` 的任何工程硬约束（模块边界、事务、验证阶梯 L0～L4、文档同步范围），只做入口与纪律增补。

## 4. 变更清单

### 4.1 新增文件（9 个路径）

| 文件 | 内容与理由 |
|---|---|
| `serviceos-architecture/docs/implementation-status-archive.md`（1,447 行） | 状态文件原 §4（M33～M182 里程碑摘要）**原样迁移**并冻结；新里程碑不再复制摘要，避免继续扩大重复维护面 |
| `serviceos-architecture/docs/milestone-index.md`（224 行，180 个里程碑行） | 一行一里程碑：Mxx / 标题 / 实现文档链接 / 验收矩阵链接；**脚本生成，禁止手改**；末尾附「未关联里程碑的文档」清单，不静默丢弃 |
| `scripts/generate-milestone-index.sh` | 索引生成器。归属判定：frontmatter `milestone:` → 文件名 `-mN-` → 单里程碑标题开头的 `Mxx `；同一里程碑同侧多候选失败关闭，程序级编号范围进入附录；支持 `--stdout` 供 diff 门禁使用 |
| `scripts/test-generate-milestone-index.sh` | 索引生成器最小回归，证明程序级范围标题不会抢占具体里程碑，并证明重复候选会被拒绝 |
| `serviceos-architecture/docs/agent-navigation.md` | 任务路由表：10 类高频任务 → 最小必读文件（3～6 个精确仓库根相对路径）+ 按需阅读；4 条检索纪律（禁止批量通读、禁止按旧编号实施身份治理等） |
| `serviceos-backend/AGENTS.md` | 后端模块地图：21 个模块的职责 / 表前缀（`apt_`～`wo_`，从迁移文件核实）/ 对外端口；包结构约定；测试布局（`*Test.java` vs `*PostgresIT.java`）；精准验证命令 |
| `serviceos-architecture/docs/milestone-playbook.md` | 标准执行手册：开工 → 模板建骨架 → 契约/迁移/代码/测试顺序（每步配精准命令）→ L3 门禁时机 → 文档同步 7 步 checklist → 提交前门禁 → CI 节奏 → 耗时陷阱 |
| `scripts/agent-verify.sh` | 命名验证入口：`compile` / `test <Class>` / `it <Class>` / `arch` / `contracts [base]` / `docs`；契约默认比较当前分支与 `origin/master` 的 merge-base，docs 同时运行索引回归 |
| `serviceos-architecture/docs/templates/`（2 个模板） | `milestone-implementation-template.md`、`milestone-acceptance-template.md`，frontmatter 与现存 Mxx 文档一致（`status: Draft` 起步） |

### 4.2 修改文件（5 个）

| 文件 | 变更 |
|---|---|
| `serviceos-architecture/docs/implementation-status.md` | **1,641 → 225 行**：§4 整节迁出，原位替换为指向冻结 archive/index 的指引段；§6 增加最小阅读集；§7 只要求重新生成索引，不再追加重复 archive 摘要；§8 登记新入口 |
| `AGENTS.md`（根） | §2.1 固定入口加入 navigation/index/后端 AGENTS.md；§3.1 增加路由定位与禁止批量通读纪律；§11 增加生成索引并明确 archive 冻结；§12 引用 playbook。**未删改任何硬约束** |
| `scripts/check-milestone-consistency.sh` | 末尾追加索引新鲜度门禁：`--stdout` 重新生成与已提交索引 diff，不一致则失败并提示重新生成 |
| `serviceos-architecture/README.md` | 阅读顺序末尾登记 4 个新文档（第 415～418 项） |
| `README.md`（根） | 事实源指引段加入任务导航与里程碑索引链接 |

## 5. 关键设计决策与取舍

1. **拆分并冻结历史摘要**：M33～M182 原摘要迁移到 archive 保留叙事完整性；新里程碑不再复制摘要，状态文件回归「当前基线入口」定位；
2. **索引必须生成式 + 门禁保鲜**：手工索引必然漂移，因此在 `check-milestone-consistency.sh`（本地与 CI 全量验证都会经过）加入重新生成 + diff 检查；
3. **验证入口只做薄封装**：`agent-verify.sh` 不发明新命令，全部包装根 `AGENTS.md` §10 已记录的命令与 CI 已有脚本，避免第二事实源；
4. **契约入口的稳定基线**：复用 CI 同款 oasdiff，默认比较当前分支与 `origin/master` 的 merge-base；无远端引用时回退到状态文件基线，否则要求显式 base，禁止无声退化为 `HEAD^`；
5. **不做构建速度侵入式调优**：不改 pom 并行度/forkCount；速度收益来自「少读文档 + 不重复推导命令 + 不滥跑全量验证」，Testcontainers reuse 仅作为用户级可选项写入手册。

## 6. 验证证据（全部实际运行，非推断）

| 验证 | 命令 | 结果 |
|---|---|---|
| 脚本语法 | `bash -n scripts/*.sh` | 通过 |
| 索引幂等 | 连跑两次生成器比较 shasum | 一致 |
| 索引歧义回归 | `bash scripts/test-generate-milestone-index.sh` | 程序级范围进入附录；重复同侧候选失败关闭 |
| 索引链接有效 | 抽查 M182 行链接路径在磁盘存在 | 有效（曾发现 `../serviceos-architecture/` 前缀错误并修复为 `../architecture/`） |
| 一致性门禁 | `bash scripts/check-milestone-consistency.sh M182 085 87` | 通过（含新索引新鲜度检查） |
| 里程碑预检 | `bash scripts/verify-milestone-preflight.sh` | 通过（一致性 + 脚本语法 + 迁移基线引用 + staging 清单 + diff --check 全链路） |
| docs 入口 | `bash scripts/agent-verify.sh docs` | 通过 |
| contracts 入口 | `bash scripts/agent-verify.sh contracts` | 默认对稳定 merge-base 检查兼容性，再执行正负探针 |
| compile 入口 | `bash scripts/agent-verify.sh compile` | BUILD SUCCESS |
| test 入口 | `bash scripts/agent-verify.sh test WorkflowDefinitionParserTest` | 4 tests，BUILD SUCCESS |
| arch 入口 | `bash scripts/agent-verify.sh arch` | 2 tests，BUILD SUCCESS |
| it 入口 | `bash scripts/agent-verify.sh it AuthorizationPolicyPostgresIT` | 原生 arm64 PostgreSQL 18；87 migrations；8 tests，BUILD SUCCESS |
| 静态检查 | `git diff --check` | 通过 |

未运行 Maven 全量 `verify`（本次为 R0/R1 文档脚本变更；已完成脚本回归、精准单测、真实 PostgreSQL IT、ArchitectureTest、契约正负探针和里程碑预检，按根 AGENTS.md §10 不需要 L3）。

## 7. 建议评审要点

1. **事实一致性**：拆分后状态文件与冻结 archive 拼接是否完整无损；§7、AGENTS.md §11、playbook 三处的“archive 不再追加、索引重新生成”是否一致；
2. **索引生成器健壮性**：三级归属判定对无 frontmatter 旧文档的覆盖（M2～M7 只有验收矩阵、M8 无验收矩阵是否正确呈现）；`$((10#...))`、补零排序、awk 合并在 BSD/GNU 下的可移植性；
3. **门禁强度**：索引新鲜度、歧义回归和稳定契约比较基线是否覆盖多提交里程碑；
4. **硬约束保持**：根 `AGENTS.md` 的 diff 是否只有入口增补与纪律强化，无任何门禁放宽；
5. **链接与路径**：navigation/playbook/状态文件/两个 README 中所有相对链接有效性；
6. **token/耗时收益核验**：状态文件 225 行 + navigation + index Grep 是否覆盖高频场景；按 playbook §8 连续记录 3 个真实里程碑的读取文件数、探索耗时、精准验证耗时、L3 次数和文档维护面；
7. **模板保真度**：两个模板与最近真实 Mxx 文档（如 `architecture/195-m182-*.md`、`testing/179-m182-*.md`）的 frontmatter 与章节结构是否一致。

## 8. 执行期间的仓库状态变化（评审须知）

- 本任务基于 M134 基线规划，执行期间另一会话合并了 PR #58 等，仓库推进到 **M182**（Flyway 085/87、Core OpenAPI 0.75.0）；方案已按最新状态适配，所有验证均在 M182 基线上运行；
- 仓库存在已登记的 **M135～M140 编号冲突**：身份治理序列编号被 Admin Pilot 里程碑占用，待项目负责人重编号（见 `implementation-status.md` §2.1 与根 `AGENTS.md` 末尾）；导航表与手册已写入「不得按旧编号实施身份治理」约束；
- 执行前工作区干净、与 origin/master 同步；本次变更未提交、未混入任何无关修改。

## 9. 明确未做事项

- 未改 190+ 个 Mxx 历史文档正文、CI workflow、pom、任何测试；
- 未做构建并行度等侵入式调优；
- 未提交变更（等待评审）；未创建分支与 PR；
- 根 `README.md` 已移除易过期的「最新切片见 M159」硬编码，改为从状态总览的 `latestMilestone` 进入生成式索引定位。

## 10. 后续动作建议

- 评审通过后：按仓库惯例整理提交（文档 + 脚本单一意图提交，或拆分为「状态文件拆分」「导航与索引」「执行手册与脚本」三个提交）；
- 若评审要求修改：直接在工作区调整后重新运行 `bash scripts/agent-verify.sh docs` 与 `bash scripts/verify-milestone-preflight.sh`；
- 若否决拆分：archive 内容可机械合并回状态文件（无信息损失），其余增量可独立保留。
