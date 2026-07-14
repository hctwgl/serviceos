package com.serviceos.dispatch.api;

import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.shared.CommandMetadata;

/** 派单容量权威命令边界。 */
public interface CapacityAuthorityService {
    CapacityCounterReceipt configure(
            CurrentPrincipal principal,
            CommandMetadata metadata,
            ConfigureCapacityCommand command);
}
