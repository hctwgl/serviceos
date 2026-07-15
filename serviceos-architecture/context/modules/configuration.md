---
module: configuration
status: Partial
lastVerifiedMilestone: M61
---

# configuration 模块卡片

## 事实所有权

- ConfigurationAsset、PublishedVersion、ConfigurationRelease 和 ConfigurationBundle；
- 配置发布、依赖闭包、适用范围和精确版本解析；
- 工单与任务运行时使用的不可变配置引用。

配置模块不执行流程、表单、资料、SLA、派单或计价业务，只发布经过治理的定义。

## 公开边界

- 生产代码：`serviceos-backend/src/main/java/com/serviceos/configuration/`；
- 迁移：`serviceos-backend/src/main/resources/db/migration/configuration/`；
- 业务模块只能引用已发布的精确版本，不得读取草稿或 `latest`。

## 必读事实源

- `serviceos-architecture/architecture/05-configuration-version-center.md`；
- `serviceos-architecture/decisions/ADR-018-configuration-schema-expression-runtime.md`；
- `serviceos-architecture/architecture/29-configuration-byd-work-order-intake-implementation.md`；
- `serviceos-architecture/docs/implementation-status.md` 中配置中心当前边界。

## 核心测试

```bash
rg --files serviceos-backend/src/test | rg 'Configuration.*(Test|PostgresIT)'
./mvnw --no-transfer-progress -pl serviceos-backend -Dtest=ArchitectureTest test
```

发布重叠、唯一解析、不可变版本和依赖图必须由 PostgreSQL IT 证明。

## 相邻模块

- 上游：project；
- 下游：workorder、workflow、forms、evidence、sla、dispatch、pricing、integration；
- 只在本次资产类型实际变化时展开对应消费者模块。

## 稳定不变量

- 草稿不可被生产运行时引用；
- 发布版本业务内容不可变；
- 工单创建时必须唯一解析并锁定完整 Bundle；
- 零命中或多命中失败关闭；
- 继承只在编辑和发布阶段展开，运行时不做动态多层继承。

## 扩大检索触发条件

新增资产类型、表达式能力、发布审批、适用范围、Bundle 解析维度、在途迁移或消费者依赖变化。
