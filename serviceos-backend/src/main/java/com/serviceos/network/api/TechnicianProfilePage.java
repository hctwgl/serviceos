package com.serviceos.network.api;

import java.time.Instant;
import java.util.List;

public record TechnicianProfilePage(List<TechnicianProfileView> items, Instant generatedAt) {}
