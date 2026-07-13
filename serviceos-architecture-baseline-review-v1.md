---
title: ServiceOS 仓库架构基线审查
version: 1.0.0
status: Proposed
reviewed_snapshot: 0.15.0
review_date: 2026-07-13
---

# ServiceOS 仓库架构基线审查 v1.0

## 1. 审查范围与方法

本次审查基于用户上传的 `serviceos-master.zip`，对应仓库文档基线 `0.15.0`。共检查：

- 100 份 Markdown 文档；
- 架构、ADR、API、逻辑数据模型、产品规格、测试矩阵、路线图和业务调研模板；
- 后端参考工程、机器可读契约、部署入口与 CI 文件的结构；
- 文档链接、状态、版本、待确认项及跨文档可追踪关系。

审查分为三层：

1. **结构检查**：文档完整性、链接、状态、版本、目录与引用；
2. **语义检查**：核心领域边界、关键不变量、API/数据/验收一致性；
3. **研发可执行性检查**：开发能否据此确定字段、命令、约束、异常、权限和验收。

限制：当前环境无法联网下载 Maven Wrapper，因此没有完成 `clean verify` 的实际复验；“Implemented”结论仅代表仓库声明与静态证据存在，仍需在 CI 或具备依赖缓存的环境复验。

## 2. 执行结论

### 2.1 总体评级

**架构成熟度：B+（平台架构基线成熟）**

**业务研发就绪度：C（尚未达到核心业务全面开工条件）**

**工程基础就绪度：B（可继续建设基础设施和参考纵向切片）**

仓库已经远超普通 PRD，具备优秀的平台化方向：

- 模块化单体优先；
- Task-centric 执行内核；
- 配置包版本锁定；
- 履约事实驱动双向计价；
- Outbox/Inbox、幂等、租约和人工接管；
- 资料不可变与审核决定只追加；
- 派单硬过滤、评分和容量预占；
- 权限、审计、文件安全、可观测性和部署 Gate；
- 架构、API、数据、测试之间已有追踪矩阵。

但当前不能直接宣布“按文档全面开发业务平台”。最大的风险不在技术架构，而在以下四个断点：

1. **所有核心架构文档仍为 Proposed，没有 Accepted 基线；**
2. **业务事实和首个试点项目资产仍未填写、签署；**
3. **API 与配置规则多数仍是人类可读设计，尚未成为完整机器契约；**
4. **逻辑模型尚未下沉为首个业务切片的物理模型、工作流和可执行验收样本。**

### 2.2 可以立即开展的工作

- 继续完成身份、授权、审计、可靠消息、Task Scheduler、安全文件、可观测性和部署基础；
- 建立配置 Schema、契约 CI、模块边界测试和数据库迁移纪律；
- 用一个试点车企项目完成端到端“薄切片”；
- 建立业务资产录入与发布工具的最小版本。

### 2.3 暂不应开展的工作

- 同时开发全部车企、全部业务类型；
- 根据未签署访谈结果直接创建大量字段和表；
- 直接实现正式结算权威链路；
- 自研完整 BPMN/DMN/低代码设计器；
- 在没有真实工单样本的情况下实现“通用派单/通用价格引擎”；
- 将 Proposed 文档当作不可变研发承诺。

## 3. P0 研发阻塞项

| 编号 | 阻塞项 | 风险 | 完成标准 |
|---|---|---|---|
| P0-01 | 核心文档尚未 Accepted | 多团队对同一概念产生不同实现 | 产品宪法、领域模型、执行内核、配置版本、授权、资料审核、派单、SLA、集成至少完成一次正式评审并标记 Accepted |
| P0-02 | 首个试点项目未确定并基线化 | 所有字段、流程和规则仍属推测 | 选定一个车企+品牌+业务类型+区域，填完 M1 九张模板并由业务负责人签字 |
| P0-03 | 缺真实脱敏工单样本包 | 无法验证流程、条件资料、异常和计价 | 至少 20 个正常样本、20 个异常样本、历史状态日志、资料和结算结果 |
| P0-04 | 配置资产缺机器 Schema | 配置中心无法可靠校验、发布和回滚 | 为 Workflow/Form/Evidence/Rule/SLA/Dispatch/Pricing/Integration Bundle 定义 JSON Schema、ID、版本、依赖和发布校验 |
| P0-05 | 业务 API 机器契约覆盖不足 | 前后端、连接器和测试无法并行 | 将 API-01～08 的 MVP 端点落入 OpenAPI；首批关键事件落入 JSON Schema |
| P0-06 | 缺首个业务流程可执行定义 | Task/Workflow 边界可能各自解释 | 给出勘安试点完整流程定义、节点输入输出、路由条件、补偿、SLA 和任务负责人策略 |
| P0-07 | 物理数据模型缺失 | 开发会自行推导表结构和唯一约束 | 产出试点切片 ERD、表/列、唯一键、外键、索引、并发版本和保留策略 |
| P0-08 | NFR 参数未签署 | 无法做容量、恢复和上线 Gate | 确认峰值、附件量、SLO、RPO/RTO、数据保留、安全等级和部署环境 |
| P0-09 | 正式 IdP/Broker/对象存储等技术选型未决 | 参考适配器可能被误用到生产 | 输出基础设施选型 ADR 和生产适配器验收标准 |
| P0-10 | 结算仍缺真实规则基线 | 正式金额链路风险最高 | 完成对上/对下价格矩阵、履约事实目录、调账/红冲/争议样本，并保持 SHADOW 优先 |

