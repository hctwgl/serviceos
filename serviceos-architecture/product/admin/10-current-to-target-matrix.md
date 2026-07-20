---
title: Admin 当前路由到目标产品映射
version: 0.1.0
status: BaselineAudit
baseline: master@b56e734495ef2621a60c486754793ecc145b3802
lastUpdated: 2026-07-20
---

# Admin 当前路由到目标产品映射

本表基于 `serviceos-admin-web/src/router.ts` 当前基线建立。正在执行的项目工单履约配置 Agent 合入后必须重新扫描并增量更新。

处理方式：

- **保留重构**：产品目标成立，按母版重做；
- **合并/预设视图**：保留后端能力，合并到统一业务页面；
- **深链子页**：不作为侧栏入口，由列表/详情进入；
- **迁入配置中心**：仅高级配置人员使用；
- **迁入审计监控**：技术/集成/诊断能力；
- **移除正式入口**：保留开发能力，但不进入生产运营导航。

| 当前路由 | 当前页面/技术语义 | 目标产品位置 | 处理方式 | 产品重点 |
|---|---|---|---|---|
| `/workbench` | Workbench | 工作台 / 运营工作台 | 保留重构 | 待办、SLA、异常、最近处理 |
| `/search` | 全局搜索 | 顶栏全局搜索 + 搜索结果页 | 保留重构 | 按业务对象分组，不以 UUID 为标题 |
| `/work-orders` | WorkOrder Directory | 工单运营 / 工单中心 | 保留重构 | 标准列表、预设视图、责任/SLA/风险 |
| `/work-orders/:id` | WorkOrder Workspace | 工单详情 | 保留重构 | 当前任务、流程、资料、审核、配置来源 |
| `/work-orders/lookup` | 技术查找 | 工单中心搜索能力 | 合并 | 不保留独立普通菜单 |
| `/work-orders/golden-path` | Golden Path 验证 | 系统诊断 / 测试工具 | 移除正式入口 | 开发/验收环境可保留 |
| `/tasks` | Task Queue | 服务履约 / 服务任务；工作台 / 我的待办 | 保留重构/预设视图 | 人工/自动任务业务化 |
| `/tasks/:id` | Task Detail/Command | 服务任务详情 | 深链子页重构 | 不显示命令调试表单 |
| `/appointments/:id` | Appointment Detail | 服务履约 / 预约管理详情 | 深链子页 | 联系、预约、改约和历史 |
| `/contact-attempts/:id` | Contact Attempt | 预约详情中的联系记录 | 合并为深链/抽屉 | 不作为独立导航 |
| `/visits/:id` | Field Visit | 工单详情 / 现场履约详情 | 深链子页 | 勘测/安装现场记录 |
| `/form-submissions/:id` | Form Submission | 工单详情 / 表单与资料 | 深链子页 | 业务表单预览，技术绑定高级披露 |
| `/evidence-items/:id` | Evidence Item | 工单详情 / 资料预览 | 深链/安全预览 | 脱敏、访问审计 |
| `/evidence-set-snapshots/:id` | Evidence Snapshot | 工单详情 / 资料版本 | 深链子页 | 展示资料包和版本，不暴露对象结构 |
| `/external-review-receipts/:id` | External Review Receipt | 审核记录或接口记录 | 深链子页 | 车企审核回执业务化 |
| `/reviews` | Review Queue | 审核与整改 / 审核队列 | 保留重构 | 勘测/安装/终审预设视图 |
| `/reviews/:id` | ReviewCase Detail | 审核工作区 | 深链专用流程 | 证据、目标、决定、整改 |
| `/corrections` | Correction Queue | 审核与整改 / 整改跟踪 | 保留重构 | 责任、截止、问题数、复审 |
| `/corrections/:id` | CorrectionCase Detail | 整改详情/复审 | 深链专用流程 | 问题、整改证据、复审结果 |
| `/sla` | SLA Queue | 工作台 / SLA 风险 | 保留重构 | 即将超时、已超时、暂停 |
| `/sla/:id` | SLA Instance Detail | 对象详情的 SLA 与异常；审计深链 | 合并/深链 | 业务时效解释，不显示原始技术字段 |
| `/exceptions` | Exception Queue | 工作台 / 运营异常 | 保留重构 | 严重度、对象、责任、恢复 |
| `/exceptions/:id` | Exception Detail | 运营异常详情 | 深链子页 | 发生原因、影响、恢复动作 |
| `/projects` | Project Directory | 客户与项目 / 项目管理 | 保留重构 | 标准项目列表和独立新建页 |
| `/projects/:id` | Project Detail | 项目详情 | 保留重构 | 履约配置、范围、网点、版本 |
| `/configuration/designer` | Unified Technical Designer | 配置中心 / 模板与版本 | 迁入配置中心并拆分 | 普通/高级模式分层，不直接 JSON-first |
| `/users` | User Directory | 系统管理 / 用户管理 | 保留重构 | 标准 CRUD、组织/角色摘要 |
| `/users/:id` | User Detail | 用户详情 | 深链子页重构 | 组织、角色、委托、安全、变更 |
| `/organizations` | Organization Directory | 系统管理 / 组织管理 | 保留重构 | 用户中心组织树和成员归属 |
| `/organizations/:id` | Organization Detail | 组织详情 | 深链子页 | 成员、角色继承、数据范围 |
| `/networks` | Network Directory | 组织与资源 / 合作组织、服务网点 | 拆分/重构 | 组织与网点分 Tab 或独立列表 |
| `/networks/:id` | Network Detail | 服务网点详情 | 深链子页 | 区域、师傅、资质、容量、表现 |
| `/technicians` | Technician Directory | 组织与资源 / 师傅档案 | 保留重构 | 技能、区域、资质、任务负载 |
| `/technicians/:id` | Technician Detail | 师傅详情 | 深链子页 | 资质、能力、任务和历史 |
| `/roles` | Role Directory | 系统管理 / 角色管理 | 保留重构 | 中文角色、成员/权限数量 |
| `/roles/:id` | Role Detail | 角色详情 | 深链配置页 | 菜单、动作、数据范围、成员 |
| `/grants` | Grant Directory | 系统管理 / 授权与委托 | 保留重构 | 授权人、被授权人、范围、有效期 |
| `/integration/inbound` | Inbound Envelope Queue | 审计与监控 / 接口记录 | 迁入审计监控 | 收单记录、映射结果和问题 |
| `/integration/inbound/:id` | Envelope Detail | 接口记录详情 | 深链子页 | attempt、映射、业务对象 |
| `/integration/canonical/:id` | Canonical Message | 系统诊断/接口详情高级区 | 迁入诊断 | 普通用户不看 Canonical JSON |
| `/integration/outbound` | Outbound Delivery Queue | 审计与监控 / 接口记录 | 迁入审计监控 | 回传/通知状态和重试 |
| `/integration/outbound/:id` | Delivery Detail | 接口记录详情 | 深链子页 | attempt、ack、重试、问题编号 |
| `/system/demo-data` | Demo Data | 非生产开发工具 | 移除正式入口 | 仅开发/测试环境 |
| `/portal-stubs` | Portal Stubs | 非生产开发工具 | 移除正式入口 | 不进入运营菜单 |
| `/settings/token` | Token/技术信息 | 系统诊断 | 迁入诊断或移除 | 不作为普通设置页 |
| `/settings/preferences` | UI Preferences | 用户菜单 / 界面偏好 | 保留轻量入口 | 密度、主题等真实能力 |
| `/auth/callback` | OIDC Callback | 系统路由 | 保留系统路由 | 不进入菜单 |
| `/*` | Not Found | 通用错误页 | 保留重构 | 返回工作台/上一页/搜索 |

