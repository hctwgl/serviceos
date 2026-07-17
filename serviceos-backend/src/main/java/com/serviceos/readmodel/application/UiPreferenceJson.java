package com.serviceos.readmodel.application;

import com.serviceos.readmodel.api.UiPreferenceEntry;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/** UI Preference JSON 编解码。 */
public final class UiPreferenceJson {
    private static final ObjectMapper JSON = JsonMapper.builder().build();

    private UiPreferenceJson() {
    }

    public static String write(JsonNode value) {
        return JSON.writeValueAsString(value);
    }

    public static JsonNode read(String json) {
        return JSON.readTree(json);
    }

    public static UiPreferenceEntry toEntry(UiPreferenceRepository.UiPreferenceRecord record) {
        return new UiPreferenceEntry(
                record.preferenceKey(),
                read(record.valueJson()),
                record.schemaVersion(),
                record.aggregateVersion(),
                record.updatedAt()
        );
    }
}
