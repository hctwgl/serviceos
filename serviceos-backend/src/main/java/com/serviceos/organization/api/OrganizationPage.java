package com.serviceos.organization.api;

import java.time.Instant;
import java.util.List;

public record OrganizationPage(List<OrganizationView> items, Instant asOf) {
}
