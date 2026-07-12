---
title: M1-03 字段与资料矩阵模板
version: 0.1.0
status: Draft
---

# M1-03 字段与资料矩阵模板

## 字段目录

| 标准字段编码 | 中文名称 | 业务对象 | 类型/单位 | 来源 | 填写节点 | 必填条件 | 可编辑角色 | 敏感等级 | 外部映射 |
|---|---|---|---|---|---|---|---|---|---|
| customer.mobile | 联系电话 | Customer | 手机号 | 车企 | 接单 | 始终 | 客服 | 高 | TBD |
| vehicle.vin | VIN | Vehicle | 文本 | 车企/现场 | 接单/施工 | 按项目 | 客服/师傅 | 高 | TBD |
| installation.cableLength | 实际线缆长度 | Installation | Decimal/米 | 现场 | 安装 | 安装完成 | 师傅 | 普通 | TBD |

## 资料要求

| 资料编码 | 名称 | 节点 | 出现条件 | 必传 | 采集方式 | 水印/GPS | OCR/一致性 | 审核原因库 | 外部映射 |
|---|---|---|---|---:|---|---|---|---|---|
| charger.nameplate | 充电桩铭牌 | 安装 | 始终 | 是 | 现场拍摄 | TBD | OCR SN 并核对 | TBD | TBD |
| installation.pillar | 立柱安装照片 | 安装 | 是否安装立柱=是 | 是 | TBD | TBD | TBD | TBD | TBD |
| installation.extraCharge | 增项收费单 | 安装 | 产生增项=是 | 是 | TBD | TBD | OCR 金额/签名 | TBD | TBD |

## 驳回原因库

| 原因编码 | 适用资料 | 原因名称 | 是否需要说明 | 整改要求 | 适用项目 |
|---|---|---|---:|---|---|
| IMAGE.BLUR | 图片类 | 图片模糊 | 否 | 重新清晰拍摄 | TBD |
| IMAGE.WRONG_SN | 铭牌/SN | SN 与工单不一致 | 是 | 核对设备并重拍 | TBD |

## 表单规则

| 规则编码 | 节点 | 条件 | 动作 | 错误提示 | 来源 |
|---|---|---|---|---|---|
| TBD | 安装 | 是否安装立柱=是 | 立柱数量必填且资料必传 | TBD | TBD |
