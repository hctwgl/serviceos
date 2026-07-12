---
title: M1-04 派单与 SLA 矩阵模板
version: 0.1.0
status: Draft
---

# M1-04 派单与 SLA 矩阵模板

## 派单硬过滤

| 规则编码 | 条件 | 不满足时结果 | 优先级 | 来源 | 生效范围 |
|---|---|---|---:|---|---|
| NETWORK.ACTIVE | 网点未停派 | 排除 | 1 | 已确认业务事实 | TBD |
| NETWORK.NOT_BLACKLISTED | 网点不在黑名单 | 排除 | 1 | 已确认业务事实 | TBD |
| NETWORK.AREA | 工单地址属于服务区域 | 排除 | 1 | 已确认业务事实 | TBD |
| NETWORK.CAPABILITY | 具备业务类型能力 | 排除 | TBD | 已确认业务事实 | TBD |
| NETWORK.CAPACITY | 在途量未超过上限 | 排除 | TBD | 已确认业务事实 | TBD |

## 候选评分

| 指标编码 | 数据口径 | 归一化方式 | 权重 | 最低门槛 | 统计周期 | 负责人 |
|---|---|---|---:|---:|---|---|
| capacity.remaining | 剩余产能 | TBD | TBD | TBD | 实时 | 风控 |
| fulfillment.rate | 履约率 | TBD | TBD | TBD | TBD | 风控 |
| contract.ratioGap | 月度签约比例缺口 | TBD | TBD | TBD | 月 | 项目经理 |

## SLA

| SLA 编码 | 业务/节点 | 开始事件 | 停止事件 | 目标时长 | 日历 | 暂停原因 | 预警 | 超时升级 |
|---|---|---|---|---|---|---|---|---|
| TBD | 自动派单失败处理 | DispatchFailed | NetworkAssigned | 24 小时 | TBD | TBD | TBD | 品牌负责人 |
| TBD | 首次联系用户 | TBD | TBD | TBD | TBD | 用户原因? | TBD | TBD |
| TBD | 安装资料审核 | EvidenceSubmitted | ReviewCompleted | TBD | TBD | TBD | TBD | TBD |

## 策略调整审批

| 调整类型 | 可申请角色 | 审批角色 | 生效时间 | 最长期限 | 必填原因/证据 |
|---|---|---|---|---|---|
| 在途上限 | 项目经理 | TBD | TBD | TBD | 是 |
| 月度比例 | 项目经理 | TBD | TBD | TBD | 是 |
