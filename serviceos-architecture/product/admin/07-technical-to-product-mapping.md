---
title: Admin 技术能力到产品能力映射
version: 0.1.0
status: Proposed
lastUpdated: 2026-07-20
---

# Admin 技术能力到产品能力映射

本文件用于防止“后端有什么对象，前端就展示什么对象；后端有什么命令，页面就放什么按钮”的实现偏差。

## 1. 核心映射

| 技术能力/对象 | 产品表达 | 普通用户是否直接看到技术名 |
|---|---|---:|
| WorkOrder | 工单 | 否 |
| Task | 服务任务/审核任务/系统任务（按场景） | 否 |
| ReviewCase | 审核单/审核工作区 | 否 |
| ReviewTarget | 审核项 | 否 |
| CorrectionCase | 整改单/整改跟踪 | 否 |
| EvidenceSet/Revision | 资料包/资料版本 | 否 |
| Workflow | 履约流程/流程模板 | 高级配置可见 |
| ConfigurationAsset | 配置模板/配置版本 | 否 |
| FORM | 表单模板 | 高级配置可见编码 |
| EVIDENCE | 资料模板 | 高级配置可见编码 |
| SLA | 时效规则/SLA 模板 | 可见业务名，不显示内部编码 |
| CALENDAR | 业务日历 | 可见业务名 |
| DISPATCH | 派单规则 | 可见业务名 |
| INTEGRATION | 车企接口与字段映射 | 可见业务名 |
| Bundle | 发布版本/配置包 | 普通模式显示版本摘要 |
| allowed-actions | 当前可执行操作 | 不显示接口或 raw action code |
| Capability | 操作权限 | 权限管理页显示中文名，编码为次级信息 |
| Scope | 数据范围 | 显示租户/项目/区域/组织/网点范围 |
| Idempotency-Key | 防重复提交机制 | 否 |
| If-Match / aggregateVersion | 并发保护 | 否；冲突时显示业务提示 |
| correlationId | 问题编号 | 普通页显示问题编号，诊断页显示完整值 |
| checkpoint/asOf | 数据更新时间/数据可能延迟 | 原始值进入诊断 |
| SHADOW | 影子试算/非正式结果 | 显示固定“非正式”语义 |

## 2. 命令到按钮文案

| 技术命令/动作 | 产品按钮 |
|---|---|
| assign-candidates | 分配处理人/分配服务网点（按上下文） |
| claim-task | 领取任务 |
| reassign-network | 改派服务网点 |
| assign-technician | 分配服务师傅 |
| revise-scope-relations | 保存服务范围调整 |
| decide-review | 提交审核决定 |
| request-correction | 驳回整改/发起整改 |
| publish-configuration | 发布新版本 |
| suspend-work-order | 暂停工单 |
| cancel-work-order | 取消工单 |
| retry-delivery | 重新发送 |
| replay-integration | 重新处理（仅授权） |

按钮文案必须以当前业务对象和用户任务为上下文，不能机械地把 actionCode 翻译成通用“执行操作”。

## 3. 技术错误到用户反馈

| 技术情形 | 用户反馈 |
|---|---|
| 409 version conflict | 数据已被其他人更新；本地内容是否保留；重新加载/复制本地修改 |
| action not allowed | 当前状态暂不能执行；列出业务阻塞原因 |
| missing capability | 当前账号无权执行此操作 |
| scope denied | 当前数据不在你的查看或操作范围内 |
| projection lag | 数据可能存在短暂延迟；显示更新时间和刷新入口 |
| idempotency replay | 操作已提交，无需重复执行 |
| referenced asset missing | 配置引用已失效，请联系项目配置管理员 |
| no effective fulfillment revision | 当前项目和工单类型没有生效的履约方案，无法创建工单 |

普通界面必须说明数据是否已保存和下一步；技术错误码进入诊断。

## 4. 领域状态到产品状态

不能使用一套全局 `statusLabel(OPEN)` 猜测所有领域语义。必须按 WorkOrder、Task、Review、Correction、Evidence、SLA、Delivery、Pricing 分别转换。

示例：

- `Task.OPEN` → 待处理；
- `ReviewCase.OPEN` → 待审核；
- `CorrectionCase.OPEN` → 整改中；
- `Exception.OPEN` → 待处理异常；
- `Delivery.FAILED` → 发送失败；
- `Pricing.SHADOW` → 影子试算（非正式）。

## 5. 菜单映射

| 技术页面 | 产品归属 |
|---|---|
| Task Queue | 工作台/服务履约 → 服务任务 |
| Review Queue | 审核与整改 → 审核队列 |
| Correction Queue | 审核与整改 → 整改跟踪 |
| Exception Queue | 工作台或审计与监控 → 运营异常 |
| Configuration Assets | 配置中心 → 对应模板目录 |
| Integration Deliveries | 审计与监控 → 接口记录 |
| Authorization | 系统管理 → 用户/角色/权限/数据范围/委托 |
| Audit | 审计与监控 → 对应日志页面 |

不允许把所有配置资产放在一个面向普通用户的“配置资产”JSON 页面中；应按业务模板分类，并保留高级统一版本查询入口。

## 6. 数据缺口处理

后端缺少名称、摘要或聚合字段时：

1. 先检查已有目录/批量查询；
2. 不做逐行 N+1；
3. 不猜测；
4. 不显示完整 UUID 代替业务名称；
5. 使用明确空值文案；
6. 登记 `UI_DATA_GAP`；
7. 产品验收可标记“前端完成、数据能力阻塞”，但不得写整体已完成。

## 7. 高级模式和诊断模式

### 普通模式

业务名称、业务编号、中文状态、业务动作、范围和影响。

### 高级配置模式

assetKey、stageCode、fieldKey、binding、表达式、版本引用；仅项目配置/高级配置角色按能力可见。

### 诊断模式

API Path、correlationId、operationId、resourceVersion、checkpoint、raw enum 和请求摘要；仅开发环境或真实诊断能力可见。