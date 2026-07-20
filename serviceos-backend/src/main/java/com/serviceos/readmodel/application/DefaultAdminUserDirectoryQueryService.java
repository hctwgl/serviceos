package com.serviceos.readmodel.application;

import com.serviceos.authorization.api.AuthorizationGovernanceQueryService;
import com.serviceos.authorization.api.RoleGrantView;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.identity.api.SecurityPrincipalPage;
import com.serviceos.identity.api.SecurityPrincipalQueryService;
import com.serviceos.identity.api.SecurityPrincipalView;
import com.serviceos.organization.api.OrgMembershipView;
import com.serviceos.organization.api.OrganizationQueryService;
import com.serviceos.organization.api.OrganizationView;
import com.serviceos.readmodel.api.AdminUserDirectoryItem;
import com.serviceos.readmodel.api.AdminUserDirectoryPage;
import com.serviceos.readmodel.api.AdminUserDirectoryQueryService;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.ProblemCode;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Admin 用户目录投影：主体列表 + 组织/角色摘要 soft-gate。
 * <p>
 * 不包外层事务；组织/授权摘要失败只置 null，不拖垮整页。
 */
@Service
class DefaultAdminUserDirectoryQueryService implements AdminUserDirectoryQueryService {
    private final SecurityPrincipalQueryService principals;
    private final OrganizationQueryService organizations;
    private final AuthorizationGovernanceQueryService authorization;

    DefaultAdminUserDirectoryQueryService(
            SecurityPrincipalQueryService principals,
            OrganizationQueryService organizations,
            AuthorizationGovernanceQueryService authorization
    ) {
        this.principals = principals;
        this.organizations = organizations;
        this.authorization = authorization;
    }

    @Override
    public AdminUserDirectoryPage list(
            CurrentPrincipal actor,
            String correlationId,
            String query,
            String status,
            String cursor,
            int limit
    ) {
        Objects.requireNonNull(actor, "actor");
        SecurityPrincipalPage page = principals.list(actor, correlationId, query, status, cursor, limit);
        Map<UUID, String> orgNames = loadOrganizationNames(actor, correlationId);

        List<AdminUserDirectoryItem> items = new ArrayList<>();
        for (SecurityPrincipalView principal : page.items()) {
            items.add(new AdminUserDirectoryItem(
                    principal.id(),
                    principal.type(),
                    principal.status(),
                    principal.displayName(),
                    principal.employeeNumber(),
                    principal.version(),
                    principal.createdAt(),
                    principal.updatedAt(),
                    organizationSummary(actor, correlationId, principal.id(), orgNames),
                    roleSummary(actor, correlationId, principal.id()),
                    lastLoginAt(actor, correlationId, principal.id())));
        }
        return new AdminUserDirectoryPage(items, page.nextCursor(), page.asOf());
    }

    private java.time.Instant lastLoginAt(
            CurrentPrincipal actor, String correlationId, UUID principalId
    ) {
        try {
            var page = principals.recentLogins(actor, correlationId, principalId, 1);
            return page.items().isEmpty() ? null : page.items().getFirst().occurredAt();
        } catch (BusinessProblem problem) {
            if (problem.code() == ProblemCode.ACCESS_DENIED
                    || problem.code() == ProblemCode.RESOURCE_NOT_FOUND) {
                return null;
            }
            throw problem;
        }
    }

    private Map<UUID, String> loadOrganizationNames(CurrentPrincipal actor, String correlationId) {
        try {
            return organizations.listOrganizations(actor, correlationId).items().stream()
                    .collect(Collectors.toMap(OrganizationView::id, OrganizationView::name, (a, b) -> a));
        } catch (BusinessProblem problem) {
            if (problem.code() == ProblemCode.ACCESS_DENIED) {
                return Map.of();
            }
            throw problem;
        }
    }

    private String organizationSummary(
            CurrentPrincipal actor,
            String correlationId,
            UUID principalId,
            Map<UUID, String> orgNames
    ) {
        try {
            List<OrgMembershipView> memberships = organizations.listMemberships(
                            actor, correlationId, null, null, principalId)
                    .items().stream()
                    .filter(item -> "ACTIVE".equals(item.status()))
                    .toList();
            if (memberships.isEmpty()) {
                return "无任职";
            }
            LinkedHashSet<String> labels = new LinkedHashSet<>();
            for (OrgMembershipView membership : memberships) {
                String name = orgNames.get(membership.organizationId());
                labels.add(name != null && !name.isBlank() ? name : "组织");
            }
            if (labels.size() == 1) {
                return labels.iterator().next()
                        + (memberships.size() > 1 ? "（" + memberships.size() + " 项）" : "");
            }
            return String.join("、", labels.stream().limit(2).toList())
                    + (labels.size() > 2 ? " 等" + labels.size() + " 个组织" : "");
        } catch (BusinessProblem problem) {
            if (problem.code() == ProblemCode.ACCESS_DENIED) {
                return null;
            }
            throw problem;
        }
    }

    private String roleSummary(CurrentPrincipal actor, String correlationId, UUID principalId) {
        try {
            List<RoleGrantView> grants = authorization.listRoleGrants(
                            actor, correlationId, principalId.toString(), "ACTIVE")
                    .items();
            if (grants.isEmpty()) {
                return "无角色";
            }
            LinkedHashSet<String> codes = grants.stream()
                    .map(RoleGrantView::roleCode)
                    .filter(code -> code != null && !code.isBlank())
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            if (codes.isEmpty()) {
                return grants.size() + " 个授权";
            }
            List<String> preview = codes.stream().limit(2).toList();
            return String.join("、", preview)
                    + (codes.size() > 2 ? " 等" + codes.size() + " 个角色" : "");
        } catch (BusinessProblem problem) {
            if (problem.code() == ProblemCode.ACCESS_DENIED) {
                return null;
            }
            throw problem;
        }
    }
}
