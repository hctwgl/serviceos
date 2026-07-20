package com.serviceos.configuration.api;

import java.util.Optional;
import java.util.UUID;

/**
 * 对任务冻结 Bundle 内 FORM/EVIDENCE 做师傅端能力预检。
 *
 * <p>供 Portal Feed/详情等入口在进入表单/资料读写前失败关闭，避免现场中途才发现不兼容。
 * {@code UNKNOWN} 与非师傅端跳过（保留 M253 迁移窗口）。</p>
 */
public interface FrozenBundleClientCapabilityProbe {
    /**
     * 不兼容时抛出 {@code CLIENT_CAPABILITY_UNSUPPORTED}。
     *
     * @param formAssetKey Task.formRef；无表单时传 null
     */
    void requireCompatible(
            String tenantId,
            String clientKind,
            UUID configurationBundleId,
            String configurationBundleDigest,
            String formAssetKey);

    /**
     * @return 不兼容时的中文 detail；兼容或跳过时 empty
     */
    Optional<String> findIncompatibilityDetail(
            String tenantId,
            String clientKind,
            UUID configurationBundleId,
            String configurationBundleDigest,
            String formAssetKey);

    /**
     * M366 / ADR-088 A4-R：解析派单级有效目标客户端（FORM∩EVIDENCE 定向交集）。
     *
     * @param formAssetKey Task.formRef；无表单时传 null（仅看 EVIDENCE）
     */
    EffectiveDispatchClientKinds resolveDispatchTargetClientKinds(
            String tenantId,
            UUID configurationBundleId,
            String configurationBundleDigest,
            String formAssetKey);
}
