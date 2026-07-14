---
title: 研发模块与架构证据追踪矩阵
version: 0.1.0
status: Proposed
---

# 研发模块与架构证据追踪矩阵

## 1. 用途

本矩阵帮助研发 Issue、代码模块、数据库迁移和测试定位权威设计来源。它是索引，不复制源文档语义；发生冲突时，以产品宪法、Accepted ADR 和对应领域文档为准。

## 2. 模块追踪

| 实现模块 | 领域/架构输入 | API/事件输入 | 数据输入 | 验收输入 | 首次阶段 |
|---|---|---|---|---|---|
| identity | ARCH-07、ARCH-21 | API-01/02 通用身份上下文 | DATA-02 | M2 AUTH、M6 SEC | E1 |
| organization | ARCH-01、ARCH-07、ARCH-11 | API-02/04 | DATA-02/04 | M2 AUTH、M4 DSP | E1/E4 |
| project | ARCH-01/03/05 | API-07 | DATA-01 | M2 CFG/WO、M7 ADM | E0/E2 |
| authorization | ARCH-07、ARCH-21 | API-01/02 | DATA-02 | M2 AUTH、M6 SEC | E1 |
| audit | ARCH-07、ARCH-21 | 所有高风险命令 | DATA-02 | M2 AUD、M6 SEC/OPS | E1 |
| authority | ARCH-17、ARCH-20 | API-01/05 authority/fence | DATA-05 | M5 CUT、M6 TX | E1/E5 |
| configuration | ARCH-05 | API-01/02 | DATA-01 | M2 CFG | E2 |
| files | ARCH-10、ARCH-21、ARCH-25 | API-03 资料引用、API-08 文件控制面 | DATA-03、V010 物理迁移 | M3 FILE、M6 SEC、M11 | E1/E3 |
| reliability | ARCH-20、ADR-014 | API-01 通用命令/事件 | DATA-01 | M6 TX | E1 |
| readmodel | PRODUCT-01～07、ARCH-19 | API-06 | DATA-06 | M7 WO/QRY | U0/U1 |
| automation | ARCH-06、ARCH-20 | API-01 事件 | DATA-01 | M2 TASK、M6 TX | E1 |
| operations | ARCH-14、ARCH-20 | API-04 exception | DATA-04 | M4 OPS、M6 TX | E1/E4 |
| workorder | ARCH-03/06 | API-01/02 | DATA-01 | M2 WO | E2 |
| task | ARCH-06 | API-01/02 | DATA-01 | M2 TASK | E1/E2 |
| workflow | ARCH-06/20、ADR-006 | API-01 领域事件 | DATA-01 process link | M2 WF、M6 TX-011 | E2 |
| appointment | ARCH-08 | API-03 | DATA-03 | M3 APT | E3 |
| fieldwork | ARCH-08 | API-03 | DATA-03 | M3 VISIT/FIELD | E3 |
| forms | ARCH-09 | API-03 | DATA-03 | M3 FORM | E3 |
| evidence | ARCH-10 | API-03 | DATA-03 | M3 EVD/FILE | E3 |
| review | ARCH-10 | API-03 | DATA-03 | M3 REV/COR | E3 |
| network | ARCH-11 | API-04 | DATA-04 | M4 NET | E4 |
| dispatch | ARCH-11、ADR-009 | API-04 | DATA-04 | M4 DSP/ASN | E4 |
| sla | ARCH-12 | API-04 | DATA-04 | M4 SLA | E4 |
| integration | ARCH-13、ADR-010/014 | API-04 | DATA-04 | M4 INT/DLV | E2/E4 |
| notification | ARCH-14 | API-04 | DATA-04 | M4 NTF | E4 |
| facts | ARCH-04/15 | API-05 | DATA-05 | M5 FACT | E5 |
| pricing | ARCH-04/15、ADR-011 | API-05 | DATA-05 | M5 CALC | E5 |
| settlement | ARCH-16、ADR-004/011 | API-05（feature-gated） | DATA-05 | FORMAL_SETTLEMENT | 二期 |
| migration | ARCH-17、ADR-012 | API-05 | DATA-05 | M5 MIG | E5 |
| rollout | ARCH-17/18、ADR-012 | API-05 | DATA-05 | M5 CUT、M6 OPS | E5/E6 |

