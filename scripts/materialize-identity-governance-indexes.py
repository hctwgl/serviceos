from pathlib import Path
import re

STATUS = Path("serviceos-architecture/docs/implementation-status.md")
ARCH_README = Path("serviceos-architecture/README.md")
AGENTS = Path("AGENTS.md")

status = STATUS.read_text(encoding="utf-8")
status = re.sub(
    r"^lastUpdated: .*?$",
    "lastUpdated: 2026-07-17",
    status,
    count=1,
    flags=re.MULTILINE,
)

plan_section = """## 2.1 已接受的下一实施序列：M135～M140

M135～M140 已由项目负责人正式接受为 M134 之后的身份与组织治理实施序列，但当前均尚未实现，不改变 `latestMilestone: M134`：

| 里程碑 | 状态 | 目标 |
|---|---|---|
| M135 | `ACCEPTED` | 统一 Principal、IdentityLink、PersonProfile、Persona 与主体生命周期 |
| M136 | `ACCEPTED` | 企业 Organization/OrgUnit/closure、人员任职和 LOCAL/外部权威同步 |
| M137 | `ACCEPTED` | 合作组织、ServiceNetwork 人员、TechnicianProfile、网点关系与资质 |
| M138 | `ACCEPTED` | Role/Capability/RoleGrant 申请审批撤销、Delegation、职责分离和授权解释 |
| M139 | `ACCEPTED` | Admin 统一用户中心与真实 OIDC 治理 E2E |
| M140 | `ACCEPTED` | `/me` contexts/capabilities/navigation、多 Persona 与三 Portal 上下文 |

正式事实源：

- [M135～M140 交付计划](../roadmap/03-identity-organization-governance-delivery-plan.md)
- [程序级验收矩阵](../testing/identity-organization-governance-program-acceptance.md)
- [Agent 工作清单](../roadmap/04-identity-organization-governance-agent-worklist.md)

Consumer Identity/CustomerProfile 是 M140 之后的已接受后续 Epic；在登录渠道、隐私同意、客户主数据和注销保留策略确认前不分配里程碑，也不得宣称已实现。
""".strip()

if "## 2.1 已接受的下一实施序列：M135～M140" not in status:
    marker = "每次完成新里程碑时，Agent 必须更新本节的最新里程碑、基线提交和更新时间。"
    if marker not in status:
        raise RuntimeError("implementation status baseline marker not found")
    status = status.replace(marker, marker + "\n\n" + plan_section, 1)

capability_rows = [
    "| 统一主体目录 | Principal、IdentityLink、PersonProfile、Persona 与生命周期 | `ACCEPTED` | 现有 JWT principalId 和后端身份上下文可复用 | M135 代码、迁移、契约、目录 API、停用失权和安全测试 | M135 正式路线 |",
    "| 企业组织目录 | Organization、OrgUnit、closure、任职与主数据同步 | `ACCEPTED` | 已有概念模型和授权范围需求 | M136 组织权威、有效期历史、同步收据、离职联动和治理 API/UI | M136 正式路线 |",
    "| 网点人员与师傅身份 | NetworkMembership、TechnicianProfile、网点关系与资质 | `ACCEPTED` | 已有 ServiceAssignment/TaskAssignment 和产品规格 | M137 目录运行时、身份绑定、多网点关系、资质和停用影响 | M137 正式路线 |",
    "| 角色与授权治理 | Role/Capability/RoleGrant 管理、审批、撤销与 Delegation | `ACCEPTED` | 实时 RoleGrant 授权运行时已实现 | M138 治理命令、职责分离、授权解释、历史和 Admin 操作面 | M138 正式路线 |",
    "| 统一用户中心 | Admin 用户、组织、网点人员、师傅、角色和授权治理 | `ACCEPTED` | Admin Web 与真实 Keycloak PKCE 基线可复用 | M139 页面、目录选择器、影响展示、真实 OIDC 治理 E2E | M139 正式路线 |",
    "| Portal 上下文与导航 | `/me`、contexts、capabilities、navigation 与多 Persona | `ACCEPTED` | pageId/capability/独立 Portal 规格已存在 | M140 服务端上下文、Page Registry、缓存失权和三 Portal 接入 | M140 正式路线 |",
    "| Consumer Identity | CustomerProfile、用户资源关系和 C 端身份 | `ACCEPTED` | Principal/IdentityLink 模型必须预留 Consumer Persona | M140 后独立 Epic；待登录、隐私、客户主数据与注销策略确认 | 后续正式 Epic |",
]

if "| 统一主体目录 | Principal、IdentityLink、PersonProfile、Persona 与生命周期 |" not in status:
    lines = status.splitlines()
    for index, line in enumerate(lines):
        if line.startswith("| 身份授权 |"):
            lines[index + 1:index + 1] = capability_rows
            break
    else:
        raise RuntimeError("identity capability row not found")
    status = "\n".join(lines) + ("\n" if status.endswith("\n") else "")

STATUS.write_text(status, encoding="utf-8")

arch_readme = ARCH_README.read_text(encoding="utf-8")
index_entries = """316. [M135～M140 统一身份、组织与授权治理交付计划](roadmap/03-identity-organization-governance-delivery-plan.md)
317. [M135～M140 统一身份、组织与授权治理验收矩阵](testing/identity-organization-governance-program-acceptance.md)
318. [M135～M140 身份与组织治理 Agent 工作清单](roadmap/04-identity-organization-governance-agent-worklist.md)"""
if "03-identity-organization-governance-delivery-plan.md" not in arch_readme:
    marker = "\n## 仓库结构"
    if marker not in arch_readme:
        raise RuntimeError("architecture README repository structure marker not found")
    arch_readme = arch_readme.replace(marker, "\n" + index_entries + "\n" + marker, 1)
ARCH_README.write_text(arch_readme, encoding="utf-8")

agents = AGENTS.read_text(encoding="utf-8")
agents_section = """## M135～M140 身份与组织治理正式执行入口

M135～M140 已接受为 M134 之后的正式实施序列，但尚未实现。涉及用户、组织、网点人员、师傅、角色授权或 Portal 上下文的 Agent 必须先阅读：

- `serviceos-architecture/roadmap/03-identity-organization-governance-delivery-plan.md`
- `serviceos-architecture/testing/identity-organization-governance-program-acceptance.md`
- `serviceos-architecture/roadmap/04-identity-organization-governance-agent-worklist.md`

不得创建保存密码的本地万能用户表，不得用单一 `user_type`、Keycloak Group、菜单或前端 scope 代替 Principal/Persona/Membership/RoleGrant 的权威边界。默认一个里程碑一个 Draft PR；只有对应工程证据成立后才更新 `latestMilestone` 或声明 IMPLEMENTED。
""".strip()
if "## M135～M140 身份与组织治理正式执行入口" not in agents:
    agents = agents.rstrip() + "\n\n" + agents_section + "\n"
AGENTS.write_text(agents, encoding="utf-8")
