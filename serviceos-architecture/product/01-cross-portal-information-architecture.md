---
title: 跨 Portal 信息架构与应用外壳
version: 0.2.0
status: Accepted
lastUpdated: 2026-07-18
---

# 跨 Portal 信息架构与应用外壳

## 1. 目标

ServiceOS 不是用一个菜单树把所有角色塞进同一个应用。总部、网点和师傅面对不同任务密度、设备、网络条件和数据范围，必须拥有独立 Portal，同时共享服务端领域动作、契约类型和设计语言。

本文件定义：应用边界、导航、页面标识、工作台、工单工作区、队列、搜索、深链、全局交互骨架，以及 Technician H5/iOS 多客户端策略。

## 2. 设计原则

1. **任务优先**：用户首先看到需要处理的 Task/Exception，而不是模糊工单状态；
2. **上下文连续**：从队列进入工单后保留来源、筛选和返回位置；
3. **服务端动作驱动**：按钮来自 allowed-actions，前端不推断业务合法性；
4. **事实分区展示**：任务、资料、审核、回传、试算各有权威区域，不复制成一个状态；
5. **渐进披露**：默认呈现决策所需信息，敏感或技术信息按能力展开；
6. **异常可恢复**：错误界面必须说明发生了什么、是否已提交、下一步动作和 correlationId；
7. **独立 Portal**：角色切换不把师傅现场体验降级为后台响应式页面；
8. **可追溯**：任何重要数字、状态和动作可进入来源对象、版本或时间线；
9. **可访问**：颜色不是唯一状态信号，键盘、读屏和触控目标纳入组件基线；
10. **无暗箱自动化**：自动派单、审核、回传和试算展示命中解释与版本；
11. **共享契约，不共享权限假设**：客户端可复用机器契约和基础组件，但数据范围、菜单和业务动作必须由服务端上下文决定；
12. **在线闭环先于离线增强**：先通过 H5 和 iOS 在线链路验证业务事实，再实现 iOS 离线运行时。

## 3. Portal 与客户端边界

| Portal / Client | 用户 | 主要终端 | 主要目标 | 发布边界 |
|---|---|---|---|---|
| `ADMIN` | 品牌负责人、项目经理、客服、审核、运营、风控、结算、系统运维 | 桌面浏览器 | 高密度队列、协调、审核、配置与观测 | 独立 Web 应用 `serviceos-admin-web` |
| `NETWORK` | 网点负责人及获授权协作人员 | 桌面/平板/移动浏览器 | 本网点任务、派师傅、预约协调、补资料与人员管理 | 独立响应式 Web 应用 `serviceos-network-web` |
| `TECHNICIAN_WEB` | 师傅、开发、测试、产品和受控运营人员 | 桌面/移动浏览器 | 在线流程参考实现、契约联调、自动化回归、产品走查和有限应急操作 | 独立 H5 应用 `serviceos-technician-web` |
| `TECHNICIAN_IOS` | 师傅 | iPhone，后续按试点决定 iPad | 正式现场作业、相机/GPS、本地安全存储、后台上传和离线执行 | 首个正式原生移动应用 `serviceos-technician-ios` |
| `TECHNICIAN_ANDROID` | 师傅 | Android | 在 iOS 试点稳定后复用已接受契约实现 | Deferred，不在当前交付主线 |
| `EXTERNAL` | 用户/车企受控协作方 | 短时 Web 页面/受控 Portal | 确认、签字、有限状态或回执 | 独立最小页面集，二期逐项启用 |

`TECHNICIAN_WEB` 与 `TECHNICIAN_IOS` 使用同一 `TECHNICIAN` Persona、同一服务端 Portal Adapter、同一 pageId/actionCode/Schema 语义，但它们是两个独立发布物。

不同 Portal 可以共享：

- OpenAPI/JSON Schema 生成类型与客户端；
- Problem Details、错误码、状态枚举和时间/金额格式化；
- 设计 token、图标、文案规范和无角色假设的基础组件；
- 脱敏测试样例、验收用例与可观测性字段；
- Page ID、Feature ID、Action Code 和版本兼容规则。

不得共享：

