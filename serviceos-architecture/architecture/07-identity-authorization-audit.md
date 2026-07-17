---
title: 身份、授权与审计设计
version: 0.1.0
status: Proposed
---

# 身份、授权与审计设计

## 1. 目标

ServiceOS 的权限不是“角色对应菜单”。一次业务动作必须同时满足身份有效、角色能力、数据范围、流程动作、字段权限和高风险控制。

审计必须回答谁在什么身份和数据范围下，基于哪个配置版本，对哪个业务对象执行了什么动作，结果和前后差异是什么。

## 2. 授权模型

```text
允许 = 身份有效
   AND 具备 Capability
   AND 命中 DataScope
   AND 当前任务允许 Action
   AND 字段/资料权限满足
   AND 高风险控制通过
```

任何一层拒绝均返回稳定原因码。前端可查询允许动作改善体验，但服务端命令执行时必须重新鉴权。

## 3. 核心对象

| 对象 | 职责 |
|---|---|
| `Principal` | 当前操作主体，通常是用户，也可以是受信系统账号 |
| `User` | 人员身份和登录关联 |
| `Organization` | 总部、区域、网点等组织层级 |
| `Role` | 业务岗位能力集合，如项目经理、客服、师傅 |
| `Capability` | 稳定操作能力，如 `workOrder.reassignNetwork` |
| `RoleGrant` | 用户在某组织/项目范围获得某角色的授权 |
| `DataScopePolicy` | 品牌、项目、区域、网点和工单关系过滤规则 |
| `FieldPolicy` | 字段和资料的查看、编辑、脱敏和导出规则 |
| `ActionPolicy` | 领域动作的状态、任务和风险约束 |
| `Delegation` | 临时代办和授权委托 |
| `AuditRecord` | 不可抵赖的操作审计记录 |

## 4. 角色与能力

角色只聚合能力，不直接硬编码页面。一个用户可以在不同范围拥有多个角色，例如在比亚迪山东是项目经理，在广汽项目只拥有运营只读角色。

首批能力编码示例：

```text
workOrder.read
workOrder.updateBasicInfo
workOrder.cancel
workOrder.forceClose
workOrder.reopen
workOrder.reassignNetwork
task.claim
task.complete
evidence.submit
evidence.review
evidence.forceApprove
pricing.calculate
settlement.adjust
configuration.edit
configuration.approve
configuration.publish
data.exportSensitive
```

能力编码是稳定安全契约；菜单和按钮可变化，但不能改变能力语义。

Portal 页面与首批动作的更完整 capability 建议、数据范围和 obligations 见[页面、动作、能力与数据范围矩阵](../product/07-page-action-permission-matrix.md)。其中角色模板仅是默认授权建议，服务端仍按本章模型判定。

## 5. 数据范围

数据范围由多个维度组合：

- 租户/公司；
- 客户与品牌；
- 运营项目；
- 行政区域；
- 组织和网点；
- 服务产品；
- 工单参与关系：负责人、客服协调人、当前网点、当前师傅、审核人；
- 数据敏感级别。

同一授权中的维度默认 `AND`，同一用户的多条有效授权默认 `OR`。显式拒绝用于高风险例外时优先于允许。

### 5.1 工单关系范围

关系权限使用带生效时间的参与记录，而不是只看工单当前字段。例如改派后原网点失去当前工单访问权，但审计人员仍可以基于历史职责查看其履约记录。

### 5.2 区域范围

区域授权必须明确是否包含下级区域，不能只依赖字符串前缀。地址变更时重新计算访问投影，并保留变更前后的访问审计。

> M64 已实现首个精确 REGION 子集：Project 创建时原子写入带有效期的 `project_region` 关系，
> authorization 通过公开端口按 tenant、稳定 regionCode 和有效时刻解析项目集合。该实现不包含区域目录或
> 下级展开。M65 以同样的失败关闭规则新增显式 `project_network` 关系和 NETWORK 项目集合解析；
> M66 新增显式完整集合的 REGION/NETWORK 即时修订，提交后授权解析即时变化且旧游标失败关闭；
> M184 已实现企业内部 Organization/OrgUnit/closure/任职与 LOCAL/EXTERNAL_AUTHORITATIVE 同步收据；
> ServiceNetwork/合作组织目录、计划生效/审批及 ORGANIZATION DataScope 匹配仍未实现。

## 6. 动作授权

动作授权接口输入：

