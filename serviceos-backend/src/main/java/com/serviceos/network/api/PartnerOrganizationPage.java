package com.serviceos.network.api;

import java.time.Instant;
import java.util.List;

public record PartnerOrganizationPage(List<PartnerOrganizationView> items, Instant generatedAt) {}
