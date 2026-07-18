---
title: Technician H5 与 iOS 多客户端产品策略
version: 0.1.0
status: Accepted
lastUpdated: 2026-07-18
---

# Technician H5 与 iOS 多客户端产品策略

## 1. 决策摘要

ServiceOS 现场端采用多客户端策略：

1. 保留并独立建设 `serviceos-technician-web`，作为在线参考实现、开发调试、产品验证、自动化回归和有限应急入口；
2. 优先建设 `serviceos-technician-ios`，作为首个正式现场生产客户端；
3. iOS 使用 Swift/SwiftUI 和原生系统能力，不用 WebView 包装 H5 代替正式客户端；
4. Android 在 iOS 在线闭环、离线运行时和小 cohort 试点稳定后另行评估；
5. H5 与 iOS 共享服务端契约、状态语义、Page ID、Action Code、设计 Token 和测试样例，不强求共享平台相关业务代码；
6. 业务能力按“服务端契约 → H5 在线参考实现 → iOS 在线实现 → iOS 离线增强 → 真机试点”推进。

本决策由项目负责人确认，状态为 `Accepted`。它不表示 iOS、H5 独立工程或离线运行时已经实现。

## 2. 背景与理由

当前团队开发人员主要使用 Mac 和 iOS 设备，适合优先建立 Xcode、SwiftUI、真机调试和 TestFlight 流程。同时，仓库已经存在 Technician Portal H5 的 Feed、日程、同步摘要、我的、当前责任任务详情和联系历史等在线切片。

只保留 H5 会降低以下生产能力的可靠性：

- 相机和现场采集可信度；
- GPS 精度和系统权限治理；
- 本地敏感数据安全；
- 后台大文件上传；
- App 被系统终止后的恢复；
- 离线工作包和命令队列；
- 改派、撤权和设备级数据隔离。

直接跳过 H5、只做 iOS 又会降低后端契约联调和产品流程试错效率。因此采用“共享契约、双参考实现、不同可靠性等级”的方式。

## 3. 客户端定位

### 3.1 Technician H5

主要用户：开发、测试、产品、受控运营人员，以及在线环境下的师傅。

主要职责：

- 首个在线业务参考实现；
- API、权限、字段策略和 allowed-actions 联调；
- 页面状态、异常、冲突和恢复流程走查；
- Playwright 自动化回归；
- 桌面环境复现现场问题；
- 在明确边界下提供有限应急操作。

H5 不作为以下能力的正式承诺：

- 长时间离线作业；
- 后台可靠上传；
- 杀进程后完整恢复；
- 原生级相机/GPS 采集；
- 本地敏感数据长期加密保存；
- 完整 OfflineCommand 和改派失权运行时。

### 3.2 Technician iOS

主要用户：正式参与现场履约的师傅。

主要职责：

- 正式在线和离线现场作业；
- 原生相机、定位、文件和系统通知；
- Keychain、本地加密数据库和多账号隔离；
- Background URLSession/BGTaskScheduler；
- WorkPackage、LocalDraft、UploadQueue、OfflineCommand、Sync Center；
- 改派失权、设备撤销和受控本地隔离；
- TestFlight、真机弱网和生产小 cohort。

### 3.3 Technician Android

Android 不属于当前主线。启动条件至少包括：

- iOS 在线现场闭环通过；
- iOS 离线和上传运行时稳定；
- WorkPackage/OfflineCommand 机器契约稳定；
- 试点设备与真实师傅确有 Android 需求；
- 团队具备 Android 开发和真机测试能力。

## 4. 功能与可靠性矩阵

| 能力 | H5 | iOS 在线阶段 | iOS 离线阶段 |
|---|---|---|---|
| OIDC/Technician Context | 支持 | 原生支持 | 原生支持 |
| Feed/日程/任务详情 | 支持 | 支持并缓存 | 工作包内可用 |
| 联系/预约 | 在线支持 | 原生在线 | 草稿/策略允许时排队 |
| 浏览/采集位置 | 浏览器能力，明确限制 | Core Location | 离线记录后服务端复核 |
| 动态表单 | 在线参考实现 | 本地草稿 | 完整离线草稿和迁移 |
| 图片/视频 | 浏览器选择/拍摄 | 原生相机 | 原生采集和本地队列 |
| 上传 | 页面存续期在线上传 | 前台可靠上传 | 后台、断点和重启恢复 |
| 提交/整改 | 在线支持 | 在线支持 | OfflineCommand/同步冲突 |
| 本地敏感数据 | 最小化、短期 | Keychain/受控缓存 | 加密数据库和文件隔离 |
| 改派失权 | 服务端拒绝/刷新 | 服务端拒绝+缓存清理 | tombstone+命令拒绝+隔离 |
| 自动化 | Playwright | XCTest/XCUITest | 真机弱网/恢复测试 |

客户端必须使用不同文案表达：

- 本地已保存；
- 已加入上传/同步队列；
- 服务器已接收；
- 业务对象已创建；
- 服务端业务校验已接受。

不得用一个“成功”状态覆盖上述差异。

## 5. 共享与隔离

### 5.1 共享

```text
serviceos-contracts
OpenAPI / JSON Schema
Problem Details / Error Code
Page ID / Feature ID / Action Code
状态与时间语义
设计 Token / 图标 / 文案
脱敏 fixtures / acceptance IDs
traceId / correlationId / client metadata
```

