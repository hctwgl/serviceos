package com.serviceos.evidence.application;

import com.serviceos.configuration.api.ConfigurationAssetDefinition;

import java.util.List;

/** 配置 JSON 到固定资料要求的解释端口；不获批的条件语义必须由实现失败关闭。 */
public interface EvidenceTemplateResolver {
    String VERSION = "FIXED_EVIDENCE_V1";

    List<ResolvedEvidenceRequirement> resolve(
            ConfigurationAssetDefinition template, String stageCode);
}
