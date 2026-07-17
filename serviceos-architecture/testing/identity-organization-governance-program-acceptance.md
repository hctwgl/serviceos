---
title: M183～M188 统一身份、组织与授权治理验收矩阵
version: 1.0.0
status: Accepted
decisionDate: 2026-07-17
---

# M183～M188 统一身份、组织与授权治理验收矩阵

## 1. 使用方式

本矩阵是 M183～M188 的程序级 P0/P1 验收基线。每个实现里程碑仍需创建对应实现文档和更细测试证据；本矩阵中的 P0 未通过时，不得声明该里程碑完成。

证据等级：

- `DB`：真实 PostgreSQL + Flyway/Testcontainers；
- `SEC`：认证、授权、字段策略和失败关闭；
- `CONTRACT`：OpenAPI/事件 Schema、兼容门禁和生成客户端；
- `ARCH`：Spring Modulith 模块边界；
- `E2E`：真实 OIDC、Backend、PostgreSQL 和适用 Portal；
- `OPS`：审计、日志、缓存失权、同步和恢复。

## 2. 程序级 P0 不变量

| ID | 场景 | 预期 | 证据 |
|---|---|---|---|
| IOG-P0-01 | 检查 ServiceOS 数据库和 API | 不保存密码、密码摘要、MFA secret 或 IdP session；敏感身份信息按字段策略最小化 | DB/SEC |
| IOG-P0-02 | 同一 `(tenant, issuer, subject)` 并发绑定 | 只产生一个 IdentityLink；冲突绑定失败关闭且有安全审计 | DB/SEC |
| IOG-P0-03 | 一人具有内部员工和师傅 Persona | 保持一个 Principal、多 Persona 和独立有效期；不能用单一 userType 覆盖 | DB/CONTRACT |
| IOG-P0-04 | 已停用主体使用旧 JWT、旧游标或旧离线上下文 | 新命令失败关闭；适用查询失权；旧缓存和导航不能恢复权限 | SEC/E2E/OPS |
| IOG-P0-05 | 内部部门、合作商、网点和师傅档案建模 | 对象边界独立，无“网点即部门”或“师傅即登录用户”的兼容字段 | DB/ARCH |
| IOG-P0-06 | 前端伪造角色、Persona、projectId 或 networkId | 服务端忽略自报授权，只接受实时 RoleGrant/Membership/责任关系 | SEC/E2E |
| IOG-P0-07 | 授权或组织关系变更 | 新请求立即按权威事实判定；旧 scope cursor/context version 失败关闭 | DB/SEC/OPS |
| IOG-P0-08 | 删除或覆盖历史授权/任职 | 普通命令不可改写历史；通过终止/撤销追加新事实 | DB/SEC |
| IOG-P0-09 | 敏感用户目录搜索、导出或查看身份绑定 | 按管理范围和字段策略过滤，行为自身增强审计 | SEC/OPS |
| IOG-P0-10 | 任一新模块读取其他模块内部表 | ArchitectureTest 阻断；只能通过公开 API、端口或事件 | ARCH |

## 3. M183 统一主体目录

| ID | 优先级 | 场景 | 预期 | 证据 |
|---|---|---|---|---|
| M183-01 | P0 | 已允许 issuer 的首次 OIDC 登录 | 幂等登记 Principal/IdentityLink，返回稳定 principalId | DB/SEC/E2E |
| M183-02 | P0 | 未知 issuer、错误 tenant claim、subject 冲突 | 失败关闭，不生成半绑定主体 | SEC/DB |
| M183-03 | P0 | 绑定第二个外部身份 | 两个 IdentityLink 指向同一 Principal，审批/能力满足且完整审计 | DB/SEC |
| M183-04 | P0 | 禁用主体 | 后续命令拒绝，记录原因和操作者；不删除历史业务事实 | DB/SEC/OPS |
| M183-05 | P0 | 普通目录查询 | 不返回完整手机号、邮箱、issuer 内部属性或服务凭据 | SEC/CONTRACT |
| M183-06 | P1 | Profile/Persona 并发更新 | 使用版本/ETag；冲突返回稳定 409，不最后写胜出 | DB/CONTRACT |

## 4. M184 企业组织与任职

| ID | 优先级 | 场景 | 预期 | 证据 |
|---|---|---|---|---|
| M184-01 | P0 | 建立多层 OrgUnit | closure 祖先/后代与 depth 正确，循环和跨租户父节点被拒绝 | DB |
| M184-02 | P0 | 员工调部门、兼职和负责人变化 | 旧 Membership 保留，新有效期准确，同一时刻约束符合策略 | DB |
| M184-03 | P0 | 离职/停用同步 | Principal 新操作失权，RoleGrant 终止/撤销，产生待重分配清单 | DB/SEC/OPS |
| M184-04 | P0 | EXTERNAL_AUTHORITATIVE 字段被 Admin 修改 | 失败关闭或走明确 override 流程，不静默覆盖来源 | SEC/E2E |
| M184-05 | P0 | 同一外部同步批次重放、乱序或部分失败 | 幂等、可恢复、逐项结果可追踪，不产生组织回退 | DB/OPS |
| M184-06 | P1 | 组织移动 | closure 原子切换，授权投影刷新，历史审计可解释 | DB/SEC/OPS |

## 5. M185 网点人员与师傅身份

