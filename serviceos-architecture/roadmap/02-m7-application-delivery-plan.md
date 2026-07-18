---
title: M7 多 Portal 应用与交互交付计划
version: 0.2.0
status: Accepted
lastUpdated: 2026-07-18
---

# M7 多 Portal 应用与交互交付计划

## 1. 目标

把页面规格转换为设计、前端、后端查询和端到端验收可并行推进的纵向交付。M7 不以“所有页面画完”为完成，而以 Admin、Network、Technician H5 和 Technician iOS 能支撑首个真实履约切片并通过跨端异常/恢复验证为准。

本计划接受以下客户端策略：

- Admin 与 Network 为独立 Web 应用；
- Technician H5 是在线参考实现、开发调试和产品验证端；
- Technician iOS 是首个正式现场生产客户端；
- Android 在 iOS 试点稳定后另行评估；
- Technician 能力按“服务端契约 → H5 在线参考实现 → iOS 在线实现 → iOS 离线增强”推进。

## 2. 输入

- M1 首个试点项目字段、资料、流程、角色和样本；
- M2～M5 领域/API/数据/验收；
- M6 工程模块、事务、安全、部署与 Gate；
- product/01～08 页面、交互和多客户端规格；
- API-06、DATA-06 查询/投影契约；
- 品牌设计规范、设备和浏览器支持矩阵；
- 师傅真实作业环境、网络、拍照/视频和设备样本；
- iOS 设备、系统版本、存储、TestFlight 和系统权限矩阵。

## 3. 切片

### U0：设计系统与应用外壳

| ID | 交付 | 证据 |
|---|---|---|
| U0-01 | Token、主题、字体、间距、状态语义 | token package + visual regression |
| U0-02 | Admin/Network/Technician H5 独立 shell、登录、环境/范围 | 独立构建/路由/会话测试 |
| U0-03 | iOS App shell、环境、OIDC PKCE、Keychain | 模拟器/真机构建与登录测试 |
| U0-04 | Web 基础组件与 Storybook | 全状态/a11y 测试 |
| U0-05 | TypeScript/Swift OpenAPI 生成 client 和错误层 | typecheck/contract CI |
| U0-06 | allowed-action renderer registry | 未知 action 安全降级 |
| U0-07 | command/operation/conflict 反馈 | M7-CMD P0 |
| U0-08 | clientKind/clientVersion/能力兼容 | 版本不兼容安全失败 |

### U1：Admin 工作台与工单工作区

| ID | 交付 | 证据 |
|---|---|---|
| U1-01 | 工作台/队列/计数/freshness | role/scope/asOf 测试 |
| U1-02 | 工单列表、筛选、SavedView | cursor/权限/Schema 迁移 |
| U1-03 | 工单 Header/概览/Task/时间线 | 版本与来源可追踪 |
| U1-04 | 表单/资料/审核/集成/试算 Tabs | section 按需加载、字段权限 |
| U1-05 | 全局搜索和深链 | 敏感搜索/失权测试 |
| U1-06 | 现场任务诊断与客户端版本定位 | trace/clientVersion/同步状态可查 |

### U2：Network Portal

| ID | 交付 | 证据 |
|---|---|---|
| U2-01 | 独立 Network Web 壳、工作台/本网点队列/工作区 | ACTIVE assignment scope |
| U2-02 | 师傅分配/更换 operation | saga/冲突/资质测试 |
| U2-03 | 联系与预约 | ETag 并发测试 |
| U2-04 | 代补与整改 | onBehalfOf/权限/版本测试 |
| U2-05 | 师傅/资质/产能 | 停用影响/商业隔离 |
| U2-06 | 异常确认、恢复与产能申请 | 领域动作/审批/恢复验证 |
| U2-07 | 消息入口 | 通知事实/深链/重鉴权 |

### U3A：Technician H5 在线参考实现

| ID | 交付 | 证据 |
|---|---|---|
| U3A-01 | 从 Admin Web 抽离独立 Technician H5 shell | 独立构建/路由/上下文测试 |
| U3A-02 | Task Feed/日程/任务详情/深链 | 增量/tombstone/scope |
| U3A-03 | 联系/预约在线动作 | 权威联系对象、ETag、幂等 |
| U3A-04 | Visit 在线动作 | 浏览器定位边界/capturedAt/receivedAt |
| U3A-05 | 动态表单在线参考实现 | Schema/条件/校验/冲突 |
| U3A-06 | Evidence 在线上传参考实现 | slot/session/checksum/finalize |
| U3A-07 | 提交前检查与整改 | exact versions/单项补传/多轮历史 |
| U3A-08 | Playwright 正常/权限/异常/冲突回归 | H5 不冒充原生离线能力 |

