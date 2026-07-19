---
title: ServiceOS 自主 Agent 交接
lastUpdated: 2026-07-19
---

# ServiceOS 自主 Agent 交接

## 当前

- PR：https://github.com/hctwgl/serviceos/pull/147
- latestMilestone：**M311**
- Flyway：**118 / 120**；OpenAPI：**1.0.40**
- 程序主线 P0～P2 完成；P3 首刀（M310）完成；P4 本地吉利切片（M311）完成

## BLOCKED_EXTERNAL（不可替代）

- 吉利 Sandbox URL / 生产 AK·SK·IV / OpenAPI 平台统一签名联调
- Swift/Xcode、签名真机、远端 verify.yml

## 已完成摘要

- P0 M296 基线；P1 Connector SPI M297～M302
- P2 六类运行时 M303～M309；P3 条件积木 M310
- P4 吉利本地 AES+7.1 建单 M311（真实联调仍阻塞）

## 下一步（有外部材料时）

- 吉利 7.2+ 接口与 Sandbox 联调里程碑
- 或继续 P3 设计器属性面板/小地图/撤销重做
