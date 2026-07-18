---
title: M271 标准家充勘测安装配置模板
status: Implemented
milestone: M271
lastUpdated: 2026-07-18
relatedMilestones: [M16, M268, M269, M270]
---

# M271 标准家充勘测安装配置模板

## 目标

交付平台中立的家充勘测安装 Workflow + SLA 模板，使用 EXCLUSIVE_GATEWAY 与 WAIT_EVENT，并可发布为 ConfigurationBundle 冒烟推进。

## 已实现

- `configuration/templates/home-charging-survey-install/`（架构源）与 backend classpath 内嵌；
- 无车企协议 DTO；Gateway 按 `serviceProductCode` 分支；两处 WAIT_EVENT；
- 漂移门禁 + 发布/冒烟 IT：受理 → 勘测 → 等待确认 → 安装 → 等待车企 ACK → END/FULFILLED。

## 明确未实现

完整 FORM/EVIDENCE 模板包、Admin 模板 UI、真实 OEM 回传、计价。

## 工程证据

- `ConfigurationSchemaDriftTest`
- `HomeChargingSurveyInstallTemplatePostgresIT`
