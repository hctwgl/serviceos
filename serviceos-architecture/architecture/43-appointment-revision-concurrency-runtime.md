---
title: M30 预约修订与并发控制运行时
version: 0.1.0
status: Implemented
---

# M30 预约修订与并发控制运行时

## 1. 范围

M30 交付现场履约预约内核的首个可运行切片：`PROPOSED -> CONFIRMED -> PROPOSED`，覆盖提议、确认、
改约和重新确认。勘察与安装预约是同一 Task 下相互独立的聚合及修订链。

取消、爽约、上门签到、现场执行和离线命令不属于本切片；这些能力必须在对应状态机、授权和证据口径
冻结后单独交付，不能借由通用“状态修改”绕过领域动作。

## 2. 责任快照与授权

客户端只提交预约类型、时间窗和地址引用。应用服务通过 Task 公共查询端口解析 project、work order、
Task 责任人，通过 Dispatch 公共查询端口解析当前 network 和 technician。两条责任链同时存在但人员不一致时，
命令失败关闭。

每次读写都实时执行 RoleGrant 授权。授权请求同时携带 tenant、project 和 network scope；token 能力声明
不能替代服务端授权。详情的 `allowedActions` 由当前状态和当前主体的 `appointment.manage` 能力共同计算。

## 3. 不可变修订与并发

预约主表只保存当前状态、当前修订指针和聚合版本。时间窗、地址、确认事实及改约原因全部以修订追加：

1. 提议创建 revision 1，状态为 PROPOSED；
2. 确认追加新修订并冻结确认对象、渠道和时间，状态为 CONFIRMED；
3. 改约追加不携带旧确认事实的新修订，状态回到 PROPOSED，必须重新确认。

所有状态变化另写不可变历史。命令要求双引号 `If-Match`，数据库条件更新同时校验 tenant、状态和版本；
陈旧并发写返回 `APPOINTMENT_VERSION_CONFLICT`，不会静默覆盖。

## 4. 事务与契约

聚合状态、修订、历史、命令冻结响应、审计、幂等结果和 Outbox 在同一事务提交。事件契约为
`appointment.proposed@v1`、`appointment.confirmed@v1` 和 `appointment.rescheduled@v1`。HTTP 契约为
OpenAPI 0.6.0，数据库基线为 V030。
