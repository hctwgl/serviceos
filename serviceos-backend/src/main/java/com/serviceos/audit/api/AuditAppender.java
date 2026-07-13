package com.serviceos.audit.api;

public interface AuditAppender {
    void append(AuditEntry entry);
}
