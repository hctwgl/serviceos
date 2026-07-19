package com.serviceos.evidence.application;

import com.serviceos.audit.api.AuditAppender;
import com.serviceos.audit.api.AuditEntry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 拒绝裁决时独立提交审计，避免与 decide 事务一并回滚导致阻断不可审查。
 */
@Service
class ReviewRuleDenyAuditor {
    private final AuditAppender audit;

    ReviewRuleDenyAuditor(AuditAppender audit) {
        this.audit = audit;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void appendDenied(AuditEntry entry) {
        audit.append(entry);
    }
}
