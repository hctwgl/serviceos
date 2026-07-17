package com.serviceos.network.api;

import java.time.Instant;
import java.util.List;

public record TechnicianQualificationPage(
        List<TechnicianQualificationView> items, Instant generatedAt) {}
