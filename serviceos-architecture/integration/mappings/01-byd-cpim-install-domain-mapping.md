---
title: 比亚迪 CPIM 安装领域映射
version: 0.1.0
status: Proposed
owner: Integration Architecture
source_documents:
  - docs/比亚迪接口文档V7.3.1.pdf
related:
  - integration/01-byd-cpim-v731-adapter-contract.md
---

# 比亚迪 CPIM 安装领域映射

## 1. 映射原则

CPIM 字段属于外部契约，ServiceOS 字段属于统一领域模型。两者必须通过版本化映射资产关联，禁止领域模型直接使用车企命名作为核心字段名。

映射类型：

- `DIRECT`：语义一致，直接转换；
- `ENUM`：外部编码映射统一枚举；
- `COMPOSE`：多个外部字段组合；
- `SPLIT`：一个字段拆成多个领域属性；
- `REFERENCE`：解析到主数据引用；
- `EVIDENCE`：转换为资料项引用；
- `DERIVED`：由规则计算；
- `RAW_ONLY`：保留原始值，不进入核心领域。

## 2. 安装订单入站映射

| CPIM 字段 | ServiceOS 路径 | 类型 | 规则 |
|---|---|---|---|
| `orderCode` | `workOrder.externalReferences[BYD].orderCode` | DIRECT | 车企维度业务唯一键 |
| `contactName` | `customer.primaryContact.name` | DIRECT | 按隐私级别 PII 标记 |
| `contactMobile` | `customer.primaryContact.mobile` | DIRECT | 标准化国家码并加密存储 |
| `contactAddress` | `serviceLocation.address.detail` | DIRECT | 不包含省市区 |
| `provinceCode` | `serviceLocation.region.externalProvinceCode` | REFERENCE | 映射统一行政区主数据 |
| `provinceName` | `serviceLocation.region.provinceName` | DIRECT | 试点必须为山东省 |
| `cityCode` | `serviceLocation.region.externalCityCode` | REFERENCE | 映射统一城市编码 |
| `cityName` | `serviceLocation.region.cityName` | DIRECT | 需与编码一致 |
| `areaCode` | `serviceLocation.region.externalDistrictCode` | REFERENCE | 映射统一区县编码 |
| `areaName` | `serviceLocation.region.districtName` | DIRECT | 需与编码一致 |
| `wallboxName` | `serviceAsset.requestedProduct.name` | DIRECT | 不代表已绑定实际资产 |
| `wallboxPower` | `serviceAsset.requestedProduct.ratedPower` | DIRECT | 需解析数值和单位 |
| `bringWallbox` | `fulfillmentPreferences.bringWallbox` | ENUM | `1=true`, `0=false` |
| `dispatchTime` | `workOrder.externalDispatchedAt` | DIRECT | 解析为带时区时间 |
| `carOwnerType` | `commercialContext.orderCategory` | ENUM | 10/20/30/40 映射统一分类 |
| `type` | `commercialContext.financialOwnership` | ENUM | 权益、PLUS、自费、对公 |
| `carBrand` | `vehicle.brand` | ENUM | 试点固定 `40=BYD_OCEAN` |
| `carSeries` | `vehicle.series` | DIRECT | 可空 |
| `carModel` | `vehicle.model` | DIRECT | 可空 |
| `vin` | `vehicle.vin` | DIRECT | 标准化大写；格式校验 |
| `contactRemark` | `workOrder.customerRemark` | DIRECT | 最大长度按内部模型控制 |
| `gunLineLength` | `serviceAsset.requestedProduct.gunCableLength` | DIRECT | 解析数值和单位 |
| `deliveryDate` | `vehicle.deliveryDate` | DIRECT | CPIM 为日期时间格式 |
| `dealerName` | `commercialContext.dealer.name` | DIRECT | 可空 |
| `salesStoreProvince` | `commercialContext.dealer.province` | DIRECT | 字段名以文档实际版本为准 |
| `rightCode` | `commercialContext.benefitCode` | DIRECT | 可空 |
| `orderAmount` | `commercialContext.externalOrderAmount` | DIRECT | Decimal；不直接作为结算权威 |
| `source` | `workOrder.source.channelCode` | ENUM | 保留外部原值与统一渠道 |
| `channel` | `workOrder.source.channelDetail` | RAW_ONLY | 需业务确认枚举 |
| `riskGrade` | `risk.current.externalGrade` | ENUM | 转统一风险等级，保留细分编码 |
| `riskReason` | `risk.current.reason` | DIRECT | 更新生成风险事件 |