- 整页路由与角色菜单；
- 含 Tenant/Project/Network/Technician 数据范围假设的 store；
- 通过前端角色判断替代服务端授权的逻辑；
- iOS 本地数据库、相机、GPS、后台上传与 OfflineCommand 实现；
- 将 H5 业务页面包装成 WebView 并宣称等价于正式原生现场能力。

## 3.1 Technician 多客户端职责

### H5 的职责

`serviceos-technician-web` 用于：

- 后端契约和 DTO 的首个可视化参考实现；
- Feed、任务详情、联系预约、Visit、表单、资料、整改等在线流程验证；
- Playwright E2E、权限边界、异常态和版本冲突验证；
- 产品、运营和测试人员在桌面环境复现现场问题；
- 在明确能力边界下提供在线只读或轻量应急操作。

H5 不承诺：

- 长时间断网和杀进程后的完整恢复；
- 后台可靠大文件上传；
- 设备级敏感数据撤销；
- 与原生相机/GPS 相同的采集可信度；
- 与 iOS 相同等级的本地加密、文件隔离和离线命令可靠性。

### iOS 的职责

`serviceos-technician-ios` 是首个正式现场生产客户端，负责：

- OIDC PKCE、Keychain 和 Technician Context；
- 原生相机、Photos、Core Location 与系统权限；
- 本地加密数据库、工作包、草稿和文件队列；
- Background URLSession/BGTaskScheduler 等后台上传与恢复；
- OfflineCommand、幂等、冲突、改派失权和设备撤销；
- TestFlight、真机弱网和小 cohort 试点。

### 交付顺序

```text
服务端契约与权限边界
→ Technician H5 在线参考实现
→ iOS 在线实现
→ iOS 离线与后台上传增强
→ 真机弱网和 TestFlight 试点
→ Android 后续评估
```

业务动作应保持一致，平台可靠性等级允许不同。H5 页面不得用“已完成”掩盖仅本地保存或仅上传完成的事实；iOS 同样必须区分本地保存、服务器接收和业务接受。

## 4. 稳定页面标识

每个页面拥有不随中文名称变化的 `pageId`：

```text
ADMIN.WORKBENCH
ADMIN.WORK_ORDER.LIST
ADMIN.WORK_ORDER.WORKSPACE
ADMIN.REVIEW.QUEUE
NETWORK.TASK.QUEUE
TECHNICIAN.TASK.FEED
TECHNICIAN.TASK.DETAIL
TECHNICIAN.SYNC.CENTER
```

pageId 用于菜单配置、前端埋点、错误定位、帮助文档、权限体验测试和发布 Coverage Report。pageId 不是授权能力；用户知道页面 URL 仍需服务端数据范围和动作鉴权。

H5 与 iOS 对同一业务页面复用相同 pageId；客户端平台通过 telemetry 的 `clientKind`/`clientVersion` 区分，不为同一业务目标发明两套页面身份。

## 5. 导航模型

### 5.1 Admin

```text
工作台
工单履约
  工单
  任务与待办
  派单
  预约与上门
资料审核
  待审核
  整改跟踪
异常与集成
  运营异常
  外部接入/回传
商业与分析
  履约事实
  影子试算
  运营报表
服务网络
  网点
  师傅与资质
项目与配置
  项目/服务产品
  配置资产/发布
  SLA/派单/通知策略
平台治理
  组织/角色/授权
  审计
  迁移/灰度/门禁
```

菜单按 capability 和 feature gate 生成；角色只影响默认首页与排序，不硬编码“客服菜单”“项目经理菜单”。

### 5.2 Network

```text
本网点工作台
工单任务
  待分配师傅
  进行中
  待补资料/整改
  全部工单
人员与能力
  师傅
  资质/能力
  产能与停派（只读或申请）
消息与异常
```

### 5.3 Technician

H5 与 iOS 的主导航语义一致：

```text
任务 | 日程 | 同步 | 消息 | 我的
```

在消息能力尚未接受/实施时，客户端不得展示无效入口；同步入口在 H5 可展示服务端同步摘要和调试信息，在 iOS 承载本地命令、上传和冲突中心。

现场执行在任务详情内按后端 allowed-actions 和流程步骤呈现，不为勘测、安装、维修复制多套 App。

## 6. 应用外壳

