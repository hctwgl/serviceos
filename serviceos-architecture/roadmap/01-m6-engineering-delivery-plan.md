---
title: M6 研发实施与首个生产切片交付计划
version: 0.1.0
status: Proposed
---

# M6 研发实施与首个生产切片交付计划

## 1. 目标

M6 不再扩写平台愿景，而是建立可运行工程、完成一个真实试点项目的纵向切片，并为 M2～M5 能力按风险递增交付提供顺序和门禁。

M6 完成不等于 ServiceOS 全部开发完成；它证明团队已经具备持续实现架构、验证业务样本和安全发布的工程能力。

## 2. 开工前置

### 2.1 必须由业务提供

- 首个试点项目和一个对照项目；
- M1-01～M1-09 中与首切片有关的真实、脱敏、已签署样本；
- 至少一条正常勘安流程和以下异常：重复收单、无法派单、改派、资料驳回、补传、回传失败；
- 角色与品牌/区域/网点数据范围；
- SLA 数值、日历、暂停和升级路径；
- 一套对上、一套对下价格与历史结果；
- 车企接入/回传 sandbox、签名、幂等、回执样本；
- 生产容量、合规、RPO/RTO 的责任人和确认日期。

### 2.2 可以先行、不依赖业务口径

- 工程脚手架、模块验证和 CI；
- OIDC 集成、权限策略骨架和审计；
- PostgreSQL/Flyway、Outbox/Inbox、Task executor；
- 文件上传会话、对象存储 sandbox 和扫描接口；
- OpenAPI/事件 Schema 管理；
- 日志、指标、trace、健康检查和本地开发环境。

缺少业务前置时不得用开发人员猜测的字段、价格、SLA 或照片规则填充生产配置。

## 3. 交付策略

采用“纵向切片优先”：每个切片同时包含 API、权限、事务、数据、前端、异常、审计、可观测性和自动化测试。

禁止按以下方式组织交付：

- 先把所有数据库表建完再做用例；
- 后端完成数月后才接前端；
- 只做正常链路，异常以后补；
- 以页面数量或接口数量计算完成率；
- 首期同时实现所有业务类型和全部车企；
- 未接真实 sandbox 就宣称集成完成。

## 4. 交付阶段

```mermaid
flowchart LR
  E0["E0 决策与样本"] --> E1["E1 可运行骨架"]
  E1 --> E2["E2 收单与履约内核"]
  E2 --> E3["E3 现场与审核闭环"]
  E3 --> E4["E4 派单/SLA/回传恢复"]
  E4 --> E5["E5 事实/影子试算/迁移"]
  E5 --> E6["E6 小 cohort 生产试点"]
```

阶段可在模块内部并行，但不得绕过前一阶段的 P0 门禁进入生产。

## 5. E0：决策与业务样本

| 任务 ID | 交付 | 证据 | 依赖 |
|---|---|---|---|
| E0-01 | 指定业务、产品、技术、安全和运维负责人 | RACI 与决策日志 | 无 |
| E0-02 | 选定试点/对照项目 | 项目画像和选择理由 | 业务决策 |
| E0-03 | 填写首切片 M1 矩阵 | 签署版本、来源和 open items | E0-02 |
| E0-04 | 决定流程引擎组件 | Spike、ADR、恢复演练 | 样本流程 |
| E0-05 | 决定身份提供方和部署平台 | sandbox、责任边界、ADR | 安全/运维 |
| E0-06 | 签署首期技术 profile | JDK/Boot/Modulith/PostgreSQL/BOM | E0-04/05 |
| E0-07 | 定义 SLO/RPO/RTO/容量档位 | 非功能参数表 | 真实峰值 |

退出：首切片没有阻断性未知口径；所有未决项有 owner、期限和默认阻塞 Gate。

## 6. E1：可运行工程骨架

| 任务 ID | 交付 | 验收证据 |
|---|---|---|
| E1-01 | 创建 backend/contracts/admin/network/technician/deploy 工程 | 一条命令构建，各 Portal 独立产物 |
| E1-02 | 建立模块包和 allowedDependencies | Modulith/ArchUnit 正负向测试 |
| E1-03 | PostgreSQL + Flyway + Testcontainers | 空库、升级、失败回滚测试 |
| E1-04 | OIDC 登录和 CurrentPrincipal | 登录、撤销、MFA policy 测试 |
| E1-05 | capability/data scope/field policy 骨架 | 跨租户/网点负向测试 |
| E1-06 | Idempotency + Audit + Outbox/Inbox | crash/replay/不同 digest 测试 |
| E1-07 | Task/Scheduler worker 基础 | claim、租约、恢复、限流测试 |
| E1-08 | File Begin/Finalize/Scan | 直传、checksum、隔离和授权下载 |
| E1-09 | OpenAPI/Event Schema CI | 兼容检查与生成客户端 |
| E1-10 | OTel、健康、看板和日志脱敏 | trace 串联示例命令，敏感扫描通过 |
| E1-11 | 容器与 staging 部署 | 同一镜像部署、迁移、smoke、回滚 |

