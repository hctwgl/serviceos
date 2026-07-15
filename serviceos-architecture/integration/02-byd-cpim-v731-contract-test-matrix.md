---
title: BYD CPIM V7.3.1 报文契约测试矩阵
version: 0.1.0
status: Proposed
owner: Integration Architecture
---

# BYD CPIM V7.3.1 报文契约测试矩阵

## 1. 目标

本矩阵用于验证“比亚迪海洋 + 山东省 + 勘安”试点中的入站和出站报文。JSON Schema 只负责单报文结构校验，跨报文条件、幂等、状态和安全要求由契约测试补充。

## 2. Schema 与接口

| Schema | CPIM 接口 | 方向 |
|---|---|---|
| `byd-cpim-install-order-v731.schema.json` | 安装订单推送 | CPIM → ServiceOS |
| `byd-cpim-survey-v731.schema.json` | `/jumpto/openapi/sp/pushSurveyInfo` | ServiceOS → CPIM |
| `byd-cpim-installation-v731.schema.json` | `/jumpto/openapi/sp/pushInstallationInfo` | ServiceOS → CPIM |
| `byd-cpim-accessories-v731.schema.json` | `/jumpto/openapi/sp/pushAccessoriesInfo` | ServiceOS → CPIM |
| `serviceos-contracts/.../external/byd-cpim-review-callback-v731.schema.json` | 厂端审核结果回调 | CPIM → ServiceOS |

## 3. 必测结构场景

| 编号 | 场景 | 预期 |
|---:|---|---|
| S-01 | 非自提桩缺少 `housingType` | Schema 拒绝 |
| S-02 | 非自提桩缺少 `surveyResult` | Schema 拒绝 |
| S-03 | 自提桩缺少桩编码或自提材料 | Schema 拒绝 |
| S-04 | 勘测不通过但无备注 | Schema 拒绝 |
| S-05 | 安装完成时间格式错误 | Schema 拒绝 |
| S-06 | `cableLength` 为负数或小数 | Schema 拒绝 |
| S-07 | 审核拒绝但无 `remark` | Schema 拒绝 |
| S-08 | 审核回调超过 100 个订单号 | Schema 拒绝 |
| S-09 | `image1` 超过三张 | Schema 拒绝 |
| S-10 | 出现未声明字段 | Schema 拒绝 |

## 4. 跨报文业务规则

以下规则不能只依赖单文件 Schema：

1. `orderCode` 必须对应已接收且处于可回传状态的工单；
2. 安装回传的 `wallboxCode` 必须与资料 OCR/人工确认的桩编码一致；
3. `cableBrand = 3`（自布线）时，按接口文档要求校验免责资料；
4. `emeterRequestProgress = 0` 时，按接口文档要求校验免责资料和同级负载确认书；
5. `increaseChargeImage` 是否必传必须以业务确认后的条件规则为准；在确认前不得使用伪造占位图进入生产；
6. 附件 URL 必须来自已完成病毒扫描、授权校验且可在有效期内被 CPIM 拉取的对象；
7. 审核回调重复到达时，不得重复创建整改任务；
8. 审核结果从通过变为拒绝或发生冲突时，必须进入人工核查并保留原始回调；
9. 运行中工单必须锁定 `adapterVersion`、`mappingVersion` 和 `configurationBundleId`；
10. 所有出站请求必须使用 Outbox，所有入站回调必须使用 Inbox 去重。

## 5. 安全与签名测试

- APP_KEY 不存在或未授权时拒绝；
- 同 APP_KEY/Nonce/Cur_Time 且同载荷时返回首次结果，不重复领域副作用；不同载荷拒绝；
- `Cur_Time` 不是 `yyyy-MM-dd` 或不等于协议时区当前日期时拒绝；
- 参数排序或签名不一致时拒绝；
- 日志不得记录 AppSecret、完整手机号、详细地址和可长期访问的附件 URL；
- 原始报文加密留存并设置保留期和访问审计。

## 6. 错误与恢复测试

| 场景 | 处理 |
|---|---|
| 网络超时、5xx | 指数退避重试，保留同一业务幂等键 |
| `errno != 0` 且为业务状态错误 | 不无限重试，创建人工处理任务 |
| `partially success` | 按失败 `orderCode` 拆分重试，不重发成功订单 |
| 附件 URL 过期 | 重新签发短期 URL 后重试，保留资料版本 |
| 回调落库成功但流程推进失败 | 由 Inbox/事件重放恢复，不能要求 CPIM 人工重推 |

## 7. 待确认并阻塞生产的事项

- `installStake`、`installProtectingBox` 的正式枚举口径；
- 无增项时 `increaseChargeImage` 的实际传值规则；
- 安装附件是否已统一切换到短链接机制；
- 厂端驳回后需要增量补传还是全量重新提交；
- 山东省正式省、市、区编码版本；
- 生产 Nonce 保留期与凭据轮换方案（当前工程保留 48 小时 replay 事实）。
