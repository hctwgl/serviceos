package com.serviceos.configuration.api;

import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.shared.CommandMetadata;

import java.util.List;
import java.util.UUID;

/** 配置设计器草稿生命周期：保存、校验、Diff、审批、发布。 */
public interface ConfigurationDraftService {
    ConfigurationDraftView create(CurrentPrincipal principal, CommandMetadata metadata,
            CreateConfigurationDraftCommand command);

    ConfigurationDraftView update(CurrentPrincipal principal, CommandMetadata metadata,
            UpdateConfigurationDraftCommand command);

    ConfigurationDraftView get(CurrentPrincipal principal, String correlationId, UUID draftId);

    List<ConfigurationDraftView> list(CurrentPrincipal principal, String correlationId,
            ConfigurationAssetType assetType);

    ConfigurationDraftView validate(CurrentPrincipal principal, CommandMetadata metadata, UUID draftId);

    ConfigurationDraftDiffView diff(CurrentPrincipal principal, String correlationId, UUID draftId);

    ConfigurationDraftView approve(CurrentPrincipal principal, CommandMetadata metadata,
            ApproveConfigurationDraftCommand command);

    ConfigurationDraftView publish(CurrentPrincipal principal, CommandMetadata metadata, UUID draftId);
}
