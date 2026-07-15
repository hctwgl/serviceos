# 按需 Agent 规则

根 `AGENTS.md` 只保留所有任务都必须知道的核心约束。以下细则按任务触发条件读取，避免每轮默认消耗全部上下文。

| 文件 | 读取触发条件 |
|---|---|
| `database.md` | MyBatis、JDBC、Flyway、锁、事务、并发、租户数据范围 |
| `contracts-security.md` | Controller、OpenAPI、事件、OIDC、授权、文件、车企接入、敏感数据 |
| `testing.md` | 选择验证范围、PostgreSQL IT、ArchitectureTest、CI、Apple Silicon/OrbStack |
| `documentation.md` | 新里程碑、事实源、ADR、状态总览、追踪矩阵、Context Pack |
| `code-quality.md` | 复杂逻辑、中文注释、错误处理、兼容、清理旧路径 |

读取某个规则文件不代表必须执行其中所有门禁，只执行与本次变更适用的部分；但安全、租户、事务、契约、数据丢失和模块边界约束不得通过“未读取”规避。
