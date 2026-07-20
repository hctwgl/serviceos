---
title: ADR-091：jOOQ 统一业务数据访问层
version: 1.0.0
status: Accepted
owner: Product Architecture
reviewers:
  - Engineering Architecture
  - Persistence Owner
related_adrs:
  - decisions/ADR-013-reference-engineering-profile-and-module-enforcement.md
  - decisions/ADR-016-single-image-explicit-migration-fail-closed-deployment.md
---

# ADR-091：jOOQ 统一业务数据访问层

## 1. 状态与已接受决策

项目负责人 2026-07-20 评审"技术架构调整方案"后确认：数据访问收敛议题**跳过 POC 直接立项**，
接受全量迁移的工程风险。正式接受以下决策：

1. 正式主干业务数据访问最终只保留 **jOOQ + Spring Transaction + Flyway + HikariCP**；
2. 业务 Repository 不再使用 MyBatis、MyBatis XML、Spring JdbcClient/JdbcTemplate、
   手写 RowMapper 和字符串字段名映射；
3. 本 ADR 取代 `architecture/36-persistence-engineering-guideline.md`
  （MyBatis 默认与 JDBC 受控例外）；该规范转为**被取代**状态，其中未涉及技术选型的
   工程约束（Repository 语义、并发条件、多租户 Scope、测试要求、事务约束）由本 ADR 继承；
4. 不引入 JPA/Hibernate，不承诺多数据库兼容，不引入数据库方言抽象层；
5. 迁移在独立分支一次性完成，正式合并后主干不得保留双栈；
6. **一次性切换原则**：ServiceOS 处于开发阶段，无生产数据与已发布客户端包袱，
   切换一次到位——合并主干前当场删除全部旧实现，不设双轨期、不写兼容适配层、
   不做兜底回退（见 §5）。

同期两项相关议题已由项目负责人另行决定，**不属于**本 ADR 范围：

- API/Worker 暂不拆分为独立部署单元，运行形态继续遵从 ADR-016 单一镜像决策；
- Technician H5 与原生 iOS 长期双端并行，均为正式产品，不删除 H5 已实现能力。

## 2. 上下文

持久化技术现状与既有规范已经背离，三套写法并存：

- MyBatis：23 个 `*Mapper.java`、20 个 Mapper XML（`serviceos-backend/src/main`）；
- Spring JDBC：`JdbcClient`/`JdbcTemplate` 分布在 85 个文件、约 259 处调用，
  覆盖 reliability、workflow、task、dispatch、configuration 等核心模块；
- 规范 36 自身状态仍为 `Proposed`，其"MyBatis 默认"条款从未成为现实多数派；
- 数据库基线：Flyway 137 个迁移（版本 135/137），深度使用 `uuid`、`jsonb`、
  `timestamptz`、条件索引、`ON CONFLICT`、`FOR UPDATE SKIP LOCKED`、`RETURNING`。

并存带来的实际问题：

- 同一模块内 MyBatis 与 JdbcClient 混用，新人与 Agent 无法从规范预测实现方式；
- 手写字符串字段名和 RowMapper 在列重命名/删除时编译期无保护，只能靠测试期发现；
- 同一套 PostgreSQL 类型映射（UUID/JSONB/枚举/时间）在 Mapper XML 与 JdbcClient
  两处重复维护；
- 规范 36 的"受控例外"条款在实践中被大量援引，例外已成常态。

jOOQ 选型理由：以 Flyway Schema 为基线生成 Java 类型，编译期即可发现字段删除与
类型变化；保留 PostgreSQL 方言与并发语义的显式表达；统一写命令与复杂查询投影的
实现方式；消除 XML/ResultMap/手工 RowMapper 的维护成本。

## 3. 决策细则

### 3.1 范围

必须迁移到 jOOQ 的范围：

- 全部业务 Repository Adapter（命令侧与查询侧）；
- 全部查询投影与运营工作台查询（readmodel）；
- 全部并发内核 SQL：Inbox/Outbox、Idempotency、Task/Worker Claim、Lease、
  Workflow 推进、SLA 时钟、Attempt/Receipt。

### 3.2 受控例外

仅允许极少量**非业务**基础设施 JDBC（如数据库健康检查、jOOQ 无法覆盖的底层初始化），
必须经架构审批并在代码注释中记录理由，不得用于领域 Repository。

### 3.3 代码生成与一致性

- jOOQ 生成物以 Flyway 迁移为唯一 Schema 基线，必须可重复生成；
- 生成代码不入库评审diff时以重新生成为准，CI 必须校验生成物与迁移基线一致，
  不一致即失败；
- 公共 PostgreSQL 类型绑定（UUID、JSONB、枚举、`timestamptz`）统一定义为
  Converter/Binding，禁止各模块自行重复实现。

### 3.4 继承约束（原规范 36 中与技术选型无关的条款继续有效）

