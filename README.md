# ServiceOS

ServiceOS 是面向新能源充电设施现场服务的可配置履约平台。当前仓库同时保存架构事实源、参考后端工程和机器可读契约。

## 当前可运行基线

- Java 21 + Spring Boot 4.1 + Spring Modulith 2.1 模块化单体；
- Maven Wrapper 一条命令构建；
- 21 个 Spring Modulith 模块，覆盖身份授权、配置、工单、工作流、任务、派单、SLA、预约、现场 Visit、
  表单、资料、审核整改、文件、集成、可靠消息、审计、运营异常与跨模块只读投影；
- PostgreSQL + Flyway 模块前缀物理表；
- `CreateProject` 首条命令链路；
- 聚合、审计、幂等结果和 Outbox 同一事务；
- OIDC/JWT 主体、capability/tenant scope 后端授权和拒绝审计；
- 稳定内部 Principal、不可变外部 IdentityLink、PersonProfile/多 Persona、受控 JIT、主体启停实时失权与安全目录 API；
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
- UNKNOWN 外部交付的 USER/HIGH capability 人工重发、审批/版本门禁、不可变 ReplayRequest，
  保留旧 UNKNOWN Attempt 并复用冻结 payload；
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
- Workflow 显式 `slaRef` 与 SLA v1 发布门禁；Task ELAPSED 时钟、到期对账、MET/MET_LATE、
  Inbox/Outbox 和不可变时钟历史；
- `sla.read` + Project Scope 授权的 SLA 工作台、工单时间线和 segment/milestone 详情投影，
  以及服务端 `asOf` 与稳定游标；
- 实时 TENANT/PROJECT RoleGrant 授权项目集合，以及无需逐行鉴权的跨项目 SLA 队列；
- Project 有效期 REGION 关系，以及 REGION RoleGrant 驱动的跨项目 SLA 队列；
- Project 有效期 NETWORK 关系，以及 NETWORK RoleGrant 驱动的跨项目 SLA 队列；
- Project REGION/NETWORK 当前关系整组修订、不可变修订收据与即时授权投影同步；
- `project.read` + 实时 TENANT/PROJECT/REGION/NETWORK RoleGrant 的项目目录、详情与范围历史查询；
- `workOrder.read` + 实时项目范围的工单目录与不含客户 PII 的详情查询；
- 复用同一授权边界的 Workflow/Stage 当前投影与工单 Task 摘要稳定分页；
- `task.read` + 实时授权范围的独立 Task 队列、`assignee=me` 事实筛选与冻结引用详情；
- 复用 Task 读取边界并按 capability、责任、状态和 guard 实时投影现有人工命令 allowed-actions；
- `task.read` 实时授权的自动 Task 执行 Attempt 历史、稳定分页与安全错误码投影；
- 独立 `readmodel` 模块可靠消费 WorkOrder/Workflow/Stage/Task、Appointment/Visit/ContactAttempt
  、SLA 与资料/审核事件形成授权工单执行时间线；
- Spring Modulith 边界验证与 PostgreSQL Testcontainers 集成测试。
- Admin Web 已进入 CI 阻断构建；开发态 Keycloak Authorization Code + PKCE 的真实
  Backend/PostgreSQL/Chrome E2E 也纳入 PR 门禁，覆盖工单目录、工作区、详情、Stage、Task、SLA、
  核心时间线，以及 Task 手工分配候选、领取、释放、锁定表单提交、资料 Begin/PUT/Finalize、
  本地扫描与机器校验、Snapshot、INTERNAL ReviewCase 普通 APPROVED、REJECTED→WAIVE、
  FORCE_APPROVED→reopen、正常补传/复审完结，预约 propose→confirm→上门
  check-in/check-out，以及 BYD 提审外发 ACK、厂端回调与入站→激活→同单预约上门→表单/资料/
  驳回整改补传复审/外发/完结的局部写链路。

当前工程基线推进至 **M196**，但仍是模块化后端纵向切片，不代表完整现场履约平台已经交付。
OCR/CV、计算字段/脚本、复杂工作流、BUSINESS 日历与 SLA 暂停/预警/升级/通知、履约事实与试算、对账结算、正式
IdP/Broker/对象存储/扫描服务、Consumer Identity、离线工作包以及 Network 写命令仍未实现。
Admin 统一用户中心（M187）、Portal `/me` 上下文/导航（M188）、Admin 个人 SavedView（M189）、
Admin UI Preferences（M190）、Admin 共享 SavedView（M191）、Admin 受控全局搜索（M192）、
Admin 最近访问（M193）、Network Portal 只读查询（M194）、Technician Portal Feed（M195）与 Network Portal 指派师傅（M196）已交付。

## 快速验证

先运行无需启动 JVM、PostgreSQL 或镜像构建的里程碑预检：

```bash
bash scripts/verify-milestone-preflight.sh
```

预检通过且已形成稳定的最终候选 HEAD 后，再执行完整门禁：

```bash
./mvnw clean verify
```

Flyway 当前版本与迁移数量由迁移目录自动推导；需要查看时执行：

```bash
bash scripts/migration-baseline.sh
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

Admin 试点局部读写链路的真实本地冒烟：

```bash
serviceos-deploy/admin-pilot/verify-admin-smoke.sh
```

该冒烟还以独立动态工单证明 INTERNAL 审核 `REJECTED` 后自动创建
CorrectionCase/整改 Task，并由具备 CRITICAL 能力的 Admin 豁免 Case、同事务取消整改 Task。
另一独立工单证明 `FORCE_APPROVED` 保持显式决定，重开后导航到同 Snapshot 的后继 OPEN Case。
第四套夹具证明正常补传 `resubmit`→`close`→复审 APPROVED→双引用 complete→`FULFILLED`。
第五套夹具证明预约 `propose`→`confirm`→上门 `check-in`/`check-out`。
第六套夹具证明 BYD 提审外发至 `ACKNOWLEDGED`（本地 stub），并经 CPIM 签名厂端回调关闭 CLIENT Case。

## 目录

```text
serviceos-architecture/  架构、产品、API、数据、测试和路线图事实源
serviceos-backend/       Java 模块化单体参考实现
serviceos-contracts/     OpenAPI 与事件 JSON Schema
serviceos-deploy/        本地/环境部署入口
```

完整实施状态与每个里程碑的代码、迁移、契约和测试证据见
[实施状态总览](serviceos-architecture/docs/implementation-status.md) 与
[Architecture Book](serviceos-architecture/README.md)。Agent 探索入口见
[任务导航](serviceos-architecture/docs/agent-navigation.md) 与
[里程碑索引](serviceos-architecture/docs/milestone-index.md)。验证阶段划分、CI 去重和最终候选规则见
[验证执行策略](serviceos-architecture/docs/verification-execution-policy.md)。最新切片从实施状态总览的
`latestMilestone` 进入生成式里程碑索引定位，不在根 README 维护易过期的单里程碑指针。
