---
title: M16 配置发布解析与 BYD 工单接入事务切片
version: 0.1.0
status: Proposed
owner: Fulfillment Platform
reviewers:
  - Product Architecture
  - Engineering Architecture
  - BYD Business Owner
approved_by: []
approved_at:
related_adrs:
  - decisions/ADR-002-versioned-configuration-bundle.md
  - decisions/ADR-005-flatten-inheritance-at-publish-time.md
  - decisions/ADR-014-local-transaction-outbox-inbox.md
  - decisions/ADR-018-configuration-schema-and-expression-runtime.md
---

# M16 配置发布解析与 BYD 工单接入事务切片

## 1. 目标

M16 关闭两个相互依赖的断点：

1. BYD CPIM 入站端点此前完成验签、DTO 校验、Nonce 防重放和统一映射后直接返回成功，没有创建工单；
2. 工单表此前没有 `tenant_id`、`project_id` 和 `configuration_bundle_id`，无法证明多租户隔离与配置版本锁定。

本切片建立最小配置发布/解析运行时，并把一次合法 BYD 请求串为单一 PostgreSQL 本地事务：

```text
验签
→ 稳定载荷摘要
→ CPIM DTO/试点范围校验
→ 解析唯一 Published ConfigurationBundle
→ Nonce 防重放
→ ReceiveExternalWorkOrder
→ 锁定 tenant/project/bundle 精确引用
→ 保存响应摘要
→ 提交
```

任一未捕获故障会回滚 Nonce 和工单写入，不留下“接口已接受但工单不存在”的半成品。

## 2. 模块边界

### 2.1 configuration

新增 `configuration` Modulith 模块，只向其他模块暴露 `configuration::api`：

- `publishAsset`：发布不可变配置资产版本；
- `publishBundle`：把同租户、已发布、类型不重复的资产版本组成配置包；
- `resolve`：按 tenant、active project、brand、service product、province 和时间解析唯一配置包。

解析优先级：

```text
省份精确配置 > province 为空的通配配置
```

同一优先级零命中或多命中均失败关闭。发布使用 PostgreSQL transaction advisory lock 串行化相同 scope，并拒绝有效期重叠。

### 2.2 integration

`integration` 只依赖 `configuration::api` 和 `workorder::api`，不写配置表或工单表。BYD 连接的 tenant/project 来自受控连接配置，不接受请求体伪造。

### 2.3 workorder

`workorder` 继续拥有外部业务键幂等和工单权威事实。业务键升级为：

```text
tenant_id + client_code + external_order_code
```

相同业务键、相同载荷和相同配置包返回原工单；载荷、项目或配置包冲突抛出明确的 `ExternalWorkOrderConflictException`。

## 3. 物理不变量

V013 创建：

- `cfg_configuration_asset_version`；
- `cfg_configuration_bundle`；
- `cfg_configuration_bundle_item`。

数据库触发器禁止 Published asset、bundle 和 item 原地 UPDATE/DELETE。Bundle 使用复合外键保证 tenant/project 与项目表一致；Bundle Item 同时用复合外键锁定 tenant、asset type、versionId 和 content digest，禁止跨租户或错类型拼装。

V014 同时支持空库首次建表和旧参考空表升级，并为 `wo_work_order` 增加：

- `tenant_id`；
- `project_id`；
- `configuration_bundle_id`；
- `(tenant_id, client_code, external_order_code)` 唯一键；
- `(tenant_id, project_id, configuration_bundle_id)` 复合外键。

旧参考表若已有数据，V014 拒绝猜测归属并中止迁移，必须先执行显式 tenant/project/bundle 回填方案。

## 4. 失败语义

| 场景 | API 结果 | 写入结果 |
|---|---|---|
| 签名错误 | `SIGNATURE_MISMATCH` | 不占 Nonce，不建工单 |
| DTO/试点校验失败 | `INVALID_ORDER` | 不占 Nonce，不建工单 |
| project 不可用 | `INVALID_ORDER` + `CONFIGURATION_PROJECT_NOT_ACTIVE` 消息前缀 | 不占 Nonce，不建工单 |
| Bundle 零命中 | `INVALID_ORDER` + `CONFIGURATION_NO_MATCH` 消息前缀 | 不占 Nonce，不建工单 |
| Bundle 多命中 | `INVALID_ORDER` + `CONFIGURATION_AMBIGUOUS_MATCH` 消息前缀 | 不占 Nonce，不建工单 |
| Nonce 不同载荷 | `REPLAY_CONFLICT` | 不改变原记录 |
| 外部业务键不同载荷 | `REPLAY_CONFLICT` + `ORDER_CONFLICT` 消息前缀 | 保留原工单，记录稳定拒绝结果 |
| 相同订单同载荷 | `REPLAYED` | 返回同一工单，不重复创建 |

## 5. 部署迁移

迁移入口、应用 Flyway locations 和 staging Gate 同步包含：

```text
configuration
integration
workorder
```

当前最高 versioned migration 为 `014`；包含两个 repeatable migration 时成功历史记录数为 `16`。发布必须同时核对版本和记录数。

## 6. 自动化证据

- `ArchitectureTest`：configuration/integration/workorder 依赖边界；
- `ConfigurationPublicationPostgresIT`：发布幂等、不可变、区域优先、重叠拒绝、跨租户负向；
- `WorkOrderCommandPostgresIT`：租户级业务幂等、冲突载荷、复合外键、重复迁移；
- `BydCpimInboundOrderHttpPostgresIT`：HTTP 到工单落库、Nonce/业务双重幂等、配置零命中和冲突载荷。

权威验收矩阵见 [M16 验收矩阵](../testing/13-m16-configuration-byd-work-order-intake-acceptance.md)。

## 7. 明确未证明

M16 不代表 E2 全部完成，仍未实现或证明：

- 配置 Draft/Review/Approval UI 与完整发布审批；
- 全部 Rule/SLA/Dispatch/Pricing/Integration Schema 及语义验证；
- ConfigurationBundle dependency closure 与运行中显式迁移；
- 原始报文长期保存、Inbox 与接入异常人工任务；
- `WorkOrderReceived`、Audit、Outbox、Stage/Task/Workflow 创建；
- BYD 更新、取消、暂停、恢复、审核结果与出站回传；
- 真实 CPIM sandbox、业务样本和业务负责人签署。

在这些证据形成前，不得把 M16 描述为“比亚迪勘安全流程完成”。
