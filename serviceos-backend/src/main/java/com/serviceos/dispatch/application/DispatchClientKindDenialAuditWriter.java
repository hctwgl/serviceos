package com.serviceos.dispatch.application;

import com.serviceos.audit.api.AuditAppender;
import com.serviceos.audit.api.AuditEntry;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 派单 kind 硬拒绝审计使用独立事务，确保外层 ManualAssign 回滚后仍保留 DENY 证据。
 */
@Component
class DispatchClientKindDenialAuditWriter {
    private final AuditAppender audit;

    DispatchClientKindDenialAuditWriter(AuditAppender audit) {
        this.audit = audit;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void appendDenied(AuditEntry entry) {
        audit.append(entry);
    }
}
