---
title: 网点协作 Portal 产品规格
version: 0.1.0
status: Proposed
---

# 网点协作 Portal 产品规格

## 1. 目标

Network Portal 服务当前有效 ServiceAssignment 所属网点。它不是总部后台的裁剪皮肤，也不是师傅 App 的放大版；核心是让网点快速完成师傅分配、履约协调、资料代补和本网点人员/资质维护，同时严格隔离其他网点和公司内部价格。

## 2. 使用者

当前确认没有独立“网点调度”岗位，相关能力由网点负责人或获授权人员承担。系统仍按 capability 拆分：

- 网点工单查看；
- 分配/更换师傅；
- 联系/预约；
- 代补资料；
- 管理本网点师傅和资质；
- 查看本网点 SLA/异常；
- 申请产能/停派调整。

未来新增岗位只需组合能力，无需新建一套 Portal。

## 3. 页面目录

| Page ID | 路由 | 页面 | MVP | 核心能力 |
|---|---|---|---:|---|
| `NETWORK.WORKBENCH` | `/workbench` | 网点工作台 | 是 | `networkWork.read` |
| `NETWORK.TASK.QUEUE` | `/tasks` | 网点任务队列 | 是 | `networkTask.read` |
| `NETWORK.WORK_ORDER.LIST` | `/work-orders` | 本网点工单 | 是 | `workOrder.readAssigned` |
| `NETWORK.WORK_ORDER.WORKSPACE` | `/work-orders/:id` | 限定工单工作区 | 是 | `workOrder.readAssigned` |
| `NETWORK.TECHNICIAN.ASSIGN` | `/tasks/:id/assign-technician` | 分配师傅 | 是 | `task.assignTechnician` |
| `NETWORK.APPOINTMENT` | `/appointments/:id` | 联系与预约 | 是 | `appointment.manageAssigned` |
| `NETWORK.CORRECTION.QUEUE` | `/corrections` | 补资料/整改队列 | 是 | `correction.readAssigned` |
| `NETWORK.EVIDENCE.SUPPLEMENT` | `/corrections/:id/supplement` | 授权代补资料 | 是 | `evidence.submitOnBehalf` |
| `NETWORK.TECHNICIAN.LIST` | `/technicians` | 本网点师傅 | 是 | `technician.manageOwnNetwork` |
| `NETWORK.QUALIFICATION` | `/qualifications` | 资质与到期 | 是 | `qualification.manageOwnNetwork` |
| `NETWORK.CAPACITY` | `/capacity` | 产能/派单状态 | 是 | `networkCapacity.read` |
| `NETWORK.EXCEPTION.QUEUE` | `/exceptions` | 本网点异常 | 是 | `exception.readAssigned` |
| `NETWORK.NOTIFICATION` | `/messages` | 消息 | 是 | `notification.readSelf` |

## 4. 工作台

首屏只展示本网点：

- 待分配师傅；
- 今日/明日预约和上门；
- 即将超时/已超时；
- 待补资料与整改；
- 师傅冲突/改派同步中；
- 网点资质即将到期；
- 关键通知/回传相关协同事项；
- 当前在途量/上限和派单状态。

“当前在途量”必须显示业务类型和统计时间；“签约比例/评分”只有合同和权限允许时展示口径，不呈现其他网点详细商业数据。

## 5. 本网点工单列表

默认列：工单号、服务产品、区域、用户脱敏信息、当前 Task、师傅、预约窗口、资料/整改、SLA 风险、更新时间。

范围规则：

- 当前 ACTIVE ServiceAssignment 才能查看当前工单；
- 改派后从当前列表立即失效，旧缓存/旧 URL 也必须被服务端拒绝；
- 历史责任只允许具备专用审计/争议能力的网点主体按最小字段查看；MVP 默认不开放；
- 网点不读取其他网点候选分数、容量、用户和师傅；
- 不显示对上价格、公司毛利、其他网点应付或车企合同正文。

## 6. 限定工单工作区

### 6.1 显示

- 服务任务、当前阶段和 SLA；
- 用户联系方式（按当前任务所需和字段策略）；
- 地址、车辆/设备必要信息；
- 当前师傅和预约；
- 本网点相关 Visit、表单提交摘要、资料和整改；
- 本网点可处理的异常和消息；
- 服务端 allowed-actions。

### 6.2 隐藏