## 1. 当前缺失的目标页面

当前 Router 基线中没有或没有形成完整产品页面的目标能力：

- 车企管理与车企详情；
- 项目新建独立表单页；
- 项目工单类型与履约配置完整路由；
- 网点派单列表/工作区；
- 师傅调度工作台；
- 预约管理列表；
- 审核记录列表；
- 技能与资质目录；
- 服务区域目录；
- 菜单管理；
- 权限目录的产品化页面；
- 数据权限配置；
- 登录与安全；
- 操作日志、登录日志、配置变更；
- 配置中心各类模板目录；
- 业务日历和派单规则产品页。

缺失页面不代表应立刻全部新建。必须先检查现有 API、Page Registry 和当前纵向功能交付，按路线图分批实现。

## 2. 优先处理顺序

1. 六张母版对应路由；
2. 用户/组织/角色/授权的系统管理闭环；
3. 项目/车企/组织资源基础资料；
4. 工单/任务/派单/预约履约闭环；
5. 审核与整改；
6. 配置中心；
7. 审计与监控；
8. 移除开发工具的正式导航入口。

## 3. 合入后复审

项目工单履约配置 Agent 合入后，必须重新扫描：

- 新增路由；
- 新增 Page ID；
- 新增服务端 Navigation；
- 新增 Capability；
- 项目详情入口；
- 配置快照入口。

任何新页面都必须映射到本蓝图，不能形成新的平行技术导航。