package com.serviceos.configuration.api;

import com.serviceos.identity.api.CurrentPrincipal;

import java.util.UUID;

/** 配置依赖分析：扫描 WORKFLOW 引用并对照已发布资产/Bundle 清单。 */
public interface ConfigurationDependencyAnalysisService {
    ConfigurationDependencyReport analyzeDraft(
            CurrentPrincipal principal, String correlationId, UUID draftId);

    ConfigurationDependencyReport analyze(
            CurrentPrincipal principal, String correlationId,
            AnalyzeConfigurationDependenciesCommand command);
}
