---
title: 授权与审计逻辑数据模型
version: 0.1.0
status: Proposed
---

# 授权与审计逻辑数据模型

本模型补充 M2 所需的身份、能力、数据范围、字段策略与不可抵赖审计。身份认证可以接入现有 IAM，但业务授权必须保留 ServiceOS 领域范围。

## 1. 授权实体

### security_principal

保存用户或服务账号的稳定主体 ID、类型、状态和外部身份系统引用。不存储明文密码或密钥。

### organization / organization_closure

`organization` 保存总部、区域、网点等组织；closure 保存祖先—后代关系和深度，支持明确的下级范围查询。

### role / capability / role_capability

角色与能力多对多。能力编码全局稳定，角色可按租户配置但不能改变能力语义。

### role_grant

| 字段 | 说明 |
|---|---|
| grant_id / principal_id / role_id | 授权身份 |
| organization_id | 授权所在组织 |
| scope_policy_id | 数据范围策略 |
| valid_from / valid_to | 生效区间 |
| source | 申请、岗位同步、临时授权等 |
| approved_by / approval_ref | 审批 |
| revoked_at / revoked_by / revoke_reason | 撤销审计 |

授权采用追加/撤销记录，不覆盖历史授权区间。

### data_scope_policy / data_scope_clause

policy 是可版本化策略；clause 保存维度、操作符和值引用，例如：

```text
brand IN [BYD]
region DESCENDANT_OF [SHANDONG]
project IN [P-2026-01]
workOrderRelation IN [OWNER, CUSTOMER_SERVICE]
```

不允许把任意 SQL 作为数据范围表达式。

### field_policy / field_policy_rule

按资源类型、字段编码、敏感级别和操作定义 `HIDDEN/MASKED/READ/WRITE/EXPORT`。脱敏算法使用受控编码引用。

### delegation

保存委托人、代理人、能力子集、数据范围、起止时间、原因和撤销信息。

## 2. 鉴权与投影

### authorization_policy_version

保存本次可执行授权策略组合的版本和摘要。每次鉴权结果记录使用的策略版本。

### principal_scope_projection

为列表查询展开主体可访问的品牌、项目、区域、网点和关系类型。投影带 `grant_version`，授权改变后异步重建；高风险命令不只依赖投影。

### authorization_decision_log

保存高风险或拒绝决策的主体、能力、资源、结果、原因码、匹配授权和策略版本。普通低风险允许决策可按策略采样，业务命令审计仍必须完整。

## 3. 审计实体

### audit_record

| 字段组 | 字段 |
|---|---|
| 身份 | audit_id、tenant_id、principal_id、effective_user_id、delegated_by |
| 上下文 | occurred_at、organization_id、session_id、client_id、ip、device |
| 动作 | action_code、capability_code、command_id、correlation_id |
| 资源 | resource_type、resource_id、resource_version、work_order_id、task_id、project_id |
| 鉴权 | decision、reason_codes、matched_grant_ids、policy_version |
| 变化 | before_digest、after_digest、change_set_redacted |
| 依据 | business_reason、approval_ref、evidence_refs |
| 结果 | result、error_code |
| 完整性 | previous_hash、record_hash、archive_batch_id |

### sensitive_access_audit

专门记录敏感字段查看、附件下载和导出，包括查询条件摘要、返回记录数、字段集合、导出文件引用和下载次数。

### audit_archive_batch

保存归档范围、记录数量、Merkle/批次摘要、存储位置、保留期限和校验状态。具体完整性技术由安全设计阶段选择。

## 4. 约束

- `role_grant` 的授权和撤销均保留历史；
- 同一主体的临时授权不得超过审批允许期限；
- 能力、数据范围和字段策略必须有版本；
- 审计表只允许专用写入角色 INSERT；
- 业务事务成功与操作审计写入应原子提交，或使用同事务 Outbox 可靠生成；
- 失败命令和拒绝访问也要审计，不能依赖业务事务提交；
- 导出文件必须有关联审计、过期时间和撤销能力；
- 审计中的敏感值按策略脱敏，不复制完整附件或表单。

## 5. 索引验证

物理设计重点验证：

- 主体 + 能力 + 有效期授权查询；
- 品牌/项目/区域/网点范围列表过滤；
- 工单、命令和 correlationId 的审计追踪；
- 敏感导出按用户、项目和日期检索；
- 审计按月分区、冷热归档和保留期限；
- 授权变更到投影生效的延迟监控。
