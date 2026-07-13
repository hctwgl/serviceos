---
title: 值对象目录
version: 0.1.0
status: Proposed
---

# 值对象目录

值对象用于承载稳定业务语义、格式校验和相等性规则。它们没有独立生命周期，不单独作为聚合根，也不通过 Repository 持久化。

## 1. 设计原则

- 值对象创建后不可变；
- 相等性由全部业务字段决定；
- 构造时完成格式与范围校验；
- 禁止把所有字符串都包装成无语义类型；
- 只有具备稳定业务规则、复用价值或错误风险的字段才建模为值对象；
- 跨限界上下文共享时只共享稳定概念，不共享内部实现类；
- JSON、数据库和 Java 表达必须有明确映射。

## 2. 身份与外部引用

### 2.1 WorkOrderId

- 内部全局唯一标识；
- 推荐 UUID；
- 不从车企订单号推导；
- 不向用户承诺可读性。

### 2.2 ExternalOrderRef

```text
clientCode + externalOrderCode + sourceSystem
```

不变量：

- `clientCode` 与 `externalOrderCode` 必填；
- 同一客户端下外部订单号唯一；
- 外部订单号保留原始大小写和前导零；
- 不把外部订单号当内部主键。

### 2.3 ConfigurationBundleRef

```text
bundleCode + exactVersion + checksum
```

不变量：

- 必须精确到已发布版本；
- 工单创建后默认不可漂移；
- checksum 用于识别同版本内容被非法替换。

## 3. 客户与联系信息

### 3.1 PhoneNumber

- 保存规范化号码与原始展示值；
- 中国大陆手机号按项目规则校验，不在平台内写死全部国家规则；
- 日志和审计默认脱敏；
- 用于幂等或匹配时必须使用规范化值。

### 3.2 PersonName

- 保留原始姓名；
- 允许少数民族、英文、空格和中点；
- 不以固定两到四个汉字做强校验；
- 输出日志时按隐私级别脱敏。

### 3.3 ServiceAddress

建议结构：

```text
provinceCode
cityCode
districtCode
streetAddress
longitude?
latitude?
```

行政区划编码与文本地址必须分离；经纬度是可选事实，不得由文本地址自动视为精确定位。

## 4. 车辆与设备

### 4.1 Vin

- 按 17 位 VIN 基础规则校验；
- 统一大写；
- 禁止 I、O、Q；
- 对历史脏数据通过显式“未验证外部值”通道处理，不放宽正式值对象。

### 4.2 DeviceSerialNumber

- 保留品牌、设备类型和原始序列号；
- 可包含大小写、短横线和前导零；
- 是否全局唯一由设备上下文决定，不由值对象单独假设。

### 4.3 PowerRating

- 数值与单位分离；
- 推荐规范化为 kW；
- 禁止使用自由文本承载功率。

## 5. 时间与时效

### 5.1 TimeWindow

```text
startInclusive + endExclusive + timezone
```

不变量：结束时间必须晚于开始时间；所有外部时间必须保留来源时区或明确转换规则。

### 5.2 BusinessDuration

用于 SLA：

- duration；
- calendarCode；
- 是否排除夜间；
- 是否排除周末；
- 是否排除法定节假日。

不得只存一个裸分钟数后丢失计算口径。

### 5.3 OccurredAt

领域事件发生时间。与入库时间、发布时间、车企回调时间分离。

## 6. 金额与计价

### 6.1 Money

```text
amount + currency
```

不变量：

- 使用十进制定点数；
- 禁止 double/float；
- 对上、对下金额不可因币种相同而混用；
- 舍入规则属于价格方案或计价上下文。

### 6.2 Quantity

```text
value + unit
```

适用于线缆米数、立柱数量、上门次数等。单位必须来自版本化字典。

### 6.3 PricingVersionRef

包含价格方案代码、精确版本、适用方向（RECEIVABLE/PAYABLE）和 checksum。

## 7. 资料与文件

### 7.1 EvidenceCode

- 在资料模板版本内唯一；
- 稳定英文编码；
- 展示名称可变化，编码不可因文案变化重命名。

### 7.2 FileRef

```text
fileId + checksum + mediaType + sizeBytes
```

不得在 Evidence 中保存临时上传 URL 作为长期引用。

### 7.3 GeoPoint

- 经度范围 [-180, 180]；
- 纬度范围 [-90, 90]；
- 可包含精度、定位来源和采集时间；
- 定位通过与否属于 ValidationResult，不属于坐标本身。

### 7.4 OcrResultRef

只保存 OCR 结果记录 ID、模型版本和关键摘要；完整结果由 Validation/Evidence 上下文维护。

## 8. 执行与责任

### 8.1 PrincipalRef

```text
principalType + principalId
```

支持用户、网点、师傅、系统执行器等主体。聚合只引用稳定 ID，不持有用户对象。

### 8.2 AssignmentScope

包含品牌、区域、业务类型和项目等作用域，用于说明责任分配的生效边界。

### 8.3 ReasonCode

- 来自版本化原因库；
- 代码和展示名称分离；
- 允许附加说明，但不能用自由文本替代标准原因；
- 必须记录原因库版本。

## 9. 结果与版本

### 9.1 PayloadDigest

- 默认 SHA-256 十六进制小写；
- 基于规范化请求内容计算；
- 相同业务报文字段顺序变化不应改变摘要；
- 不得将摘要误当加密或脱敏手段。

### 9.2 SchemaVersion

采用不可变正整数或语义版本，由具体契约约定；禁止使用 `latest` 作为运行中工单引用。

### 9.3 CorrelationId / CausationId

用于链路追踪，不承担业务唯一性。

## 10. 不应建模为值对象的内容

以下内容通常不适合作为值对象：

- 具有独立生命周期的 Task、EvidenceItem、ReviewCase；
- 需要独立查询和权限控制的实体；
- 仅为技术传输存在的 DTO；
- 没有稳定业务规则的任意字符串；
- 高频变化且属于配置资产的完整规则对象。

## 11. 实现与测试要求

每个值对象至少验证：

1. 合法构造；
2. 边界值；
3. 非法格式拒绝；
4. 相等性与 hashCode；
5. JSON 往返；
6. 数据库映射；
7. 日志脱敏（涉及 PII 时）。
