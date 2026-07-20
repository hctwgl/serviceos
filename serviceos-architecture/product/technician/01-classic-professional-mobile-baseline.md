---
title: Technician 经典专业移动作业风视觉与交互基线
version: 1.0.0
status: Accepted
lastUpdated: 2026-07-20
owner: Product Owner
approvedStyle: 方案 A｜经典专业移动作业风
---

# Technician 经典专业移动作业风视觉与交互基线

## 1. 决策结论

产品负责人已批准 ServiceOS 师傅端采用：

> **方案 A｜经典专业移动作业风**

该方案强调专业可信、操作路径短、离线可靠、状态清楚和现场可用。

师傅端必须让用户始终知道：

1. 当前要做哪一张任务；
2. 现在处于哪一步；
3. 下一步应该做什么；
4. 哪些内容已经本地保存；
5. 哪些文件正在上传；
6. 哪些结果已经被服务器确认；
7. 任务是否存在 SLA、改派或权限风险。

## 2. 产品气质

### 2.1 必须呈现

- 专业可靠；
- 信息层级清楚；
- 单手可操作；
- 关键动作突出；
- 状态可解释；
- 弱网下不制造虚假成功；
- 大量拍摄和上传仍有秩序；
- 长流程通过步骤分解降低认知负担。

### 2.2 禁止呈现

- 桌面后台式表格；
- 一屏堆叠全部工单字段；
- 小字号、小按钮和密集链接；
- 用一个“完成”跨过到场、表单、资料、签字和提交；
- 将“已保存本地”写成“提交成功”；
- 用模糊的“网络错误”掩盖具体同步状态；
- 原始 JSON、UUID、Capability、Scope 和英文异常；
- 依赖动画、颜色或图标单独表达关键状态。

## 3. 品牌与视觉 Token

### 3.1 颜色

| Token | 建议值 | 用途 |
|---|---:|---|
| `technician-color-primary` | `#1677FF` | 主按钮、当前步骤、链接 |
| `technician-color-primary-dark` | `#0958D9` | 品牌栏、按下状态 |
| `technician-color-canvas` | `#F5F7FA` | 页面背景 |
| `technician-color-surface` | `#FFFFFF` | 卡片、表单、底部栏 |
| `technician-color-border` | `#E5E7EB` | 卡片和分隔线 |
| `technician-color-text-primary` | `#111827` | 标题和主要值 |
| `technician-color-text-secondary` | `#4B5563` | 说明和标签 |
| `technician-color-success` | `#16A34A` | 已同步、已确认、已完成 |
| `technician-color-warning` | `#D97706` | 待同步、即将超时、需注意 |
| `technician-color-critical` | `#DC2626` | 紧急、失败、冲突、超时 |
| `technician-color-local` | `#2563EB` | 本地已保存 |
| `technician-color-uploading` | `#1677FF` | 上传中 |
| `technician-color-quarantined` | `#7C3AED` | 隔离/待人工处理 |

### 3.2 字体和触控

- 页面标题：18～20px，600；
- 卡片标题：16px，600；
- 正文：14～16px；
- 辅助信息：12～13px；
- SLA 倒计时：18～24px，600；
- 主要按钮高度：48～52px；
- 次要按钮高度：44～48px；
- 最小触控区域：44×44px；
- 卡片圆角：8～12px；
- 页面水平边距：16px；
- 卡片间距：12px；
- 底部主操作避开系统安全区。

字体缩放后不得截断工单号、SLA、主操作和错误原因。

## 4. 导航与应用壳

### 4.1 底部导航

正式底部导航：

1. 今日任务；
2. 工单；
3. 消息；
4. 我的。

规则：

- 当前项使用蓝色图标和文字；
- 消息和待同步可显示数字徽标；
- 上传队列和同步中心通过任务详情、消息或“我的”进入，不默认占用一级导航；
- 现场执行中底部导航可以隐藏，保留返回和当前任务入口，避免误跳转。

### 4.2 顶部栏

- 普通页面使用白色标题栏；
- 今日任务首页可以使用企业蓝品牌栏；
- 显示页面标题、返回、消息或更多；
- “更多”只包含当前页面适用动作；
- 不在顶部堆叠租户、项目和内部上下文 ID。

### 4.3 底部主操作

关键页面使用固定底部主操作：

- 开始执行；
- 到场签到；
- 下一步：填写表单；
- 下一步：拍摄资料；
- 提交资料；
- 全部重试；
- 提交完成。

主操作由 allowed-actions 或离线工作包动作清单决定。客户端不得仅根据本地状态启用。

## 5. 通用页面模式

