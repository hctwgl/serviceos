package com.serviceos.authorization.api;

import java.time.Instant;
import java.util.List;

public record RolePage(List<RoleView> items, Instant asOf) {
}
