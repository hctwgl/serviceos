package com.serviceos.readmodel.api;

import tools.jackson.databind.JsonNode;

/**
 * PUT 单键写入：value + schemaVersion；expectedVersion 可选，用于按键乐观并发。
 */
public record UiPreferenceWrite(
        JsonNode value,
        int schemaVersion,
        Long expectedVersion
) {
}
