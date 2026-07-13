---
title: 比亚迪 CPIM V7.3.1 适配器契约
version: 0.1.0
status: Proposed
owner: Integration Architecture
reviewers:
  - BYD Business Owner
  - Product Architecture
  - Engineering Architecture
source_documents:
  - docs/比亚迪接口文档V7.3.1.pdf
---

# 比亚迪 CPIM V7.3.1 适配器契约

## 1. 目标

本文件定义 ServiceOS 与比亚迪 CPIM V7.3.1 在“海洋品牌、山东省、勘安业务”试点中的适配边界。CPIM DTO 不得直接进入领域模型；所有请求和回调必须先经过认证、验签、反重放、幂等、映射和业务校验。

## 2. 适配器边界

```text
CPIM
  ↓ HTTP/JSON
BYD CPIM Adapter
  ├── Authentication
  ├── Anti-Replay
  ├── Idempotency
  ├── DTO Validation
  ├── Canonical Mapping
  ├── Error Normalization
  └── Inbox/Outbox
  ↓ Commands / Events
ServiceOS Domain
```

禁止：

- 领域对象保存 CPIM 原始枚举作为唯一语义；
- 业务模块直接调用 CPIM URL；
- Controller 直接推进工单状态；
- 收到重复回调时重复创建任务或重复推进流程；
- 将 APP_KEY、AppSecret 或签名明文写入日志。

## 3. 认证与签名

CPIM 请求头包含：

| Header | 说明 |
|---|---|
| `APP_KEY` | 服务商标识 |
| `Nonce` | 随机数 |
| `Cur_Time` | 当前日期，格式 `yyyy-MM-dd` |
| `Sign` | SHA-256 签名 |

签名原文按以下顺序构造：

```text
AppSecret & Nonce & Cur_Time & Params
```

其中 `Params` 为请求参数按 key 的 ASCII 顺序拼接后的字符串。

### 3.1 接收端要求

- 验证 APP_KEY 是否属于已启用连接；
- 验证签名；
- 以 `APP_KEY + Nonce + Cur_Time` 建立反重放记录；
- Nonce 重复且请求摘要不同必须拒绝；
- 原始请求摘要、验签结果、来源 IP、接收时间写审计；
- AppSecret 仅从 Secret Provider 获取。

### 3.2 时间窗口

文档仅给出日期级 `Cur_Time`，无法单独防止同日重放。因此 ServiceOS 必须额外依赖：

- Nonce 唯一性；
- 请求体摘要；
- 接口级业务幂等键；
- 可配置的反重放保留期，试点默认 48 小时。

## 4. 接口目录

### 4.1 CPIM → ServiceOS

| 接口语义 | 方向 | 领域命令/事件 | 幂等键 |
|---|---|---|---|
| 推送安装订单 | 入站 | `ReceiveExternalWorkOrder` | `BYD:INSTALL:{orderCode}` |
| 更新安装订单 | 入站 | `UpdateExternalWorkOrder` | `BYD:INSTALL-UPDATE:{orderCode}:{payloadHash}` |
| 厂端审核结果 | 入站 | `RecordClientReviewResult` | `BYD:REVIEW:{orderCode}:{result}:{examineDate}` |
| 用户取消订单 | 入站 | `RequestExternalCancellation` | `BYD:CANCEL:{orderCode}:{cancelDate}` |
| 关闭订单 | 入站 | `RecordExternalClosure` | `BYD:CLOSE:{orderCode}:{modifyDate}` |
| 暂停订单 | 入站 | `SuspendWorkOrder` | `BYD:SUSPEND:{orderCode}:{suspendDate}` |
| 恢复订单 | 入站 | `ResumeWorkOrder` | `BYD:RESUME:{orderCode}:{modifyDate}` |
| 风险信息 | 入站 | `UpdateWorkOrderRisk` | `BYD:RISK:{orderCode}:{riskGrade}:{payloadHash}` |
| 超时未安装取消请求 | 入站 | `CreateOvertimeCancellationReview` | `BYD:OVERTIME-CANCEL:{orderCode}` |

### 4.2 ServiceOS → CPIM

