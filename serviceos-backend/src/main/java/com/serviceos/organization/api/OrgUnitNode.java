package com.serviceos.organization.api;

import java.util.List;
import java.util.UUID;

public record OrgUnitNode(
        UUID id,
        UUID parentUnitId,
        String unitCode,
        String unitName,
        long version,
        List<OrgUnitNode> children
) {
}