## 4. 主要跨文档问题

### 4.1 状态治理不完整

文档规定 `Draft / Proposed / Accepted / Superseded`，但：

- 核心架构几乎全部为 `Proposed`；
- ADR 文件没有统一 YAML 元数据和明确的 `Accepted` 状态；
- 已实现文档与对应 ADR/架构提案之间缺少“批准人、批准日期、适用版本”。

建议建立 `docs/document-governance.md`，并在 CI 校验文档元数据。

### 4.2 “事实源”层级仍不够明确

已经有追踪矩阵，但冲突优先级需进一步固定：

```text
Accepted ADR
> Accepted 领域/架构规范
> 机器可读契约与 Schema
> 逻辑/物理数据模型
> 产品规格
> 验收矩阵
> 参考实现说明
> 代码注释
```

机器契约与架构文档冲突时，由谁阻断、谁裁决，需要写入治理规则。

### 4.3 平台设计领先于业务证据

平台抽象质量高，但 `research/00-confirmed-business-facts.md` 仍是 Draft，且大量关键内容待确认。当前最大的架构风险是“正确地实现了未经验证的抽象”。

应把 M1 业务资产包从文档附属品提升为开发 Gate。

### 4.4 人类可读 API 与机器契约存在落差

仓库中有 8 份 HTTP/API 设计文档，但机器可读契约目前只看到一个核心 OpenAPI 文件和少量事件 Schema。需要建立端点覆盖率与事件覆盖率报告。

### 4.5 图表源目录基本为空

`diagrams/` 只有 README。核心模型依赖大量文本描述，缺少：

- C4 Context / Container / Component；
- 核心聚合关系图；
- 勘安端到端时序；
- 配置发布与锁定时序；
- 派单并发预占时序；
- 资料补传与多轮审核状态图；
- Outbox/Inbox 和外部回传恢复图；
- 结算 SHADOW 到 AUTHORITATIVE 状态图。

这不会阻止单人理解，但会显著增加团队沟通成本。

## 5. 分组审查结论

### 5.1 架构文档

`architecture/00～21` 已足以指导详细设计，边界、原则和关键不变量总体优秀；但仍不足以单独指导开发具体业务功能，因为缺少试点配置、机器 Schema、物理模型和真实样本。

`architecture/22～28` 对工程参考切片描述清楚，能够指导基础工程延续；不应被误解为工单、派单、表单、资料、结算等核心业务已经实现。

### 5.2 ADR

16 份 ADR 的方向合理且相互一致，是仓库最有价值的资产之一。主要缺口：

- 缺标准元数据；
- 缺状态和决策人；
- 缺替代方案的量化比较；
- 缺何时重新评估的触发条件；
- 尚缺配置 DSL、工作流产品选择、规则运行时、搜索/投影、对象存储和多租户隔离等关键 ADR。

### 5.3 API

API 文档覆盖面很好，命令式写入、幂等、乐观并发和服务端动作授权方向正确。当前不足：

- 请求/响应字段仍有概念性描述；
- 错误码目录不完整；
- 分页、过滤、排序、导出、异步任务统一规范需独立成文；
- 文件、批量操作和长任务的状态模型需要统一；
- 外部车企接口需要独立的适配器契约和映射版本规范；
- 尚未全部下沉为 OpenAPI 和事件 Schema。

### 5.4 数据模型

6 份逻辑数据模型覆盖核心领域，足以指导聚合和模块边界。开发前仍需补：

- 物理 ERD；
- 字段数据类型、长度、空值和默认值；
- 唯一键与业务幂等键；
- 外键策略和跨模块引用策略；
- 索引与分区；
- JSONB 字段 Schema；
- 数据保留、归档、脱敏和删除；
- 读模型刷新与重建；
- 物理迁移顺序和大表变更策略。

