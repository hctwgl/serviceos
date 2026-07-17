package com.serviceos.readmodel.application;

import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.readmodel.api.SavedViewPage;
import com.serviceos.readmodel.api.SavedViewQueryService;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.ProblemCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

@Service
class DefaultSavedViewQueryService implements SavedViewQueryService {
    private static final String PORTAL = "ADMIN";

    private final SavedViewRepository repository;
    private final Clock clock;

    DefaultSavedViewQueryService(SavedViewRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public SavedViewPage list(CurrentPrincipal actor, String correlationId, String pageId) {
        requireText(pageId, "pageId");
        if (!SavedViewFilterCatalog.supports(pageId.trim())) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED, "pageId 不受支持或未接受个人 SavedView");
        }
        // 列表只返回本人租户内视图；页面 capability 仍由业务查询入口重新鉴权。
        return new SavedViewPage(
                repository.listByOwnerPage(actor.tenantId(), actor.principalId(), PORTAL, pageId.trim()),
                Instant.now(clock));
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED, field + " 不能为空");
        }
    }
}
