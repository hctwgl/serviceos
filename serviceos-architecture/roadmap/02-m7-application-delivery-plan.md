---
title: M7 多 Portal 应用与交互交付计划
version: 0.1.0
status: Proposed
---

# M7 多 Portal 应用与交互交付计划

## 1. 目标

把页面规格转换为设计、前端、后端查询和端到端验收可并行推进的纵向交付。M7 不以“所有页面画完”为完成，而以三个 Portal 能支撑首个真实履约切片并通过跨端异常/恢复验证为准。

## 2. 输入

- M1 首个试点项目字段、资料、流程、角色和样本；
- M2～M5 领域/API/数据/验收；
- M6 工程模块、事务、安全、部署与 Gate；
- product/01～07 页面和交互规格；
- API-06、DATA-06 查询/投影契约；
- 品牌设计规范（如有）、设备和浏览器支持矩阵；
- 师傅真实作业环境、网络、拍照/视频和设备样本。

## 3. 切片

### U0：设计系统与应用外壳

| ID | 交付 | 证据 |
|---|---|---|
| U0-01 | Token、主题、字体、间距、状态语义 | token package + visual regression |
| U0-02 | 三 Portal shell、登录、环境/范围 | 独立构建/路由/会话测试 |
| U0-03 | 基础组件与 Storybook | 全状态/a11y 测试 |
| U0-04 | OpenAPI 生成 client 和错误层 | typecheck/contract CI |
| U0-05 | allowed-action renderer registry | 未知 action 安全降级 |
| U0-06 | command/operation/conflict 反馈 | M7-CMD P0 |

### U1：Admin 工作台与工单工作区

| ID | 交付 | 证据 |
|---|---|---|
| U1-01 | 工作台/队列/计数/freshness | role/scope/asOf 测试 |
| U1-02 | 工单列表、筛选、SavedView | cursor/权限/Schema 迁移 |
| U1-03 | 工单 Header/概览/Task/时间线 | 版本与来源可追踪 |
| U1-04 | 表单/资料/审核/集成/试算 Tabs | section 按需加载、字段权限 |
| U1-05 | 全局搜索和深链 | 敏感搜索/失权测试 |

### U2：Network Portal

| ID | 交付 | 证据 |
|---|---|---|
| U2-01 | 工作台/本网点队列/工作区 | ACTIVE assignment scope |
| U2-02 | 师傅分配/更换 operation | saga/冲突/资质测试 |
| U2-03 | 联系与预约 | ETag 并发测试 |
| U2-04 | 代补与整改 | onBehalfOf/权限/版本测试 |
| U2-05 | 师傅/资质/产能 | 停用影响/商业隔离 |

### U3：Technician 基础与离线

| ID | 交付 | 证据 |
|---|---|---|
| U3-01 | 登录、设备、本地加密库 | 撤销/多账号/日志安全 |
| U3-02 | Task Feed/日程/深链 | 增量/tombstone/scope |
| U3-03 | WorkPackage 下载/校验/过期 | 最小化和版本测试 |
| U3-04 | OfflineCommand/Sync Center | 拓扑、幂等、冲突、重启 |
| U3-05 | UploadQueue | 分片、断点、checksum、磁盘 |

### U4：Technician 现场闭环

| ID | 交付 | 证据 |
|---|---|---|
| U4-01 | 联系/预约 | 在线/离线草稿/确认语义 |
| U4-02 | Check-in/Visit/异常 | GPS、captured/received、撤权 |
| U4-03 | 动态表单 | 条件/单位/校验/草稿冲突 |
| U4-04 | 相机/视频/OCR/资料槽位 | 真实设备与项目策略 |
| U4-05 | 提交前检查/FieldOperation 提交 | exact versions/服务端确认 |
| U4-06 | 整改 | 单项补传/多轮/历史保留 |

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
| U6-02 | 审核整改端到端 | technician/network/admin 多轮 |
| U6-03 | 预约并发 | 三 Portal 同时编辑 |
| U6-04 | 集成失败恢复 | Admin 处理、其他端最小信息 |
| U6-05 | 可访问性/兼容/真机矩阵 | M7-A11Y/MOB |
| U6-06 | 性能/投影重建/缓存失权 | M7-WO/QRY/NFR |
| U6-07 | 小 cohort 体验验收 | 业务用户签署和问题闭环 |

## 4. 原型与开发同步

每个切片采用：

```text
真实样本/任务
→ 低保真流程和页面状态
→ API/投影契约评审
→ 高保真组件复用
→ 前后端纵向实现
→ 自动化/可访问/真机验证
→ 业务走查
```

不要求先画完全部高保真页面；但进入开发的页面必须完成所有状态、权限和错误，而不是只有正常态截图。

## 5. Page Spec 模板

```markdown
Page ID / route / Portal
User goal / entry / exit
Required capability and scope
Data sources + freshness
Layout / components
Allowed actions + obligations
Loading / empty / permission / conflict / error / async / offline
Responsive / keyboard / screen reader
Analytics (no sensitive values)
Acceptance IDs
Out of scope
```

## 6. 前后端联调 Gate

- OpenAPI/schema 已合并且生成 client；
- fixture 来自脱敏真实样本，包括异常；
- 服务端 ScopePredicate/FieldPolicy 已实现；
- actionCode/inputSchema/obligations 已注册；
- 命令幂等/ETag/authorityVersion 已定义；
- 投影 freshness 和重建行为已定义；
- correlation/trace 可查；
- 未实现 API 不用永久 mock 冒充完成。

## 7. UX 研究与走查

至少覆盖：

- 客服连续处理审核/预约队列；
- 项目经理处理无网点和改派；
- 网点负责人分配师傅、补资料；
- 师傅在停车场/地下弱网完成勘测或安装；
- 审核员处理 30 项资料及多轮补传；
- 运维/客服处理回传 UNKNOWN；
- 结算人员解释双向 SHADOW 差异。

记录任务完成率、时间、错误、回退、理解偏差和建议；不能只问“喜欢这个页面吗”。

## 8. 发布顺序

1. 内部测试主体和合成数据；
2. 真实脱敏历史只读工作区；
3. staging sandbox 全链路；
4. 内部受控测试工单；
5. 影子生产只读/计算；
6. 小 cohort 网点与师傅；
7. Gate 后扩大。

Portal 可以独立发布，但契约版本和 cohort capability 必须兼容。旧移动客户端不支持新必需 action/Schema 时，不把任务派给该版本。

## 9. M7 完成定义

- U0～U6 适用 P0 完成；
- 页面/route/action/query/acceptance 追踪完整；
- 三 Portal 首切片能处理正常、异常和恢复；
- 移动端真机离线、上传、改派和整改通过；
- Admin 高频审核/派单达到签署可用性目标；
- Network 数据/价格隔离通过；
- a11y、敏感数据、缓存和埋点审查通过；
- 未完成页面不出现在生产菜单，或明确显示不可用原因和阶段。
