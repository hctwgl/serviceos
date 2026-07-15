---
title: M58 BYD 提审 OutboundDelivery 可靠运行时
version: 1.0.0
status: Implemented
---

# M58 BYD 提审 OutboundDelivery 可靠运行时

## 1. 决策基线

M58 以仓库内《比亚迪接口文档 V7.3.1》2.5 节、M55、M56、M57、ADR-010 和
ADR-014 为事实源。项目负责人于 2026-07-15 批准本里程碑范围内的以下语义：

- 外发业务意图、真实网络尝试和外部确认分离持久化；
- Task 是唯一重试时钟，DeliveryAttempt 不自建调度器；
- CPIM 协议没有幂等键或远程状态查询，因此 UNKNOWN 必须人工处理，绝不自动重发；
- `operatePerson` 来自已批准 INTERNAL ReviewDecision 的 `decidedBy` 稳定主体标识，不猜测姓名或默认操作人。

## 2. 实现范围

1. SERVICE-only 命令从 APPROVED/FORCE_APPROVED INTERNAL ReviewCase 创建不可变
   `OutboundDelivery`，要求 `integration.submitClientReview` 和 tenant/project scope；
2. 通过 ReviewCase 冻结 Snapshot、源 Task 及 M56 CanonicalMessage 系谱精确派生 BYD
   orderCode；不跨模块读内部表，不根据字符串猜测关联；
3. 提审 JSON 仅含 `operatePerson/orderCode/commitDate`，在网络前写入私有对象存储并
   冻结 SHA-256；查询 API 不返回正文、对象引用、签名、nonce 或凭据；
4. V058 建立 `int_outbound_delivery`、`int_delivery_attempt` 和
   `int_external_acknowledgement`，通过唯一约束、条件更新和 trigger 保护业务幂等及不可变性；
5. 自动 Task 在短事务内登记 Attempt，在事务外仅发送一次 HTTP，再以短事务持久化
   结果；不在数据库锁内执行网络调用；
6. 发送前配置、payload 或签名失败为 `FAILED_FINAL`；明确 CPIM `errno != 0` 为
   `REJECTED`；只有严格 `errno=0, data=null` 才记录 `DELIVERED` 和不可变业务确认；
7. 超时、断连、非 2xx、非法响应、响应存储失败或进程中断会进入 `UNKNOWN`；
   `task.execution.manual-intervention-required@v1` 开启 OperationalException 及 HUMAN 处置 Task；
8. 已明确 `DELIVERED` 后，同事务幂等创建 CLIENT ReviewCase、登记 M57 回调路由并将
   Delivery 转为 `ACKNOWLEDGED`；本地落账失败只重试这段本地逻辑，不再发 HTTP；
9. 创建和确认分别发布 `integration.outbound-delivery-created@v1` 与
   `integration.outbound-delivery-acknowledged@v1`，并保留授权、审计、Task 和 Outbox 证据；
10. Core OpenAPI 0.31.0 发布创建/查询契约，BYD OpenAPI 0.3.0 发布精确外发协议，
    外部 JSON Schema 固定三个必填字段。

## 3. 失败与恢复语义

| 可观测事实 | Delivery 结果 | Task 结果 | 是否可再发 HTTP |
|---|---|---|---|
| 请求确定未发送 | FAILED_FINAL | FINAL_FAILURE | 否，需新的明确业务命令 |
| CPIM `errno != 0` | REJECTED | FINAL_FAILURE | 否 |
| CPIM 严格 `errno=0` | DELIVERED → ACKNOWLEDGED | SUCCEEDED | 否 |
| 网络/响应/存储结果不确定 | UNKNOWN | UNKNOWN → 人工接管 | 绝不自动重发 |
| 已 DELIVERED，本地 Case/Route 落账失败 | DELIVERED | RETRYABLE | 否，只重试本地幂等落账 |

## 4. 明确非目标

- 不实现人工“再发/标记已送达”处置命令，也不猜测 UNKNOWN 结果；
- 不实现 BYD 远程状态查询，因原始协议未提供该能力；
- 不实现取消/暂停/恢复等其他 CPIM 消息、文件批次/SFTP 或通用 Connector SDK；
- 不实现生产 Secret Manager/凭据轮换、正式对象存储或真实 sandbox 演练；
- 不实现 remark 到 EvidenceRevision 的自动 target 映射、Portal 或完整通知中心；
- 不重做 Evidence 主线，不改变 INTERNAL/CLIENT ReviewCase 状态机。

## 5. 工程证据

验收范围见 [M58 验收矩阵](../testing/55-m58-byd-review-submission-outbound-delivery-acceptance.md)。
主要入口包括：

- `DefaultOutboundDeliveryService`、`BydReviewSubmissionTaskHandler` 和
  `OutboundDeliveryCompletionService`；
- `HttpBydCpimSubmitReviewGateway` 与 Task 失败到 OperationalException 的本地可靠消费者；
- V058 及 Core OpenAPI 0.31.0 / BYD OpenAPI 0.3.0 / 外部和事件 Schema；
- `ReviewCasePostgresIT`、HTTP Gateway/MVC Security/契约/客户端/ArchitectureTest。

M58 仅宣称本文第 2 节的最小可靠纵向切片，第 4 节仍是明确未实现边界。
