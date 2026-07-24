package com.serviceos.configuration.api;

import java.util.Objects;
import java.util.UUID;

/**
 * 原子发布同一解析作用域的后继配置包。
 *
 * <p>expectedCurrentBundleId 是并发保护：只有调用方看到的当前开放版本仍然有效时，
 * 才允许在 successor.effectiveFrom 关闭旧区间并发布新版本。历史工单继续引用旧
 * bundleId 和 manifestDigest，不受版本切换影响。</p>
 */
public record PublishConfigurationBundleSuccessorCommand(
        UUID expectedCurrentBundleId,
        PublishConfigurationBundleCommand successor
) {
    public PublishConfigurationBundleSuccessorCommand {
        expectedCurrentBundleId = Objects.requireNonNull(
                expectedCurrentBundleId, "expectedCurrentBundleId");
        successor = Objects.requireNonNull(successor, "successor");
        if (successor.effectiveUntil() != null) {
            throw new IllegalArgumentException(
                    "successor effectiveUntil must be null when replacing an open bundle");
        }
    }
}
