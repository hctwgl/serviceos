package com.serviceos.identity.application;

import com.serviceos.identity.domain.SecurityPrincipal;

import java.util.List;
import java.util.UUID;

/** 主体目录查询端口；筛选和 tenant 隔离必须在 SQL 内完成。 */
public interface IdentityDirectoryQueryRepository {
    List<SecurityPrincipal> findPage(
            String tenantId, String query, String status, String cursorName, UUID cursorId, int fetchSize);
}
