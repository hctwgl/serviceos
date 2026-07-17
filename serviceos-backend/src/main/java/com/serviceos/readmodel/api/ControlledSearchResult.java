package com.serviceos.readmodel.api;

import java.time.Instant;
import java.util.List;

public record ControlledSearchResult(
        List<ControlledSearchHit> items,
        ControlledSearchMeta meta,
        Instant asOf
) {}
