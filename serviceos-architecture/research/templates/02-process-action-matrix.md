---
title: M1-02 流程与动作矩阵模板
version: 0.1.0
status: Draft
---

# M1-02 流程与动作矩阵模板

## 流程节点

| 顺序 | 阶段编码 | 任务编码 | 任务类型 | 创建条件 | 负责人策略 | 完成条件 | 下一任务编码 | 配置来源 |
|---:|---|---|---|---|---|---|---|---|
| 1 | INTAKE | INTAKE.RECEIVE | 接收工单 | 外部报文校验通过 | 系统 | 标准工单创建 | ASSIGNMENT.OWNER | TBD |
| 2 | ASSIGNMENT | ASSIGNMENT.OWNER | 分配跟进人 | 工单创建 | 规则 | 负责人确定 | REVIEW.INITIAL 或 DISPATCH.NETWORK | TBD |
| 3 | REVIEW | REVIEW.INITIAL | 工单初审 | 项目要求初审 | 客服 | 通过或驳回 | DISPATCH.NETWORK/整改 | TBD |
| 4 | DISPATCH | DISPATCH.NETWORK | 派网点 | 初审通过或跳过 | 系统/项目经理 | 网点确定 | DISPATCH.TECHNICIAN | TBD |
| 5 | SURVEY | SURVEY.EXECUTE | 勘测履约 | 师傅确定 | 师傅 | 数据资料提交 | REVIEW.SURVEY | TBD |
| 6 | INSTALL | INSTALL.EXECUTE | 安装履约 | 勘测通过 | 师傅 | 数据资料提交 | REVIEW.INSTALL | TBD |
| 7 | CALLBACK | CALLBACK.CLIENT | 回传车企 | 总部审核通过 | 系统/客服 | 回传成功 | ACCEPTANCE.CLIENT | TBD |
| 8 | ACCEPTANCE | ACCEPTANCE.CLIENT | 车企确认 | 回传成功 | 外部/客服 | 通过 | FACT.CONFIRM | TBD |

以上内容只是访谈形成的初始骨架，必须按真实项目逐行修订。

## 动作清单

| 动作编码 | 所属任务编码 | 操作角色 | 前置条件 | 必填字段/资料 | 结果事件 | 可撤回 | 异常策略 |
|---|---|---|---|---|---|---:|---|
| TBD | 预约 | 客服/网点/师傅 | TBD | TBD | AppointmentScheduled | TBD | TBD |
| TBD | 改派网点 | 项目经理/助理/客服 | TBD | 改派原因 | ServiceNetworkReassigned | 否 | TBD |
| TBD | 强制关闭 | 授权角色 | TBD | 关闭原因 | WorkOrderForceClosed | 否 | TBD |
| TBD | 单项驳回 | 客服 | 资料待审核 | 原因、说明 | EvidenceRejected | 否 | 生成整改任务 |

## 异常场景

| 异常编码 | 触发条件 | 当前处理方式 | 责任人 | SLA | 恢复点 | 是否影响考核/结算 |
|---|---|---|---|---|---|---|
| TBD | 自动派单失败 | 人工处理 | 项目经理 | 24 小时 | 派网点任务 | TBD |
| TBD | 无可派网点 | 人工处理，不自动跨区 | 项目经理 | TBD | 派网点任务 | TBD |
| TBD | 车企驳回 | 客服协调整改 | 客服 | TBD | 对应整改任务 | TBD |
