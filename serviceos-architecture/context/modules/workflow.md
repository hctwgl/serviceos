---
module: workflow
status: Implemented
lastVerifiedMilestone: M61
---

# workflow 模块卡片

## 事实所有权

- WorkflowInstance、StageInstance、NodeInstance；
- 已发布 ProcessDefinition 的运行时解释与精确版本绑定；
- 节点推进、跨阶段推进和 END 完结事实。

Workflow 不拥有 WorkOrder、Task、Evidence、Review 或 SLA 的业务状态，也不得直接更新这些模块的内部表。

## 公开边界

- 生产代码：`serviceos-backend/src/main/java/com/serviceos/workflow/`；
- 迁移：`serviceos-backend/src/main/resources/db/migration/workflow/`；
- 跨模块协作只使用公开 API、领域事件和 Inbox/Outbox。

## 必读事实源

- `serviceos-architecture/architecture/06-work-order-task-execution-kernel.md`；
- `serviceos-architecture/architecture/30-workflow-bootstrap-runtime-implementation.md`；
- `serviceos-architecture/architecture/31-workflow-linear-progression-implementation.md`；
- `serviceos-architecture/architecture/32-workflow-stage-completion-implementation.md`；
- `serviceos-architecture/domain/06-state-machines.md`。

## 核心测试

```bash
rg --files serviceos-backend/src/test | rg 'Workflow.*(Test|PostgresIT)'
./mvnw --no-transfer-progress -pl serviceos-backend -Dtest=ArchitectureTest test
```

涉及迁移、推进并发或 Inbox 时必须运行对应 PostgreSQL IT，不能只运行解析器单元测试。

## 相邻模块

- 上游：configuration、workorder；
- 下游：task；
- `slaRef`、表单或资料引用变化时展开 configuration、task 和对应能力卡片；
- TaskCompleted 等事件变化时展开 reliability 与事件 Schema。

## 稳定不变量

- 运行实例锁定精确流程版本；
- 流程变量只保存稳定标识和小型路由数据；
- 流程引擎不直接写业务模块内部表；
- 重复事件不得重复创建节点或推进流程；
- 当前只证明线性推进，不能外推为并行/汇聚能力。

## 扩大检索触发条件

并行或汇聚网关、条件表达式、补偿、取消重开、公开事件、跨模块依赖或数据库结构变化。
