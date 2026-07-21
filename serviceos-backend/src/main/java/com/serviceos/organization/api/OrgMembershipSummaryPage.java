package com.serviceos.organization.api;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record OrgMembershipSummaryPage(List<OrgMembershipSummaryView> items, Instant asOf) {
    public OrgMembershipSummaryPage {
        items = List.copyOf(Objects.requireNonNull(items, "items"));
        Objects.requireNonNull(asOf, "asOf");
    }
}
