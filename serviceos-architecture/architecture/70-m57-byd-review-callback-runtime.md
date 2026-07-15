---
title: M57 BYD 厂端审核回调权威入站运行时
version: 1.0.0
status: Implemented
---

# M57 BYD 厂端审核回调权威入站运行时

## 1. 决策基线

M57 以仓库内《比亚迪接口文档 V7.3.1》2.6 节、
[BYD 适配器契约](../integration/01-byd-cpim-v731-adapter-contract.md)、M49、M54、M55 和 M56 为事实源，
贯通厂端审核回调到 CLIENT ReviewCase 的可靠纵向链路。

原始协议明确 `Cur_Time=yyyy-MM-dd`，签名原文为
`AppSecret&Nonce&Cur_Time&Params`，Params 按 key ASCII 排序。M16/M56 的 epoch-second 与键值全集拼接
不符合原协议，M57 直接纠正唯一实现和 replay key，不保留兼容解析或双轨认证。

## 2. 实现范围

1. 新增服务主体内部命令，显式登记 BYD `externalOrderCode -> CLIENT ReviewCase` 路由；
2. 路由登记实时校验 Case 为 OPEN CLIENT，且 externalSubmissionRef、callbackBatchRef、mappingVersionId
   与 Case 冻结值完全一致；同一订单最多一个 ACTIVE 路由；
3. 新增由服务商提供的 BYD 厂端审核回调端点，使用统一 APP_KEY/Nonce/Cur_Time/Sign 验证；
4. 合法原文先注册 Envelope/replay guard 并私有留存，再按最多 100 个 orderCode 拆分逐订单处理；
5. 每个订单生成独立 CanonicalMessage 与不可变 item result，单项业务失败不回滚其他订单；
6. `result=1|2` 映射为 `APPROVED|REJECTED`；通过 integration 路由向 M49 公共 API 记录
   ExternalReviewReceipt，并继续执行 M54/M55 门禁；
7. 拒绝回调使用稳定原因码 `BYD.REVIEW.REJECTED`，原始 remark/examinePerson 仅保存在私有规范载荷；
8. 路由缺失、冲突或 Case 已变化时失败关闭，并幂等创建 `integration.external-review-manual` HUMAN Task；
9. 全部成功返回 `message=success`；任一单项失败返回 `message=partially success` 和失败订单列表；
10. 同 transport 重放返回首次批次结果；同业务键同摘要不重复决定/Task/事件，不同摘要记录冲突并人工接管；
11. Envelope、Canonical、item result、路由终态、ExternalReviewReceipt、审计与 Outbox 保持明确事务边界。

## 3. 明确非目标

- 不实现 BYD 提审的网络 OutboundDelivery；路由只登记已经确认提交成功的事实；
- 不根据 orderCode 猜测 ReviewCase，不把 externalSubmissionRef 默认解释为 orderCode；
- 不把外部 remark 自动映射为具体 EvidenceRevision；驳回仍由客服协调；
- 不实现取消、暂停、恢复、关闭等其他 CPIM 回调；
- 不实现生产凭据轮换、正式对象存储、网络限流、Portal 或完整 OperationalException 聚合；
- 不修改 INTERNAL/CLIENT ReviewCase 的业务状态机，也不重做 Evidence 主线。

## 4. 工程证据

验收范围见 [M57 验收矩阵](../testing/54-m57-byd-review-callback-acceptance.md)。实现入口包括：

- `BydCpimReviewCallbackService`、`BydCpimReviewCallbackMapper` 与回调 Controller；
- `DefaultExternalReviewRouteService` 与内部路由登记 Controller；
- V056 将 ExternalReviewReceipt 幂等权威改为 CanonicalMessage，V057 建立路由、逐项结果和日期 replay key；
- OpenAPI Core 0.30.0、BYD CPIM 0.2.0，以及两个 integration 事件 Schema；
- `ReviewCasePostgresIT` 的通过、驳回、部分成功、transport/业务重放和故障恢复场景；
- 签名、严格映射、MVC Security、契约、客户端生成与 ArchitectureTest 门禁。

M57 只实现本文件第 2 节范围；第 3 节仍为明确未实现边界。

Core OpenAPI 0.30.0 允许路由缺失的 CanonicalMessage 以 nullable `projectId` 保留租户级冲突事实，并新增
`RECORD_CLIENT_REVIEW_RESULT` messageType。相对 0.29.0 的 1 个 error、2 个 warning 已由兼容门禁准确报告，
属于项目负责人于 2026-07-15 明确批准的版本化破坏性纠正；不得以默认 project 或旧枚举兜底。
