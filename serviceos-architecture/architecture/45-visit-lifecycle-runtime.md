# M32 Visit 现场到离场运行时

## 目标

M32 闭合 M3 验收矩阵中的 VIS-001～VIS-004：有效预约能够产生独立 Visit，当前责任技师可签到、签退或中断，围栏策略和改派冲突在服务端权威判定，重复上门不覆盖历史。

本里程碑实现既有的 [预约与现场作业](08-appointment-field-operations.md)、[现场作业 HTTP API](../api/03-field-operations-http-api.md) 和 [现场作业逻辑数据模型](../data/03-field-operations-logical-model.md)，没有改变已接受的模块边界，因此不需要新增 ADR。

## 聚合与状态机

- `Appointment CONFIRMED -> IN_PROGRESS -> COMPLETED/INTERRUPTED`，状态只通过 fieldwork 调用 appointment 的公开窄端口推进。
- `Visit IN_PROGRESS -> COMPLETED/INTERRUPTED`。签到身份、预约、Task、资源责任、采集/接收时间、位置、设备和围栏结论创建后不可改写。
- 每个终态追加一条不可变 `fld_visit_fact`；数据库触发器拒绝 Visit 身份/签到事实更新、事实更新和删除。
- `visitSequence` 在同一 `tenant + task` 下单调增长。二次上门使用新 Appointment 和新 Visit，旧 Visit 保持原终态和证据。

## 权威校验

签到前必须同时满足：

1. Appointment 处于 `CONFIRMED`；
2. Appointment 与 HUMAN Task 的 tenant/project/workOrder/task 一致；
3. JWT subject、Task ACTIVE RESPONSIBLE、ACTIVE TECHNICIAN ServiceAssignment 和 Appointment 技师快照完全一致；
4. Appointment 与当前 NETWORK ServiceAssignment 一致；
5. 当前主体具有 `visit.checkIn` 的项目/网络 scope；
6. `Idempotency-Key` 等于 `deviceCommandId`，且采集时间没有超出允许的未来时钟偏差。

改派后的旧技师即使上传离线命令也返回 `TECHNICIAN_ASSIGNMENT_CHANGED`；校验发生在幂等占位和 Visit 写入前，因此不会污染 Visit、事实或可靠事件。

签退和中断再次实时校验当前 Task/ServiceAssignment 责任，并使用双引号 ETag 进行 Visit 版本控制。签退必须包含至少一个 `operationRef`；中断必须包含受控 `exceptionCode`，证据仅保存受治理引用。

## 围栏策略

`fld_geofence_policy` 按 `tenant + project` 保存目标坐标、半径、最大定位精度、策略版本和 `WARN/BLOCK` 动作。

- 精度超限：`LOW_ACCURACY`；
- 坐标超出半径：`OUTSIDE_GEOFENCE`；
- 未配置项目策略：`LOCATION_UNAVAILABLE + WARNING`，不伪造“围栏内”；
- `BLOCK` 对异常定位失败关闭，不创建 Visit；
- `WARN` 允许签到并冻结 `WARNING`、距离和策略版本。

GPS 只形成证据和策略结论，不单独证明技师身份或业务真实性。

## 事务、授权与可靠事件

Visit 写入/条件更新、Appointment 状态推进、不可变事实、幂等冻结响应、审计和 Outbox 在同一 PostgreSQL 事务提交。事件为：

- `visit.checked-in@v1`；
- `visit.checked-out@v1`；
- `visit.interrupted@v1`。

HTTP 查询使用 `visit.read`；写命令分别使用 `visit.checkIn`、`visit.checkOut` 和 `visit.interrupt`。所有 tenant、actor、project 和资源责任均来自 JWT 与服务端权威查询，不接受客户端代填。

## 已实现边界

M32 提供 Visit 生命周期和操作引用门禁，但不在 Visit 内复制动态现场结果。`FieldOperation` 的结构化提交/接受状态、表单/资料完整性校验、离线工作包有效期和人工合并属于后续里程碑；在这些能力完成前，`operationRefs` 仍是受约束但未反向校验的引用。

基线：OpenAPI 0.8.0、Flyway V032（空库共 34 个迁移）、三个 Visit v1 事件 Schema。
