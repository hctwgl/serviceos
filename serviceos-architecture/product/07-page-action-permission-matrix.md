---
title: 页面、动作、能力与数据范围矩阵
version: 0.1.0
status: Proposed
---

# 页面、动作、能力与数据范围矩阵

## 1. 使用规则

本矩阵定义前端渲染和验收所需的能力编码建议。它不把角色变成授权真相：

```text
页面可见 ≠ 数据可见 ≠ 动作允许
```

最终允许由服务端同时判断 Principal、Capability、DataScope、Task Action、FieldPolicy、FeatureGate、AuthorityVersion 和 Obligations。

## 2. 页面访问

| 页面组 | Portal | 查看能力 | 数据范围 |
|---|---|---|---|
| 工作台 | ADMIN | `workbench.read` | 当前授权项目/区域/角色队列 |
| 工单/任务 | ADMIN | `workOrder.read`、`task.read` | 品牌/项目/区域/参与关系 |
| 派单 | ADMIN | `dispatch.read` | 管理项目/区域 |
| 预约 | ADMIN | `appointment.read` | 工单范围 |
| 审核/整改 | ADMIN | `review.read`、`correction.read` | 候选/已领取任务范围 |
| 运营异常 | ADMIN | `exception.read` | 异常类型 + 项目/区域范围 |
| 集成 | ADMIN | `integration.read` | 项目/连接器范围，原文另授权 |
| 事实/试算 | ADMIN | `fact.read`、`pricing.read` | 项目/方向/金额字段策略 |
| 网点/师傅 | ADMIN | `network.read`、`technician.read` | 风控/项目/区域范围 |
| 配置 | ADMIN | `configuration.read` | 资产 owner/项目范围 |
| 平台治理 | ADMIN | 对应治理 capability | 租户/组织和职责分离 |
| 运营分析 | ADMIN | `analytics.read*` | 指标目录 + 项目/区域/网点范围 |
| 本网点工单 | NETWORK | `workOrder.readAssigned` | 当前 ACTIVE 网点责任 |
| 本网点任务 | NETWORK | `networkTask.read` | 当前网点 Task/Assignment |
| 本网点人员 | NETWORK | `technician.readOwnNetwork` | 当前 NetworkMembership |
| 师傅任务 | TECHNICIAN | `task.readAssigned` | 当前 TaskAssignment 本人 |
| 师傅资料/整改 | TECHNICIAN | 随 Task action | 当前工作包/任务 |

## 3. 工单与任务动作

| 动作 | Portal | Capability | 额外条件 | Obligations | API/命令 |
|---|---|---|---|---|---|
| 查看工单 | ADMIN/NETWORK | `workOrder.read` / `workOrder.readAssigned` | Scope/ACTIVE assignment | FieldPolicy | `GET /work-orders/{id}` |
| 编辑基础信息 | ADMIN | `workOrder.updateBasicInfo` | 当前 Task 允许、字段归属/FieldPolicy 可写 | reason、impact/approval（按字段） | `CorrectWorkOrderData` |
| 激活工单 | ADMIN/系统 | `workOrder.activate` | 初始条件完整 | — | `ActivateWorkOrder` |
| 暂停 | ADMIN | `workOrder.suspend` | action allowed | reason、evidence（按策略） | `SuspendWorkOrder` |
| 恢复 | ADMIN | `workOrder.resume` | action allowed | reason | `ResumeWorkOrder` |
| 取消 | ADMIN | `workOrder.cancel` | 补偿可执行 | reason、second confirmation | `CancelWorkOrder` |
| 强制关闭 | ADMIN | `workOrder.forceClose` | 高风险策略、补偿可处理 | impact preview、reason、approval、MFA | `ForceCloseWorkOrder` |
| 重开 | ADMIN | `workOrder.reopen` | recovery point 有效 | reason、approval、MFA | `ReopenWorkOrder` |
| 领取 Task | ADMIN | `task.claim` | 候选人、Task READY | — | `ClaimTask` |
| 释放 Task | ADMIN | `task.release` | 当前领取人/授权管理员 | reason（按策略） | `ReleaseTask` |
| 完成 Task | 各 Portal | `task.complete` | 当前执行人、完成条件 | result refs | `CompleteTask` |
| 阻塞 Task | 各 Portal | `task.block` | action allowed | reason、evidence | `BlockTask` |
| 人工完成自动 Task | ADMIN | `task.manualComplete` | MANUAL_INTERVENTION | repair、approval | `ManualComplete` |

