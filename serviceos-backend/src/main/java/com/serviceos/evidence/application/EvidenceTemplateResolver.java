package com.serviceos.evidence.application;

import com.serviceos.configuration.api.ConfigurationAssetDefinition;
import com.serviceos.configuration.api.ExpressionContext;

import java.util.function.Supplier;

/** 配置 JSON 到资料要求的解释端口；条件语义由 ADR-018 求值，未知路径失败关闭。 */
public interface EvidenceTemplateResolver {
    String VERSION = "CONDITIONAL_EVIDENCE_V1";

    ResolvedEvidenceTemplate resolve(
            ConfigurationAssetDefinition template,
            String stageCode,
            Supplier<ExpressionContext> expressionContext
    );
}
