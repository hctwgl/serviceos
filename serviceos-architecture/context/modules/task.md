---
module: task
status: Implemented
lastVerifiedMilestone: M61
---

# task 模块卡片

## 事实所有权

- Task 生命周期、责任归属、候选人和执行保护；
- HUMAN/AUTOMATED/EXTERNAL/TIMER/COORDINATION 任务实例；
- 自动任务尝试、退避、下次执行时间和人工接管；
- Task 完成命令及其幂等结果。

Task 不拥有表单提交、Evidence、ReviewCase、ServiceAssignment、SLA 时钟或外部交付事实。

## 公开边界

- 生产代码：`serviceos-backend/src/main/java/com/serviceos/task/`；
- 迁移：`serviceos-backend/src/main/resources/db/migration/task/`；
- 其他模块不得直接更新 Task 表或绕过执行保护。

## 必读事实源

- `serviceos-architecture/architecture/06-work-order-task-execution-kernel.md`；
- `serviceos-architecture/architecture/24-task-scheduler-manual-intervention-implementation.md`；
- `serviceos-architecture/architecture/33-human-task-command-runtime-implementation.md`；
- `serviceos-architecture/architecture/34-task-assignment-runtime-implementation.md`；
- `serviceos-architecture/architecture/35-task-execution-guard-runtime-implementation.md`。

## 核心测试

```bash
rg --files serviceos-backend/src/test | rg '(Task|Assignment|ExecutionGuard).*(Test|PostgresIT)'
./mvnw --no-transfer-progress -pl serviceos-backend -Dtest=ArchitectureTest test
```

修改 claim/lease/retry、责任排他、完成门禁或并发迁移时必须运行真实 PostgreSQL IT。

## 相邻模块

- 上游：workflow、authorization；
- 下游：sla、operations、forms、evidence、review；
- 完成条件变化时只展开被引用的能力模块；
- 重试和人工接管变化时展开 reliability 与 operations。

## 稳定不变量

- 同一 Task 只有一个当前责任归属事实源；
- claim、start、complete、release 使用聚合版本或等价并发条件；
- 完成必须重新校验权限、输入版本和完成条件；
- 自动任务失败不能伪装成完成；
- 业务副作用的重试时钟只能有一个权威拥有者。

## 扩大检索触发条件

责任策略、跨模块完成门禁、Task 事件、Worker 调度、租户授权、状态机或数据库并发语义变化。
