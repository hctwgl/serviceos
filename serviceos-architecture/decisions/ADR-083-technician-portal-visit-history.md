---
title: ADR-083：Technician Portal Visit 历史安全摘要
status: Accepted
date: 2026-07-18
---

# ADR-083：Technician Portal Visit 历史安全摘要

## 背景

M243～M244 已提供当前责任任务、预约和联系安全摘要。完整 `Visit` 含 GPS、围栏距离、设备、离线命令、
现场备注与作业/资料引用，不能直接复用到 Technician 详情。

## 决策

1. M243 详情增加 `visits[]`，不新增 HTTP；
2. Fieldwork 模块以 SQL 字段白名单返回 Visit ID、task/appointment、序次、生命周期状态、到离场时间、
   围栏分类/策略结论、受控结果/异常码和版本；
3. 禁止坐标、精度、距离、策略版本、设备、deviceCommandId、offline、note、operation/evidence refs；
4. 继续复用 M243 可信上下文、`task.readAssigned`、ACTIVE 当前责任与统一 404；
5. Core OpenAPI 升至 `1.0.19`；Flyway 100/102、Page Registry v16 不变；
6. 不接受 check-in/out/interrupt 写入；写命令仍需独立接受 GPS、设备、离线和实时责任策略。

## 后果

详情能展示是否到场、现场状态与权威版本，同时不会把敏感定位/设备事实扩散到 readmodel DTO。
