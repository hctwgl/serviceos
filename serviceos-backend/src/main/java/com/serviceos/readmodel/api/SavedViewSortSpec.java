package com.serviceos.readmodel.api;

import java.util.List;

/** 可选排序偏好；字段必须在页面允许目录内。 */
public record SavedViewSortSpec(List<SavedViewSortField> fields) {
    public SavedViewSortSpec {
        fields = fields == null ? List.of() : List.copyOf(fields);
    }

    public record SavedViewSortField(String field, String direction) {
    }
}