### 5.1 任务 Feed

```text
AppHeader
→ TodaySummary
→ EmergencySection
→ TodayTaskCards
→ BottomNavigation
```

### 5.2 任务详情

```text
TaskHeader + SLA
→ Customer / Address / ServiceProduct
→ CurrentStep
→ NextAction
→ Appointment / Priority / SyncStatus
→ FixedActionBar
```

### 5.3 现场步骤

```text
TaskSummary
→ StepProgress
→ CurrentStepInstruction
→ Checklist / Form / Evidence
→ FixedNextAction
```

### 5.4 资料拍摄

```text
CapturePolicyNotice
→ EvidenceGroupCards
→ ThumbnailGrid
→ CaptureButton
→ Local/Upload/ValidationStatus
→ FixedSubmitAction
```

### 5.5 同步与冲突

```text
TaskGrouping
→ LocalOnly / Pending / Uploading / Conflict / Failed / Confirmed
→ PerItemReason and RecoveryAction
```

## 6. 状态语言

### 6.1 任务状态

用户看到：

- 待联系；
- 待预约；
- 待开始；
- 上门中；
- 填写中；
- 待拍摄；
- 上传中；
- 待提交；
- 待同步；
- 待整改；
- 已完成；
- 已改派；
- 已取消。

### 6.2 数据状态

必须严格区分：

| 状态 | 用户文案 | 含义 |
|---|---|---|
| `LOCAL_ONLY` | 已保存到本机 | 数据只在设备上 |
| `QUEUED` | 待同步 | 已进入本地命令/上传队列 |
| `UPLOADING` | 上传中 | 正在传输文件 |
| `SERVER_VALIDATING` | 平台校验中 | 文件已到服务端，尚未可用 |
| `AVAILABLE` | 已上传并确认 | 服务端确认可用 |
| `COMMAND_PENDING` | 提交待同步 | 命令尚未被服务端接受 |
| `CONFIRMED` | 平台已确认 | 服务端资源和版本已返回 |
| `CONFLICT` | 需要处理冲突 | 责任、版本或配置已变化 |
| `QUARANTINED` | 已隔离，待协助处理 | 本地成果不能直接提交 |
| `FAILED_FINAL` | 无法自动恢复 | 需要人工处理或重新采集 |

“保存成功”必须明确保存位置；“提交完成”只能在服务端确认后使用。

### 6.3 SLA

- 紧急和超时使用红色、文字和图标；
- 即将超时使用橙色；
- SLA 倒计时显示服务端时间基准；
- 离线时标记截至最近同步时间；
- 不用客户端本地时间单独判定超时。

## 7. 今日任务卡片

每张任务卡必须展示：

- 时间窗口；
- 工单号；
- 任务标题；
- 客户脱敏姓名；
- 地址摘要；
- 距离或区域；
- SLA；
- 当前状态；
- 同步/离线包状态；
- 下一动作。

排序优先级来自服务端：

```text
进行中的 Visit
→ 临近预约
→ SLA 风险
→ 整改
→ 其他任务
```

离线时保持最后快照并显示 `截至 xx:xx`。

## 8. 任务详情

首屏必须看到：

- 任务类型；
- SLA；
- 客户和联系方式；
- 地址和导航；
- 服务产品/设备；
- 故障或作业说明；
- 当前步骤；
- 下一动作；
- 预约时间；
- 同步状态。

联系电话和地址按当前任务和字段策略展示；任务被改派后立即隐藏敏感信息。

## 9. 现场步骤

标准步骤投影：

```text
1. 到场签到
2. 现场勘测/安装
3. 填写表单
4. 拍摄资料
5. 提交前检查
6. 离场
```

步骤条是执行导航，不是第二套 Workflow 状态机。

当前步骤：

- 使用蓝色高亮；
- 展示步骤说明和操作指引；
- 列出必做检查；
- 提供异常入口；
- 只有当前 allowed-action 允许时进入下一步。

## 10. 表单

- 单列字段；
- 按业务分组；
- 必填标记清晰；
- 单位、精度和枚举来自 Schema；
- 自动保存本地草稿；
- 显示最后保存时间；
- 条件显隐不静默丢值；
- 错误就近提示并在顶部汇总；
- 复杂数据源离线时说明限制和恢复方式。

## 11. 资料拍摄

每个资料组展示：

- 名称；
- 为什么需要；
- 正确示例；
- 拍摄/相册策略；
- 数量要求；
- GPS/水印要求；
- 已拍/应拍数量；
- 本地、上传和校验状态；
- 当前 Revision。

缩略图显示：

