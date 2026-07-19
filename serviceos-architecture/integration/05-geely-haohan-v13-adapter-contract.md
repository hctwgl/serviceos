---
title: 吉利浩瀚家充 V1.3 适配契约（本地切片）
status: Partial
lastUpdated: 2026-07-19
---

# 吉利浩瀚家充 V1.3 适配契约（本地切片）

权威协议输入：`serviceos-architecture/docs/吉利接口文档V1.3.pdf`（V1.3，2026-04-15）。

## 已落地（M311 本地可测）

| 能力 | ServiceOS 入口 | 说明 |
|---|---|---|
| AES-128-CBC/PKCS5Padding | `GeelyAesCipher` | 密钥=AK 前 16 字节；IV=浩瀚分配；文档 4.5.2 示例密文可解密 |
| 7.1 安装单创建通知 | `POST /api/v1/integrations/geely/haohan/v1.3/notify_create_order` | 解密 → Canonical → `InboundCreateWorkOrderPipeline` |
| 7.18 用户信息更新 | `POST .../notify_update_order_info` | → `InboundUpdateWorkOrderPipeline`（M314） |
| 7.17 安装单关闭 | `POST .../notify_close_order` | → `InboundCancelWorkOrderPipeline`（M314） |
| 提审出站（本地 stub） | `integration.geely.submit-settlement` | AES 包装 + LOCAL_ACCEPT 技术 ACK（M316） |
| 7.13 核销审核回调 | `POST .../notify_settlement_audit_result` | → `InboundReviewCallbackItemPipeline`（M316） |
| 映射版本 | `geely-haohan-v1.3-create-order-v1` | `installProcessNo` → externalOrderCode；省市区/联系人/地址/VIN |

## BLOCKED_EXTERNAL / TBD_EXTERNAL_CONTRACT

- 吉利 OpenAPI 平台统一签名 / SDK（https://openservice.geely.com/guide）
- Sandbox URL 正式联调凭据、生产 AK/SK、邮件分配 IV（文档示例仅用于契约测试）
- 7.2～7.22 其余出向/进向接口（分配师傅、首联、材料、核销、取消、关闭、查询等）
- 真实脱敏报文包与错误码联调矩阵
- 不得标记「真实吉利全链路 IMPLEMENTED」

## 字段映射（7.1 → Canonical）

| 吉利字段 | Canonical / 领域 | 备注 |
|---|---|---|
| installProcessNo | externalOrderCode / businessKey | 改派会换新单号 |
| workNo | 审计保留 | 不得作唯一键 |
| province/city/district | province/city/districtCode | 行政区划 |
| contactName/Phone | customerName/Mobile | 安装联系人 |
| address | serviceAddress | |
| carBrand | brandCode | 缺省 `GEELY` |
| vin | vehicleVin | 可选 |
| assignProviderTime | dispatchedAt | `yyyy-MM-dd HH:mm:ss` |
| （固定） | serviceProductCode=`HOME_CHARGING_SURVEY_INSTALL` | 待 Bundle 配置覆盖 |
| （固定） | clientCode=`GEELY` | |

## 本地验证

```bash
bash scripts/agent-verify.sh test GeelyAesCipherTest,GeelyCreateOrderMapperTest
bash scripts/agent-verify.sh it GeelyInboundCreateOrderPostgresIT
```
