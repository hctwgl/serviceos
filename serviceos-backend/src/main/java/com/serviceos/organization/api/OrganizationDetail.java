package com.serviceos.organization.api;

import java.time.Instant;
import java.util.List;

public record OrganizationDetail(
        OrganizationView organization,
        List<OrgUnitView> units,
        Instant asOf
) {
}