## 4. 派单与责任

| 动作 | Portal | Capability | 数据范围 | Obligations | 命令 |
|---|---|---|---|---|---|
| 查看候选/解释 | ADMIN | `dispatch.read` | 项目/区域 | 商业评分字段策略 | Dispatch query |
| 指定网点 | ADMIN | `dispatch.assignNetwork` | 项目/区域 | reason；例外时 approval | AssignNetwork |
| 改派网点 | ADMIN | `workOrder.reassignNetwork` | 项目/区域 | impact preview、reason、second confirm | ReassignNetwork |
| 覆盖软评分 | ADMIN | `dispatch.overrideSoftScore` | 项目/区域 | reason、审计 | Dispatch override |
| 覆盖硬条件 | ADMIN | `dispatch.overrideHardRule` | 仅明确可覆盖规则 | approval、MFA、evidence | 专用高风险命令 |
| 分配师傅 | NETWORK/ADMIN | `task.assignTechnician` | 当前网点/管理范围 | 资质和冲突提示 | AssignTechnician |
| 更换师傅 | NETWORK/ADMIN | `task.reassignTechnician` | 当前网点/管理范围 | reason、impact preview | ReassignTechnician |
| 调整容量/比例 | ADMIN | `dispatchPolicy.adjust` | 项目/网点 | 申请、审批、有效期 | PolicyAdjustmentRequest |

黑名单、停派、无必需资质默认属于不可覆盖硬条件。即使 UI 显示管理员入口，服务端策略仍可 DENY。

## 5. 预约与现场

| 动作 | Portal | Capability | 条件 | Obligations | API |
|---|---|---|---|---|---|
| 记录联系 | ADMIN/NETWORK/TECHNICIAN | `appointment.recordContact` | 当前工单/Task 范围 | 标准结果 | `POST .../contact-attempts` |
| 提议预约 | 三 Portal | `appointment.propose` | 当前责任/协同范围 | window、party | `POST .../appointments` |
| 确认/改约 | 三 Portal | `appointment.manage` | ETag、action allowed | reason（改约） | confirm/reschedule |
| 取消预约 | 三 Portal | `appointment.cancel` | ETag、action allowed | reason | cancel |
| Check-in | TECHNICIAN | `visit.checkIn` | 当前师傅/工作包 | location/device；异常说明 | check-in |
| Check-out | TECHNICIAN | `visit.checkOut` | 必需提交满足 | result | check-out |
| 中断现场 | TECHNICIAN | `visit.interrupt` | Visit IN_PROGRESS | reason/evidence | interrupt |
| 现场异常覆盖 | ADMIN | `fieldwork.overrideException` | 高风险策略 | approval/evidence | 专用命令 |

## 6. 表单、资料与审核