```text
principal
capability
resourceType/resourceId
taskId（可选）
requestedFields（可选）
commandContext
```

输出：

```text
ALLOW / DENY
reasonCodes[]
matchedGrantIds[]
dataScopeExplanation
obligations[]
```

`obligations` 表达必须二次确认、必须填写原因、必须发起审批、输出需脱敏等附加要求。

## 7. 字段、资料和金额权限

每个标准字段定义敏感级别。授权结果可以是：

- `HIDDEN`：不可返回；
- `MASKED`：脱敏显示；
- `READ`：可查看原值；
- `WRITE`：可修改；
- `EXPORT`：可导出。

附件下载使用短时授权 URL，并校验资料类型、工单范围和水印策略。金额查看、价格编辑、结算调整和导出分别授权，不能因为能看工单就默认能看金额。

## 8. 高风险操作

下列操作默认需要二次确认、原因和更强权限，部分还需审批：

- 强制关闭、恢复和重开；
- 强制审核通过；
- 手工修改确认事实；
- 调整价格、派单比例或网点上限；
- 结算核减、补差和红冲；
- 发布高风险配置；
- 批量导出敏感数据；
- 代替他人提交资料或执行任务。

平台超级管理员不自动获得业务高风险权限。技术运维与业务审批必须职责分离。

## 9. 临时授权和代办

临时授权必须限定能力、范围、起止时间和原因；到期自动失效。代办保留原责任人和实际操作人，不把操作伪装为原责任人执行。

离职、停用、转岗或网点清退时，立即撤销登录与新操作能力，并生成待重新分配任务清单。

## 10. 服务账号

车企接口、定时任务和内部服务使用独立服务主体：

- 使用最小权限和明确调用范围；
- 密钥可轮换、可吊销并保存在密钥系统；
- 不共享人员账号；
- 每次调用记录客户端、凭据版本和关联业务键；
- 外部系统不能伪造内部用户身份。

## 11. 审计分类

| 类型 | 记录内容 | 示例 |
|---|---|---|
| 安全审计 | 登录、失败、凭据、授权变更 | 用户被授予结算调整权限 |
| 业务操作审计 | 命令、对象、前后差异和结果 | 人工改派网点 |
| 配置审计 | 草稿、审批、发布、停用和迁移 | 发布价格 V3 |
| 数据访问审计 | 敏感查看、下载和导出 | 导出用户手机号 |
| 系统自动审计 | 自动任务、规则版本和解释 | 自动派单选择网点 |
| 管理审计 | 组织、网点、人员和资质变更 | 停派网点 |

## 12. AuditRecord

审计记录至少包含：

```text
auditId / tenantId
occurredAt
principalType / principalId
effectiveUserId / delegatedBy
organizationId
sessionId / clientId / ip / device
actionCode / capabilityCode
resourceType / resourceId / resourceVersion
taskId / workOrderId / projectId
commandId / correlationId
decision / reasonCodes / matchedGrantIds
beforeDigest / afterDigest / changeSet
businessReason / approvalRef / evidenceRefs
result / errorCode
```

敏感值不直接写入通用审计正文；差异记录按字段策略脱敏或只保存摘要。

## 13. 不可变性与保留

- 审计记录仅追加，普通应用账号不可更新或删除；
- 使用独立写入权限、完整性摘要和定期归档；
- 保留期限按合同、财务、等保和个人信息要求分类；
- 合法删除个人信息时保留必要业务审计，但去标识化不再需要的个人值；
- 审计查询本身也需要记录审计。

## 14. 性能与投影

列表查询通过预计算的数据范围投影和组织路径过滤，不能对每一行远程调用权限服务。执行高风险命令时始终进行权威实时鉴权。

权限缓存键必须包含主体、授权版本、资源范围摘要和策略版本。授权变化时使相关缓存失效。

## 15. MVP 验收

1. 项目经理只能查看授权品牌与区域；
2. 网点改派后原网点不能继续读取当前工单；
3. 师傅只能处理分配给自己的任务；
4. 客服可预约但不能修改师傅已提交字段；
5. 普通审核员不能强制通过；
6. 平台管理员没有业务结算调整权限；
7. 临时授权到期自动失效；
8. 所有强制动作和敏感导出均有完整审计；
9. 前端伪造角色或隐藏按钮均不能绕过服务端鉴权；
10. 授权变更后列表投影和命令鉴权在约定时间内一致。
