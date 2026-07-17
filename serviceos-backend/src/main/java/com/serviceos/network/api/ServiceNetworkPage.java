package com.serviceos.network.api;

import java.time.Instant;
import java.util.List;

public record ServiceNetworkPage(List<ServiceNetworkView> items, Instant generatedAt) {}
