---
title: M185 网点人员与师傅身份目录验收矩阵
status: Implemented
milestone: M185
lastUpdated: 2026-07-17
---

# M185 网点人员与师傅身份目录验收矩阵

| 编号 | 场景 | 预期 | 结果 |
|---|---|---|---|
| M185-01 | 合作商拥有多个 ServiceNetwork | 网点独立资源；关联 partnerOrganizationId；不写入 org_unit_closure | pass |
| M185-02 | 网点负责人邀请成员 | 仅授权网点可邀请；创建 NetworkMembership；不创建新 Principal/IdentityLink | pass |
| M185-03 | 一名师傅关联多个网点 | 每条 NetworkTechnicianMembership 有效期独立；跨网点读取失败关闭 | pass |
| M185-04 | 账号有效但资质过期或师傅档案停用 | canAcceptAssignment=false；不把 Principal ACTIVE 误当可服务 | pass |
| M185-05 | 清退网点或停用师傅 | 返回未完成工作影响摘要并创建 clearance 待办；其后可接单拒绝 | pass |
| M185-06 | 资质提交与总部审核 | 只追加；网点不能自批 APPROVED；需 network.reviewQualification | pass |
| M185-SEC-01 | 无 network.read 查询网点/师傅 | 403，不泄露名称 | pass |
| M185-SEC-02 | 跨租户读取 | 404 | pass |
| M185-ARCH-01 | network 读取 organization/dispatch 内部表 | ArchitectureTest 阻断 | pass |

## 证据

- `./mvnw -pl serviceos-backend -am test -Dtest=ArchitectureTest,NetworkControllerSecurityTest`
- `./mvnw -pl serviceos-backend -am test -Dtest=NetworkDirectoryPostgresIT`
- Contracts/docs/preflight + targeted IT after OpenAPI/docs sync
- Full L3 `bash scripts/verify-local.sh` before/with PR delivery
