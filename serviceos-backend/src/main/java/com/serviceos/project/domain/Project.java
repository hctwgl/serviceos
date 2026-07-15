package com.serviceos.project.domain;

import com.serviceos.project.api.CreateProjectCommand;
import com.serviceos.project.api.ProjectView;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 运营项目聚合。车企、品牌与服务产品通过独立目录/绑定表达，禁止在此聚合中写品牌分支。
 */
public record Project(
        UUID id,
        String tenantId,
        String code,
        String clientId,
        String name,
        LocalDate startsOn,
        LocalDate endsOn,
        List<String> regionCodes,
        List<String> networkIds,
        Status status,
        long version,
        Instant createdAt
) {
    public enum Status { DRAFT, ACTIVE, SUSPENDED, CLOSED }

    public static Project create(String tenantId, CreateProjectCommand command, UUID id, Instant now) {
        String code = requireText(command.code(), "code", 64).toUpperCase();
        if (!code.matches("[A-Z0-9][A-Z0-9_-]*")) {
            throw new IllegalArgumentException("code contains unsupported characters");
        }
        String clientId = requireText(command.clientId(), "clientId", 128);
        String name = requireText(command.name(), "name", 200);
        if (command.startsOn() == null) {
            throw new IllegalArgumentException("startsOn must not be null");
        }
        if (command.endsOn() != null && command.endsOn().isBefore(command.startsOn())) {
            throw new IllegalArgumentException("endsOn must not be before startsOn");
        }
        List<String> regionCodes = requireRegionCodes(command.regionCodes());
        List<String> networkIds = requireStableReferences(command.networkIds(), "networkIds");
        return new Project(id, tenantId, code, clientId, name, command.startsOn(), command.endsOn(), regionCodes,
                networkIds, Status.DRAFT, 1L, now);
    }

    public ProjectView toView() {
        return new ProjectView(id, tenantId, code, clientId, name, startsOn, endsOn, regionCodes, networkIds,
                status.name(), version, createdAt);
    }

    private static List<String> requireRegionCodes(List<String> values) {
        return requireStableReferences(values, "regionCodes");
    }

    private static List<String> requireStableReferences(List<String> values, String field) {
        if (values == null) {
            throw new IllegalArgumentException(field + " must not be null");
        }
        if (values.size() > 100) {
            throw new IllegalArgumentException(field + " exceeds 100 items");
        }
        Set<String> unique = new HashSet<>();
        for (String value : values) {
            if (value == null || value.isBlank() || !value.equals(value.trim()) || value.length() > 128) {
                throw new IllegalArgumentException(field + " contains an invalid stable reference");
            }
            if (!unique.add(value)) {
                throw new IllegalArgumentException(field + " contains duplicate references");
            }
        }
        return values.stream().sorted().toList();
    }

    private static String requireText(String value, String name, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        String trimmed = value.trim();
        if (trimmed.length() > maxLength) {
            throw new IllegalArgumentException(name + " exceeds " + maxLength + " characters");
        }
        return trimmed;
    }
}
