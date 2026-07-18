---
title: M263 Technician 在线基础表单交付批次验收矩阵
status: Implemented
milestone: M263
lastUpdated: 2026-07-18
---

# M263 Technician 在线基础表单交付批次验收矩阵

| 编号 | 场景 | 预期 | 证据 |
|---|---|---|---|
| M263-01 | Technician 命令边界 | 请求不接受 tenant/actor/submittedBy/prefillVersion | OpenAPI + MVC + client smoke |
| M263-02 | Context | ACTIVE Profile/网点关系与资源网点一致，伪造 Context 失败关闭 | PostgreSQL IT + MVC |
| M263-03 | 当前责任 | 非当前责任、改派、撤权或失效关系后不能读写表单 | service unit + Forms 既有授权测试 |
| M263-04 | 冻结版本 | 查询 Task 冻结 FormVersion，提交必须精确匹配 | Forms PostgreSQL IT + contract |
| M263-05 | 幂等与事务 | 重复 key 收敛；提交、审计、幂等结果、Outbox 原子提交 | Forms PostgreSQL IT |
| M263-06 | 基础类型 | H5/iOS 支持文本、整数、小数、布尔、日期和日期时间 | Playwright + Foundation smoke |
| M263-07 | 类型保持 | INTEGER/DECIMAL/BOOLEAN 不降级为字符串 | Playwright + Swift request assertion |
| M263-08 | 服务端校验 | 客户端展示字段键和稳定问题码，不伪造成功 | E2E + contract |
| M263-09 | 不支持规则 | 条件、validationRules、optionsRef、validators、未知类型禁止提交 | H5/iOS source + E2E |
| M263-10 | 无伪草稿 | 输入仅页面内存，不发送/声明 prefillVersion 或恢复能力 | E2E + Foundation smoke |
| M263-11 | iOS App | Simulator/device build 与 XCTest/XCUITest 通过 | `technician-ios-app` |
| M263-12 | 契约客户端 | OpenAPI 1.0.23 可校验、兼容并生成 TS/Swift 客户端 | contract/client gates |
| M263-13 | 模块/全量 | Forms 公开 API 依赖合法且最终 L3 通过 | ArchitectureTest + `verify-local.sh` |

## 明确未验收

本矩阵只覆盖 MCP-ONLINE-05 的基础在线切片，不代表完整动态表单或草稿完成。可移植条件求值、远程选项、
高级控件、预填/草稿冲突、Evidence、整改、离线恢复、签名真机和真实 IdP 仍未验收。
