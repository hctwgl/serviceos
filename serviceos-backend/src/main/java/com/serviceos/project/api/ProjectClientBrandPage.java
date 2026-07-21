package com.serviceos.project.api;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** 车企品牌目录页。 */
public record ProjectClientBrandPage(List<ProjectClientBrandItem> items, Instant asOf) {
    public ProjectClientBrandPage {
        items = List.copyOf(Objects.requireNonNull(items, "items"));
        Objects.requireNonNull(asOf, "asOf");
    }
}
