package com.serviceos.authorization.api;

import java.time.Instant;
import java.util.List;

public record RoleGrantPage(List<RoleGrantView> items, Instant asOf) {
}
