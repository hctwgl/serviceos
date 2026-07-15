# ServiceOS

ServiceOS 是面向新能源充电设施现场服务的可配置履约平台。当前仓库同时保存架构事实源、参考后端工程和机器可读契约。

## 当前可运行基线

- Java 21 + Spring Boot 4.1 + Spring Modulith 2.1 模块化单体；
- Maven Wrapper 一条命令构建；
- 19 个 Spring Modulith 模块，覆盖身份授权、配置、工单、工作流、任务、派单、预约、现场 Visit、
  表单、资料、审核整改、文件、集成、可靠消息、审计与运营异常；
- PostgreSQL + Flyway 模块前缀物理表；
- `CreateProject` 首条命令链路；
- 聚合、审计、幂等结果和 Outbox 同一事务；
- OIDC/JWT 主体、capability/tenant scope 后端授权和拒绝审计；
- Inbox 去重与 Outbox claim/lease/publish worker；
- 自动 Task 调度、执行尝试、租约恢复、受控重试和最终失败人工接管；
- 安全文件 Begin/受限直传/Finalize/隔离扫描/授权下载闭环；
- OpenAPI 3.1 破坏性变更门禁、不可变事件 Schema 治理与可重复 TypeScript 客户端生成；
- W3C API/Outbox/Worker Trace 串联、健康探针、Prometheus 看板与 ECS JSON 日志脱敏；
- 单一非 root OCI 镜像、独立 Flyway 迁移、staging smoke、失败关闭与应用回滚演练；
- 不可变配置资产/Bundle 最小发布解析，以及 tenant/project/bundle 工单版本锁定；
- BYD CPIM V7.3.1 验签、防重放、统一映射到工单创建的事务切片；
- BYD CPIM 创建工单的权威 InboundEnvelope/CanonicalMessage、私有原文留存、业务键幂等与崩溃恢复；
- BYD 厂端审核回调的显式订单路由、逐项 Canonical、部分成功、外部决定与故障恢复；
- BYD 提审不可变 OutboundDelivery/Attempt/Acknowledgement、Task 可靠执行、UNKNOWN 人工接管，
  以及明确成功后自动创建 CLIENT ReviewCase 与回调路由；
- `WorkOrderReceived` Outbox、Inbox 去重、精确 Workflow 版本启动、首个 Stage/Task 创建与工单激活；
- `TaskCompleted` 领域事件、NodeInstance 运行时，以及冻结流程定义下的同阶段唯一下一任务推进；
- 唯一无条件跨阶段推进、END 完结，以及 Stage/Workflow/WorkOrder 履约完成事件；
- 人工工作流 Task 的安全 claim/start/complete HTTP、冻结幂等响应和统一流程推进；
- TaskAssignment USER 候选快照、唯一当前责任、候选领取门禁与 release/reclaim；
- TaskExecutionGuard 改派保护窗、精确解除、幂等事实与人工命令失败关闭；
- PREPARED TaskAssignment prepare/activate/abort 可靠责任切换握手；
- Dispatch/Task 改派 Inbox saga、切换前可靠终止、阶段 deadline、超时对账、异常人工接管与成功后自动关单；
- 动态表单不可变提交、表单/资料双输入 Task 完成门禁；
- Evidence 固定/条件槽位、不可变 Item/Revision、机器校验、Snapshot、作废、Review/Correction、
  整改 Task、强制通过/重开、外部审核回执与 WAIVED；
- SERVICEOS_EXPR_V1 白名单布尔子集，包含输入/true-false 决策审计与复杂度失败关闭；
- VALIDATED 表单事实驱动 EvidenceSlot 只追加重解析、槽位世代/lineage、条件变化人工处置与完成门禁；
- 车企外部审核回执 `affectedTargets` 对 ReviewCase 冻结 SnapshotMember 的精确权威校验；
- INTERNAL/CLIENT ReviewCase 来源分离、已通过总部审核的车企提交登记，以及回执批次/mapping 冻结门禁；
- Spring Modulith 边界验证与 PostgreSQL Testcontainers 集成测试。

当前工程基线推进至 **M58**，但仍是模块化后端纵向切片，不代表完整现场履约平台已经交付。
OCR/CV、计算字段/脚本、复杂工作流、SLA/通知、履约事实与试算、对账结算、正式
IdP/Broker/对象存储/扫描服务，以及 Admin/Network/Technician Portal 工程仍未实现。

## 快速验证

```bash
./mvnw clean verify
```

契约变更还必须相对目标 Git 基线执行兼容门禁，并验证客户端可重复生成：

```bash
OASDIFF_BIN="$(serviceos-contracts/scripts/install-oasdiff.sh serviceos-contracts/target/contract-tools)" \
  serviceos-contracts/scripts/check-contract-compatibility.sh origin/master

serviceos-contracts/scripts/verify-client-generation-reproducibility.sh
```

本机存在 Docker/兼容容器运行时时，构建会运行 PostgreSQL 18 集成测试；没有容器运行时时，Testcontainers 会明确显示该组测试被跳过。CI 环境不得把这组 P0 数据库测试标为不适用。

本地启动数据库：

```bash
docker compose -f serviceos-deploy/compose.yaml up -d postgres
./mvnw -pl serviceos-backend spring-boot:run
```

完整容器化 staging 发布与回滚演练：

```bash
serviceos-deploy/staging/verify-rehearsal.sh
```

## 目录

```text
serviceos-architecture/  架构、产品、API、数据、测试和路线图事实源
serviceos-backend/       Java 模块化单体参考实现
serviceos-contracts/     OpenAPI 与事件 JSON Schema
serviceos-deploy/        本地/环境部署入口
```

完整实施状态与每个里程碑的代码、迁移、契约和测试证据见
[实施状态总览](serviceos-architecture/docs/implementation-status.md) 与
[Architecture Book](serviceos-architecture/README.md)。最新切片见
[M58 BYD 提审 OutboundDelivery 可靠运行时](serviceos-architecture/architecture/71-m58-byd-review-submission-outbound-delivery.md)。
