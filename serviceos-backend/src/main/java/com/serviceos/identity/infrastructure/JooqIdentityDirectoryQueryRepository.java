package com.serviceos.identity.infrastructure;

import com.serviceos.identity.application.IdentityDirectoryQueryRepository;
import com.serviceos.identity.domain.SecurityPrincipal;
import com.serviceos.jooq.generated.tables.IdnPersonProfile;
import com.serviceos.jooq.generated.tables.IdnSecurityPrincipal;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

import static com.serviceos.jooq.generated.tables.IdnPersonProfile.IDN_PERSON_PROFILE;
import static com.serviceos.jooq.generated.tables.IdnSecurityPrincipal.IDN_SECURITY_PRINCIPAL;

/**
 * 主体目录 jOOQ 查询适配器：状态/关键词筛选、游标分页与 tenant 隔离全部在 SQL 内完成。
 * jOOQ 生成类型把 timestamptz 统一映射为 Instant，旧 MyBatis Map 结果的多类型时间容错随之移除。
 */
@Repository
final class JooqIdentityDirectoryQueryRepository implements IdentityDirectoryQueryRepository {
    private final DSLContext dsl;

    JooqIdentityDirectoryQueryRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public List<SecurityPrincipal> findPage(
            String tenantId, String query, String status, String cursorName, UUID cursorId, int fetchSize
    ) {
        IdnSecurityPrincipal p = IDN_SECURITY_PRINCIPAL;
        IdnPersonProfile profile = IDN_PERSON_PROFILE;
        Field<String> loweredName = DSL.lower(profile.DISPLAY_NAME);
        Condition condition = p.TENANT_ID.eq(tenantId);
        if (status != null) {
            condition = condition.and(p.PRINCIPAL_STATUS.eq(status));
        }
        if (query != null) {
            condition = condition.and(loweredName.like(DSL.lower(DSL.val(query)).concat("%"))
                    .or(profile.EMPLOYEE_NUMBER.eq(query)));
        }
        if (cursorName != null) {
            condition = condition.and(DSL.row(loweredName, p.PRINCIPAL_ID)
                    .gt(DSL.lower(DSL.val(cursorName)), DSL.val(cursorId)));
        }
        return dsl.select(p.PRINCIPAL_ID, p.TENANT_ID, p.PRINCIPAL_TYPE, p.PRINCIPAL_STATUS,
                        p.AGGREGATE_VERSION, p.CREATED_AT, p.UPDATED_AT,
                        p.DISABLED_AT, p.DISABLED_BY, p.DISABLED_REASON,
                        profile.DISPLAY_NAME, profile.EMPLOYEE_NUMBER, profile.PROFILE_VERSION)
                .from(p)
                .join(profile)
                .on(profile.TENANT_ID.eq(p.TENANT_ID))
                .and(profile.PRINCIPAL_ID.eq(p.PRINCIPAL_ID))
                .where(condition)
                .orderBy(loweredName, p.PRINCIPAL_ID)
                .limit(fetchSize)
                .fetch(this::mapPrincipal);
    }

    private SecurityPrincipal mapPrincipal(Record record) {
        IdnSecurityPrincipal p = IDN_SECURITY_PRINCIPAL;
        IdnPersonProfile profile = IDN_PERSON_PROFILE;
        return new SecurityPrincipal(
                record.get(p.PRINCIPAL_ID), record.get(p.TENANT_ID),
                SecurityPrincipal.Type.valueOf(record.get(p.PRINCIPAL_TYPE)),
                SecurityPrincipal.Status.valueOf(record.get(p.PRINCIPAL_STATUS)),
                record.get(p.AGGREGATE_VERSION),
                record.get(p.CREATED_AT), record.get(p.UPDATED_AT),
                record.get(p.DISABLED_AT), record.get(p.DISABLED_BY), record.get(p.DISABLED_REASON),
                record.get(profile.DISPLAY_NAME), record.get(profile.EMPLOYEE_NUMBER),
                record.get(profile.PROFILE_VERSION));
    }
}