| 接口路径 | 语义 | 触发事件 | 失败策略 |
|---|---|---|---|
| `/jumpto/openapi/sp/pushContactInfo` | 联系信息回传 | `CustomerContactRecorded` | 指数重试→人工接管 |
| `/jumpto/openapi/sp/pushSurveyInfo` | 勘测信息回传 | `SurveyApprovedForClientSubmission` | 指数重试→客服处理 |
| `/jumpto/openapi/sp/pushInstallationInfo` | 安装信息回传 | `InstallationApprovedForClientSubmission` | 指数重试→客服处理 |
| `/jumpto/openapi/sp/pushAccessoriesInfo` | 安装附件回传 | `InstallationEvidenceApproved` | 分项校验→重试→人工处理 |
| `/jumpto/openapi/sp/pushSubmitReviewInfo` | 提交厂端审核 | `ClientReviewSubmissionRequested` | 指数重试→客服处理 |
| `/jumpto/openapi/sp/pushCancelReviewInfo` | 取消审核结果回传 | `CancellationReviewCompleted` | 指数重试→人工处理 |
| `/jumpto/openapi/sp/pushBranchRecord` | 网点与施工人员记录 | `TechnicianAssigned` 或资料齐备 | 指数重试→项目经理处理 |
| `/jumpto/openapi/sp/pushMovePileInstallSocket` | 挪桩/插座意向 | `CustomerIntentConfirmed` | 单向状态保护→人工处理 |
| `/jumpto/openapi/sp/io/pushOvertimeVerifyResult` | 超时未安装核验结果 | `OvertimeCancellationReviewCompleted` | 指数重试→人工处理 |

## 5. 统一响应规范

CPIM 文档存在两类成功响应：

```json
{"errno":0,"errmsg":"成功","data":null}
```

以及：

```json
{"message":"success"}
```

适配器必须归一化为：

```text
SUCCESS
PARTIAL_SUCCESS
BUSINESS_REJECTED
AUTHENTICATION_FAILED
TEMPORARY_FAILURE
PROTOCOL_ERROR
```

`partially success` 必须逐条记录失败订单，不能把整批视为成功。

## 6. 事务与可靠性

### 6.1 入站

1. 验签与反重放；
2. 写入 `integration_inbox`；
3. 校验 DTO；
4. 映射为领域命令；
5. 在同一事务内记录命令接收结果；
6. 返回 CPIM 规定响应；
7. 重复请求返回前次结果，不重复执行。

### 6.2 出站

1. 领域事件写 Outbox；
2. Adapter 构造 CPIM DTO；
3. 记录请求摘要与幂等键；
4. 调用 CPIM；
5. 归一化响应；
6. 成功确认；
7. 临时失败重试；
8. 业务拒绝转人工任务。

## 7. 日志与隐私

必须脱敏：联系人姓名、手机号、详细地址、VIN、桩 SN、证件照片 URL。

允许日志记录：orderCode、接口代码、请求摘要、响应分类、errno、errmsg 摘要、correlationId、attempt、耗时。

## 8. 版本策略

- Adapter 版本：`byd-cpim-v7.3.1`；
- DTO Schema 独立版本化；
- 字段新增默认向后兼容；
- 枚举未知值映射为 `UNKNOWN` 并告警；
- 文档升级不得覆盖旧映射，创建新 Adapter 版本；
- 运行中工单继续锁定原 Integration Asset 版本。

## 9. 试点范围

首期仅启用：

- `carBrand = 40`（海洋）；
- `provinceName = 山东省` 或经主数据映射后的山东省编码；
- 安装订单、联系、勘测、安装、附件、提审、审核结果、取消、暂停、恢复、更新、网点记录和风险信息；
- 结算接口不作为首期正式财务权威。

## 10. 验收条件

- 同一订单重复推送不会创建重复工单；
- 同一 Nonce 不可复用；
- 未知枚举不会导致数据静默丢失；
- 部分成功响应可定位具体失败订单；
- CPIM 业务拒绝不会无限重试；
- 所有出站请求可关联到领域事件与工单时间线；
- 敏感字段不进入普通应用日志；
- 断网恢复后 Outbox 能继续发送且不重复推进业务。