| 动作 | Portal | Capability | 条件 | Obligations | API |
|---|---|---|---|---|---|
| 查看任务表单定义 | 三 Portal | `form.read` | Task Project Scope | — | task forms GET |
| 保存本人草稿 | TECHNICIAN | `form.saveDraft` | 当前 TaskAssignment | — | form draft PUT |
| 提交表单 | TECHNICIAN | `form.submit` | FormVersion/Task 有效 | validation | form submission POST |
| 更正已提交表单 | TECHNICIAN/授权人员 | `form.correctSubmission` | Correction Task | reason、source review | supersede |
| 上传资料 | TECHNICIAN | `evidence.submit` | 当前 slot/Task | capture policy | upload/finalize |
| 网点代补 | NETWORK | `evidence.submitOnBehalf` | slot 允许、当前网点 | onBehalfOf、reason | upload/finalize |
| 查看缩略图 | 三 Portal | `evidence.read` | Scope/Task | FieldPolicy | evidence query |
| 下载原件 | ADMIN/NETWORK | `evidence.downloadOriginal` | Scope + type | purpose、watermark、audit | download authorization |
| 领取审核 | ADMIN | `task.claim` + `evidence.review` | 候选 ReviewTask | — | ClaimTask |
| 单项决定 | ADMIN | `evidence.review` | 精确 targetVersion | reason on reject | DecideReview |
| 强制通过 | ADMIN | `evidence.forceApprove` | 专用角色/范围 | reason、approval、MFA | ForceApprove |
| 提交整改 | TECHNICIAN/NETWORK | `correction.resubmit` | 当前 Correction Task | exact refs | ResubmitCorrection |
| 重开审核 | ADMIN/系统 | `review.reopen` | 车企驳回/抽检等 trigger | reason/approval | ReopenReview |

审核员默认没有 `form.correctSubmission`、`evidence.submitOnBehalf` 或 `evidence.forceApprove`。

## 7. 集成、异常与通知

| 动作 | Portal | Capability | 条件 | Obligations | API |
|---|---|---|---|---|---|
| 查看交付摘要 | ADMIN | `integration.read` | 项目/连接器范围 | payload 脱敏 | Delivery query |
| 查看原始报文 | ADMIN | `integration.readRawPayload` | 高敏范围 | purpose、audit | payload authorization |
| 重试业务 Task | ADMIN | `integration.retryTask` | retryable/人工接管 | repair note | RetryTask |
| Replay | ADMIN | `integration.replay` | 原 Delivery 已终结/预演 | reason、approval | ReplayRequest |
| 查询外部结果 | ADMIN | `integration.reconcileUnknown` | UNKNOWN | reason | connector query action |
| 查看异常 | ADMIN/NETWORK | `exception.read` | type + scope | 技术字段策略 | Exception query |
| 处理异常 Task | ADMIN/NETWORK | `exception.handle` | handling Task action | 原领域命令 | Task/Domain command |
| 抑制异常 | ADMIN | `exception.suppress` | 已知重复/策略允许 | reason、expiry、approval | SuppressException |
| 关闭异常 | 系统/授权人员 | `exception.close` | recovery verified | resolution evidence | CloseException |
| 通知 fallback | ADMIN | `notification.useFallback` | plan 允许 | reason | fallback command |

## 8. 事实与价格

| 动作 | Portal | Capability | 条件 | Obligations | API |
|---|---|---|---|---|---|
| 查看事实 | ADMIN | `fact.read` | 项目/工单 | 来源字段策略 | fact query |
| 确认事实 | ADMIN/系统 | `fact.confirm` | policy 允许 | reason/evidence | ConfirmFact |
| 更正事实 | ADMIN | `fact.correct` | 高风险授权 | reason、approval、impact preview | CorrectFact |
| 发起影子试算 | ADMIN/系统 | `pricing.calculate` | FactSet 合格 | — | RequestCalculation |
| 查看对上 | ADMIN | `pricing.readReceivable` | 项目范围 | 金额策略 | calculation query |
| 查看对下 | ADMIN | `pricing.readPayable` | 专用角色/项目 | 增强审计 | calculation query |
| 候选价格比较 | ADMIN | `pricing.shadowOverride` | SHADOW only | approvalRef | CompareCalculations |
| 导出试算 | ADMIN | `pricing.export` | SHADOW/validated | purpose、敏感导出 | ExportCalculation |
| 正式结算动作 | ADMIN | settlement capability | `FORMAL_SETTLEMENT` 激活 | 职责分离/审批 | API-05 第 3/4 节 |

`pricing.calculate` 不能让调用方选择普通价格/合同上下文；服务端 resolver 决定。M5 所有试算界面显著标记未结算。

## 9. 配置与治理

