---
title: M57 BYD 厂端审核回调验收矩阵
version: 1.0.0
status: Implemented
---

# M57 BYD 厂端审核回调验收矩阵

| ID | 优先级 | 场景 | 必须证明的结果 |
|---|---|---|---|
| M57-SIG-001 | P0 | V7.3.1 日期头和签名顺序 | `yyyy-MM-dd` + AppSecret/Nonce/Cur_Time/ASCII Params 可验；epoch 秒、错日、旧算法拒绝 |
| M57-RTE-001 | P0 | 登记 OPEN CLIENT Case 路由 | Case 冻结 submission/batch/mapping 精确匹配，路由不可变 |
| M57-RTE-002 | P0 | INTERNAL/终态 Case、错批次、重复活动订单 | 失败关闭，不产生可路由事实 |
| M57-RTE-003 | P0 | USER、缺 capability 或跨 project | 401/403，不泄露 Case 或路由 |
| M57-CBK-001 | P0 | 单订单通过回调 | Envelope→Canonical→ExternalReviewReceipt→EXTERNAL 决定→路由完成与事件完整 |
| M57-CBK-002 | P0 | 单订单拒绝回调 | CLIENT Case REJECTED，客服协调 Task 创建，remark 仅私有留存 |
| M57-CBK-003 | P0 | 多订单部分失败 | 成功订单独立提交；失败订单返回 `partially success`，人工 Task 幂等创建 |
| M57-CBK-004 | P0 | 同 transport 重放 | 返回首次不可变批次结果，不重复决定、Task、审计或 Outbox |
| M57-CBK-005 | P0 | 新 transport、同业务键同摘要 | 复用首次 Canonical/领域结果；新 Envelope item 投影一致 |
| M57-CBK-006 | P0 | 同业务键不同摘要或终态后冲突 | 首次事实不覆盖；失败 item 与人工 Task 可追溯 |
| M57-REC-001 | P0 | 单项处理期间基础设施失败 | Envelope 保持 RECEIVED；已完成项幂等；重试继续未完成项，不伪造批次成功 |
| M57-SEC-001 | P0 | 无效签名/日期/未知字段/非法批量 | 不注册业务 Envelope 或不执行领域命令，错误精确可定位 |
| M57-DB-001 | P0 | V056/V057 空库迁移 | PostgreSQL 18 原生镜像到 v057；Canonical 回执幂等、路由、item、日期 replay key、唯一键和不可变门禁生效 |
| M57-CON-001 | P0 | OpenAPI/事件/外部 Schema/客户端 | 契约可解析、可重复生成；兼容门禁准确报告经批准的版本化破坏性修正 |
| M57-MOD-001 | P0 | 模块边界 | integration 仅经 evidence/task 等公开 API 协作，Modulith 校验通过 |

批次人工接管只证明 M57 的幂等 HUMAN Task，不外推为完整 OperationalException 工作台；OutboundDelivery、
其他 CPIM 消息、生产 Connector 与 Portal 仍需后续里程碑。

## 自动化证据映射

- `BydCpimSignatureVerifierTest`：M57-SIG-001；
- `BydCpimReviewCallbackMapperTest`：M57-SEC-001 的未知字段、重复订单、严格日期和拒绝备注；
- `ReviewCasePostgresIT`：M57-RTE-001/002、M57-CBK-001～006、M57-REC-001 和领域事务结果；
- `ExternalReviewRouteControllerSecurityTest`、`BydCpimReviewCallbackControllerSecurityTest`：M57-RTE-003 与协议身份边界；
- Flyway PostgreSQL IT、`ContractValidationTest`、客户端生成与 `ArchitectureTest`：M57-DB/CON/MOD。

兼容门禁相对 0.29.0 对 Core 0.30.0 准确报告 Canonical `projectId` nullable 的 1 个 error 和新增
messageType 的 2 个 warning；该版本化破坏性纠正已于 2026-07-15 获项目负责人明确批准，不要求门禁伪通过。

生产凭据、真实脱敏流量和 OperationalException 聚合不在本矩阵完成声明内。