### U3B：Technician iOS 工程基础

| ID | 交付 | 证据 |
|---|---|---|
| U3B-01 | SwiftUI 工程、模块边界和环境配置 | Xcode build + lint/test |
| U3B-02 | OIDC PKCE、Keychain、/me 和 Technician Context | 登录/刷新/撤销/多账号测试 |
| U3B-03 | Generated Swift Client、Problem Details 和 TraceId | OpenAPI reproducibility |
| U3B-04 | 设计 Token、状态语义和可访问性基础 | snapshot/VoiceOver/Dynamic Type |
| U3B-05 | 日志、崩溃和诊断字段 | 无 PII/Token/文件路径泄漏 |
| U3B-06 | TestFlight 与签名/环境基础 | 内部测试包可安装 |

### U4A：Technician iOS 在线现场闭环

| ID | 交付 | 证据 |
|---|---|---|
| U4A-01 | Feed/日程/任务详情 | 与 H5/服务端契约一致 |
| U4A-02 | 联系/预约 | 系统拨号/确认语义/并发 |
| U4A-03 | Check-in/Visit/异常/Check-out | Core Location、精度、撤权 |
| U4A-04 | 动态表单 | 条件/单位/校验/本地草稿 |
| U4A-05 | 原生相机/视频/资料槽位 | 真实设备与项目策略 |
| U4A-06 | 在线上传、提交前检查和完成门禁 | checksum/exact versions/服务端确认 |
| U4A-07 | 整改 | 单项补传/多轮/历史保留 |

### U4B：Technician iOS 离线与上传运行时

| ID | 交付 | 证据 |
|---|---|---|
| U4B-01 | 本地加密数据库与迁移 | 密钥/升级/多账号/回滚测试 |
| U4B-02 | WorkPackage 下载、校验、过期和最小化 | assignment/authority/config version |
| U4B-03 | LocalDraft | 杀进程/重启不丢表单与采集事实 |
| U4B-04 | OfflineCommand/Sync Center | 拓扑、幂等、冲突、重启 |
| U4B-05 | UploadQueue/Background URLSession | 分片、断点、checksum、磁盘 |
| U4B-06 | 改派失权与本地隔离 | old/new technician/设备撤销 |
| U4B-07 | 客户端升级和 Schema/DB 迁移 | 带未同步数据升级测试 |

### U5：Admin 专项工作区

| ID | 交付 | 证据 |
|---|---|---|
| U5-01 | 派单工作区 | 解释、改派、activation saga |
| U5-02 | 审核工作区 | 30 项资料、版本冲突、快捷可访问 |
| U5-03 | 整改跟踪 | 客服协调/车企驳回 |
| U5-04 | 异常/集成工作区 | UNKNOWN/replay/恢复验证 |
| U5-05 | 事实/影子试算 | 血缘/解释/方向/SHADOW |
| U5-06 | 配置发布最小工作流 | draft/validate/replay/approve/publish |
| U5-07 | 受控运营分析 | metric definition、范围、下钻、SHADOW 隔离 |

### U6：跨端硬化与试点

| ID | 交付 | 证据 |
|---|---|---|
| U6-01 | 改派端到端 | Admin/Network/old/new technician |
| U6-02 | 审核整改端到端 | H5/iOS/network/admin 多轮 |
| U6-03 | 预约并发 | 三 Portal 与两个 Technician 客户端同时编辑 |
| U6-04 | 集成失败恢复 | Admin 处理、其他端最小信息 |
| U6-05 | 可访问性/兼容/真机矩阵 | M7-A11Y/MOB |
| U6-06 | 性能/投影重建/缓存失权 | M7-WO/QRY/NFR |
| U6-07 | 断网/弱网/杀进程/后台上传 | iOS 真机矩阵 |
| U6-08 | TestFlight 小 cohort 体验验收 | 业务用户签署和问题闭环 |
| U6-09 | 旧客户端兼容与强制升级 | action/schema/work-package 门禁 |

## 4. 原型与开发同步

每个业务切片采用：

```text
真实样本/任务
→ 低保真流程和页面状态
→ API/投影/权限契约评审
→ Technician H5 在线参考实现
→ iOS 在线实现
→ 适用时补充 iOS 离线实现
→ 自动化/可访问/真机验证
→ 业务走查
```