### 6.1 桌面 Portal

```text
顶部：租户/环境、全局搜索、通知、帮助、身份
左侧：主导航，可折叠
内容头：页面标题、范围、更新时间、主动作
内容区：筛选/队列/工作区
右侧抽屉：快速预览、动作表单或解释
```

生产和 shadow/staging 必须有持续可见、不可仅靠颜色区分的环境标识。切换品牌/项目/区域范围会重新加载服务端 ScopePredicate，不只是前端过滤。

### 6.2 Technician H5

- 保留当前 Technician Portal 页面作为迁移输入，不立即删除；
- 从 Admin Web 逐步抽离为独立构建、独立路由和独立会话边界；
- 支持桌面和移动浏览器调试，但不假定浏览器后台运行可靠；
- 浏览器相机、定位和文件选择必须展示平台限制；
- 不使用永久 mock 冒充未实现服务端命令。

### 6.3 Technician iOS

- 顶部或任务页持续显示网络、同步状态和当前任务上下文；
- 主动作固定在拇指可达区域；
- 表单、拍摄和上传支持中断恢复；
- 高风险动作使用全屏确认，不在小型弹窗内堆叠长表单；
- 离线时明确哪些动作可保存、哪些必须联网确认；
- Token/密钥使用系统安全存储，本地日志不得记录 PII、文件路径和 Token；
- 客户端必须报告 `clientKind`、`clientVersion`、支持的 action/schema/work-package 版本。

## 7. 工作台模型

工作台由可解释的 `WorkQueueCard` 构成，而不是写死统计卡片：

```text
queueCode
title / description
count / trend（可选）
severity / dueRisk
scopeSummary
queryRef
asOf / freshness
requiredCapability
```

首批队列：

- 我的待办；
- 今日到期/已超时；
- 自动派单失败；
- 待分配师傅；
- 待预约/需再次联系；
- 待审核；
- 待整改；
- 回传失败/结果未知；
- 事实冲突/不可试算；
- P0/P1 运营异常。

计数只用于导航，用户进入队列后以服务器查询结果为准。计数与列表存在投影延迟时显示 `asOf`，不能用动画伪装实时。

## 8. 列表与队列

所有高密度列表遵循：

- 默认视图由角色/任务提供，但用户可以保存个人视图；
- 过滤条件可复制为 URL，敏感值不写 URL；
- 列、排序和密度可个性化；
- 固定显示对象 ID、关键业务键、当前 Task、责任人、SLA 风险和更新时间；
- 状态筛选使用业务维度组合，不只提供一个 workOrder.status；
- 批量选择跨页时必须显示实际命中范围和排除项；
- 操作完成后保持来源视图与滚动位置；
- 空结果区分“没有数据”“筛选无结果”“无权限”“投影暂不可用”。

## 9. 客户端兼容与发布门禁

服务端和任务分配策略必须能够判断客户端是否支持任务所需能力，至少考虑：

```text
clientKind
clientVersion
supportedActionCodes
supportedSchemaVersions
supportedWorkPackageVersions
supportedCaptureCapabilities
```

当旧客户端不支持新的必需 action、表单 Schema、Evidence 采集策略或 WorkPackage 版本时，系统必须：

1. 阻止将不兼容任务派给该客户端版本；或
2. 明确要求升级并保持任务未被错误接受；或
3. 进入受控人工处理，而不是让客户端执行到中途失败。

Portal 可独立发布，但契约版本、Feature Gate 和 cohort capability 必须兼容。任何客户端成功状态都必须能关联服务器资源 ID/version 和 trace/correlation 标识。

## 10. 决策状态

本文件接受以下方向：

1. Admin、Network、Technician H5、Technician iOS 是独立应用；
2. 当前继续使用单仓库，允许共享机器契约和基础包；
3. Technician H5 作为在线参考实现、开发调试和受控备用；
4. iOS 是首个正式现场客户端；
5. Android 在 iOS 试点稳定后评估；
6. 不使用 WebView 包装 H5 代替正式 iOS；
7. 在线业务闭环先于离线运行时；
8. 后续实施里程碑必须从最新 `master` 领取空闲编号，不与正在推进的 M245 或其他 Agent 工作冲突。
