# DEC-015：ServiceOS 多租户、多组织与数据隔离模型决策 V1.0

状态：Accepted

版本：1.0.0

日期：2026-07-24

适用范围：

- Tenant
- Customer
- Project
- Organization
- Network
- Region
- User
- Role
- Permission
- Data Scope


---

# 1. 决策背景

ServiceOS 是面向新能源现场履约的企业级 SaaS 平台。

平台同时服务：

- 多个合作客户
- 多个项目
- 多个服务网络
- 多个组织
- 多类用户角色

因此必须建立清晰的数据隔离模型。

核心原则：

> 权限决定操作能力，数据范围决定可见范围。


---

# 2. 数据隔离层级

ServiceOS 数据隔离模型：

```
Tenant

  ↓

Customer

  ↓

Project

  ↓

Region / Network

  ↓

WorkOrder

  ↓

Task
```


---

# 3. Tenant 租户模型

Tenant 是最高数据隔离边界。

不同 Tenant：

- 数据完全隔离
- 用户体系隔离
- 配置资产隔离
- 权限体系隔离


示例：

```
Tenant A

  ServiceOS运营平台


Tenant B

  某集团独立部署环境
```


---

# 4. Customer 合作方边界

Customer 表示商业合作主体。

例如：

- 比亚迪
- 特斯拉


Customer 属于 Tenant。

关系：

```
Tenant

 ↓

Customer

 ↓

Project
```


---

# 5. Project 数据边界

Project 是业务运营隔离单元。

用户访问项目数据必须满足：

- 用户属于 Tenant
- 用户拥有 Project Scope


例如：

项目经理：

只能看到：

```
比亚迪山东家充项目
```

不能看到：

```
比亚迪上海维修项目
```


---

# 6. Organization 组织模型

ServiceOS 支持多组织协作。


组织类型：

- 平台组织
- 客户组织
- 服务商组织
- 网点组织


关系：

```
Organization

  ↓

Membership

  ↓

User
```


---

# 7. Network 服务网络模型

Network 表示现场服务能力组织。


例如：

```
山东安装服务商

  ↓

济南服务网点

  ↓

工程师
```


Network 参与：

- 派单
- 任务执行
- 服务质量评价


---

# 8. Region 区域模型

Region 用于数据范围和运营管理。


例如：

```
中国

 ↓

山东省

 ↓

济南市
```


区域影响：

- 项目范围
- 服务覆盖
- 调度范围


---

# 9. 用户模型

User 不直接拥有业务权限。

通过：

```
User

 ↓

Membership

 ↓

Role

 ↓

Permission

```


---

# 10. 权限模型

采用四层模型：

## 功能权限

能否执行操作。

例如：

- 创建项目
- 发布方案
- 审核资料


## 数据权限

能看到哪些数据。

例如：

- 哪些项目
- 哪些区域
- 哪些网点


## 对象权限

能否操作当前对象。


例如：

某项目负责人可以编辑自己的项目。


## 字段权限

敏感字段控制。

例如：

结算金额。


---

# 11. Scope 数据范围模型

统一 Scope：

```
Tenant Scope

Project Scope

Region Scope

Network Scope

Organization Scope
```


权限判断：

```
用户权限

+

数据范围

+

对象状态

+

业务规则

```


---

# 12. 角色模型

核心角色：

## 平台管理员

范围：

Tenant


能力：

- 全局配置
- 用户管理
- 模板管理


---

## 项目经理

范围：

Project


能力：

- 项目管理
- 履约方案管理
- 查看工单


---

## 运营人员

范围：

Project / Region


能力：

- 履约监控
- 异常处理


---

## 服务商管理员

范围：

Network


能力：

- 管理服务团队
- 查看任务


---

## 调度员

范围：

Region / Network


能力：

- 分配任务
- 调整资源


---

## 工程师

范围：

个人任务


能力：

- 执行现场任务
- 提交资料


---

# 13. 后端授权原则

后端是最终权限裁决者。

前端权限：

只用于：

- 隐藏入口
- 优化体验


禁止：

只依靠前端控制权限。


---

# 14. Agent 开发约束

禁止：

1. 在前端写死角色逻辑。

2. 通过隐藏按钮实现权限。

3. 绕过后端 Scope 校验。

4. 直接根据用户ID判断业务权限。


必须：

1. 使用统一权限服务。

2. 使用数据范围模型。

3. 关键操作记录审计事件。

4. 遵循 Tenant 隔离。


---

# 15. 验收标准

任何页面必须回答：

用户为什么能看到这些数据？

用户为什么能执行这个操作？


如果无法解释：

权限设计失败。


---

# End of Decision
