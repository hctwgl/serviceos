package com.serviceos.configuration.api;

import java.util.List;

/**
 * 师傅端运行时客户端能力门禁。
 *
 * <p>对已知 {@code TECHNICIAN_WEB}/{@code TECHNICIAN_IOS} 校验冻结 FORM/EVIDENCE 定义所需能力；
 * 不兼容时抛出 {@code CLIENT_CAPABILITY_UNSUPPORTED}。{@code UNKNOWN} 与非师傅端跳过，
 * 以保留 M253 旧客户端 Header 迁移窗口，不得借此绕过已知师傅端判断。</p>
 */
public interface ClientCapabilityRuntimeGate {
    void requireCompatible(
            String clientKind, ConfigurationAssetType assetType, String definitionJson);

    /**
     * 带定向目标的运行时校验：若 {@code supportedClientKinds} 非空且当前客户端不在其中，直接拒单。
     */
    void requireCompatible(
            String clientKind,
            ConfigurationAssetType assetType,
            String definitionJson,
            List<String> supportedClientKinds);

    /**
     * 由已解析槽位构造合成 EVIDENCE 定义后校验。
     *
     * @param requirementDefinitionJsons 各槽位的 requirement 定义（可含 requiredWhen）
     * @param mediaTypes 各槽位 mediaType
     */
    void requireCompatibleEvidenceSlots(
            String clientKind, List<String> mediaTypes, List<String> requirementDefinitionJsons);

    void requireCompatibleEvidenceSlots(
            String clientKind,
            List<String> mediaTypes,
            List<String> requirementDefinitionJsons,
            List<String> supportedClientKinds);
}
