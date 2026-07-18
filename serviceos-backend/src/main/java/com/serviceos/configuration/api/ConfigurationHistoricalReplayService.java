package com.serviceos.configuration.api;

import com.serviceos.identity.api.CurrentPrincipal;

/** 对已发布冻结 Bundle 的 WORKFLOW 做无副作用历史回放。 */
public interface ConfigurationHistoricalReplayService {
    ConfigurationHistoricalReplayReport replay(
            CurrentPrincipal principal,
            String correlationId,
            RunConfigurationHistoricalReplayCommand command);
}
