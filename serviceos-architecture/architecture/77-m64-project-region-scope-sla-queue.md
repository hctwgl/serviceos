---
title: M64 项目区域关系与 REGION SLA 队列
status: Implemented
lastUpdated: 2026-07-15
---

# M64 项目区域关系与 REGION SLA 队列

## 1. 目标

M64 补齐 M63 明确留下的第一个权威关系缺口：Project 创建时可声明零到多个 REGION 稳定引用，
项目目录以有效期关系持久化；authorization 将有效 REGION RoleGrant 解析为精确项目集合，复用 M63
的单条范围化 SQL 查询 SLA 工作台。

本切片不从工单地址、行政区前缀或用户组织猜测项目关系。NETWORK、区域层级后代、组织关系、缓存和
导出仍未实现；只有这些范围时继续失败关闭并记录拒绝审计。

## 2. 契约与聚合

- Core OpenAPI 0.35.0 为 `CreateProjectRequest` 和 `Project` 增加可选 `regionCodes`；省略或空数组明确
  表示不创建 REGION 关系，不存在默认区域。
- 每个引用必须非空、首尾无空白、长度不超过 128；最多 100 个且不得重复。聚合按字典序冻结，保证
  幂等摘要和事件载荷稳定。
- `project.created@v2` 强制携带 `regionCodes`；已发布 v1 保持字节级不变，消费者按 schemaVersion 演进。

## 3. 数据与事务

V064 创建 `prj_project_region`：

- `(tenant_id, project_id)` 复合外键保证关系不能跨租户；
- `valid_from/valid_to` 表达只追加有效期，不以数组列充当关系事实源；
- `(tenant_id, project_id, region_code, valid_from)` 唯一约束阻止重复关系事实；
- `(tenant_id, region_code, valid_from, valid_to, project_id)` 支持实时范围解析。

项目、REGION 关系、审计、Outbox 和幂等结果在 `project.create` 同一事务提交；任一步失败全部回滚。
重放从项目目录重建创建时持久化且尚未终止的 REGION 列表；独立关系修订尚未开放，不伪造旧响应。

## 4. 模块边界与授权

`authorization::api` 暴露 `ProjectRegionScopeResolver`，project 模块实现该端口。authorization 不依赖
project 内部包、不读取项目表；project 只依赖 authorization 的公开 API，Spring Modulith 边界保持通过。

授权解析规则：

1. TENANT 仍表示租户内全部项目；
2. PROJECT UUID 与所有有效 REGION 映射结果做并集；
3. REGION 精确匹配稳定引用和关系有效期，不使用字符串前缀扩权；
4. 解析为空时按 `PROJECT_SCOPE_MISSING` 拒绝；
5. 只有尚无权威目录的 NETWORK 时按 `PROJECT_SCOPE_UNRESOLVED` 拒绝并审计；
6. 最终项目集合摘要继续绑定 SLA 游标，关系变化会使旧游标失败关闭。

## 5. 工程证据

- `ProjectCommandPostgresIT`：项目/关系/审计/Outbox/幂等原子提交与重放；
- `AuthorizationPolicyPostgresIT`：租户隔离、有效期、精确 REGION 映射和 NETWORK 失败关闭；
- `SlaClockPostgresIT`：REGION RoleGrant 经权威关系进入跨项目 SLA 队列；
- `ProjectControllerSecurityTest`、契约兼容/客户端生成和 `ArchitectureTest`；
- PostgreSQL 18 上 66 个迁移到达 V064。

## 6. 明确未实现

- NETWORK/ServiceNetwork 到 Project 的权威关系；
- Region 目录、父子层级、后代展开和组织到项目关系；
- 项目 REGION 关系的独立修订/终止命令与治理 UI；
- 授权范围缓存、导出、运营分析和各 Portal；
- BUSINESS 日历、暂停/恢复、免责/重算、预警/升级/通知及其他 SLA subject。
