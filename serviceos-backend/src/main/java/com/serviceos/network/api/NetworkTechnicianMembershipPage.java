package com.serviceos.network.api;

import java.time.Instant;
import java.util.List;

public record NetworkTechnicianMembershipPage(
        List<NetworkTechnicianMembershipView> items, Instant generatedAt) {}