## 3. 勘测回传映射

| CPIM 字段 | ServiceOS 路径 | 类型 | 关键规则 |
|---|---|---|---|
| `operatePerson` | `survey.submission.operatorDisplayName` | DIRECT | 实际操作者 ID 由内部上下文确定 |
| `orderCode` | `workOrder.externalReference` | DIRECT | 必须命中唯一工单 |
| `plotName` | `survey.site.communityName` | DIRECT | 可空 |
| `publicChargingStation` | `survey.site.publicStationInfo` | DIRECT | 可空 |
| `propertyName` | `survey.property.name` | DIRECT | 可空 |
| `propertyContact` | `survey.property.contactName` | DIRECT | PII |
| `propertyTelephone` | `survey.property.contactMobile` | DIRECT | PII |
| `needStake` | `survey.requirements.stakeRequired` | ENUM | `1=true`, `0=false` |
| `carportStatus` | `survey.site.parkingSpaceType` | ENUM | 1/2/3/4/5/10 |
| `housingType` | `survey.site.housingType` | ENUM | 非自提桩时按接口要求必填 |
| `emeterRequestProgress` | `survey.power.meterApplicationRequired` | ENUM | 非自提桩时按接口要求必填 |
| `emeterRequestCompletedTime` | `survey.power.meterApplicationCompletedAt` | DIRECT | 需早于安装完成时间 |
| `surveyCompletedTime` | `survey.completedAt` | DIRECT | 必填 |
| `surveyResult` | `survey.result` | ENUM | `1=INSTALLABLE`, `2=NOT_INSTALLABLE` |
| `surveyRemark` | `survey.remark` | DIRECT | 可空 |
| `agreementImage` | `evidence[BYD_SURVEY_AGREEMENT]` | EVIDENCE | 不符合条件仍坚持安装时 |
| `selfPick` | `survey.selfPickup.enabled` | ENUM | `1=true`, `0=false` |
| `wallboxCode` | `survey.selfPickup.wallboxCode` | DIRECT | 自提时必填 |
| `selfPickImage` | `evidence[BYD_SELF_PICK_APPLICATION]` | EVIDENCE | 自提时必填 |
| `pileSequenceImage` | `evidence[BYD_SELF_PICK_SERIAL_IMAGE]` | EVIDENCE | 自提时必填 |
| `drivingLicenseImage` | `evidence[BYD_VEHICLE_CERTIFICATE]` | EVIDENCE | 自提时必填 |

## 4. 安装信息回传映射

| CPIM 字段 | ServiceOS 路径 | 类型 | 关键规则 |
|---|---|---|---|
| `operatePerson` | `installation.submission.operatorDisplayName` | DIRECT | 内部保留 actorId |
| `orderCode` | `workOrder.externalReference` | DIRECT | 唯一命中 |
| `wallboxCode` | `installedAsset.serialNumber` | DIRECT | 与资料 OCR 结果核对 |
| `installationCompletedTime` | `installation.completedAt` | DIRECT | 必须晚于电力报装完成时间 |
| `installationCode` | `installation.verificationCode` | DIRECT | 可空 |
| `powerSupplyMethod` | `installation.powerSupplyMethod` | ENUM | 国网、物业、入户、其他 |
| `cableBrand` | `installation.materials.cable.brand` | ENUM | 包含桂林国际、恒飞、自布线、联嘉祥、万马、其他 |
| `cableType` | `installation.materials.cable.specification` | DIRECT | 可空 |
| `cableLength` | `installation.materials.cable.lengthMeters` | DIRECT | 正整数 |
| `breakerBrand` | `installation.materials.breaker.brand` | DIRECT | 可空 |
| `breakerType` | `installation.materials.breaker.model` | DIRECT | 可空 |
| `installStake` | `installation.constructionItems.stake.installed` | ENUM | 文档版本需确认 0/1 或 1/2 编码 |
| `installProtectingBox` | `installation.constructionItems.protectingBox.installed` | ENUM | 按 V7.3.1 最终定义映射 |
| `groundElectrode` | `installation.constructionItems.groundElectrode.installed` | ENUM | 注意文档出现 `1否/2是` |
| `frontEndCable` | `installation.materials.frontEndCable.material` | ENUM | 1铜、2铝、9其他 |
| `attachmentSource` | `installation.submission.attachmentSource` | ENUM | 手持端、系统、其他 |

## 5. 安装资料映射

