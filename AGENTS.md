# ServiceOS Agent Engineering Constraints

本文件是仓库级强约束。任何在本仓库中生成、修改或评审代码的 Agent 与开发者都必须遵守。若实现与本文件冲突，以本文件和 `serviceos-architecture/architecture/36-persistence-engineering-guideline.md` 为准；确需例外时，必须先补充 ADR 并说明原因、影响和回退方案。

## 1. 持久化技术基线

1. 新增普通业务持久化代码时，默认使用 **MyBatis**。
2. 新增复杂列表、动态筛选、分页、聚合统计和报表查询时，默认使用 **MyBatis XML**。
3. Spring JDBC 只用于已经证明需要直接控制底层 SQL 执行语义的少数场景，例如：
   - Inbox / Outbox；
   - Task claim、lease、续租和恢复；
   - `FOR UPDATE SKIP LOCKED` 批量抢占；
   - 条件更新、幂等写入和并发状态迁移；
   - PostgreSQL `RETURNING`、CTE 或批量操作，且 MyBatis 实现会显著降低可读性或可验证性。
4. 不得仅以“历史代码使用 JDBC”作为新增 JDBC 实现的理由。
5. 不得为了技术统一，重写已经稳定并有完整并发集成测试覆盖的 JDBC 可靠性内核。

## 2. 分层约束

持久化调用必须保持以下方向：

```text
Application / Domain Service
    -> Domain Repository Interface
    -> Infrastructure Repository Adapter
    -> MyBatis Mapper 或受控 Spring JDBC
    -> PostgreSQL
```

禁止：

- Controller、Application Service 或 Domain Service 直接依赖 MyBatis Mapper、`JdbcTemplate`、`JdbcClient`；
- 领域模型直接使用 MyBatis 注解或持久化框架基类；
- 使用数据库 Record/DO 替代领域聚合在领域层流转；
- 绕过 Repository 直接更新领域状态；
- 使用通用 CRUD 方法绕过状态机、版本条件、租户范围或审计要求。

## 3. MyBatis 使用规范

1. 简单、稳定、无动态条件的 SQL 可使用 Mapper 注解。
2. 包含动态条件、多表查询、批量操作、复杂映射或较长 SQL 时必须使用 XML。
3. Mapper 只负责数据库访问，不承载业务规则。
4. Mapper 参数必须显式命名；复杂查询使用独立 Query/Criteria 类型。
5. 查询返回对象使用专用 Record/DTO，不直接暴露领域聚合内部结构。
6. 必须为 UUID、枚举、值对象、JSONB、时间类型建立统一 TypeHandler 或明确映射策略。
7. SQL 必须显式列出字段，禁止在生产代码中使用 `select *`。
8. 所有多租户业务查询必须显式包含 tenant/project scope，且由集成测试验证越权不可见。

## 4. MyBatis-Plus 约束

核心业务模块默认禁止使用 MyBatis-Plus 的以下模式：

- `BaseMapper` 直接暴露给业务层；
- `IService` / `ServiceImpl` 作为领域服务；
- `save`、`updateById`、`removeById` 等通用 CRUD 直接修改领域聚合；
- Lambda Wrapper 在业务层拼装数据库查询。

如确需引入 MyBatis-Plus，只允许在无领域行为的辅助管理模块中使用，并必须通过 ADR 审批。ServiceOS 的默认选择是原生 MyBatis + 显式 Repository。

## 5. Repository 语义

Repository 方法必须表达业务意图，而不是表操作意图。

推荐：

```java
boolean claim(TaskId taskId, UserId claimantId, long expectedVersion, Instant claimedAt);
void save(Project project);
Optional<Project> findById(ProjectId projectId);
```

禁止：

```java
updateById(entity);
saveOrUpdate(entity);
Map<String, Object> selectByMap(...);
```

涉及状态迁移的更新必须包含当前状态、版本号或等价并发条件，并校验受影响行数。

## 6. 事务与可靠性

1. 聚合修改、审计、幂等结果和 Outbox 必须保持同一数据库事务。
2. 不得在 Mapper 或 Repository 内部开启隐藏的新事务。
3. 外部网络调用不得放入持有数据库行锁的长事务中。
4. Claim/Lease/Retry 类 SQL 必须有 PostgreSQL Testcontainers 集成测试。
5. 数据库迁移继续由 Flyway 管理；禁止应用运行账号执行 DDL。

## 7. Agent 执行要求

Agent 在新增或修改持久化代码前必须：

1. 判断该场景属于普通业务持久化、查询侧，还是可靠性并发内核；
2. 普通业务与查询侧默认选择 MyBatis；
3. 选择 Spring JDBC 时，在代码注释或变更说明中写明必须直接使用 JDBC 的具体数据库语义；
4. 保持领域 Repository 接口不依赖具体框架；
5. 同步补充单元测试、PostgreSQL 集成测试和必要的架构文档；
6. 评审时将违反本文件的实现视为阻断问题。

详细设计与迁移策略见：

- `serviceos-architecture/architecture/36-persistence-engineering-guideline.md`
