package com.serviceos.bootstrap;

import com.serviceos.shared.SystemRedactionPolicy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** 将 Spring Environment 中的总开关同步给日志和各业务输出边界共享的策略。 */
@Component
final class SystemRedactionConfiguration {
    SystemRedactionConfiguration(
            @Value("${serviceos.redaction.enabled:${SERVICEOS_REDACTION_ENABLED:false}}") boolean enabled
    ) {
        SystemRedactionPolicy.configureApplicationValue(enabled);
    }
}
