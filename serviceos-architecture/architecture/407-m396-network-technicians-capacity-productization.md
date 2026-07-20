---
title: M396 Network 师傅与产能产品化
status: Implemented
milestone: M396
lastUpdated: 2026-07-20
relatedMilestones: [M204, M206, M208, M390, M392]
openapiVersion: "1.0.64"
flywayVersion: "138"
---

# M396 Network 师傅与产能产品化

## 已实现

1. 师傅列表：SummaryStrip、中文状态、关系详情深链、可折叠关系/资质管理；
2. 产能页：SummaryStrip、占用/可用/已满、负载条；
3. 诚实登记技能/任务量/产能申请写模型 UI_DATA_GAP；
4. Playwright 1440 截图；产品状态 `READY_FOR_REVIEW`。

## 明确未实现

- 技能、服务区域、当前任务量、资质到期提醒专用读模型；
- 产能调整申请写流程；
- 人工视觉批准。

## 环境说明

本轮优先检查 Technician iOS 离线闭环，但当前 Linux 云环境无 Xcode/`xcodebuild`，无法交付 iOS Simulator/真机证据，故切换为本 Network 产品化切片。
