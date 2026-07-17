package com.serviceos.network.api;

import java.time.Instant;
import java.util.List;

public record ClearanceWorkItemPage(List<ClearanceWorkItemView> items, Instant generatedAt) {}
