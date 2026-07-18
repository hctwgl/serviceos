from pathlib import Path
import re

MASTER_MERGE = "088018a6eac180f7fe36536fae9d073fa797757c"

status_path = Path("serviceos-architecture/docs/implementation-status.md")
status = status_path.read_text(encoding="utf-8")

old_frontmatter = "baselineCommit: df343390c9924f1b75d09080c46369091a5fb744"
new_frontmatter = f"baselineCommit: {MASTER_MERGE}"
if status.count(old_frontmatter) != 1:
    raise SystemExit("implementation-status frontmatter baseline precondition failed")
status = status.replace(old_frontmatter, new_frontmatter, 1)

old_table = "| 基线提交 | `df343390c9924f1b75d09080c46369091a5fb744` |"
new_table = f"| 基线提交 | `{MASTER_MERGE}` |"
if status.count(old_table) != 1:
    raise SystemExit("implementation-status baseline table precondition failed")
status = status.replace(old_table, new_table, 1)

marker = "每次完成新里程碑时，Agent 必须更新本节的最新里程碑、基线提交和更新时间。"
semantic = (
    marker
    + "\n\n`baselineCommit` 统一指向包含 `latestMilestone` 全部最终证据、且已经进入 `master` 的合并提交；"
      "里程碑分支中的中间实现提交只记录在对应实现文档和验收证据中，不再作为本文件的当前基线。"
)
if status.count(marker) != 1:
    raise SystemExit("implementation-status baseline semantic marker precondition failed")
status = status.replace(marker, semantic, 1)

old_identity = (
    "| 身份授权 | OIDC/JWT、Capability、Tenant/Project/REGION/NETWORK Scope、拒绝审计 | `IMPLEMENTED` | "
    "后端认证授权和范围校验基线；实时 TENANT/PROJECT/REGION/NETWORK 集合；Project 有效期关系、整组修订与授权目录读取 | "
    "组织关系、Region 层级后代、计划修订/审批、正式企业 IdP、完整组织治理 UI | M9、M63～M67 |"
)
new_identity = (
    "| 身份授权 | OIDC/JWT、Capability、Tenant/Project/REGION/NETWORK Scope、拒绝审计 | `IMPLEMENTED` | "
    "后端认证授权和范围校验基线；实时 TENANT/PROJECT/REGION/NETWORK 集合；Project 有效期关系、整组修订与授权目录读取；"
    "M183～M188 已补齐 Principal、企业组织任职、RoleGrant 治理、统一用户中心与 `/me` 上下文/导航 | "
    "Region 层级后代、Project 计划修订/审批、正式企业 IdP、HR Connector、ORGANIZATION DataScope、MFA obligation 执行器 | "
    "M9、M63～M67、M183～M188 |"
)
if status.count(old_identity) != 1:
    raise SystemExit("implementation-status identity row precondition failed")
status = status.replace(old_identity, new_identity, 1)
status_path.write_text(status, encoding="utf-8")

readme_path = Path("serviceos-architecture/README.md")
readme = readme_path.read_text(encoding="utf-8")
heading = "## 当前基线\n"
if readme.count(heading) != 1:
    raise SystemExit("architecture README current-baseline heading precondition failed")
prefix, old_tail = readme.split(heading, 1)
if "当前工程基线推进至 **M182**" not in old_tail:
    raise SystemExit("architecture README stale M182 baseline precondition failed")

new_tail = f'''## 当前基线

当前 `master` 基线为 **M266 Technician 在线资料整改交付批次**，最终合并提交为
`{MASTER_MERGE}`。

当前已具备 Admin、独立 Network Web、独立 Technician H5、原生 Technician iOS，以及统一主体/组织/授权治理、在线 Visit、冻结基础表单、Evidence 上传、Snapshot/Task 完成和多轮整改闭环。下一已接受优先级是 Track F：iOS 离线工作包、持久化命令/上传队列、同步、冲突、改派失权和恢复。

权威进度入口：

- [实施状态总览](docs/implementation-status.md)
- [里程碑索引](docs/milestone-index.md)
- [M266 实现文档](architecture/279-m266-technician-correction-batch.md)
- [M266 验收矩阵](testing/263-m266-technician-correction-batch-acceptance.md)
- [多客户端 Portal 持续交付计划](roadmap/05-multi-client-portal-delivery-plan.md)
- [多客户端 Portal 程序级验收矩阵](testing/multi-client-portal-program-acceptance.md)

本 README 只保留当前基线摘要；逐能力完成范围、明确未实现项、OpenAPI/Flyway 版本和后续顺序统一由 `implementation-status.md` 维护，避免重复叙事再次过期。
'''
readme_path.write_text(prefix + new_tail, encoding="utf-8")
