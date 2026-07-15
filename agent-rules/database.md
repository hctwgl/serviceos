# 数据库、持久化与事务规则

仅在任务涉及持久化、SQL、Flyway、锁、并发、Inbox/Outbox 或租户数据范围时读取。

## 分层与技术选择

- Domain/Application 通过 Repository 端口访问数据，不直接依赖 Mapper、JdbcTemplate、JdbcClient 或通用 CRUD；
- 普通业务持久化默认 MyBatis，复杂查询默认 MyBatis XML；
- Spring JDBC 仅用于需要精确控制 SQL、锁、claim/lease、更新计数或并发迁移的可靠性内核，并在代码附近说明理由；
- 禁止用 MyBatis-Plus 通用 CRUD 绕过状态机、授权、审计、幂等或版本检查；
- 模块不得访问其他模块的内部 Mapper、Repository 或表。

详细工程规范以 `serviceos-architecture/architecture/36-persistence-engineering-guideline.md` 为准。

## 事务与可靠消息

- 聚合修改、审计、幂等结果和 Outbox 必须同一事务提交；
- 事件消费者使用 Inbox 或等价唯一约束去重；
- Worker 使用可恢复的 claim/lease/retry，包含上限、退避和人工接管；
- 外部网络调用不得位于持有数据库锁的长事务；
- 不得提交事务后临时发送消息、吞异常、伪造成功或把未知结果当成功；
- 并发状态迁移必须带状态、版本或等价条件，并检查影响行数；
- 锁顺序、幂等键、失败结果和恢复方式必须可审查。

## Flyway

- 结构变化只通过连续 Flyway 迁移；
- 默认采用 expand/contract，但不得留下无期限双写、默认值或旧查询回退；
- runtime 账号不得拥有 DDL 权限；
- 发布迁移必须能够从空库执行，并验证重复 migrate 的预期行为；
- 迁移脚本、应用映射、约束和测试必须在同一变更中保持一致；
- 已发布迁移默认不可改写，修复应追加新迁移；
- 破坏性数据变更、回填或不可逆操作必须有迁移、校验、回退和审批依据。

## 租户与范围

- 多租户业务数据显式包含 tenant/project scope；
- 查询和写入必须使用服务端认证上下文，不信任客户端传入的 tenant、project 或 operator；
- 唯一约束、外键和索引应覆盖正确的租户/项目业务键；
- 跨租户数据不可见，越权必须失败关闭并留下拒绝审计。

## 最低验证

- SQL、锁、唯一约束、claim/lease、迁移和 PostgreSQL 特性必须由真实 PostgreSQL Testcontainers 证明；
- 不得用 H2、Mock 或单元测试替代必须证明的数据库行为；
- 修改模块公开 API 或跨模块调用时额外运行 `ArchitectureTest`；
- Flyway 变化至少执行对应迁移测试和受影响 PostgreSQL IT。