生成客户端：

```text
OpenAPI
├── generated-typescript-client
└── generated-swift-client
```

### 5.2 隔离

不得跨客户端共享：

- 整页路由和导航；
- 含数据范围假设的状态容器；
- 浏览器/原生权限处理；
- iOS 本地库、后台上传和离线命令；
- 平台相关 ViewModel/Store；
- 通过 `role == ...` 决定业务授权的逻辑。

## 6. iOS 技术基线

推荐基线：

```text
Swift
SwiftUI
Swift Concurrency
URLSession / Background URLSession
Core Location
AVFoundation / PhotosUI
Keychain
BGTaskScheduler
SQLite 持久化层
XCTest / XCUITest
```

架构原则：

- 采用模块化 MVVM/UseCase/Repository 边界；
- ViewModel 不直接实现离线命令和上传状态机；
- 网络、持久化、日志、权限和设计系统独立于业务 Feature；
- 本地数据库必须有显式 Schema 版本和迁移测试；
- 日志不得含 Token、联系人、地址、VIN、照片路径或表单敏感值；
- 所有服务端成功可定位到资源 ID/version 和 trace/correlation 标识。

建议工程结构：

```text
serviceos-technician-ios/
├── App
├── Core
│   ├── Authentication
│   ├── Networking
│   ├── Persistence
│   ├── Observability
│   ├── Security
│   └── DesignSystem
├── Features
│   ├── Context
│   ├── TaskFeed
│   ├── TaskDetail
│   ├── Schedule
│   ├── Contact
│   ├── Appointment
│   ├── Visit
│   ├── DynamicForm
│   ├── EvidenceCapture
│   ├── Submission
│   ├── Correction
│   ├── SyncCenter
│   └── Profile
└── OfflineRuntime
    ├── WorkPackage
    ├── LocalDraft
    ├── UploadQueue
    ├── OfflineCommand
    ├── ConflictResolver
    └── Revocation
```

具体第三方依赖必须在 iOS 工程启动时通过独立技术评审确定；本文件不强制引入特定数据库或状态管理库。

## 7. H5 工程策略

当前 `/technician-portal/*` 页面先作为迁移输入保留，不立即删除。迁移方式：

1. 建立独立 `serviceos-technician-web` 构建和 AppShell；
2. 迁移 `/me`、Context、Feed、Schedule、Sync Summary、Task Detail；
3. 保持旧 Admin 内入口用于短期回归；
4. 双运行验证通过后删除正式旧路由；
5. Admin 可保留只读诊断入口，但不得复用 Technician 的会话和业务路由。

H5 采用移动优先布局，但其定位不是“把 Admin 页面缩窄”。任务详情必须按照下一动作、现场步骤和同步事实组织。

## 8. 认证、上下文和版本兼容

H5 和 iOS 统一使用 OIDC Authorization Code + PKCE，并消费：

```text
/me
/me/contexts
/me/capabilities
/me/navigation
X-Technician-Context
allowedActions
```

服务端必须逐步建立以下兼容信息：

```text
clientKind
clientVersion
supportedActionCodes
supportedSchemaVersions
supportedWorkPackageVersions
supportedCaptureCapabilities
```

不兼容时必须安全失败：

- 不向旧客户端分配无法完成的任务；
- 或要求升级后才能接受任务；
- 或进入受控人工处理；
- 不允许执行到现场中途才发现必需能力缺失。

## 9. 发布和环境

环境至少包括：

```text
Local
Development
Test
Staging
Production
```

H5 与 iOS 必须使用显著环境标识。iOS 发布顺序：

```text
模拟器
→ 开发者真机
→ 内部 TestFlight
→ 受控测试工单
→ 小 cohort 真实师傅
→ 扩大范围
```

正式 App Store、Unlisted App、Custom App 或企业内部发布方式在试点前单独评审，不在本决策中提前锁定。

## 10. 尚待确认但不阻塞工程壳的问题

以下问题在相应阶段前确认，不阻塞 H5 独立化和 iOS 基础工程：

- iOS 最低系统版本；
- iPhone/iPad 支持范围；
- 正式分发方式；
- 试点设备所有权和 MDM；
- 视频大小、压缩和蜂窝上传策略；
- 本地文件保留期限；
- Jailbreak 风险处置；
- 截图/录屏限制；
- WorkPackage 和 OfflineCommand 机器契约；
- Android 启动时间。

## 11. 里程碑和并发 Agent 规则

本策略不预分配具体里程碑编号。当前其他 Agent 已推进到 M245，执行者必须在开始任何实施切片前读取最新主干和开放 PR，从最新已合并工作之后领取空闲编号。

禁止：

- 与活跃 Agent 重复使用 M245 或其后已占用编号；
- 从旧分支继续构造长堆叠 PR；
- 在设计接受 PR 中把工程能力标记为 `IMPLEMENTED`；
- 未接受契约就发明联系对象、Visit、表单、资料或离线命令字段。

## 12. 完成定义

本策略本身完成的含义仅为：

- 客户端方向和职责边界已接受；
- 交付顺序和验收原则已建立；
- 后续工程可以据此领取里程碑。

它不代表：

- 独立 H5 已建立；
- iOS 工程已建立；
- 在线写链路已完成；
- 离线、后台上传、改派失权或 TestFlight 试点已完成。