E1-10 已由 [M13 参考实现](../architecture/27-observability-health-redaction-implementation.md)及其[验收矩阵](../testing/11-m13-observability-acceptance.md)提供本仓库证据；E1-11 与 Foundation P0 总退出门禁仍未完成。

退出：通过 [M6 工程就绪验收矩阵](../testing/05-m6-engineering-readiness-acceptance.md) 的 Foundation P0。

## 7. E2：收单与履约内核纵向切片

主链路：

```text
接收 sandbox 工单
→ InboundEnvelope 验签/去重
→ CanonicalMessage 映射
→ Resolve ConfigurationBundle
→ CreateWorkOrder
→ 创建 Stage/Task/参与关系/SLA
→ Admin Web 可查询与执行初审动作
```

| 任务 ID | 交付 | 关键异常 |
|---|---|---|
| E2-01 | Configuration Asset/Bundle 最小发布链 | 多命中、缺版本、发布冲突 |
| E2-02 | InboundEnvelope 与 CanonicalMessage | 重复、同 key 不同 payload、签名失败 |
| E2-03 | CreationAuthority reservation + WorkOrder/Stage/Timeline | 配置锁定、外部键唯一、Create/Bind 崩溃恢复、乐观锁 |
| E2-04 | Task/Action/Assignment/Guard | 非法动作、重复完成、旧负责人 |
| E2-05 | Workflow adapter | 引擎重试、停机恢复、事件重复 |
| E2-06 | Admin 工单列表/详情/任务动作 | 数据范围、字段脱敏、并发提示 |
| E2-07 | 端到端契约和审计 | 从 envelope 到 Task/Outbox 可追踪 |

退出：M2 P0 与试点样本正常/重复/非法/恢复路径通过。

## 8. E3：现场提交、资料审核与整改

主链路：

```text
分配师傅
→ 师傅工作包同步
→ 预约/改约
→ Visit 到场与现场表单
→ 条件资料要求
→ 离线草稿/上传队列
→ EvidenceSet 提交
→ 客服逐项审核
→ 驳回/补传/再审
```

| 任务 ID | 交付 | 关键证据 |
|---|---|---|
| E3-01 | Appointment/ContactAttempt | 多次联系、改约、并发更新 |
| E3-02 | Visit 与现场动作 | 到场、取消、二次上门 |
| E3-03 | FormDefinition/Submission | 条件显隐/必填、版本锁定、服务端校验 |
| E3-04 | EvidenceRequirement/Item/Set | 条件项、单项多版本、原文件不可变 |
| E3-05 | MachineCheck/OCR 接口 | 降级人工、SN/VIN 一致性 trace |
| E3-06 | ReviewTask/Decision/Correction | 单项驳回、多轮补传、强制通过审计 |
| E3-07 | Technician 工作包与离线队列 | 弱网、断网、重启、冲突、重复上传 |
| E3-08 | Network 补资料 Portal | 仅所属网点、明确代传身份 |
| E3-09 | Admin 审核工作台 | 单人领取、SLA、标准原因 |

退出：M3 P0、移动端离线/恢复和真实照片样本通过。

## 9. E4：自动派单、SLA、回传与异常恢复

| 任务 ID | 交付 | 关键不变量 |
|---|---|---|
| E4-01 | Network/Technician 能力与资质 | 有效期、停派、数据范围 |
| E4-02 | Dispatch filter/score/explain | 硬过滤优先、评分可解释 |
| E4-03 | CapacityReservation | 并发不超配 |
| E4-04 | ServiceAssignment 激活 saga | 与 TaskAssignment 对齐、失败补偿 |
| E4-05 | SLA clock/calendar/milestone | 暂停、恢复、重算和升级幂等 |
| E4-06 | OutboundDelivery/Attempt/Ack | 结果 UNKNOWN、重试唯一调度 |
| E4-07 | Notification Intent/Delivery | 收件人快照、去重、fallback |
| E4-08 | OperationalException 工作台 | 自动失败必有处理 Task |
| E4-09 | sandbox 车企状态回传 | 签名、限流、回执、业务拒绝恢复 |

退出：M4 P0 故障注入通过，无重复派单/通知/回传，所有最终失败可人工闭环。

## 10. E5：履约事实、影子试算与迁移准备

| 任务 ID | 交付 | 关键证据 |
|---|---|---|
| E5-01 | Fact catalog/extraction/snapshot | 来源、冲突、零/缺失和版本链 |
| E5-02 | FactCorrection/EligibilityGuard | 更正与新 run/line 并发不穿透 |
| E5-03 | PricingContextResolver | 服务端唯一解析，候选 override 受限 |
| E5-04 | SHADOW CalculationRun/ChargeItem | 双向隔离、确定性、可解释 |
| E5-05 | CalculationComparison/Export | 差异分类、明确未结算 |
| E5-06 | Migration Snapshot/Mapping/Lineage | dry-run、幂等、文件/金额核对 |
| E5-07 | CutoverCohort/Authority/Fence | 影子零副作用、普通命令防双写 |
| E5-08 | RollbackPlan 演练 | 水位、副作用、反向同步和阻塞验证 |