| 动作 | Portal | Capability | Obligations |
|---|---|---|---|
| 创建/编辑草稿 | ADMIN | `configuration.edit` | owner scope |
| 校验/样本回放 | ADMIN | `configuration.validate` | sample refs |
| 审批 | ADMIN | `configuration.approve` | 不得审批自己高风险提交 |
| 发布 | ADMIN | `configuration.publish` | approval、MFA、impact summary |
| 停用/替代 | ADMIN | `configuration.retire` | impact/日期/approval |
| 授予角色 | ADMIN | `authorization.grant` | scope、有效期、职责分离 |
| 敏感导出 | ADMIN | `data.exportSensitive` | purpose、approval、MFA |
| 创建/扩大 cohort | ADMIN | `rollout.manage` | Gate report、approval、MFA |
| 切换/回退 | ADMIN | `rollout.cutover` / `rollout.rollback` | watermarks、side-effect inventory、多人签署 |

## 9.1 运营分析

| 动作 | Portal | Capability | 条件/Obligations |
|---|---|---|---|
| 查看运营指标 | ADMIN | `analytics.readOperations` | 项目/品牌/区域 scope |
| 查看网点指标 | ADMIN | `analytics.readNetwork` | 区域/网点 scope；商业字段策略 |
| 查看质量指标 | ADMIN | `analytics.readQuality` | 项目 scope；资料原件另授权 |
| 下钻 | ADMIN | 同指标 read capability + 资源 read | 重新应用 ScopePredicate |
| 导出指标 | ADMIN | `analytics.export` | purpose、FieldPolicy、行数、增强审计 |
| 查看正式财务指标 | ADMIN | `analytics.readFinance` | FORMAL_SETTLEMENT + 金额范围/职责分离 |

## 10. 角色模板建议

`✓` 表示默认角色模板可包含，仍需数据范围和动作条件；空白不代表永久禁止，可以通过受控 RoleGrant 配置。

| 能力组 | 品牌负责人 | 项目经理 | 跟进专员 | 客服/审核 | 风控 | 结算 | 网点负责人 | 师傅 |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| 工单只读 | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| 工单协调动作 | ✓ | ✓ | ✓ | ✓ |  |  | 限定 | 限定 |
| 改派网点 | ✓ | ✓ | ✓ | 可配置 | ✓ |  |  |  |
| 分配师傅 |  | 可配置 |  |  |  |  | ✓ |  |
| 联系/预约 |  | ✓ | ✓ | ✓ |  |  | ✓ | ✓ |
| 现场提交 |  |  |  |  |  |  | 代补 | ✓ |
| 普通审核 |  | 可配置 |  | ✓ |  |  |  |  |
| 强制审核 | 专项 | 专项 |  |  |  |  |  |  |
| 网点/资质管理 | 只读 | 只读 |  |  | ✓ |  | 本网点 | 本人只读 |
| 对上试算 | ✓ | 可配置 |  |  |  | ✓ |  |  |
| 对下试算 | 专项 |  |  |  |  | ✓ | 仅本人应付（二期） |  |
| 配置发布 | 专项 |  |  |  | 专项 | 价格专项 |  |  |
| rollout/cutover | 业务签署 |  |  |  |  | 财务签署 |  |  |

## 11. 前端实现规则

- 菜单查询可以基于 capability；
- 资源页面加载后必须查询 allowed-actions；
- actionCode 与 renderer 显式注册；
- 按钮禁用时优先给出 reason/obligation，不用 tooltip 隐藏关键原因；
- 服务器 403/409 后立即刷新相关 action 和资源；
- 不在前端 role name 判断安全；
- UI 单元测试验证渲染，服务端集成测试证明安全；
- 每个矩阵动作必须有正向和至少一个越权/状态冲突用例。

## 12. 待业务确认

- 客服是否默认允许人工改派网点；
- 品牌负责人的高风险强制动作是否需另一角色审批；
- 网点代补允许的资料类型和项目范围；
- 结算、运营和财务的金额可见边界；
- 工单强制关闭的审批链；
- 用户联系方式对网点/师傅的显示时段与脱敏口径。

这些项在未签署前以更严格权限为默认，不通过前端便利性扩大授权。
