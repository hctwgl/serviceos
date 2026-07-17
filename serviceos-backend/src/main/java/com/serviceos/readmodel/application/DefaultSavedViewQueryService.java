package com.serviceos.readmodel.application;

import com.serviceos.authorization.api.PrincipalActiveRoleQuery;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.readmodel.api.SavedViewPage;
import com.serviceos.readmodel.api.SavedViewQueryService;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.ProblemCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
class DefaultSavedViewQueryService implements SavedViewQueryService {
    private static final String PORTAL = "ADMIN";

    private final SavedViewRepository repository;
    private final PrincipalActiveRoleQuery activeRoles;
    private final Clock clock;

    DefaultSavedViewQueryService(
            SavedViewRepository repository,
            PrincipalActiveRoleQuery activeRoles,
            Clock clock
    ) {
        this.repository = repository;
        this.activeRoles = activeRoles;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public SavedViewPage list(CurrentPrincipal actor, String correlationId, String pageId) {
        requireText(pageId, "pageId");
        if (!SavedViewFilterCatalog.supports(pageId.trim())) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED, "pageId 不受支持或未接受个人 SavedView");
        }
        Instant now = Instant.now(clock);
        // 列表合并本人视图与可见共享；页面 capability 仍由业务查询入口重新鉴权。
        Set<String> roleIds = activeRoles.listActiveRoleIds(actor.tenantId(), actor.principalId(), now)
                .stream()
                .map(UUID::toString)
                .collect(Collectors.toUnmodifiableSet());
        return new SavedViewPage(
                repository.listVisible(
                        actor.tenantId(), actor.principalId(), PORTAL, pageId.trim(), roleIds),
                now);
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED, field + " 不能为空");
        }
    }
}
