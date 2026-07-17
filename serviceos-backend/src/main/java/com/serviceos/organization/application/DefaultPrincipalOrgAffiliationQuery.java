package com.serviceos.organization.application;

import com.serviceos.organization.api.OrgMembershipView;
import com.serviceos.organization.api.PrincipalOrgAffiliationQuery;
import com.serviceos.organization.domain.OrgMembership;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** 有效企业任职只读投影；供 Portal 上下文合成，不做目录能力门禁。 */
@Service
final class DefaultPrincipalOrgAffiliationQuery implements PrincipalOrgAffiliationQuery {
    private final OrganizationDirectoryRepository directory;

    DefaultPrincipalOrgAffiliationQuery(OrganizationDirectoryRepository directory) {
        this.directory = directory;
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrgMembershipView> listActiveMemberships(String tenantId, UUID principalId, Instant at) {
        return directory.listMemberships(tenantId, null, null, principalId).stream()
                .filter(membership -> membership.status() == OrgMembership.Status.ACTIVE)
                .filter(membership -> !membership.validFrom().isAfter(at))
                .filter(membership -> membership.validTo() == null || membership.validTo().isAfter(at))
                .map(OrgMembership::toView)
                .toList();
    }
}