- Repository 接口表达领域意图，禁止通用表操作语义泄漏到领域层；
- 状态迁移 SQL 必须带状态/版本条件并校验受影响行数；
- 聚合、审计、幂等响应、Outbox 保持同一事务；
- 多租户访问必须显式 tenant/project scope，禁止依赖调用方记得加条件；
- 锁、Claim、Lease、唯一约束、并发恢复必须由真实 PostgreSQL Testcontainers 证明；
- Domain 层只依赖 Repository 端口，不得依赖 jOOQ 生成类型、DSL 或 JDBC。

## 4. 迁移计划与完成门禁

### 4.1 迁移方式

```text
创建独立迁移分支
→ 冻结主干新增大规模业务功能（仅接受缺陷/安全/迁移所需修复）
→ 建立 jOOQ Code Generation 与公共类型绑定
→ 按分组迁移 Repository 与并发内核
→ 迁移测试夹具
→ 删除 MyBatis Mapper / XML / 业务 JdbcClient 与相关依赖
→ 全量验证后合并主干
```

### 4.2 分组顺序

1. 可靠性内核：Idempotency、Outbox、Inbox、Worker Claim、Attempt、Receipt；
2. 核心履约：WorkOrder、Workflow、Task、Dispatch、SLA；
3. 现场业务：Appointment、Fieldwork、Forms、Evidence（含审核/整改相关持久化）；
4. 治理：Identity、Authorization、Organization、Project、Configuration、
   Integration、Audit、Files；
5. 查询投影：readmodel 全部工作台/目录/时间线查询。

### 4.3 删除旧实现的前置门禁（全部满足才允许删除）

- 全部 Repository 与并发内核已迁移，PostgreSQL IT 全绿；
- 模块测试与 Web E2E 黄金路径通过；
- 关键 SQL（Claim、复杂投影、游标分页）完成执行计划与数据规模抽查，无性能退化；
- MyBatis 依赖从 Maven 删除、仓库无 Mapper XML、业务代码无 `JdbcClient`；
- jOOQ 生成物可由 Flyway 基线重复生成且 CI 一致性校验通过；
- `bash scripts/verify-local.sh` 全量验证通过（L3 门禁）。

## 5. 一次性切换原则（项目负责人 2026-07-20 确认）

ServiceOS 处于开发阶段，无生产数据与已发布客户端包袱，切换按一次到位执行：

- 迁移启动后在同一批次内完成全部 Repository、查询投影与并发内核的 jOOQ 化，
  合并主干前当场删除全部旧实现：MyBatis 依赖、Mapper、XML、业务 JdbcClient；
- 不设双轨期、不写新旧兼容适配层、不留 `deprecated` 旧代码、不做兜底回退分支；
  旧实现只保留在 Git 历史中；
- 迁移期间禁止数据库双写，禁止新旧 Repository 对同一表并行写；
- 切换不附带任何行为兼容承诺：迁移后实现以通过 PostgreSQL IT 与 E2E 验证为准，
  禁止为"与旧实现保持一致"而保留已知错误语义；
- 迁移窗口内主干暂停新增业务功能，仅接受缺陷与安全修复，窗口尽量压缩；
- 迁移里程碑启动前，存量代码维持现状模式，新增功能沿用模块内主导模式，
  不得借机在 MyBatis 与 JdbcClient 之间来回改写制造第四种风格；
- 启动时机（2026-07-20 确认）：当前在途里程碑完成后启动迁移窗口，
  启动前主干业务开发照常。

## 6. 后果

正面：

- 数据访问技术栈唯一，规范与现实重新一致；
- Schema 变更获得编译期保护，降低评审与返工成本；
- UUID/JSONB/枚举/时间映射单点维护；
- 并发内核 SQL 语义保持显式可审查。

负面与已接受风险：

- 一次性迁移工作量大（137 个迁移基线、85 个 JdbcClient 文件、23 个 Mapper），
  迁移窗口内业务功能冻结，已由项目负责人确认接受；
- Repository 行为可能出现细微差异（NULL 语义、类型转换、分页边界），
  必须依靠既有 PostgreSQL IT 与 E2E 兜底，禁止放宽核心断言；
- jOOQ 代码生成引入新的构建时复杂度（生成物与 Flyway 基线一致性校验）；
- 迁移分支存在合并漂移风险，通过压缩窗口与冻结范围控制。

## 7. 明确不做

- 不引入 JPA/Hibernate 或任何 ORM 脏检查模型；
- 不引入 MyBatis-Plus；
- 不承诺 MySQL 等多数据库兼容，不引入方言抽象层；
- 不在主干保留 MyBatis/JdbcClient/jOOQ 双栈或三栈；
- 不进行数据库双写，不自动向下回滚数据库迁移；
- 不借迁移改变任何业务语义、状态机、授权或事务边界；
- 不将 jOOQ 生成类型泄漏到 Domain 层或跨模块公开 API。
