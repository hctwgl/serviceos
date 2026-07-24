---
title: ServiceOS 当前实施状态
version: 1.0.0
status: Implemented
lastUpdated: 2026-07-24
baselineCommit: "afbbd9ab3752f4c319a869302077ea1e525b516f"
---

# ServiceOS 当前实施状态

本文件只描述当前仓库可证明的能力和明确缺口，不保存开发过程、PR 交接或逐切片历史。历史实现过程从 Git 查询；工程事实以代码、机器契约、Flyway 和自动化测试为准。

## 1. 当前工程

| 范围 | 当前事实 |
|---|---|
| 后端 | Java 21、Spring Boot、Spring Modulith 模块化单体，源码位于 `serviceos-backend` |
| 数据库 | PostgreSQL + Flyway；版本和数量由 `bash scripts/migration-baseline.sh` 从迁移目录推导 |
| HTTP/事件契约 | Core OpenAPI 2.0.0、BYD CPIM OpenAPI 0.3.0、版本化事件 JSON Schema，位于 `serviceos-contracts` |
| Web | `serviceos-frontend` pnpm Workspace；包含 Admin、Network、Technician 三个应用，以及 api-client、auth-context、design-system、product-language 四个共享包 |
| iOS | `serviceos-technician-ios` 原生 App、`serviceos-ios-core` 共享基础 |
| 部署 | 本地、产品开发、staging、可观测性入口位于 `serviceos-deploy` |
| 可靠性门禁 | PostgreSQL Testcontainers、Spring Modulith `ArchitectureTest`、契约兼容、客户端生成与 staging 检查 |

`baselineCommit` 是本文件最近一次对齐过的主干提交，不是发布版本。HEAD 晚于该提交时，必须结合 diff 判断事实是否变化。

## 2. 后端能力

下表表示代码和直接测试已存在，不代表正式生产环境、最终产品体验或所有业务分支均已验收。

| 能力组 | 模块 | 状态 | 当前边界 |
|---|---|---|---|
| 身份、组织、授权、审计 | `identity`、`organization`、`authorization`、`audit` | 已实现基础 | OIDC/JWT、Principal/Persona、组织任职、RoleGrant、Tenant/Project/Region/Network Scope；正式企业 IdP、HR Connector、完整 MFA obligation 尚未接入 |
| 项目与工单 | `project`、`workorder` | 部分实现 | 项目、范围关系、工单受理、版本绑定和履约生命周期已有；完整项目审批、工单取消/暂停/恢复及客户敏感详情治理未闭环 |
| 配置与工作流 | `configuration`、`workflow` | 部分实现 | 不可变配置资产、项目级 Vue Flow 草稿设计、服务端图校验/模拟/编译发布和 `SERIAL_V1` 单活跃节点约束已有；同一服务产品多方案、品牌/省域硬匹配、优先级/具体度决策、发布期冲突阻断和工单版本冻结已有；Workflow 当前 Phase/Node 已进入工作区查询；节点级审核责任策略、通用系统动作绑定及最终外部回执全旅程仍未闭环 |
| 任务与派单 | `task`、`dispatch`、`network` | 部分实现 | Task 责任/执行保护/重试、自动网点/师傅指派、整单责任跨阶段继承、终态容量释放和按责任事实待派筛选已有；完整评分、地理硬过滤和通用高风险特批未闭环 |
| 现场履约 | `appointment`、`fieldwork`、`forms`、`evidence`、`files` | 部分实现 | 预约、Visit、动态表单、受限文件上传、资料槽位、机器校验、内部审核与 CLIENT ReviewCase 主链路已有；内部审核通过驱动资料门闸，外部复核通过驱动车企回执门闸；离线、后台上传、OCR/CV、生产对象存储和扫描服务未闭环 |
| SLA 与异常 | `sla`、`operations` | 部分实现 | 基础时钟、对账、授权查询和异常工作台已有；暂停、预警、升级、通知与更多自动闭环未完成 |
| 外部集成 | `integration` | 部分实现 | BYD 入站建单、配置匹配、出站提审、回执路由、签名回调和工单终态闭环已由本地真实运行验证；通用 Connector 边界、可靠交付与人工恢复已有；真实 Geely Sandbox 和生产凭据仍受外部条件阻塞 |
| 可靠消息与读模型 | `reliability`、`readmodel` | 已实现基础 | Inbox/Outbox、claim/lease/retry、授权队列、工作区和时间线投影已有；正式 Broker、多投影平台和完整重建运维面未完成 |
| 计价与结算 | 暂无独立运行时模块 | 设计阶段 | 仅保留履约事实/试算边界；正式对账、结算、争议和调整运行时未实现 |

## 3. 产品与客户端状态

| 客户端 | 当前事实 | 未完成边界 |
|---|---|---|
| Admin Web | 项目履约方案已有 Vue Flow 三栏设计器、Phase/Node/Transition 编辑、节点资产空态创建、校验/模拟/发布和只读版本；工单工作区展示冻结版本的当前 Phase/Node | 审核责任与系统动作配置尚未完成真实运行闭环，最终双分辨率人工产品验收仍未完成 |
| Network Web | 工单工作区、候选师傅、责任分配、联系预约和当前阶段事实进度已有真实联调入口 | 整改、异常和更多协作旅程仍需按当前产品决策验收 |
| Technician Web | 在线任务 Feed、接单开工、预约、到场、冻结表单、资料上传与完成前复核已有 | 不承担原生离线、后台上传和设备可信采集承诺；浏览器定位仍依赖用户站点权限 |
| Technician iOS | 原生工程、共享基础、在线 Visit/表单/资料能力已有 | 真机弱网、签名、TestFlight、VoiceOver、后台/离线闭环尚未形成完整证据 |

产品事实源为 `serviceos-architecture/product-design/`。工程实现存在不等于 `PRODUCT_ACCEPTED`。

## 4. 生产化缺口

- 正式企业 IdP、Secret Manager、Broker、对象存储、专业扫描服务和通知供应商；
- 多故障域部署、PITR、正式制品发布、SBOM/签名与完整运行演练；
- 真实 OEM Sandbox、凭据、签名联调和生产回执样本；
- Technician 真机弱网、后台上传、离线命令、设备撤销和 TestFlight；
- Consumer Identity、正式对账结算、完整 SLA 暂停/预警/升级；
- 尚未由产品负责人确认的计价、加权、派单评分和部分业务处置规则。

这些缺口不得用默认值、演示数据、Mock 或历史文档伪装成已完成。

## 5. 下一步如何判断

1. 产品和页面任务：读取 `product-design/README.md`、对应决策和当前页面代码；
2. 后端规则任务：读取对应长期架构、Accepted ADR、公共 API 和直接测试；
3. 契约或数据库任务：读取机器契约或连续 Flyway 迁移；
4. 交付优先级：由当前用户任务或产品负责人决定，不再通过递增里程碑编号推导；
5. 若本文与更高优先级事实冲突，修正文档，不建立兼容叙事。
