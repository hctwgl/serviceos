package com.serviceos.network.api;

import java.time.Instant;
import java.util.List;

public record NetworkMembershipPage(List<NetworkMembershipView> items, Instant generatedAt) {}
