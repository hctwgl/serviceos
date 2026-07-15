package com.serviceos.project.api;

import java.util.List;
import java.util.UUID;

/**
 * 显式整组修订 Project 的 REGION/NETWORK 关系。
 *
 * <p>两个集合都必须提供；空集合表示终止该类型全部当前关系，null 不具有“保持不变”语义。</p>
 */
public record ReviseProjectScopeRelationsCommand(
        UUID projectId,
        long expectedVersion,
        List<String> regionCodes,
        List<String> networkIds,
        String reason
) {
    public ReviseProjectScopeRelationsCommand {
        if (projectId == null) {
            throw new IllegalArgumentException("projectId must not be null");
        }
        if (expectedVersion < 1) {
            throw new IllegalArgumentException("expectedVersion must be positive");
        }
        if (regionCodes == null || networkIds == null) {
            throw new IllegalArgumentException("regionCodes and networkIds must both be provided");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("reason must not be blank");
        }
        reason = reason.trim();
        if (reason.length() > 500) {
            throw new IllegalArgumentException("reason exceeds 500 characters");
        }
        regionCodes = List.copyOf(regionCodes);
        networkIds = List.copyOf(networkIds);
    }
}