| ID | 优先级 | 场景 | 预期 | 证据 |
|---|---|---|---|---|
| M185-01 | P0 | 合作商拥有多个 ServiceNetwork | 网点是独立业务资源，可关联经营/结算主体，不进入内部部门 closure | DB/ARCH |
| M185-02 | P0 | 网点负责人邀请成员 | 只能作用于授权网点，身份绑定与 NetworkMembership 分开登记 | SEC/E2E |
| M185-03 | P0 | 一名师傅关联多个网点 | 每条关系有效期独立；分配时明确使用 membership；跨网点数据隔离 | DB/SEC |
| M185-04 | P0 | 账号有效但资质过期或师傅档案停用 | 不能获得新分配候选，不把账号状态误当可服务状态 | DB/SEC |
| M185-05 | P0 | 清退网点或停用师傅 | 展示并处理未完成 Task/Appointment/Visit/离线包，旧命令拒绝 | E2E/OPS |
| M185-06 | P1 | 资质提交与总部审核 | 文件、Review、有效期和决定只追加；网点不能自标“已认证” | DB/SEC/E2E |

## 6. M186 角色与授权治理

| ID | 优先级 | 场景 | 预期 | 证据 |
|---|---|---|---|---|
| M186-01 | P0 | 创建租户角色 | 只能组合稳定 Capability，不能更改能力语义或风险级别 | DB/SEC |
| M186-02 | P0 | 申请高风险 RoleGrant | 申请人不能自批；审批者不能超过自己的可授予范围 | SEC/E2E |
| M186-03 | P0 | 撤销/到期 RoleGrant | 新请求立即失权，旧游标/context version 失败关闭，历史保留 | DB/SEC/OPS |
| M186-04 | P0 | 同一主体多范围授权 | 同条授权维度按 AND，多条有效授权按 OR；显式拒绝优先 | DB/SEC |
| M186-05 | P0 | 创建 Delegation | 能力、范围和期限不得超过委托人；实际操作者和原责任人均审计 | SEC/OPS |
| M186-06 | P1 | authorization:explain | 只向专用管理能力返回脱敏解释，不泄露他人敏感 grant/policy | SEC/CONTRACT |

## 7. M187 Admin 统一用户中心

| ID | 优先级 | 场景 | 预期 | 证据 |
|---|---|---|---|---|
| M187-01 | P0 | 按姓名/工号/网点搜索并选择人员 | 使用授权目录，不要求复制 principal UUID；结果按范围和脱敏策略过滤 | E2E/SEC |
| M187-02 | P0 | 查看主体详情 | 分区显示身份、Persona、任职、网点/师傅关系和授权来源，不混成单一角色字段 | E2E |
| M187-03 | P0 | 调动、停用或撤权 | 页面展示影响和 obligations，提交后重读权威状态及待重分配结果 | E2E/OPS |
| M187-04 | P0 | 低权限管理员打开治理深链 | 安全的无权/不存在响应，不泄露姓名、部门、手机号或角色 | SEC/E2E |
| M187-05 | P0 | 并发修改 | ETag/version 冲突可恢复，不覆盖新事实 | E2E/DB |
| M187-06 | P1 | EXTERNAL_AUTHORITATIVE 组织 | 来源字段只读并显示 lastSyncedAt/同步状态，人工扩展字段边界清晰 | E2E |

## 8. M188 Portal 上下文与导航

| ID | 优先级 | 场景 | 预期 | 证据 |
|---|---|---|---|---|
| M188-01 | P0 | 多 Persona 用户调用 `/me/contexts` | 返回其当前有效 ADMIN/NETWORK/TECHNICIAN contexts、范围摘要和版本 | CONTRACT/SEC |
| M188-02 | P0 | 请求未返回的 network/project context | 失败关闭，不能通过请求体或本地存储扩权 | SEC/E2E |
| M188-03 | P0 | `/me/navigation` | 由 Page Registry、Capability、feature gate 和 context 计算；pageId 稳定 | CONTRACT/E2E |
| M188-04 | P0 | 直接访问隐藏 URL 或伪造 allowed action | 后端数据和命令仍重新鉴权；导航不成为授权事实 | SEC/E2E |
| M188-05 | P0 | 授权/成员关系变化后刷新 | context/navigation 版本变化，旧缓存不可恢复已撤权限 | E2E/OPS |
| M188-06 | P1 | Consumer Persona 兼容性 | Principal/IdentityLink/API Schema 不假设企业组织；未实现 C 端页面不暴露入口 | CONTRACT/ARCH |

## 9. 非功能与恢复

| ID | 优先级 | 场景 | 预期 |
|---|---|---|---|
| IOG-NFR-01 | P0 | 10 万主体、深组织树和多范围授权基准 | 查询使用索引/投影，不对列表逐行远程鉴权；目标和证据写入里程碑文档 |
| IOG-NFR-02 | P0 | IdP/HR 同步不可用 | 已登录且未撤权主体按明确策略运行；新绑定/同步不伪造成功，恢复后可重试 |
| IOG-NFR-03 | P0 | 日志与诊断包扫描 | 不出现密码、token、完整手机号/邮箱、证件、subject 映射或审批敏感正文 |
| IOG-NFR-04 | P0 | 迁移失败/回滚 | Flyway 失败关闭；不建立虚假默认 Principal、Organization、Membership 或 Grant |
| IOG-NFR-05 | P1 | 组织/授权投影重建 | 有 checkpoint/freshness/失败可见性，高风险命令始终读权威事实 |

## 10. 完成判定

单个里程碑至少提交：实现文档、迁移、机器契约、目标测试、ArchitectureTest、适用 Portal E2E、敏感输出门禁、`implementation-status.md` 更新和可追踪 PR 证据。仅有页面、fixture、Keycloak realm JSON 或初始化 SQL 不算完成。
