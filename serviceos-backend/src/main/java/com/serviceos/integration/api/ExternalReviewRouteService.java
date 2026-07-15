package com.serviceos.integration.api;

import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.shared.CommandMetadata;

/** 外部审核回调路由登记端口。 */
public interface ExternalReviewRouteService {
    ExternalReviewRouteView register(
            CurrentPrincipal principal,
            CommandMetadata metadata,
            RegisterExternalReviewRouteCommand command);
}
