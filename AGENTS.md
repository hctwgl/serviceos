# ServiceOS Agent Development Guide

本文件是所有任务都必须读取的最小仓库入口。详细数据库、契约、安全、测试、文档和代码质量规则已拆到 `agent-rules/`，只在触发条件满足时加载。

ServiceOS 是面向新能源充电设施现场服务的可配置履约平台，采用 Java 21、Spring Boot、Spring Modulith、PostgreSQL、Flyway、MyBatis、OpenAPI、事件 Schema、Inbox/Outbox、OIDC/JWT、Capability 和 Tenant/Project Scope。

## 1. 工作目录与基线

- 必须在真实仓库 `/Users/louis/code/serviceos` 工作；
- 开始任务先执行 `git status --short --branch`，保留用户已有修改；
- 不向镜像、导出副本或历史任务目录写代码；
- 默认目标是模块边界清晰、事务可靠、失败关闭、可重放、可审计、可测试；
- 不为短期 CRUD 速度或形式上的框架统一破坏领域边界和可靠性。

## 2. 默认上下文路由

新任务不得先全文检索整个仓库或完整阅读 Architecture Book。默认启动顺序：

1. 本文件；
2. `serviceos-architecture/context/current-baseline.md`；
3. 用户指定、分支声明或当前计划对应的 `serviceos-architecture/context/milestones/<ID>.md`；
4. 运行 `bash scripts/plan-context.sh <ID>`；
5. 只读取输出中的必读文件、模块卡片和直接代码/测试；
6. 发生第 4 节触发条件后再扩大检索。

没有 Context Pack 的新里程碑，应先按 `serviceos-architecture/context/milestones/CTX-001.md` 模板创建，再编码。局部缺陷可直接声明能力、模块、非目标和风险等级，不必为了形式创建业务里程碑。

本地跨会话续作可运行：

```bash
bash scripts/init-agent-session.sh <ID>
```

`.agent/session-state.md` 只用于本地缓存，已被 Git 忽略，不是共享事实源。继续任务前比较其中的 baselineCommit、当前 HEAD 和已读取文件版本，只重新读取发生变化的部分。

## 3. 事实源优先级

1. 当前任务中用户明确批准的最新决策；
2. Accepted ADR；
3. Accepted/Implemented 架构与验收文档；
4. OpenAPI、事件 Schema、Flyway 等机器契约；
5. 自动化测试证明的行为；
6. 当前代码；
7. README、注释和历史说明。

`serviceos-architecture/` 是产品与架构事实源。代码与高优先级事实冲突时不得默认代码正确。涉及核心业务语义、破坏性契约、长期架构、安全、租户或数据损失风险时必须暂停确认；局部、可逆且不改变业务语义的细节按现有模式推进。

## 4. 风险等级、阅读预算与扩大条件

| 等级 | 典型变更 | 初始读取预算 | 最低交付门禁 |
|---|---|---:|---|
| R0 | 文案、索引、注释、无语义重构 | 约 3K tokens | diff、链接、格式 |
| R1 | 局部缺陷、单一领域规则、内部重构 | 约 8K tokens | 精准测试；SQL 则 PostgreSQL IT |
| R2 | 新用例、跨模块、API/事件/Flyway | 约 20K tokens | 模块回归、契约/迁移、ArchitectureTest |
| R3 | 新业务里程碑、状态机、安全边界、发布候选 | 初始约 30K tokens | 全量 verify 和适用 staging/兼容/回滚 |

风险按最高影响项升级。可先运行：

```bash
bash scripts/plan-impact.sh master
```

脚本只给出最低风险下限。以下情况允许或要求扩大到相邻模块、完整历史或更高风险等级：

- 高优先级事实源互相冲突；
- 修改核心状态机、事务边界、授权、Tenant/Project Scope；
- 修改公共 API、领域事件、外部协议或兼容语义；
- 新增模块、跨模块依赖、数据所有权变化；
- 破坏性或不可逆数据库迁移；
- 外部副作用、生产数据损失或敏感信息风险；
- 当前自动化证据不能证明目标行为；
- Context Pack 引用缺失或明显过期。

超过初始阅读预算时，必须在工作摘要中记录触发原因，不得仅以“仓库很大”为由全量扫描。

## 5. 所有任务的硬约束

### 5.1 模块与分层

```text
Interface / Adapter
    -> Application
    -> Domain
    -> Port / Repository Interface
    -> Infrastructure Adapter
```

- 模块只通过公开 API、领域事件或明确 SPI 协作；
- 禁止跨模块访问内部包、内部 Repository、Mapper 或表；
- Controller 只做协议适配；Application Service 编排用例和事务；Domain 维护规则与不变量；Infrastructure 隔离技术实现；
- Domain 不得依赖 Web、MyBatis、JDBC、消息客户端或外部 SDK；
- `shared` 只放稳定、无业务归属的最小公共能力；
- 不得通过扩大 `allowedDependencies`、开放内部包或移动代码到 `shared` 绕过 Spring Modulith 失败。

