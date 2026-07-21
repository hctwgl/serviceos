package com.serviceos.audit.api;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record AuditRecordPage(List<AuditRecordView> items, Instant asOf) {
    public AuditRecordPage {
        items = List.copyOf(Objects.requireNonNull(items, "items"));
        Objects.requireNonNull(asOf, "asOf");
    }
}
