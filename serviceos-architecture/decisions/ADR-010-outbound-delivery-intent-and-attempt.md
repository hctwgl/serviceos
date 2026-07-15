# ADR-010：外部交付意图与网络尝试分离

- 状态：Proposed
- 日期：2026-07-13

> M58 范围内的 Delivery/Attempt/Acknowledgement 分离已于 2026-07-15 获项目负责人明确批准并实现。
> M59 范围内同一 Delivery 的单笔 UNKNOWN 人工重发、冻结 payload/external key 复用、
> ReplayRequest 审批/版本门禁已于 2026-07-15 获项目负责人明确批准并实现。
> M60 仅在获权重发取得严格 ACK 后发布恢复事实并闭环对应运营异常；不引入人工猜测结果，
> 该子集已于 2026-07-15 获明确批准并实现。
> ADR 的其余通用 Connector 范围仍保持 Proposed，不因局部落地外推为全局 Accepted。

## 背景

车企回传可能超时、重试或先技术成功后业务驳回。若每次 HTTP 请求都被视为一次独立业务回传，会重复完成、取消、收费或资料提交。

## 决策

`OutboundDelivery` 表示一次不可变业务交付意图和 payload 快照；`DeliveryAttempt` 表示该意图的一次网络/文件尝试；`ExternalAcknowledgement` 表示技术或业务确认。重试复用同一 delivery 和外部幂等键，对象版本改变时创建新 delivery。

## 约束

- Delivery payload 创建后不可修改；
- attempt 全部保留；
- 技术送达不等于业务确认；
- 超时后优先查询或使用幂等机制；
- 无法判断外部结果时转人工；
- 人工重放需要原因、审批和审计。

## 后果

状态和存储比简单接口日志复杂，但能可靠解释每次外部副作用并避免重复业务结果。
