---
title: M437 Admin 工单目录按区域筛选
version: 0.1.0
status: Implemented
milestone: M437
lastUpdated: 2026-07-21
relatedMilestones: [M430, M431, M436]
openapiVersion: "1.0.99"
---

# M437 Admin 工单目录按区域筛选

## 1. 目标

关闭 Admin `ADMIN.WORKORDER.LIST` UI_DATA_GAP「按区域筛选」：目录查询支持省/市/区县国标码精确过滤。

## 2. 已实现

| 层 | 内容 |
|---|---|
| OpenAPI | **1.0.99**：`provinceCode` / `cityCode` / `districtCode` 可选 query |
| Backend | SQL AND 精确匹配；写入 cursor filterDigest；非法码 400 |
| Admin Web | 更多筛选「服务区域」Select（region-catalog）；按级别映射参数 |
| 证据 | PostgresIT + MVC + Playwright |

## 3. 边界

- 精确码匹配，不在服务端做名称模糊/拼音
- 多码 AND；UI 一次只选一个码并映射到对应级别参数
- 网点/师傅/阶段/SLA 筛选仍未交付
- 无 Flyway、无新 capability

## 4. 明确未实现

- 网点/师傅/阶段/SLA/创建时间筛选
- 区域级联多选 / 全国区县全量树
- 产品负责人视觉金标（`READY_FOR_REVIEW`）