### 5.5 产品规格

总部、网点和师傅端的信息架构较完整，尤其跨 Portal 状态反馈、移动端离线和权限矩阵方向正确。仍缺：

- 首个试点的页面清单冻结版；
- 页面级字段表、默认值和校验；
- 每个动作对应命令、权限、成功/失败反馈；
- 空状态、异常状态、批量操作和导出；
- 可点击原型或至少低保真线框；
- 客服审核、派单异常、资料整改等关键工作台的队列优先级；
- 用户侧预约/签字/确认入口的产品边界。

### 5.6 测试与验收

验收矩阵系统性很好，包含并发、故障、幂等和恢复，这是明显优势。缺口是：

- 尚未绑定具体业务样本 ID；
- 大部分还是文档用例，不是可执行测试；
- 缺端到端测试数据工厂；
- 缺性能模型和基准脚本；
- 缺安全威胁场景映射；
- 缺跨 Portal 契约测试和移动端离线自动化方案。

### 5.7 路线图

MVP 和 M6/M7 计划具有 Gate 思维，优于按日期堆功能。需要进一步补充：

- 明确的试点项目；
- 人员与团队容量；
- 依赖和关键路径；
- 每个里程碑的产品负责人、技术负责人和业务签署人；
- 预算、外部采购和环境准备；
- “停止/缩小/回退”阈值；
- 文档 0.15.0 与 M8～M14 的编号关系说明，避免 M6/M7 未完成却出现 M14 的理解混乱。

### 5.8 业务调研与资产模板

模板覆盖面很好，但当前全部是空模板或 Draft。它们是最紧迫的工作，不是后续补充材料。没有这些资产，配置中心、表单、资料、派单、SLA 和计价都只能实现框架，不能实现正确业务。

## 6. 下一批必须补齐的文件

### P0：下一批立即编写

| 建议文件 | 目的 |
|---|---|
| `research/pilots/01-byd-survey-install-baseline.md` | 冻结首个试点范围、组织、区域、业务类型和成功标准 |
| `research/pilots/02-byd-sample-catalog.md` | 登记真实脱敏正常/异常工单样本及预期结果 |
| `configuration/01-configuration-asset-meta-model.md` | 定义所有配置资产共有 ID、版本、依赖、状态和生命周期 |
| `configuration/schemas/*.schema.json` | Workflow/Form/Evidence/Rule/SLA/Dispatch/Pricing/Integration 机器 Schema |
| `workflow/01-byd-survey-install-executable-spec.md` | 首个勘安流程可执行定义和任务边界 |
| `data/07-pilot-physical-data-model.md` | 首个切片物理表、约束、索引和迁移顺序 |
| `api/09-api-style-error-pagination.md` | 统一错误、分页、过滤、排序、批量和异步任务规范 |
| `security/01-threat-model.md` | STRIDE/滥用场景、敏感数据流和控制映射 |
| `operations/01-production-slo-rpo-rto-capacity.md` | 签署容量、SLO、RPO/RTO 与保留参数 |
| `governance/01-document-and-decision-governance.md` | 文档状态、批准、冲突优先级和 CI 校验 |
| `testing/13-pilot-e2e-sample-matrix.md` | 将业务样本绑定命令、事件、页面、数据和自动化测试 |
| `decisions/ADR-017-workflow-runtime-selection.md` | 明确采用自研状态编排、Flowable/Camunda 或其他运行时 |
| `decisions/ADR-018-configuration-schema-and-expression-runtime.md` | 明确表达式、脚本沙箱和配置验证技术 |
| `decisions/ADR-019-production-infrastructure-adapters.md` | IdP、Broker、对象存储、扫描、短信、地图等生产选型 |

### P1：首个业务切片开发前补齐

| 建议文件 | 目的 |
|---|---|
| `product/08-pilot-page-field-action-spec.md` | 页面字段、动作、权限、校验和反馈的冻结版 |
| `integration/01-oem-adapter-contract.md` | 车企接入、映射、幂等、回执、重放和人工修复 |
| `pricing/01-pricing-rule-language.md` | 价格上下文、公式、阶梯、封顶、舍入和解释模型 |
| `dispatch/01-dispatch-policy-language.md` | 硬过滤、评分、比例修正、容量预占和解释模型 |
| `sla/01-business-calendar-and-clock-spec.md` | 工作日历、暂停、恢复、升级和重算细节 |
| `data/08-data-retention-privacy-classification.md` | 数据分级、脱敏、访问、归档和删除 |
| `operations/02-runbook-index.md` | 回传失败、队列积压、扫描故障、派单失败、回滚等 Runbook |
| `diagrams/*.mmd` | 补齐核心 C4、状态、时序和数据流图 |

