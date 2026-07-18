---
title: 多客户端 Portal 持续交付计划
version: 0.1.0
status: Accepted
lastUpdated: 2026-07-18
---

# 多客户端 Portal 持续交付计划

## 1. 目标

把 Admin、Network、Technician H5 和 Technician iOS 的产品方向转换为可连续实施、可独立验收、可与其他 Agent 并行的交付程序。

本计划不预占固定 `Mxxx` 编号。当前其他 Agent 已推进到 M245；每个执行切片必须在开始前读取最新主干、开放 PR 和实施状态，领取当时的下一个空闲里程碑。

## 2. 程序边界

### 包含

- Web 共享工程底座；
- Network Web 独立化；
- Technician H5 独立化；
- Swift Client 和 iOS 工程基础；
- Technician 在线履约闭环；
- iOS 离线、后台上传和改派失权；
- TestFlight 和真机试点；
- 跨端版本兼容、可观测性和验收。

### 不包含

- Consumer Identity；
- External Portal 完整实现；
- 正式结算；
- Android 实现；
- 未接受的复杂工作流、通知、自动派单或商业规则。

## 3. 交付轨道

### Track A：共享工程底座

目标：在不把角色假设放入共享包的前提下，统一机器契约和基础体验。

交付：

- TypeScript OpenAPI Client；
- Swift OpenAPI Client；
- Design Token；
- Web auth/context/error/trace 基础；
- iOS networking/auth/error/trace 基础；
- Page ID、Feature ID、Action Code 注册；
- clientKind/clientVersion 元数据；
- 独立构建和契约兼容 Gate。

门禁：

- 共享包不引用 ADMIN/NETWORK/TECHNICIAN 菜单；
- 不通过前端角色判断替代后端鉴权；
- TypeScript 与 Swift 客户端可从同一 OpenAPI 重复生成；
- 未知 action/schema 安全降级。

### Track B：Network Web 独立化

迁移输入：当前 `serviceos-admin-web` 中 Network Portal 的 M194～M242 能力。

步骤：

1. 独立 AppShell、构建和环境；
2. `/me`、Network Context、Capability 和导航；
3. 工作台、工单、任务和限定工作区；
4. 预约、联系、指派、改派、资料代补；
5. 师傅、资质、产能、整改和异常；
6. Network E2E；
7. 双运行验证；
8. 删除 Admin 中正式 Network 路由，仅保留必要诊断入口。

完成标准：

- 独立部署和会话；
- 数据、价格和跨网点隔离；
- 原有 Network E2E 在新应用通过；
- Admin 不再承担正式网点产品入口。

### Track C：Technician H5 独立化

迁移输入：当前 Technician Portal 的 Feed、日程、同步摘要、我的、当前任务详情和联系历史。

步骤：

1. 独立 H5 AppShell、构建和环境；
2. `/me`、Technician Context 和导航；
3. Feed、Schedule、Task Detail、Sync Summary、Me；
4. Playwright 权限、tombstone、深链和异常回归；
5. 增加在线联系/预约参考实现；
6. 增加 Visit、表单、Evidence、提交和整改参考实现；
7. 双运行验证后移除 Admin 中正式 Technician 路由。

完成标准：

- H5 可作为后端在线契约的第一参考实现；
- H5 明确显示浏览器能力限制；
- 不把本地草稿/上传中误报为业务已接受；
- 不宣称具备原生离线可靠性。

### Track D：Technician iOS 工程基础

步骤：

1. SwiftUI 工程和模块边界；
2. Local/Development/Test/Staging/Production 环境；
3. OIDC PKCE、Keychain 和 Token 生命周期；
4. `/me`、Technician Context、Capability 和导航；
5. Generated Swift Client；
6. Problem Details、TraceId、日志和崩溃诊断；
7. Design Token、Dynamic Type、VoiceOver；
8. iOS CI、模拟器和开发真机；
9. TestFlight 内部通道。

完成标准：

- 真机登录和上下文切换；
- Token、PII 和文件路径不进入日志；
- 环境不可混淆；
- Swift Client 可重复生成；
- 基础页面可关联 trace/correlation 标识。

### Track E：Technician 在线履约闭环

每个业务切片必须按以下顺序：

```text
接受服务端契约
→ H5 在线参考实现
→ H5 自动化与业务走查
→ iOS 在线实现
→ iOS 真机验证
```

推荐业务顺序：

1. 联系对象权威引用、联系记录和预约；
2. Check-in、Visit、无法施工、Check-out；
3. 动态表单和在线草稿（M263 已交付冻结基础字段在线提交；条件/选项/高级控件与草稿冲突仍待后续）；
4. EvidenceSlot、相机/浏览器上传、checksum 和 finalize（M264 已交付在线前台单文件链路；真机、弱网、断点/后台和生产扫描待补）；
5. 提交前检查和任务完成门禁；
6. 审核驳回、Correction Task 和单项补传。

在线闭环完成定义：

```text
收到当前责任任务
→ 联系并预约
→ 到场
→ 填表
→ 拍摄/上传资料
→ 离场并提交
→ 审核驳回
→ 整改补传
→ 任务完成
```

