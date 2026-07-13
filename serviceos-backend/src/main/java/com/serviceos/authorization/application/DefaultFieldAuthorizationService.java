package com.serviceos.authorization.application;

import com.serviceos.authorization.api.AuthorizationDecision;
import com.serviceos.authorization.api.AuthorizationService;
import com.serviceos.authorization.api.FieldAccessDecision;
import com.serviceos.authorization.api.FieldAuthorizationDecision;
import com.serviceos.authorization.api.FieldAuthorizationRequest;
import com.serviceos.authorization.api.FieldAuthorizationService;
import com.serviceos.identity.api.CurrentPrincipal;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;

/**
 * 字段授权执行器：动作/数据范围拒绝时所有字段隐藏；规则缺失时同样默认隐藏，避免新增字段意外泄露。
 */
@Service
final class DefaultFieldAuthorizationService implements FieldAuthorizationService {
    private static final String DENY_ALL_POLICY_VERSION = "field-policy-deny-all-v1";

    private final AuthorizationService authorization;
    private final FieldPolicyStore policies;

    DefaultFieldAuthorizationService(AuthorizationService authorization, FieldPolicyStore policies) {
        this.authorization = authorization;
        this.policies = policies;
    }

    @Override
    public FieldAuthorizationDecision evaluate(
            CurrentPrincipal principal,
            FieldAuthorizationRequest request,
            String correlationId
    ) {
        AuthorizationDecision action = authorization.authorize(
                principal, request.authorization(), correlationId);
        if (action.effect() == AuthorizationDecision.Effect.DENY) {
            return hiddenDecision(request, action.policyVersion() + "+" + DENY_ALL_POLICY_VERSION);
        }

        FieldPolicyMatch fieldPolicy = policies.resolve(
                request.authorization().tenantId(),
                action.matchedGrantIds(),
                request.authorization().capability(),
                request.authorization().resourceType(),
                request.fieldCodes());
        LinkedHashMap<String, FieldAccessDecision> result = new LinkedHashMap<>();
        request.fieldCodes().stream().sorted().forEach(field -> result.put(
                field,
                fieldPolicy.decisions().getOrDefault(field, FieldAccessDecision.hidden())));
        return new FieldAuthorizationDecision(
                result,
                action.matchedGrantIds(),
                action.policyVersion() + "+" + fieldPolicy.policyVersion());
    }

    private static FieldAuthorizationDecision hiddenDecision(
            FieldAuthorizationRequest request,
            String policyVersion
    ) {
        LinkedHashMap<String, FieldAccessDecision> hidden = new LinkedHashMap<>();
        request.fieldCodes().stream().sorted()
                .forEach(field -> hidden.put(field, FieldAccessDecision.hidden()));
        return new FieldAuthorizationDecision(hidden, java.util.List.of(), policyVersion);
    }
}
