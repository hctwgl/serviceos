package com.serviceos.readmodel.application;

import com.serviceos.readmodel.api.SavedView;
import com.serviceos.readmodel.api.SavedViewFilterAst;
import com.serviceos.readmodel.api.SavedViewSortSpec;
import com.serviceos.readmodel.api.SavedViewVisibility;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

/** SavedView JSON 编解码；与 JDBC 适配器共享，避免 application 依赖 infrastructure 实现类。 */
public final class SavedViewJson {
    private static final ObjectMapper JSON = JsonMapper.builder().build();

    private SavedViewJson() {
    }

    public static String write(Object value) {
        if (value == null) {
            return null;
        }
        return JSON.writeValueAsString(value);
    }

    public static SavedView toView(SavedViewRepository.SavedViewRecord record) {
        return new SavedView(
                record.id(),
                record.principalId(),
                record.portal(),
                record.pageId(),
                record.name(),
                record.visibility() == null ? SavedViewVisibility.PRIVATE : record.visibility(),
                record.sharedScopeRef(),
                record.schemaVersion(),
                readFilter(record.filterJson()),
                readSort(record.sortJson()),
                readColumns(record.columnJson()),
                record.isDefault(),
                record.aggregateVersion(),
                record.createdAt(),
                record.updatedAt()
        );
    }

    private static SavedViewFilterAst readFilter(String json) {
        return JSON.readValue(json, SavedViewFilterAst.class);
    }

    private static SavedViewSortSpec readSort(String json) {
        if (json == null || json.isBlank() || "null".equals(json)) {
            return null;
        }
        return JSON.readValue(json, SavedViewSortSpec.class);
    }

    private static List<String> readColumns(String json) {
        if (json == null || json.isBlank() || "null".equals(json)) {
            return null;
        }
        return JSON.readValue(json, new TypeReference<>() {
        });
    }
}