正式 Settlement 不在 M6/MVP 默认启用范围；相关契约仅作为二期 feature gate 后的实现输入。

退出：M5 milestone P0、真实脱敏历史回放和影子差异解释通过。

## 11. E6：小 cohort 生产试点

1. 完成 staging 全链路、性能、故障、备份恢复和回退演练；
2. 旧系统保持权威，实时 SHADOW 对比；
3. 未解释流程/资料/金额差异闭环；
4. 选择受控新工单 cohort 切换 ServiceOS authority；
5. 运行 smoke、监控、war room 和观察窗口；
6. Gate 决定 Hold/Expand/Shrink/Rollback；
7. 只对新工单扩大；在途迁移单独审批；
8. 形成试点复盘、技术债和下一 cohort 计划。

## 12. 首批可立即创建的工程 Issue

| Issue | 标题 | 完成定义 |
|---|---|---|
| BOOT-001 | 初始化 Maven Wrapper 与 Boot 应用 | clean verify + image build |
| BOOT-002 | 建立模块目录和依赖验证 | 故意跨模块 internal 依赖会失败 |
| BOOT-003 | 本地 PostgreSQL/对象存储/OIDC 环境 | 新开发者一份文档可启动 |
| BOOT-004 | Flyway 模块迁移框架 | 空库/升级/重复运行通过 |
| CORE-001 | CommandContext 与 Problem Details | actor/tenant/correlation/idempotency 统一 |
| CORE-002 | IdempotencyRecord | 同 key 同/不同 digest 测试 |
| CORE-003 | AuditRecord | 成功/拒绝/失败均可审计 |
| CORE-004 | Outbox/Inbox | 发布/ack 崩溃故障测试 |
| CORE-005 | TaskExecution worker | claim/lease/retry/dead/manual 测试 |
| IAM-001 | OIDC 与主体映射 | 登录/撤销/过期测试 |
| IAM-002 | Capability + ScopePredicate | 跨租户/区域/网点负向测试 |
| FILE-001 | 三段式文件上传 | checksum/scan/授权下载测试 |
| OBS-001 | OTel 和结构化日志 | 示例 trace 跨 API/DB/worker |
| CICD-001 | 质量与安全流水线 | P0 gate 失败阻止合并 |

在 E0 业务样本未完成前，团队应优先实施这些 Issue，不创建猜测性的工单字段和车企专属代码。

## 13. Definition of Ready

一个业务 Issue 进入开发前必须包含：

- 对应架构/API/数据/测试场景 ID；
- 真实或合成业务样本与预期结果；
- 权限、数据范围和敏感字段；
- 幂等键、并发版本和事务边界；
- 成功、业务失败、技术失败和恢复路径；
- 领域事件/外部副作用；
- 配置版本和历史兼容影响；
- 可自动化验收标准；
- 不在范围内的明确事项。

## 14. Definition of Done

- 代码、数据库迁移、契约和文档一致；
- 单元、模块、集成、契约、授权和故障测试通过；
- P0 场景有自动化证据，不能只靠演示；
- 关键操作有审计、指标、trace 和 runbook；
- 无跨模块 internal/表写入；
- 无明文 secret、敏感日志和未处理高危漏洞；
- staging 使用生产同源镜像验证迁移和回滚；
- 产品/业务对真实样本签署；
- 未完成项不是隐藏 TODO，而是有 owner/期限/风险的债务。

## 15. 追踪规则

Issue、提交、测试和发布证据使用稳定 ID：

```text
Architecture: ARCH-19/20/21 section
API: API-01..05 endpoint/command
Data: DATA-01..05 entity
Acceptance: M2/M3/M4/M5/M6 scenario ID
Business sample: M1 form + sample ID
Decision: ADR-xxx
```

每次发布生成 Coverage Report，列出本次支持的业务类型、流程版本、Portal、P0 场景和未覆盖异常；不得以“整体完成百分比”掩盖矩阵缺口。

## 16. 团队最小责任边界

| 责任 | 必须有人承担 |
|---|---|
| 产品/业务口径 | 项目负责人 + 业务 owner |
| 领域和模块边界 | 产品架构 + 技术架构 |
| 后端/前端/移动端交付 | 各技术 owner |
| 自动化与质量 Gate | 测试 owner，开发共同负责 |
| 数据迁移/金额核对 | 数据 owner + 财务/业务 |
| 安全与隐私 | 安全 owner |
| 部署、SLO、备份和 on-call | SRE/运维 owner |
| Go/No-go | 业务、技术、安全共同签署 |

角色可以由同一人兼任，但责任、审批冲突和 on-call 不能无人承担。
