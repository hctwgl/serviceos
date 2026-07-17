package com.serviceos.readmodel.application;

import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.readmodel.api.RecentResourceCommandService;
import com.serviceos.readmodel.api.RecentResourceItem;
import com.serviceos.readmodel.api.RecentResourceTouch;
import com.serviceos.readmodel.api.RecentResourceType;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.ProblemCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

/**
 * 个人最近访问 touch：同事务 upsert last_visited_at 并裁剪超额行。
 * <p>
 * 事务边界：upsert + trim 同事务。幂等键：tenant+principal+portal+type+id。
 * 不要求新 capability；跨主体不可达。失败关闭：未知类型/敏感 displayRef。
 */
@Service
class DefaultRecentResourceCommandService implements RecentResourceCommandService {
    private final RecentResourceRepository repository;
    private final Clock clock;

    DefaultRecentResourceCommandService(RecentResourceRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Override
    @Transactional
    public RecentResourceItem touch(
            CurrentPrincipal actor,
            String correlationId,
            String portal,
            RecentResourceTouch touch
    ) {
        Objects.requireNonNull(actor, "actor must not be null");
        Objects.requireNonNull(touch, "touch must not be null");
        RecentResourceCatalog.requireAdminPortal(portal);
        RecentResourceType type = touch.resourceType() != null
                ? touch.resourceType()
                : null;
        if (type == null) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED, "resourceType 不能为空");
        }
        String resourceId = RecentResourceCatalog.requireResourceId(touch.resourceId());
        String pageId = RecentResourceCatalog.normalizePageId(touch.pageId());
        String displayRef = RecentResourceCatalog.sanitizeDisplayRef(
                type, resourceId, touch.displayRef());
        Instant now = Instant.now(clock);
        Instant createdAt = repository.findOwned(
                        actor.tenantId(),
                        actor.principalId(),
                        RecentResourceCatalog.PORTAL_ADMIN,
                        type.name(),
                        resourceId)
                .map(RecentResourceRepository.RecentResourceRecord::createdAt)
                .orElse(now);

        RecentResourceRepository.RecentResourceRecord record =
                new RecentResourceRepository.RecentResourceRecord(
                        actor.tenantId(),
                        actor.principalId(),
                        RecentResourceCatalog.PORTAL_ADMIN,
                        type.name(),
                        resourceId,
                        pageId,
                        displayRef,
                        now,
                        createdAt
                );
        repository.upsert(record);
        repository.trimExcess(
                actor.tenantId(),
                actor.principalId(),
                RecentResourceCatalog.PORTAL_ADMIN,
                RecentResourceCatalog.MAX_LIST_LIMIT);

        return new RecentResourceItem(
                type,
                resourceId,
                pageId,
                displayRef,
                now,
                RecentResourceCatalog.deepLink(type, resourceId)
        );
    }
}