- 删除/重拍；
- 本地已保存；
- 上传进度；
- 已同步；
- 校验失败原因。

现场强制拍摄的资料不显示相册入口。

## 12. 到场签到

页面展示：

- 工单摘要；
- SLA；
- 客户和电话；
- 地址和地图；
- 预约窗口；
- 客户要求；
- 当前定位、精度和服务范围；
- 项目定位策略；
- 到场签到按钮。

定位只在用户主动操作时采集。范围外或精度低时明确说明允许、警告、审批或阻止。

## 13. 上传队列

按任务分组展示：

- 本地已保存；
- 上传中；
- 平台校验中；
- 已同步；
- 可重试失败；
- 冲突；
- 已隔离；
- 最终失败。

每个文件展示：名称、缩略图、大小、进度、网络策略、失败原因和操作。

支持：

- 暂停/继续；
- 单项重试；
- 全部重试；
- 蜂窝/Wi-Fi 说明；
- 剩余空间提醒；
- App 重启恢复。

## 14. 提交前检查

分区展示：

- 表单完整性；
- 资料完整性；
- 上传和校验状态；
- OCR/SN/VIN 一致性；
- 预约、Visit、Assignment 和 Authority；
- 用户签字/确认；
- 异常说明；
- 将创建的业务结果摘要。

只有本地预检通过且服务端策略允许时启用提交。

离线允许排队时按钮文案应为：

```text
保存并等待同步
```

而不是“提交完成”。

## 15. 整改

整改卡片展示：

- 被驳回项；
- 原版本；
- 驳回原因；
- 正确示例；
- 截止时间；
- 整改轮次；
- 最新补传；
- 下一动作。

只允许处理被驳回项，已通过项只读。补传创建新 Revision，不覆盖历史。

## 16. 同步与冲突中心

冲突必须业务化解释：

| 冲突 | 用户说明 | 操作 |
|---|---|---|
| Assignment changed | 任务已被改派，当前资料不能直接提交 | 联系网点、申请转交、清理 |
| Aggregate version changed | 平台数据已更新 | 刷新并重新确认 |
| Form/config mismatch | 表单或配置版本已变化 | 迁移草稿或转人工 |
| Evidence slot invalid | 资料要求已变化 | 选择新资料项或转人工 |
| Authority changed | 当前账号已无权限 | 停止提交并联系管理员 |
| Idempotent accepted | 平台已接收相同操作 | 绑定既有结果并清理重复待办 |

不得使用“最后写入覆盖”。

## 17. 安全与隐私

- 本地数据库加密；
- iOS 密钥使用 Keychain；
- 不在非作业时间持续定位；
- 日志不包含客户、地址、VIN、照片路径和 Token；
- 多账号不共享工作包和文件；
- 设备撤销后清理可清理数据；
- 未同步资料进入受控恢复，不静默删除；
- 截图/录屏限制按平台和业务风险；
- root/jailbreak 风险按策略警告或阻断。

## 18. 可访问性

- 支持系统字体缩放；
- 关键图标提供读屏标签；
- 不依赖颜色单独表达状态；
- 触控区域至少 44px；
- 错误与操作结果可被读屏读取；
- 相机和上传进度有文本反馈；
- 重要操作提供视觉和触觉反馈，但不依赖动画。

## 19. 实现组件建议

- `TechnicianAppShell`；
- `TodayTaskSummary`；
- `TaskCard`；
- `SlaCountdown`；
- `SyncStatusBadge`；
- `TaskDetailHeader`；
- `CustomerContactCard`；
- `AddressNavigationCard`；
- `StepProgress`；
- `CurrentStepCard`；
- `FixedActionBar`；
- `DynamicFormRenderer`；
- `EvidenceGroupCard`；
- `EvidenceThumbnail`；
- `CapturePolicyNotice`；
- `UploadQueueItem`；
- `SubmissionChecklist`；
- `CorrectionItemCard`；
- `ConflictResolutionCard`；
- `OfflineBanner`；
- `ProblemPresenter`。

## 20. 与效果图的关系

批准效果图确定：

- 今日任务使用蓝色品牌栏、概览数字和任务卡片；
- 任务详情突出 SLA、客户、地址、当前步骤和底部主操作；
- 现场作业使用步骤式流程；
- 资料拍摄使用分组缩略图和数量完成度；
- 到场签到突出地图、范围判断和大按钮；
- 上传队列清楚区分本地、上传中和已同步；
- 提交前检查以清单和服务器确认作为最终闭环。

效果图中的名称、图片、时间和工单号只是脱敏示例。实际实现必须使用真实 API/工作包数据和统一 Presenter。
