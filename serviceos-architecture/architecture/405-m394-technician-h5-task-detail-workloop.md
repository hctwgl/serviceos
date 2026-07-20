---
title: M394 Technician H5 任务详情作业闭环产品化
status: Implemented
milestone: M394
lastUpdated: 2026-07-20
relatedMilestones: [M243, M262, M263, M264, M265, M393]
openapiVersion: "1.0.64"
flywayVersion: "138"
---

# M394 Technician H5 任务详情作业闭环产品化

## 已实现

1. 作业步骤条：预约确认 → 到场签到 → 现场表单 → 资料上传 → 提交前检查；
2. 签到区块前置到表单/资料之前；
3. 提交前检查清单（预约/签到/表单/资料/可执行状态）；
4. 底部固定主操作（随当前步骤切换）；
5. 复用既有在线 Visit/Form/Evidence/Complete API；不伪造离线；
6. Playwright 390 截图；产品状态 `READY_FOR_REVIEW`。

## 明确未实现

- 客户/地址/导航正式读模型；
- 原生签退 FieldOperation；
- 离线草稿与后台上传；
- 人工视觉批准。
