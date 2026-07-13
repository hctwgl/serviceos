# ServiceOS Architecture Book

ServiceOS 是面向新能源充电设施现场服务的履约平台。本仓库是产品、业务、研发、测试和运维共同使用的架构事实源。

首个落地行业是新能源汽车家充勘测、安装、维修和拆装；平台边界面向可复用的现场履约能力，而不是为每家车企复制业务代码。

## 阅读顺序

1. [产品宪法](architecture/00-product-constitution.md)
2. [业务领域](architecture/01-business-domain.md)
3. [业务能力地图](architecture/02-capability-map.md)
4. [核心领域模型](architecture/03-domain-model.md)
5. [履约事实、计价与结算](architecture/04-fulfillment-pricing-settlement.md)
6. [配置资产与版本中心](architecture/05-configuration-version-center.md)
7. [工单、任务与流程执行内核](architecture/06-work-order-task-execution-kernel.md)
8. [核心命令与事件契约](api/01-command-event-contracts.md)
9. [工单与任务 HTTP API](api/02-work-order-task-http-api.md)
10. [配置与执行内核逻辑数据模型](data/01-execution-logical-model.md)
11. [身份、授权与审计](architecture/07-identity-authorization-audit.md)
12. [预约与现场作业](architecture/08-appointment-field-operations.md)
13. [动态表单与字段引擎](architecture/09-dynamic-form-field-engine.md)
14. [资料、审核与整改闭环](architecture/10-evidence-review-correction.md)
15. [现场作业 HTTP API](api/03-field-operations-http-api.md)
16. [现场作业逻辑数据模型](data/03-field-operations-logical-model.md)
17. [服务网络与派单引擎](architecture/11-service-network-dispatch.md)
18. [SLA 时钟、预警与升级](architecture/12-sla-clock-escalation.md)
19. [车企集成、回传与可靠交付](architecture/13-integration-reliability.md)
20. [通知与运营异常中心](architecture/14-notification-operational-exception.md)
21. [M4 自动化 HTTP API](api/04-automation-integration-http-api.md)
22. [M4 逻辑数据模型](data/04-automation-integration-logical-model.md)
23. [履约事实提取与双向试算运行时](architecture/15-fulfillment-fact-calculation-runtime.md)
24. [对账、结算、争议与调整边界](architecture/16-reconciliation-settlement-boundary.md)
25. [历史数据迁移、双轨与切换](architecture/17-data-migration-cutover.md)
26. [试点、灰度发布与可观测性](architecture/18-pilot-rollout-observability.md)
27. [M5 HTTP API](api/05-pricing-migration-pilot-http-api.md)
28. [M5 逻辑数据模型](data/05-pricing-migration-pilot-logical-model.md)
29. [授权与审计逻辑数据模型](data/02-authorization-audit-logical-model.md)
30. [M2 执行内核验收矩阵](testing/01-m2-execution-acceptance-matrix.md)
31. [M3 现场作业验收矩阵](testing/02-m3-field-operations-acceptance-matrix.md)
32. [M4 自动化验收矩阵](testing/03-m4-automation-integration-acceptance.md)
33. [M5 试算与试点验收矩阵](testing/04-m5-pricing-migration-pilot-acceptance.md)
34. [研发工程、模块与应用服务实施蓝图](architecture/19-engineering-module-blueprint.md)
35. [事务、消息、幂等与并发实施蓝图](architecture/20-transaction-messaging-concurrency-blueprint.md)
36. [安全、非功能、部署与运维实施蓝图](architecture/21-security-nfr-deployment-blueprint.md)
37. [M6 工程就绪验收矩阵](testing/05-m6-engineering-readiness-acceptance.md)
38. [M6 研发交付计划](roadmap/01-m6-engineering-delivery-plan.md)
39. [MVP 与实施路线](roadmap/00-mvp-roadmap.md)
40. [已确认业务事实](research/00-confirmed-business-facts.md)
41. [M1 业务资产基线填写手册](research/01-m1-business-asset-pack.md)
42. [术语表](docs/glossary.md)
43. [研发模块与架构证据追踪矩阵](docs/implementation-traceability-matrix.md)

## 仓库结构

```text
serviceos-architecture/
├── architecture/    # 产品与领域架构
├── decisions/       # 架构决策记录（ADR）
├── docs/            # 术语、规范和索引
├── product/         # PRD、应用与交互设计
├── research/        # 已确认事实、待验证假设和调研材料
├── roadmap/         # MVP、里程碑与交付计划
├── api/             # API 与事件契约
├── data/            # 逻辑数据模型与数据字典
├── diagrams/        # 独立 Mermaid/PlantUML 源文件
└── testing/         # 架构验收矩阵与故障恢复场景
```

## 文档状态

文档头部使用以下状态：

- `Draft`：正在形成，不可作为研发承诺；
- `Proposed`：可评审的完整提案；
- `Accepted`：已通过决策，可指导实现；
- `Superseded`：已被新文档或 ADR 替代。

## 变更纪律

- 业务事实与架构建议必须分开记录；
- 会影响长期边界的选择必须写 ADR；
- 流程、表单、资料、规则、SLA、派单策略和价格方案必须版本化；
- 新车企或新品牌默认通过配置接入，不新增车企专属核心表或核心分支；
- MVP 只实现已验证的共性能力，通用平台能力按明确扩展点预留，不提前铺满。

## 当前基线

当前版本为 `0.7.0`，包含 M0–M6 架构与研发实施基线：业务资产、履约内核、现场作业、自动化闭环、双向试算、迁移灰度、工程模块、事务消息、安全非功能和生产交付门禁。它不是完整 PRD，也不代表所有车企和业务类型已经实现。

核心业务编码前，必须使用 `research/templates/` 中的模板完成首个试点项目基线，并用真实脱敏工单完成桌面演练。
