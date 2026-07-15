package com.serviceos.sla.api;

import java.time.Instant;
import java.util.List;

/** deadlineAt、slaInstanceId 正序的稳定游标页。 */
public record SlaInstancePage(List<SlaInstanceItem> items, String nextCursor, Instant asOf) {
    public SlaInstancePage {
        items = List.copyOf(items);
    }
}