### P2：试点上线前补齐

- OpenAPI 全覆盖与 SDK 发布策略；
- 性能测试方案和容量基准；
- 灾备与恢复演练脚本；
- 数据迁移字段映射和对账报告；
- 配置发布审批、灰度和回滚操作手册；
- 客服、网点、师傅培训手册；
- 生产监控面板、告警阈值和责任人；
- SaaS 多租户、计费和租户生命周期方案（如确定产品化）。

## 7. 推荐的下一步顺序

1. **冻结试点**：建议选“比亚迪某品牌、一个区域、勘安业务”，不要一开始覆盖全部品牌；
2. **完成 M1 资产包**：九张模板全部填写并签署；
3. **接受架构基线**：核心文档评审后从 Proposed 转 Accepted；
4. **定义配置 Schema 和首个可执行流程**；
5. **生成物理模型、完整 OpenAPI 和业务事件 Schema**；
6. **建立端到端薄切片**：接单→分配→派网点→派师傅→预约→勘测→资料→审核；
7. **用真实样本做桌面演练和自动化验收**；
8. **再扩展安装、回传和影子计价**；
9. **最后启用正式对上/对下结算权威链路。**

## 8. 最终判断

### 是否足以指导基础工程研发？

**是。** 模块化单体、事务消息、授权审计、Task Scheduler、安全文件、契约 CI、可观测性和部署方向已经足够明确。

### 是否足以指导首个业务薄切片？

**有条件地可以。** 前提是先补齐试点业务资产、配置 Schema、可执行流程、物理模型和机器契约。

### 是否足以指导整个平台全面开发？

**否。** 当前业务事实、项目配置、价格结算和外部接口仍未完成签署；直接全面开发会把合理的架构假设固化成昂贵代码。

### 是否需要推翻当前架构？

**不需要。** 当前架构主方向正确。下一阶段应从“继续扩写宏观架构”转向“用一个真实试点把架构压实为可执行资产和端到端证据”。

## 附录 A：逐份文档检查表

评级说明：

- **A**：可直接作为研发权威输入；
- **B**：内容成熟，完成评审/参数化后可指导研发；
- **C**：可作为设计输入，但缺机器契约、物理细节或真实业务基线；
- **D**：尚未填写或未签署，不能作为研发依据。

