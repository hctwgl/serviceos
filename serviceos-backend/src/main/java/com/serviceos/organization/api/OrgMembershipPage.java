package com.serviceos.organization.api;

import java.time.Instant;
import java.util.List;

public record OrgMembershipPage(List<OrgMembershipView> items, Instant asOf) {
}
