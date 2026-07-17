package com.serviceos.organization.api;

import java.time.Instant;
import java.util.List;

public record ReassignmentWorkItemPage(List<ReassignmentWorkItemView> items, Instant asOf) {
}