### 5.2 事务、并发与可靠消息

- 聚合修改、审计、幂等结果和 Outbox 同事务提交；
- 外部请求、命令、事件和回调必须定义幂等键及重复语义；
- 消费者使用 Inbox 或等价唯一约束去重；
- Worker 使用可恢复的 claim/lease/retry，包含上限、退避和人工接管；
- 外部网络调用不得位于持有数据库锁的长事务；
- 不得提交后临时发消息、吞异常、伪造成功或把 UNKNOWN 当成功；
- 并发迁移必须带状态、版本或等价条件，并检查影响行数。

### 5.3 授权、安全与失败关闭

- 身份来自 OIDC/JWT，授权基于 Capability 与 Tenant/Project Scope；
- 不信任客户端 tenant、project、operator、角色或权限结果；
- 前端隐藏按钮不能替代后端授权；
- 车企接入必须验签、校验时间窗、防重放并幂等；
- Secret、敏感原文和个人信息不得进入仓库、镜像、普通日志、Trace 或测试快照；
- 非法输入、未知状态、配置冲突、权限不足和并发冲突必须明确失败，禁止静默默认值和宽松兜底。

### 5.4 唯一权威路径

ServiceOS 是新系统。默认直接修正模型、契约、迁移、测试和调用方，不为假设中的旧客户端或脏数据增加永久兼容。真实兼容需求必须有 ADR、对象、边界适配层、负责人、截止日期、删除里程碑、迁移/监控/回退方案和自动化证据。

## 6. 按需规则文件

| 任务触发条件 | 必须读取 |
|---|---|
| MyBatis、JDBC、Flyway、SQL、锁、事务、并发、租户数据 | `agent-rules/database.md` |
| Controller、OpenAPI、事件、OIDC、授权、文件、车企接入、敏感数据 | `agent-rules/contracts-security.md` |
| 测试范围、PostgreSQL IT、ArchitectureTest、CI、Apple Silicon/OrbStack | `agent-rules/testing.md` |
| 新里程碑、ADR、事实源、状态总览、追踪矩阵、Context Pack | `agent-rules/documentation.md` |
| 复杂逻辑、中文注释、错误处理、兼容和旧路径清理 | `agent-rules/code-quality.md` |

安全、租户、事务、契约、数据丢失和模块边界约束不能因未读取细则而规避。

## 7. 推荐实施节奏

```text
1. git status，确认基线与已有修改
2. 选择 Context Pack、能力、模块和 R0～R3
3. 运行 plan-context；只读最小事实源
4. 写明目标、非目标、已冻结决策和验证范围
5. 小步修改领域代码、契约、迁移、测试和必要文档
6. 每个逻辑小步运行静态/精准验证；风险点运行专项门禁
7. 运行 plan-impact，检查是否遗漏相邻模块和测试
8. 清理被替代代码、默认值、双轨和无依据兜底
9. 里程碑完成前执行适用全量门禁并同步状态/追踪
10. 检查 diff、工作区和事实源一致性后提交
```

优先保持提交单一意图。实现与对应测试/文档可以同提交；必须回填未知 SHA 时使用紧随其后的状态提交。不得混入无关格式化、依赖升级或用户已有修改。

## 8. 完成与汇报

局部任务完成至少满足：

- 请求范围已实现，非目标、未实现内容和已知风险明确；
- 领域不变量、状态机、模块边界、事务、幂等、授权和租户范围未被破坏；
- 受影响的数据库、HTTP、事件、文件和外部集成契约已同步；
- 最低充分验证通过，未运行项及原因明确；
- 复杂业务、事务、并发、SQL、安全和恢复逻辑具有足够中文说明；
- 被替代路径和无依据兼容已清理；
- 受影响事实源、Context Pack、模块卡片和注释保持一致；
- 没有隐藏失败、跳过门禁、放宽核心断言或虚假完成状态。

新业务里程碑还必须具备代码、Flyway、机器契约和适用自动化证据，并同步实现文档、验收矩阵、追踪矩阵、实施状态与当前轻量基线。`Implemented` 只证明声明范围，不能外推为整个领域完成。

最终报告保持紧凑，说明：完成内容、影响模块/契约/迁移、事务/授权/幂等保证、实际验证及结果、未实现或未运行项、兼容/清理情况、分支和提交状态。

## 9. 例外

确需偏离本指南时，必须说明冲突规则、原因、影响、迁移与回退方案；涉及长期架构、数据、安全或契约的偏离需更新 ADR、事实源和验收矩阵并获得明确批准。