## 3. 不变量追踪

| 不变量 | 运行时执行点 | 数据约束 | 自动化证据 |
|---|---|---|---|
| 发布配置不可变 | Configuration publish/use case | version+digest、禁止 UPDATE published content | M2 CFG |
| 工单锁定配置包 | CreateWorkOrder | configuration_bundle_id/version 非空 | M2 WO |
| Task 是执行责任源 | Task command/assignment | active assignment 排他、aggregateVersion | M2 TASK、M4 ASN |
| 流程不改业务表 | Workflow adapter | 仅 process link/correlation inbox | M2 WF、M6 TX-011 |
| 资料版本不可覆盖 | Evidence finalize/submit | revision append-only、snapshot member 固定 | M3 EVD |
| 审核决定不可覆盖 | Review command | decision version append-only | M3 REV/COR |
| 改派责任原子切换 | Assignment saga | ACTIVE 排他、capacity reservation、task guard | M4 ASN |
| 业务重试唯一调度 | Task executor | Task.nextRunAt 是调度事实，Attempt 记录本次选定值；Delivery 不拥有重试钟 | M4 DLV/NTF、M6 TX |
| 对上/对下隔离 | Pricing context resolver | direction-specific run/items | M5 CALC |
| 缺失不等于零 | Fact/Pricing validation | typed fact state | M5 FACT/CALC |
| 事实更正不穿透结算 | CorrectFact/eligibility/collect | FactEligibilityGuard + run hold | M5 FACT-004A/B |
| SHADOW 永不可结算 | Pricing/Settlement gate | mode + pricingAuthorityVersion + feature | M5 CALC-006/010、SET-009 |
| 一张工单单一写权威 | 所有领域命令 | AuthorityAssignment ACTIVE/version | M5 CUT-002/004、M6 TX-010 |
| 副作用最终门禁 | Delivery/Notification/Assignment/Settlement | fence decision + authorityVersion | M5 CUT-011/012 |
| 同一费用不重复结算 | Settlement collect/line | lineBusinessKey 排他、source XOR | FORMAL_SETTLEMENT SET |
| 数据迁移可追溯 | Migration batch/import API | SourceSnapshot/Lineage/IdMapping | M5 MIG |
| 消息至少一次但结果一次 | Outbox/Inbox/consumer | eventId/digest unique | M6 TX-004/005/006 |
| 跨租户不可见 | Query/Command authorization | tenant + ScopePredicate | M2 AUTH、M6 SEC |

## 4. Issue 模板中的必需引用

```markdown
Architecture: ARCH-xx §n
Decision: ADR-xxx
API/Event: API-xx / Command / Event
Data owner: module + entity
Business sample: M1-xx / SAMPLE-xxx
Acceptance: scenario IDs
Security classification: INTERNAL/CONFIDENTIAL/RESTRICTED
Feature gate/authority: if applicable
```

缺少引用时，Issue 只能作为 discovery/spike，不能直接成为生产业务实现任务。

## 5. 发布 Coverage Report

每次发布按以下维度输出“已验证/未验证/不适用”：

- 业务产品与流程版本；
- 项目、品牌、区域和 cohort；
- Admin/Network/Technician Portal；
- 正常、异常、恢复和权限场景 ID；
- 外部连接器和 sandbox/production 模式；
- 配置包、数据迁移和价格模式；
- SLO/备份/回退证据；
- 已知风险与债务。

存在通用代码或某个样例通过，不得推断所有服务类型、项目或异常已经闭环。

