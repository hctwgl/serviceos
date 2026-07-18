---
title: M245 Technician Portal Visit 历史安全摘要
status: Implemented
milestone: M245
lastUpdated: 2026-07-18
relatedMilestones: [M32, M159, M222, M243, M244]
---

# M245 Technician Portal Visit 历史安全摘要

## 目标与范围

在当前责任任务详情增加 Visit 生命周期安全历史：序次、状态、到离场时间、围栏分类、策略结论、结果/异常码
和聚合版本。Fieldwork SQL 不读取 GPS、距离、设备、离线命令、备注或引用。Core OpenAPI `1.0.19`；
Flyway 100/102；catalog v16。

## 已实现

- [x] Fieldwork 公开只读查询端口与白名单 DTO；
- [x] M243 当前责任门禁后按 tenant/task fan-in；
- [x] Admin Web 上门历史表；
- [x] PostgreSQL IT 以真实敏感列证明投影隔离；
- [x] MVC、契约、ArchitectureTest、build/E2E。

## 明确未实现

- GPS/距离/设备/note/operation/evidence refs 展示；
- check-in/check-out/interrupt 写命令与离线同步；
- 表单、Evidence、整改、MESSAGE/PROFILE。

## 事务与安全

查询只读；责任无法证明时统一 404 且不 fan-in。SQL 字段白名单、DTO 最小字段和 OpenAPI
`additionalProperties: false` 共同防止敏感 Visit 列旁路泄露。