- 总部内部负责人和敏感备注（除非明确共享）；
- 其他网点历史和候选信息；
- 对上价格、其他网点成本、内部毛利；
- 配置草稿、规则源码、连接器凭据；
- 完整技术日志和外部原始敏感报文。

## 7. 师傅分配

### 7.1 候选

显示本网点有效师傅：能力/资质、今日任务、时间冲突、服务范围、停用状态和必要设备/桩型技能。平台推荐只作为解释，网点可在授权范围人工选择。

### 7.2 提交

提交前显示：

- 工单与 Task；
- 当前/目标师傅；
- 预约和在途任务冲突；
- 目标师傅资质/能力缺口；
- 是否触发通知；
- 改派后原师傅离线工作包失效；
- 必填原因/审批 obligations。

提交返回 activation saga/operation。只有 ServiceAssignment 与 TaskAssignment 均激活后显示成功；处理中锁定重复提交但允许查看进度。

## 8. 联系与预约

网点与客服/师傅共享同一 ContactAttempt 和 Appointment 历史。页面必须显示操作者和渠道，不把“网点代约”伪装成师傅预约。

- 新建联系记录只追加；
- 改约展示当前 revision 和 ETag；
- 与客服并发修改时返回冲突并展示最新修订；
- 用户拒绝/无法联系使用标准 reasonCode；
- 关键通知失败转协同 Task，不回滚预约事实。

## 9. 资料代补与整改

默认责任仍是师傅。只有异常场景、资料项允许代补且网点具备 `evidence.submitOnBehalf` 时显示代补动作。

代补流程：

```text
选择 CorrectionCase/资料槽位
→ 查看驳回原因、示例和最新版本
→ 选择 onBehalfOf 师傅
→ 填写代补原因
→ 拍摄/上传（按资料项采集策略）
→ 服务端校验
→ 创建新 EvidenceRevision
→ 提交整改
```

系统保存 actualUploader、onBehalfOf、原因、设备、来源和时间。网点不能修改师傅已经提交的表单字段；需要字段更正时走明确 Correction Task。

## 10. 师傅与资质

### 10.1 师傅列表

显示账号绑定状态、启用状态、业务能力、服务区域、资质有效期、当前任务量和最近同步。隐私字段按最小化展示。

### 10.2 变更

- 新增/邀请师傅需要身份绑定和网点关系；
- 停用前展示未完成 Task/预约并要求重新分配计划；
- 资质上传走 FileObject/Review，不直接修改为“已认证”；
- 资质过期按策略阻止新分配，不一定终止现场已开始任务；
- 网点只能提交或维护材料，总部风控审批独立记录。

## 11. 产能与停派

MVP 展示：按业务类型在途量、上限、预占、派单状态、停派原因/有效期。网点不能直接修改签约比例和总部评分。

若允许网点申请调整，创建 `CapacityAdjustmentRequest` 或配置审批任务，显示原值、目标值、期限、原因和审批结果；不能直接写 CapacityBucket。

## 12. 异常

本网点只看到与当前 ServiceAssignment/Task 相关、且策略允许网点处理的异常。例如：师傅未分配、资料整改超时、关键通知失败、资质到期。车企连接器技术错误和其他网点信息默认隐藏。

异常解决必须调用原领域动作：分配师傅、补资料、重新预约等，不能仅点击“已处理”。

## 13. 移动 Web 边界

Network Portal 可以响应式支持负责人快速查看、分配和联系，但不替代 Technician App 的离线表单、现场拍摄、GPS 和大文件上传队列。

## 14. 安全与会话

- 网点切换只在主体拥有多个有效 NetworkMembership 时出现；
- 当前 networkId 由服务端授权上下文解析，不接受请求体自报；
- 页面缓存、下载 URL、导出和通知深链在改派/停用后失效；
- 公共设备建议短会话、MFA 和无敏感本地持久化；
- 所有代办、下载、导出和人员变更增强审计。

## 15. 完成定义

1. 当前网点可以完成分配师傅、预约协同和授权代补；
2. 改派后旧网点列表、详情、下载、命令和缓存均拒绝；
3. 网点看不到其他网点和对上/内部价格；
4. 分配师傅显示 saga 真实完成状态；
5. 代补资料保存实际人与代办关系；
6. 资质/停用不会留下无人处理 Task；
7. 页面覆盖 409、SLA、投影延迟和异步 operation；
8. 所有查询与动作通过 Network scope 自动化验证。
