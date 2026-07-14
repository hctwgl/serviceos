package com.serviceos.operations.api;

import java.util.List;

public record OperationalExceptionPage(List<OperationalExceptionItem> items, String nextCursor) {
    public OperationalExceptionPage {
        items = List.copyOf(items);
    }
}