| CPIM 字段 | Evidence Code | 必传条件 |
|---|---|---|
| `constructionImage` | `BYD_CONSTRUCTION_CABLE` | 固定必传 |
| `sequenceImage` | `BYD_WALLBOX_SERIAL` | 固定必传；OCR 桩 SN |
| `zeroVoltageImage` | `BYD_POWER_ZERO_VOLTAGE` | 固定必传 |
| `disclaimersImage` | `BYD_POWER_APPLICATION_WAIVER` | 自布线或不做电力报装时 |
| `loadConfirmationImage` | `BYD_PEER_LOAD_CONFIRMATION` | 不做电力报装时 |
| `lineStartImage` | `BYD_CABLE_START` | 固定必传 |
| `lineEndImage` | `BYD_CABLE_END` | 固定必传 |
| `fireZeroResistanceImage` | `BYD_BREAKER_UPSTREAM_INSULATION` | 固定必传 |
| `zeroGroundVoltageImage` | `BYD_BREAKER_DOWNSTREAM_ZERO_GROUND` | 固定必传 |
| `groundWireImage` | `BYD_GROUNDING` | 固定必传 |
| `manPileImage` | `BYD_PERSON_WALLBOX_PHOTO` | 固定必传 |
| `increaseChargeImage` | `BYD_INCREMENT_CHARGE_FORM` | 文档标为必传；业务需确认无增项时口径 |
| `confirmationImage` | `BYD_INSTALL_CONFIRMATION` | 固定必传 |
| `trialChargeImage` | `BYD_TRIAL_CHARGE` | 固定必传 |
| `pileNameplateImage` | `BYD_WALLBOX_NAMEPLATE` | 固定必传；OCR SN/型号 |
| `image1` | `BYD_OTHER_IMAGES` | 可选，最多三张，逗号分隔 |

单文件大小按文档上限 10MB。外部 URL/短链接只属于 Integration DTO，内部 Evidence 保存受控文件引用。

## 6. 网点与施工人员映射

| CPIM 字段 | ServiceOS 路径 | 规则 |
|---|---|---|
| `issuedBranchTime` | `assignment.branchAssignedAt` | 无数据不得用字符串 `NO` 进入领域层 |
| `branchCode` | `serviceNetwork.externalBranchCode` | 映射网点主数据 |
| `branchName` | `serviceNetwork.nameSnapshot` | 仅快照 |
| `personnelNumber` | `technician.externalPersonnelNumber` | 映射师傅主数据 |
| `personnelName` | `technician.nameSnapshot` | PII |
| `personnelPhoneNumber` | `technician.mobileSnapshot` | PII |
| `electricianCertificateNumber` | `technicianQualification.certificateNumber` | 必须校验证书有效性 |
| `electricianCertificateImage` | `technicianQualification.evidenceRef` | 文件安全规则适用 |
| `expirationDateBegin` | `technicianQualification.validFrom` | 日期 |
| `expirationDateEnd` | `technicianQualification.validTo` | 派单/上门时不得过期 |

## 7. 审核结果映射

CPIM 厂端审核结果：

| 外部值 | 领域事件 |
|---|---|
| `result=1` | `ClientReviewApproved` |
| `result=2` | `ClientReviewRejected` |

`remark` 转为外部驳回原因快照，不直接覆盖内部标准驳回原因；客服协调任务负责把外部原因映射到内部整改项。

批量回调中 `orderCode` 可能以逗号分隔且单次最多 100 个。适配器必须拆分为逐订单 Inbox 项，保证单订单失败不影响其他订单。

## 8. 取消、暂停、恢复和关闭

- 用户取消是外部取消请求，不等价于直接删除工单；
- 暂停必须创建暂停事实并交给 SLA 模块判断是否停钟；
- 恢复必须关联最近一次有效暂停；
- 外部关闭如果与内部状态冲突，进入人工接管，不得强制覆盖；
- 所有操作保留外部操作者、发生时间和原始原因。

## 9. 待业务确认

1. `installStake`、`installProtectingBox` 的最终编码以 V7.3.1 变更页还是接口表为准；
2. `increaseChargeImage` 在无增项场景是否允许固定占位或免传；
3. 海洋山东试点具体省编码、城市编码与 ServiceOS 主数据对应关系；
4. 订单基础信息接口在最新版本是否已直接包含 `riskGrade/riskReason`，还是仅由 2.14 单独推送；
5. 附件在 V7.3.1 是否统一先调用上传接口得到短链接；
6. 厂端审核拒绝后是否允许只整改部分资料，回传时是否必须重新全量提交。