| 文件 | 标题 | 状态 | 版本 | 评级 | 结论 |
|---|---|---|---|---|---|
| `README.md` | ServiceOS Architecture Book | — | — | B | 结构和阅读路径清晰 |
| `api/01-command-event-contracts.md` | 核心命令与事件契约 | Proposed | 0.1.0 | C | 可指导接口设计，尚不足以替代完整 OpenAPI/Schema |
| `api/02-work-order-task-http-api.md` | 工单与任务 HTTP API 基线 | Proposed | 0.1.0 | C | 可指导接口设计，尚不足以替代完整 OpenAPI/Schema |
| `api/03-field-operations-http-api.md` | 预约、现场作业、表单、资料与审核 HTTP API | Proposed | 0.1.0 | C | 可指导接口设计，尚不足以替代完整 OpenAPI/Schema |
| `api/04-automation-integration-http-api.md` | 派单、SLA、集成、通知与异常 HTTP API | Proposed | 0.1.0 | C | 可指导接口设计，尚不足以替代完整 OpenAPI/Schema |
| `api/05-pricing-migration-pilot-http-api.md` | 履约事实、试算、结算、迁移与试点 HTTP API | Proposed | 0.1.0 | C | 可指导接口设计，尚不足以替代完整 OpenAPI/Schema |
| `api/06-application-query-preference-http-api.md` | 应用工作区、队列与用户偏好 HTTP API | Proposed | 0.1.0 | C | 可指导接口设计，尚不足以替代完整 OpenAPI/Schema |
| `api/07-project-configuration-access-governance-http-api.md` | 项目、配置、授权与审计治理 HTTP API | Proposed | 0.1.0 | C | 可指导接口设计，尚不足以替代完整 OpenAPI/Schema |
| `api/08-secure-file-http-api.md` | 安全文件生命周期 HTTP API | Proposed | 0.1.0 | C | 可指导接口设计，尚不足以替代完整 OpenAPI/Schema |
| `api/README.md` | API 与事件契约 | — | — | B | 结构和阅读路径清晰 |
| `architecture/00-product-constitution.md` | ServiceOS 产品宪法 | Proposed | 0.1.0 | B | 领域/平台设计成熟，可作为详细设计输入，但尚未 Accepted |
| `architecture/01-business-domain.md` | 业务领域与边界 | Proposed | 0.1.0 | B | 领域/平台设计成熟，可作为详细设计输入，但尚未 Accepted |
| `architecture/02-capability-map.md` | 业务能力地图 | Proposed | 0.1.0 | B | 领域/平台设计成熟，可作为详细设计输入，但尚未 Accepted |
| `architecture/03-domain-model.md` | 核心领域模型 | Proposed | 0.1.0 | B | 领域/平台设计成熟，可作为详细设计输入，但尚未 Accepted |
| `architecture/04-fulfillment-pricing-settlement.md` | 履约事实、计价与结算设计 | Proposed | 0.1.0 | B | 领域/平台设计成熟，可作为详细设计输入，但尚未 Accepted |
| `architecture/05-configuration-version-center.md` | 配置资产与版本中心设计 | Proposed | 0.1.0 | B | 领域/平台设计成熟，可作为详细设计输入，但尚未 Accepted |
| `architecture/06-work-order-task-execution-kernel.md` | 工单、任务与流程执行内核设计 | Proposed | 0.1.0 | B | 领域/平台设计成熟，可作为详细设计输入，但尚未 Accepted |
| `architecture/07-identity-authorization-audit.md` | 身份、授权与审计设计 | Proposed | 0.1.0 | B | 领域/平台设计成熟，可作为详细设计输入，但尚未 Accepted |
| `architecture/08-appointment-field-operations.md` | 预约与现场作业设计 | Proposed | 0.1.0 | B | 领域/平台设计成熟，可作为详细设计输入，但尚未 Accepted |
| `architecture/09-dynamic-form-field-engine.md` | 动态表单与字段引擎设计 | Proposed | 0.1.0 | B | 领域/平台设计成熟，可作为详细设计输入，但尚未 Accepted |
| `architecture/10-evidence-review-correction.md` | 资料、审核与整改闭环设计 | Proposed | 0.1.0 | B | 领域/平台设计成熟，可作为详细设计输入，但尚未 Accepted |
| `architecture/11-service-network-dispatch.md` | 服务网络与派单引擎设计 | Proposed | 0.1.0 | B | 领域/平台设计成熟，可作为详细设计输入，但尚未 Accepted |
| `architecture/12-sla-clock-escalation.md` | SLA 时钟、预警与升级设计 | Proposed | 0.1.0 | B | 领域/平台设计成熟，可作为详细设计输入，但尚未 Accepted |
| `architecture/13-integration-reliability.md` | 车企集成、回传与可靠交付设计 | Proposed | 0.1.0 | B | 领域/平台设计成熟，可作为详细设计输入，但尚未 Accepted |
| `architecture/14-notification-operational-exception.md` | 通知与运营异常中心设计 | Proposed | 0.1.0 | B | 领域/平台设计成熟，可作为详细设计输入，但尚未 Accepted |
| `architecture/15-fulfillment-fact-calculation-runtime.md` | 履约事实提取与双向试算运行时 | Proposed | 0.1.0 | B | 领域/平台设计成熟，可作为详细设计输入，但尚未 Accepted |
| `architecture/16-reconciliation-settlement-boundary.md` | 对账、结算、争议与调整边界 | Proposed | 0.1.0 | B | 领域/平台设计成熟，可作为详细设计输入，但尚未 Accepted |
| `architecture/17-data-migration-cutover.md` | 历史数据迁移、双轨与切换设计 | Proposed | 0.1.0 | B | 领域/平台设计成熟，可作为详细设计输入，但尚未 Accepted |
| `architecture/18-pilot-rollout-observability.md` | 试点、灰度发布与生产可观测性 | Proposed | 0.1.0 | B | 领域/平台设计成熟，可作为详细设计输入，但尚未 Accepted |
| `architecture/19-engineering-module-blueprint.md` | 研发工程、模块与应用服务实施蓝图 | Proposed | 0.1.0 | B | 领域/平台设计成熟，可作为详细设计输入，但尚未 Accepted |
| `architecture/20-transaction-messaging-concurrency-blueprint.md` | 事务、消息、幂等、并发与后台执行实施蓝图 | Proposed | 0.1.0 | B | 领域/平台设计成熟，可作为详细设计输入，但尚未 Accepted |
| `architecture/21-security-nfr-deployment-blueprint.md` | 安全、非功能、部署与运维实施蓝图 | Proposed | 0.1.0 | B | 领域/平台设计成熟，可作为详细设计输入，但尚未 Accepted |
| `architecture/22-engineering-reference-implementation.md` | M8 工程参考实现与首条事务纵向切片 | Proposed | 0.1.0 | B | 参考实现边界明确，需结合可运行证据复验 |
| `architecture/23-identity-authorization-reliable-worker-implementation.md` | M9 身份、授权与可靠消息执行参考实现 | Proposed | 0.1.0 | B | 参考实现边界明确，需结合可运行证据复验 |
| `architecture/24-task-scheduler-manual-intervention-implementation.md` | M10 Task Scheduler 与人工接管执行参考实现 | Proposed | 0.1.0 | B | 参考实现边界明确，需结合可运行证据复验 |
| `architecture/25-secure-file-lifecycle-implementation.md` | M11 安全文件生命周期参考实现 | Proposed | 0.1.0 | B | 参考实现边界明确，需结合可运行证据复验 |
| `architecture/26-contract-ci-client-generation-implementation.md` | M12 契约兼容 CI 与客户端生成参考实现 | Implemented | 0.2.0 | B | 参考实现说明充分，但只覆盖工程基础切片 |
| `architecture/27-observability-health-redaction-implementation.md` | M13 可观测性、健康探针与日志脱敏参考实现 | Implemented | 0.1.0 | B | 参考实现说明充分，但只覆盖工程基础切片 |
| `architecture/28-container-staging-deployment-implementation.md` | M14 容器、独立迁移与 staging 发布参考实现 | Implemented | 0.1.0 | B | 参考实现说明充分，但只覆盖工程基础切片 |
| `data/01-execution-logical-model.md` | 配置与执行内核逻辑数据模型 | Proposed | 0.1.0 | C | 逻辑模型较完整，缺物理模型、约束与迁移设计 |
| `data/02-authorization-audit-logical-model.md` | 授权与审计逻辑数据模型 | Proposed | 0.1.0 | C | 逻辑模型较完整，缺物理模型、约束与迁移设计 |
| `data/03-field-operations-logical-model.md` | 现场作业、表单、资料与审核逻辑数据模型 | Proposed | 0.1.0 | C | 逻辑模型较完整，缺物理模型、约束与迁移设计 |
| `data/04-automation-integration-logical-model.md` | 派单、SLA、集成、通知与异常逻辑数据模型 | Proposed | 0.1.0 | C | 逻辑模型较完整，缺物理模型、约束与迁移设计 |
| `data/05-pricing-migration-pilot-logical-model.md` | 履约事实、试算、结算、迁移与试点逻辑数据模型 | Proposed | 0.1.0 | C | 逻辑模型较完整，缺物理模型、约束与迁移设计 |
| `data/06-application-projection-preference-logical-model.md` | 应用投影、队列、保存视图与偏好逻辑数据模型 | Proposed | 0.1.0 | C | 逻辑模型较完整，缺物理模型、约束与迁移设计 |
| `data/README.md` | 数据架构 | — | — | B | 结构和阅读路径清晰 |
| `decisions/ADR-001-modular-monolith-first.md` | ADR-001：MVP 采用模块化单体 | — | — | B | 决策内容清晰，但缺统一状态/审批元数据 |
| `decisions/ADR-002-versioned-configuration-bundle.md` | ADR-002：工单锁定版本化配置包 | — | — | B | 决策内容清晰，但缺统一状态/审批元数据 |
| `decisions/ADR-003-task-centric-execution.md` | ADR-003：采用任务中心的履约执行模型 | — | — | B | 决策内容清晰，但缺统一状态/审批元数据 |
| `decisions/ADR-004-settlement-from-fulfillment-facts.md` | ADR-004：结算必须基于履约事实 | — | — | B | 决策内容清晰，但缺统一状态/审批元数据 |
| `decisions/ADR-005-flatten-inheritance-at-publish-time.md` | ADR-005：配置继承在发布时扁平化 | — | — | B | 决策内容清晰，但缺统一状态/审批元数据 |
| `decisions/ADR-006-workflow-engine-as-orchestrator.md` | ADR-006：流程引擎只负责编排，不拥有业务事实 | — | — | B | 决策内容清晰，但缺统一状态/审批元数据 |
| `decisions/ADR-007-hybrid-field-storage.md` | ADR-007：标准领域字段与动态提交采用混合存储 | — | — | B | 决策内容清晰，但缺统一状态/审批元数据 |
| `decisions/ADR-008-immutable-evidence-and-review-decisions.md` | ADR-008：资料版本和审核决定只追加 | — | — | B | 决策内容清晰，但缺统一状态/审批元数据 |
| `decisions/ADR-009-dispatch-filter-score-reserve.md` | ADR-009：派单采用硬过滤、可解释评分和原子容量预占 | — | — | B | 决策内容清晰，但缺统一状态/审批元数据 |
| `decisions/ADR-010-outbound-delivery-intent-and-attempt.md` | ADR-010：外部交付意图与网络尝试分离 | — | — | B | 决策内容清晰，但缺统一状态/审批元数据 |
| `decisions/ADR-011-shadow-before-authoritative-pricing.md` | ADR-011：新试算先影子运行再成为正式依据 | — | — | B | 决策内容清晰，但缺统一状态/审批元数据 |
| `decisions/ADR-012-migration-lineage-and-single-writer.md` | ADR-012：迁移保留血缘且每张工单单一写入权威 | — | — | B | 决策内容清晰，但缺统一状态/审批元数据 |
| `decisions/ADR-013-reference-engineering-profile-and-module-enforcement.md` | ADR-013：采用可自动验证的模块化单体参考工程 | — | — | B | 决策内容清晰，但缺统一状态/审批元数据 |
| `decisions/ADR-014-local-transaction-outbox-inbox.md` | ADR-014：本地事务与 Outbox/Inbox 构成可靠消息基线 | — | — | B | 决策内容清晰，但缺统一状态/审批元数据 |
| `decisions/ADR-015-separate-portals-shared-contracts-server-actions.md` | ADR-015：Portal 独立，契约共享，动作由服务端决定 | — | — | B | 决策内容清晰，但缺统一状态/审批元数据 |
| `decisions/ADR-016-single-image-explicit-migration-fail-closed-deployment.md` | ADR-016：单一镜像、独立迁移与失败关闭发布 | — | — | B | 决策内容清晰，但缺统一状态/审批元数据 |
| `diagrams/README.md` | 图表源文件 | — | — | B | 结构和阅读路径清晰 |
| `docs/glossary.md` | 术语表 | Draft | 0.1.0 | C | 索引有效；术语仍需完成和签署 |
| `docs/implementation-traceability-matrix.md` | 研发模块与架构证据追踪矩阵 | Proposed | 0.1.0 | B | 索引有效；术语仍需完成和签署 |
| `product/01-cross-portal-information-architecture.md` | 跨 Portal 信息架构与应用外壳 | Proposed | 0.1.0 | C | 可指导产品拆解，缺试点级页面字段、流程和原型 |
| `product/02-admin-operations-portal-spec.md` | 总部运营后台产品规格 | Proposed | 0.1.0 | C | 可指导产品拆解，缺试点级页面字段、流程和原型 |
| `product/03-network-portal-spec.md` | 网点协作 Portal 产品规格 | Proposed | 0.1.0 | C | 可指导产品拆解，缺试点级页面字段、流程和原型 |
| `product/04-technician-mobile-app-spec.md` | 师傅移动端产品与离线交互规格 | Proposed | 0.1.0 | C | 可指导产品拆解，缺试点级页面字段、流程和原型 |
| `product/05-cross-portal-interaction-state-spec.md` | 跨 Portal 协作、命令反馈与状态交互规格 | Proposed | 0.1.0 | C | 可指导产品拆解，缺试点级页面字段、流程和原型 |
| `product/06-design-system-accessibility-spec.md` | ServiceOS 设计系统与可访问性规格 | Proposed | 0.1.0 | C | 可指导产品拆解，缺试点级页面字段、流程和原型 |
| `product/07-page-action-permission-matrix.md` | 页面、动作、能力与数据范围矩阵 | Proposed | 0.1.0 | C | 可指导产品拆解，缺试点级页面字段、流程和原型 |
| `product/README.md` | 产品设计 | — | — | B | 结构和阅读路径清晰 |
| `research/00-confirmed-business-facts.md` | 已确认业务事实 | Draft | 0.1.0 | D | 事实仍为 Draft，关键口径未签署 |
| `research/01-m1-business-asset-pack.md` | M1 业务资产基线填写手册 | Proposed | 0.1.0 | C | 填写手册完整，但缺实际资产包 |
| `research/templates/01-project-profile.md` | M1-01 项目画像模板 | Draft | 0.1.0 | D | 仅模板，未填写，不能指导业务研发 |
| `research/templates/02-process-action-matrix.md` | M1-02 流程与动作矩阵模板 | Draft | 0.1.0 | D | 仅模板，未填写，不能指导业务研发 |
| `research/templates/03-field-evidence-matrix.md` | M1-03 字段与资料矩阵模板 | Draft | 0.1.0 | D | 仅模板，未填写，不能指导业务研发 |
| `research/templates/04-dispatch-sla-matrix.md` | M1-04 派单与 SLA 矩阵模板 | Draft | 0.1.0 | D | 仅模板，未填写，不能指导业务研发 |
| `research/templates/05-pricing-settlement-matrix.md` | M1-05 价格与结算矩阵模板 | Draft | 0.1.0 | D | 仅模板，未填写，不能指导业务研发 |
| `research/templates/06-integration-contract-inventory.md` | M1-06 集成契约清单模板 | Draft | 0.1.0 | D | 仅模板，未填写，不能指导业务研发 |
| `research/templates/07-role-data-scope-matrix.md` | M1-07 角色与数据范围矩阵模板 | Draft | 0.1.0 | D | 仅模板，未填写，不能指导业务研发 |
| `research/templates/08-fulfillment-fact-catalog.md` | M1-08 履约事实目录模板 | Draft | 0.1.0 | D | 仅模板，未填写，不能指导业务研发 |
| `research/templates/09-open-items-and-proposals.md` | M1-09 待确认与改进建议登记表 | Draft | 0.1.0 | D | 仅模板，未填写，不能指导业务研发 |
| `roadmap/00-mvp-roadmap.md` | MVP 范围与实施路线 | Proposed | 0.1.0 | B | 阶段和 Gate 清晰，需用真实人员、预算和业务资产校准 |
| `roadmap/01-m6-engineering-delivery-plan.md` | M6 研发实施与首个生产切片交付计划 | Proposed | 0.1.0 | B | 阶段和 Gate 清晰，需用真实人员、预算和业务资产校准 |
| `roadmap/02-m7-application-delivery-plan.md` | M7 多 Portal 应用与交互交付计划 | Proposed | 0.1.0 | B | 阶段和 Gate 清晰，需用真实人员、预算和业务资产校准 |
| `testing/01-m2-execution-acceptance-matrix.md` | M2 配置与执行内核验收矩阵 | Proposed | 0.1.0 | C | 验收矩阵可用，尚未绑定实际业务样本和自动化用例 |
| `testing/02-m3-field-operations-acceptance-matrix.md` | M3 现场作业、表单、资料与审核验收矩阵 | Proposed | 0.1.0 | C | 验收矩阵可用，尚未绑定实际业务样本和自动化用例 |
| `testing/03-m4-automation-integration-acceptance.md` | M4 自动化、集成与异常恢复验收矩阵 | Proposed | 0.1.0 | C | 验收矩阵可用，尚未绑定实际业务样本和自动化用例 |
| `testing/04-m5-pricing-migration-pilot-acceptance.md` | M5 事实、试算、迁移与试点验收矩阵 | Proposed | 0.1.0 | C | 验收矩阵可用，尚未绑定实际业务样本和自动化用例 |
| `testing/05-m6-engineering-readiness-acceptance.md` | M6 工程、部署、安全与运行就绪验收矩阵 | Proposed | 0.1.0 | C | 验收矩阵可用，尚未绑定实际业务样本和自动化用例 |
| `testing/06-m7-application-experience-acceptance.md` | M7 多 Portal 应用与交互验收矩阵 | Proposed | 0.1.0 | C | 验收矩阵可用，尚未绑定实际业务样本和自动化用例 |
| `testing/07-m9-security-reliability-acceptance.md` | M9 身份授权与可靠消息验收矩阵 | Proposed | 0.1.0 | C | 验收矩阵可用，尚未绑定实际业务样本和自动化用例 |
| `testing/08-m10-task-scheduler-acceptance.md` | M10 Task Scheduler 与人工接管验收矩阵 | Proposed | 0.1.0 | C | 验收矩阵可用，尚未绑定实际业务样本和自动化用例 |
| `testing/09-m11-secure-file-acceptance.md` | M11 安全文件生命周期验收矩阵 | Proposed | 0.1.0 | C | 验收矩阵可用，尚未绑定实际业务样本和自动化用例 |
| `testing/10-m12-contract-ci-acceptance.md` | M12 契约兼容 CI 与客户端生成验收矩阵 | Implemented | 0.2.0 | B | 工程切片已有证据，仍需在目标环境复验 |
| `testing/11-m13-observability-acceptance.md` | M13 可观测性、健康探针与日志脱敏验收矩阵 | Implemented | 0.1.0 | B | 工程切片已有证据，仍需在目标环境复验 |
| `testing/12-m14-container-staging-deployment-acceptance.md` | M14 容器、迁移、staging 与回滚验收矩阵 | Implemented | 0.1.0 | B | 工程切片已有证据，仍需在目标环境复验 |
| `testing/README.md` | 测试与架构验收 | — | — | B | 结构和阅读路径清晰 |