不要求先画完全部高保真页面；但进入开发的页面必须完成所有状态、权限和错误，而不是只有正常态截图。

H5 验证通过不等于 iOS 离线能力完成；iOS 页面完成不等于后台上传、改派失权或断网恢复完成。每个里程碑必须在“明确未实现”中保留这些差异。

## 5. Page Spec 模板

```markdown
Page ID / route / Portal / Client kind
User goal / entry / exit
Required capability and scope
Data sources + freshness
Layout / components
Allowed actions + obligations
Loading / empty / permission / conflict / error / async / offline
H5 capability boundary
Native capability requirements
Responsive / keyboard / screen reader / VoiceOver / Dynamic Type
Analytics (no sensitive values)
Acceptance IDs
Out of scope
```

## 6. 前后端联调 Gate

- OpenAPI/schema 已合并且 TypeScript/Swift client 可重复生成；
- fixture 来自脱敏真实样本，包括异常；
- 服务端 ScopePredicate/FieldPolicy 已实现；
- actionCode/inputSchema/obligations 已注册；
- 命令幂等/ETag/authorityVersion 已定义；
- 投影 freshness 和重建行为已定义；
- correlation/trace 可查；
- clientKind/clientVersion 和最低支持能力已定义；
- 未实现 API 不用永久 mock 冒充完成；
- H5 不把浏览器能力描述为与 iOS 原生能力等价；
- iOS 不根据本地 Task 状态自行发明服务端命令。

## 7. UX 研究与走查

至少覆盖：

- 客服连续处理审核/预约队列；
- 项目经理处理无网点和改派；
- 网点负责人分配师傅、补资料；
- 师傅通过 H5 走查在线流程；
- 师傅通过 iOS 在停车场/地下弱网完成勘测或安装；
- 审核员处理 30 项资料及多轮补传；
- 运维/客服处理回传 UNKNOWN；
- 结算人员解释双向 SHADOW 差异。

记录任务完成率、时间、错误、回退、理解偏差和建议；不能只问“喜欢这个页面吗”。

## 8. 发布顺序

1. 内部测试主体和合成数据；
2. Technician H5 在线参考实现；
3. 真实脱敏历史只读工作区；
4. staging sandbox 全链路；
5. iOS 开发者真机和内部 TestFlight；
6. 内部受控测试工单；
7. 影子生产只读/计算；
8. 小 cohort 网点与师傅；
9. Gate 后扩大；
10. iOS 试点稳定后评估 Android。

Portal 可以独立发布，但契约版本和 cohort capability 必须兼容。旧移动客户端不支持新必需 action/Schema/WorkPackage 时，不把任务派给该版本。

## 9. 里程碑分配规则

本计划定义交付顺序，不预占具体 `Mxxx` 编号。

当前已有其他 Agent 按里程碑推进，且 M245 正在进行中。后续执行者必须：

1. 在开始前读取最新 `master`、开放 PR、`implementation-status.md` 和 `milestone-index.md`；
2. 从当时最新已合并里程碑之后领取下一个空闲编号；
3. 不创建与活跃 Agent 相同的 ADR、实现文档、验收矩阵或分支编号；
4. 每个里程碑从最新 `master` 建分支，不继续构造长堆叠链；
5. 设计接受 PR 不标记业务能力 `IMPLEMENTED`；
6. 工程壳、在线功能、离线运行时和试点分别声明完成范围。

推荐实施顺序：

```text
共享工程与生成客户端基础
→ Network Web 独立化
→ Technician H5 独立化
→ iOS 工程基础
→ 联系/预约在线闭环
→ Visit 在线闭环
→ 动态表单在线闭环
→ Evidence 在线采集与上传
→ 提交/整改在线闭环
→ iOS WorkPackage/本地库
→ OfflineCommand/Sync Center
→ 后台上传/改派失权
→ TestFlight 试点
```

## 10. M7 完成定义

- U0～U6 适用 P0 完成；
- 页面/route/action/query/acceptance 追踪完整；
- Admin、Network、Technician H5、Technician iOS 首切片能处理正常、异常和恢复；
- H5 在线参考实现能稳定支撑联调和自动化回归；
- iOS 真机离线、上传、改派和整改通过；
- Admin 高频审核/派单达到签署可用性目标；
- Network 数据/价格隔离通过；
- a11y、敏感数据、缓存和埋点审查通过；
- 旧客户端兼容和任务分配门禁通过；
- 未完成页面不出现在生产菜单，或明确显示不可用原因和阶段。