## 6. 当前参考实现证据

| 里程碑 | 代码/迁移 | 自动化测试 | 仍未证明 |
|---|---|---|---|
| M8 | Project、Audit、Idempotency、Outbox 事务切片 | Modulith、Domain、Contract、PostgreSQL IT | 完整 E1/履约链路 |
| M9 | OIDC principal、tenant/capability 授权、Inbox、Outbox worker/attempt | Identity/Auth/Web MVC/Worker Unit + PostgreSQL IT | RoleGrant/ScopePredicate/FieldPolicy、正式 IdP/Broker、Outbox DEAD 异常 Task |
| M10 | 自动 Task、ExecutionAttempt、claim/lease、重试时钟、OperationalException、HUMAN handling Task | Task worker Unit + PostgreSQL IT + Event Schema | automation 模块提取、正式 Broker 路由、通用 Task 动作/负责人、authority fence、运行指标 |
| M11 | UploadSession、私有短期传输、Finalize 校验、StoredFile 隔离、扫描 Task、授权下载 | File Unit/Web/Contract + PostgreSQL IT + Event Schema | 正式对象存储/反病毒、multipart/清理、EvidenceSlot 关系、OCR/质量/保留销毁、真实环境 SLO |
| M12 | Git 基线 OpenAPI diff、事件版本不可变门禁、固定 TypeScript 客户端生成与来源清单 | Parser/Schema/Generator JUnit + Shell 正负向门禁 + 生成树重现性 + 根仓库 22 个 PostgreSQL IT | Portal 消费/编译、npm 发布签名/SBOM、多语言 SDK、远端 workflow 绿色结果 |
| M13 | correlation 上下文、W3C Outbox Trace、探针、Prometheus 指标、ECS JSON 脱敏、本地可观测性栈 | Unit/Web + PostgreSQL IT + in-memory Span + Shell 泄露门禁 + Compose/组件 smoke | 生产 HA/告警/容量、集中日志、跨服务完整 Trace、远端 workflow 绿色结果 |
| M14 | 单一非 root OCI 镜像、独立 Flyway 迁移、runtime 最小数据库权限、失败关闭发布、应用回滚/恢复 | Docker build/inspect + PostgreSQL 18 空库迁移 + HTTP/security smoke + rollback/negative rehearsal | 正式 registry/SBOM/签名、真实 orchestrator 滚动、PITR/对象恢复、容量、生产审批、远端 workflow 绿色结果 |
| M15 | tenant/project/region/network RoleGrant 解析、scope 解释、FieldPolicy 缺省隐藏与显式拒绝优先 | Authorization Unit + PostgreSQL IT | organization/relation scope、读模型/导出/文件全入口接线、配置发布 UI |
| M16 | Published Asset/Bundle 最小发布解析、tenant/project/bundle 工单锁定、BYD CPIM 入站创建工单 | Modulith + Configuration/WorkOrder/BYD HTTP PostgreSQL IT + V014 fail-closed migration | Stage/Task/Workflow、Audit/Outbox、完整配置审批、真实 CPIM sandbox 与全勘安链路 |
| M17 | WorkOrderReceived Outbox、Inbox 幂等、精确 Workflow 启动、首 Stage/Task 与 WorkOrder 激活 | Modulith + Parser/Dispatcher Unit + Event Schema + WorkflowBootstrap PostgreSQL IT + V015～V017 | 后续节点推进、网关/并行/等待、负责人/SLA 绑定、正式 Broker、完整勘安链路 |
| M18 | TaskCompleted 领域事件、冻结定义解析、NodeInstance 与同阶段唯一无条件下一任务推进 | Parser/Dispatcher Unit + Event Schema + WorkflowLinearProgression PostgreSQL IT + V018 | 跨阶段/END、网关/并行/等待、人工任务动作、负责人/SLA、正式 Broker、完整勘安链路 |
