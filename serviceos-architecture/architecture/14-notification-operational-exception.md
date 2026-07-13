---
title: 通知与运营异常中心设计
version: 0.1.0
status: Proposed
---

# 通知与运营异常中心设计

## 1. 目标

通知能力把领域事件转成面向用户、网点、师傅和内部角色的消息；异常中心汇集自动任务无法继续、数据冲突、集成失败和 SLA 超时等需要人工协调的问题。

通知失败不应回滚业务事实；异常中心也不能演变为另一套工单状态机。

## 2. 通知核心对象

| 对象 | 职责 |
|---|---|
| `NotificationPlanVersion` | 事件、条件、收件人、渠道和模板组合 |
| `MessageTemplateVersion` | 已发布、不可变的模板和变量 Schema |
| `NotificationIntent` | 一次业务通知意图 |
| `RecipientResolution` | 收件人解析输入、结果和解释 |
| `NotificationDelivery` | 某收件人某渠道的一次交付 |
| `NotificationAttempt` | 一次供应商发送尝试 |
| `DeliveryReceipt` | 供应商送达/阅读回执 |
| `PreferencePolicy` | 用户偏好与不可退订事务通知规则 |

## 3. 事件到通知

```text
领域事件
→ 匹配 NotificationPlanVersion
→ 校验条件和去重键
→ 解析收件人
→ 锁定模板与变量快照
→ 创建 NotificationIntent
→ 按收件人/渠道创建 Delivery
→ 发送、重试、回执
```

领域模块只发布事件，不直接调用短信、微信或推送供应商。

## 4. 收件人解析

支持：

- 工单客户；
- 当前师傅和网点负责人；
- 当前客服、项目经理、品牌负责人；
- 指定角色 + 品牌/区域/项目数据范围；
- 显式外部联系人。

RecipientResolution 保存角色策略版本、工单参与关系版本、解析时间和最终地址摘要。发送前再次校验人员是否停用以及敏感渠道是否允许。

## 5. 模板

模板定义：渠道、语言、标题/正文、变量 Schema、敏感变量策略、签名和供应商模板 ID。

- 发布版本不可变；
- 变量必须来自允许的数据提供器；
- 模板渲染失败是配置错误，不盲目重试；
- 审计默认保存模板版本和变量摘要，不复制完整敏感正文；
- 短信、微信、App 推送、站内信和邮件通过渠道适配器实现。

## 6. 去重和合并

NotificationIntent 使用业务去重键：

```text
eventId + planVersion + recipientPolicy + purpose
```

重复事件不会重复通知。高频 SLA 预警可按策略合并或抑制，但首次超时和高风险升级不得静默丢弃。

## 7. 通知状态

```text
NotificationIntent: CREATED -> RESOLVED -> COMPLETED/PARTIAL/FAILED
NotificationDelivery: PENDING -> SENDING -> SENT -> DELIVERED/READ
                                      \-> RETRY_WAIT -> SENDING
                                      \-> FAILED_FINAL
```

供应商“已接收”与用户“已送达/已读”分离。没有回执的渠道不能伪装成已读。

## 8. 失败策略

- 网络/供应商临时错误：有界重试；
- 无效号码/账号：标记永久失败并提示数据修复；
- 模板变量错误：配置异常；
- 关键通知最终失败：创建 OperationalException 和人工 Task；
- 非关键营销/提醒：记录失败，不阻塞履约；
- 渠道故障时可按计划使用备用渠道，但必须避免重复扰民。

## 9. 用户偏好和合规

事务通知与营销通知分离。用户偏好可以关闭非必要渠道，但合同、安全、预约变更等必要事务通知按合规规则处理。

联系方式不写入模板配置；退订、静默时段、频控和供应商黑名单按渠道策略执行。

## 10. OperationalException

异常中心统一展示“系统无法自动继续且需要人处理”的问题：

```text
operationalExceptionId
exceptionType / severity
sourceType / sourceId / sourceVersion
workOrderId / taskId / projectId
deduplicationKey
detectedAt
status
resolutionCode
resolutionEvidenceRefs[]
handlingTaskIds[]
correlationId
```

责任人、候选角色和处理 SLA 只属于 handling Task。Exception 只引用 Task 并投影处理进度。

## 11. 异常分类

| 类别 | 示例 | 默认处理 |
|---|---|---|
| `DISPATCH` | 无可派网点、容量竞争失败 | 项目经理人工派单 |
| `INTEGRATION` | 回传永久失败、回执冲突 | 客服/技术协调 |
| `CONFIGURATION` | 多命中、引用失效、规则错误 | 配置管理员修复 |
| `DATA_QUALITY` | 地址无法解析、外部字段冲突 | 客服修复数据 |
| `SLA` | 节点超时、升级未响应 | 品牌负责人协调 |
| `NOTIFICATION` | 关键通知最终失败 | 客服人工联系 |
| `AUTOMATION` | 自动任务重试耗尽 | 指定人工接管角色 |
| `SECURITY` | 异常导出、凭据失效 | 安全/运维处理 |

异常类型目录版本化，明确严重度、去重策略、处理 Task 定义、SLA 和允许解决动作。

## 12. 状态与去重

```text
OPEN -> ACKNOWLEDGED -> IN_PROGRESS -> RESOLVED -> CLOSED
                    \-> SUPPRESSED（仅授权重复/已知问题）
```

同一 source + type + 业务上下文使用去重键聚合重复检测，但每次发生仍记录 occurrence。关闭后再次发生可重开新实例或新一轮，不能丢失频次。

`RESOLVED` 表示处理动作完成；`CLOSED` 表示系统验证恢复。人工不能只点“已处理”而不提供 resolutionCode 和证据。

## 13. 人工处理

异常检测后按策略创建 Task：

- 项目经理处理无网点；
- 客服处理车企驳回或关键通知失败；
- 结算专员处理试算异常；
- 配置管理员修复发布配置；
- 技术运维处理连接器健康和凭据。

人工动作必须调用原领域能力，例如重新派单、修复并重试 delivery、重新解析配置。不得直接改 Exception 为成功来跳过业务恢复。

## 14. 自动恢复

当依赖恢复或重试成功，领域事件可以自动将 Exception 标记 `RESOLVED`，再执行验证关闭。人工 Task 若不再需要，按任务取消规则关闭并记录自动恢复原因。

## 15. 运营工作台

查询投影支持：严重度、异常类型、品牌、项目、区域、网点、处理角色、SLA 风险、最老未处理和重复次数。

异常详情展示：来源对象、失败时间线、自动尝试、当前 handling Task、配置/策略版本、允许动作和恢复验证。技术堆栈信息按角色控制，不向业务用户暴露密钥或完整报文。

## 16. 指标

- 异常新增、未处理、平均确认和解决时长；
- 自动恢复率、人工接管率；
- 按类型/项目/网点的重复异常；
- 自动任务最终失败率；
- 通知发送、送达、失败和备用渠道率；
- 人工解决后再次失败率。

## 17. MVP 验收

1. 重复领域事件不产生重复通知；
2. 预约改约通知使用正确预约修订；
3. SLA 升级解析当前品牌负责人；
4. 关键通知最终失败创建人工处理 Task；
5. 非关键通知失败不回滚业务；
6. 相同自动失败按去重策略聚合 occurrence；
7. 派单失败、回传失败和配置错误使用对应处理角色；
8. 人工解决通过原领域命令完成；
9. 自动恢复能够关闭异常并取消不再需要的 Task；
10. 所有异常、尝试、解决和验证均可审计。