### Track F：iOS 离线和后台上传

步骤：

1. 本地加密数据库和迁移；
2. WorkPackage；
3. LocalDraft；
4. UploadQueue；
5. OfflineCommand；
6. Sync Center；
7. Background URLSession；
8. assignmentVersion/authorityVersion；
9. 改派失权、本地隔离和设备撤销；
10. App 升级时未同步数据迁移。

完成标准：

- 断网、杀进程和重启不丢草稿、文件和命令；
- 重试不重复创建 Visit/Submission/Revision；
- 旧师傅不能提交或读取新敏感数据；
- 本地保存、上传完成、服务器接收和业务接受不混淆；
- 磁盘、网络、权限和版本冲突有可恢复路径。

### Track G：试点和生产化

步骤：

1. 合成数据和内部测试主体；
2. 开发真机；
3. 内部 TestFlight；
4. 受控测试工单；
5. 小 cohort 真实师傅；
6. 问题闭环和发布 Gate；
7. 扩大 cohort；
8. Android 需求评估。

核心指标：

- 任务在线完成率；
- 离线命令成功率；
- 上传失败与恢复率；
- 崩溃率；
- 同步冲突率；
- 改派失权失败数；
- 平均现场操作时间；
- 师傅误解“本地/服务器状态”的问题数。

## 4. 里程碑拆分原则

每个里程碑只交付一个可证明的纵向切片，必须包含：

- 已接受 ADR/API/Schema 或明确的 UI-only 边界；
- 代码或工程配置；
- 自动化测试；
- 适用的真机证据；
- 实现文档；
- 验收矩阵；
- 追踪矩阵；
- 状态文档和明确未实现范围。

禁止：

- 将工程壳、全部在线链路、离线和试点塞进一个里程碑；
- 以“字段展示”持续替代完整用户任务；
- H5 与 iOS 分别发明不同的业务动作；
- 为了复用把 iOS 做成 H5 WebView；
- 从旧里程碑分支堆叠几十个后续 PR；
- 在 M245 仍活跃时预占 M245 或未经核对的后续编号。

## 5. 并发 Agent 协作规则

每个 Agent 开工前执行：

```text
读取 master 最新提交
→ 读取 implementation-status.md
→ 读取 milestone-index.md
→ 检查开放 PR 和分支
→ 确认下一个空闲 Mxxx/ADR/文档编号
→ 从最新 master 创建分支
```

文件所有权建议：

- 业务里程碑 Agent：实现文档、验收矩阵、OpenAPI、代码和状态更新；
- Portal 程序 Agent：product/01、product/08、roadmap/02、roadmap/05 和程序验收矩阵；
- 同一文件有并行修改时，后启动者先 rebase 并保留较新的里程碑事实；
- 文档 PR 不回退 `latestMilestone`、baselineCommit、OpenAPI 或 Flyway 版本。

当前本计划的 PR 基于 M244 主干创建，而 M245 正在其他 Agent 中推进。合并前必须：

1. 等待或确认 M245 的分支/PR 状态；
2. 更新本分支到最新 `master`；
3. 确认没有覆盖 M245 的产品或路线文档更新；
4. 重新执行文档门禁。

## 6. CI 与验证

Web：

- 独立 `npm ci`/build/typecheck；
- Playwright；
- 视觉回归和可访问性；
- OpenAPI client reproducibility。

iOS：

- Xcode build；
- XCTest；
- XCUITest；
- Swift client reproducibility；
- 模拟器矩阵；
- 适用时真机和弱网证据；
- 本地数据库迁移测试。

程序级：

- 三端/四客户端独立发布；
- Portal Context 不可跨用；
- 旧客户端兼容门禁；
- 改派端到端；
- 审核整改端到端；
- 断网和恢复；
- PII、日志和缓存审查。

## 7. 依赖和阻塞

| 能力 | 前置条件 |
|---|---|
| 联系写动作 | 权威联系对象引用、字段策略、幂等和审计 |
| Visit | Check-in/out 契约、GPS 策略、assignment 校验 |
| 动态表单 | FormVersion、可移植校验、草稿和冲突策略 |
| Evidence | Slot policy、上传会话、checksum、FileObject |
| 离线 | WorkPackage/OfflineCommand 机器契约 |
| 消息 | NotificationIntent/Delivery/Inbox 契约 |
| TestFlight 试点 | 签名、隐私、设备和支持流程 |
| Android | iOS 试点稳定和真实设备需求 |

前置条件未接受时，不得猜测字段或把 mock 当正式实现。

## 8. 程序完成定义

程序完成不是“工程目录存在”，而是：

- Admin 和 Network 已独立正式运行；
- Technician H5 可稳定承担在线参考实现和自动化回归；
- Technician iOS 完成在线现场闭环；
- iOS 离线、后台上传、改派失权和升级迁移通过；
- TestFlight 小 cohort 试点完成并通过扩大发布 Gate；
- 各客户端契约和版本兼容可治理；
- 未完成 Android/Consumer/External/Settlement 保持明确 Deferred/Proposed 状态。
