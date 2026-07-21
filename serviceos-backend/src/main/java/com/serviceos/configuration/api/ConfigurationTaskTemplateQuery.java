package com.serviceos.configuration.api;

import com.serviceos.identity.api.CurrentPrincipal;

import java.util.List;

/** 任务模板产品读模型查询端口。 */
public interface ConfigurationTaskTemplateQuery {
    List<ConfigurationTaskTemplateItem> list(CurrentPrincipal principal, String correlationId);
}
