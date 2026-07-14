# M31 预约联系与终态运行时

## 目标

M31 闭合 M3 的 APT-003、APT-004：平台能够审计每一次联系，计算首次联系时间，并将预约可靠推进到取消或爽约终态。

## 联系事实

- `ContactAttempt` 属于 appointment 模块，按 `tenant + task` 查询。
- 每次联系独立追加，数据库触发器禁止更新和删除；不得复制电话号码，只保存 `contactedPartyRef`。
- 受控结果为 `CONNECTED`、`NO_ANSWER`、`BUSY`、`WRONG_NUMBER`、`USER_REQUESTED_LATER`、`INVALID_CONTACT`。
- `startedAt` 是首次联系 SLA 的业务时间，`createdAt` 是平台接收时间；两者不得混用。
- 写入、幂等冻结响应、审计和 `contact.attempt.recorded@v1` Outbox 在同一事务完成。

## 预约终态

- `PROPOSED/CONFIRMED -> CANCELLED`，要求 `appointment.cancel`、`If-Match` 和原因代码。
- `CONFIRMED -> NO_SHOW`，要求 `appointment.manage`、窗口已经结束、爽约对象、原因与证据引用。
- 终态不是原地字段覆盖：必须追加 `CANCEL` 或 `NO_SHOW` 修订，再以聚合版本和当前状态执行条件更新。
- 终态后 `allowedActions` 为空；爽约只改变 Appointment，不隐式创建 Visit。
- 可靠通知分别为 `appointment.cancelled@v1`、`appointment.no-show-marked@v1`。后续任务策略由独立消费者里程碑定义，本里程碑不臆造任务规则。

## 权限与隐私

查询沿用 `appointment.read`，联系写入使用 `appointment.recordContact`，取消使用 `appointment.cancel`。所有授权均实时匹配 RoleGrant 的租户、项目和网络 scope，客户端不能提供租户、操作者、项目或网络快照。

## 基线

HTTP 契约为 OpenAPI 0.7.0；数据库迁移为 V031；事件契约新增三个 v1 Schema。
