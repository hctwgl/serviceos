package com.serviceos.authorization.application;

import com.serviceos.authorization.api.MeCapabilitiesView;
import com.serviceos.authorization.api.MeContextView;
import com.serviceos.authorization.api.MeContextsView;
import com.serviceos.authorization.api.MeNavigationItemView;
import com.serviceos.authorization.api.MeNavigationView;
import com.serviceos.authorization.api.MeProfileView;
import com.serviceos.authorization.api.PortalContextQueryService;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.identity.api.PrincipalPersonaQuery;
import com.serviceos.identity.api.PrincipalPersonaView;
import com.serviceos.network.api.NetworkMembershipView;
import com.serviceos.network.api.NetworkTechnicianMembershipView;
import com.serviceos.network.api.PrincipalNetworkAffiliationQuery;
import com.serviceos.network.api.TechnicianProfileView;
import com.serviceos.organization.api.OrgMembershipView;
import com.serviceos.organization.api.PrincipalOrgAffiliationQuery;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.ProblemCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Portal 上下文与导航合成。
 *
 * <p>不变量：contextId 只能选择服务端已返回项；contextVersion 绑定租户 grant generation；
 * 导航可见不等于数据可见或动作允许；CONSUMER Persona 不产生 Portal 入口。</p>
 */
@Service
final class DefaultPortalContextQueryService implements PortalContextQueryService {
    private final PrincipalPersonaQuery personas;
    private final PrincipalOrgAffiliationQuery orgAffiliations;
    private final PrincipalNetworkAffiliationQuery networkAffiliations;
    private final AuthorizationPolicyStore policyStore;
    private final CodePageRegistry pageRegistry;
    private final PageRegistryOverrideStore overrideStore;
    private final Clock clock;

    DefaultPortalContextQueryService(
            PrincipalPersonaQuery personas,
            PrincipalOrgAffiliationQuery orgAffiliations,
            PrincipalNetworkAffiliationQuery networkAffiliations,
            AuthorizationPolicyStore policyStore,
            CodePageRegistry pageRegistry,
            PageRegistryOverrideStore overrideStore,
            Clock clock
    ) {
        this.personas = personas;
        this.orgAffiliations = orgAffiliations;
        this.networkAffiliations = networkAffiliations;
        this.policyStore = policyStore;
        this.pageRegistry = pageRegistry;
        this.overrideStore = overrideStore;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public MeProfileView me(CurrentPrincipal actor, String correlationId) {
        Instant at = clock.instant();
        UUID principalId = PortalContextSupport.requirePrincipalUuid(actor);
        requireActivePrincipal(actor.tenantId(), principalId);
        String version = policyStore.policyVersion(actor.tenantId());
        return new MeProfileView(
                actor.principalId(),
                actor.tenantId(),
                personas.displayName(actor.tenantId(), principalId).orElse(actor.principalId()),
                personas.listEffectivePersonas(actor.tenantId(), principalId, at),
                version,
                at);
    }

    @Override
    @Transactional(readOnly = true)
    public MeContextsView contexts(CurrentPrincipal actor, String correlationId) {
        Instant at = clock.instant();
        UUID principalId = PortalContextSupport.requirePrincipalUuid(actor);
        requireActivePrincipal(actor.tenantId(), principalId);
        List<MeContextView> contexts = resolveContexts(actor, principalId, at);
        return new MeContextsView(contexts, policyStore.policyVersion(actor.tenantId()), at);
    }

    @Override
    @Transactional(readOnly = true)
    public MeCapabilitiesView capabilities(
            CurrentPrincipal actor, String correlationId, String contextId, String expectedContextVersion
    ) {
        Instant at = clock.instant();
        UUID principalId = PortalContextSupport.requirePrincipalUuid(actor);
        requireActivePrincipal(actor.tenantId(), principalId);
        String version = policyStore.policyVersion(actor.tenantId());
        PortalContextSupport.requireMatchingVersion(expectedContextVersion, version);
        MeContextView context = PortalContextSupport.requireContext(
                resolveContexts(actor, principalId, at), requireContextId(contextId));
        List<String> codes = policyStore.listEffectiveCapabilityCodes(
                actor.tenantId(), actor.principalId(), context.scopeType(), context.scopeRef(), at);
        return new MeCapabilitiesView(context.contextId(), context.portal(), codes, version, at);
    }

    @Override
    @Transactional(readOnly = true)
    public MeNavigationView navigation(
            CurrentPrincipal actor, String correlationId, String contextId, String expectedContextVersion
    ) {
        Instant at = clock.instant();
        UUID principalId = PortalContextSupport.requirePrincipalUuid(actor);
        requireActivePrincipal(actor.tenantId(), principalId);
        String version = policyStore.policyVersion(actor.tenantId());
        PortalContextSupport.requireMatchingVersion(expectedContextVersion, version);
        MeContextView context = PortalContextSupport.requireContext(
                resolveContexts(actor, principalId, at), requireContextId(contextId));
        Set<String> capabilities = new HashSet<>(policyStore.listEffectiveCapabilityCodes(
                actor.tenantId(), actor.principalId(), context.scopeType(), context.scopeRef(), at));
        Map<String, PageRegistryOverrideStore.PageOverride> overrides =
                overrideStore.overridesForTenant(actor.tenantId());
        Set<String> enabledGates = overrideStore.enabledFeatureGates(actor.tenantId());

        List<MeNavigationItemView> items = new ArrayList<>();
        for (RegisteredPage page : pageRegistry.forPortal(context.portal())) {
            PageRegistryOverrideStore.PageOverride override = overrides.get(page.pageId());
            if (override != null && !override.enabled()) {
                continue;
            }
            String gate = override != null && override.featureGate().isPresent()
                    ? override.featureGate().get()
                    : page.featureGate();
            // feature gate 失败关闭：页面声明了 gate 则必须在租户启用表中显式开启
            if (gate != null && !gate.isBlank() && !enabledGates.contains(gate)) {
                continue;
            }
            if (!capabilities.containsAll(page.requiredCapabilities())) {
                continue;
            }
            String title = override != null && override.titleOverride().isPresent()
                    ? override.titleOverride().get()
                    : page.defaultTitle();
            int order = override != null && override.sortOrder().isPresent()
                    ? override.sortOrder().get()
                    : page.defaultOrder();
            items.add(new MeNavigationItemView(
                    page.pageId(), page.routeKey(), title, order, page.section(),
                    page.requiredCapabilities()));
        }
        items.sort(Comparator.comparingInt(MeNavigationItemView::order)
                .thenComparing(MeNavigationItemView::pageId));
        return new MeNavigationView(
                context.contextId(),
                context.portal(),
                version,
                CodePageRegistry.CATALOG_VERSION,
                items,
                at);
    }

    private List<MeContextView> resolveContexts(CurrentPrincipal actor, UUID principalId, Instant at) {
        List<PrincipalPersonaView> effective = personas.listEffectivePersonas(
                actor.tenantId(), principalId, at);
        Set<String> personaTypes = new LinkedHashSet<>();
        for (PrincipalPersonaView persona : effective) {
            personaTypes.add(persona.personaType());
        }
        String version = policyStore.policyVersion(actor.tenantId());
        List<MeContextView> contexts = new ArrayList<>();

        List<OrgMembershipView> orgMemberships = orgAffiliations.listActiveMemberships(
                actor.tenantId(), principalId, at);
        List<String> orgIds = orgMemberships.stream()
                .map(membership -> membership.organizationId().toString())
                .distinct()
                .toList();

        if (personaTypes.contains("INTERNAL_EMPLOYEE") || personaTypes.contains("SERVICE_ACCOUNT")) {
            List<String> adminCaps = policyStore.listEffectiveCapabilityCodes(
                    actor.tenantId(), actor.principalId(), "TENANT", actor.tenantId(), at);
            if (!adminCaps.isEmpty()) {
                String personaType = personaTypes.contains("INTERNAL_EMPLOYEE")
                        ? "INTERNAL_EMPLOYEE" : "SERVICE_ACCOUNT";
                contexts.add(new MeContextView(
                        PortalContextSupport.adminContextId(actor.tenantId()),
                        "ADMIN",
                        personaType,
                        "TENANT",
                        actor.tenantId(),
                        new MeContextView.MeContextScopeSummary(orgIds, List.of(), List.of()),
                        version));
            }
        }

        if (personaTypes.contains("NETWORK_MEMBER")) {
            List<NetworkMembershipView> networkMemberships =
                    networkAffiliations.listActiveNetworkMemberships(actor.tenantId(), principalId, at);
            for (NetworkMembershipView membership : networkMemberships) {
                List<String> caps = policyStore.listEffectiveCapabilityCodes(
                        actor.tenantId(), actor.principalId(),
                        "NETWORK", membership.serviceNetworkId().toString(), at);
                if (caps.isEmpty()) {
                    continue;
                }
                contexts.add(new MeContextView(
                        PortalContextSupport.networkContextId(membership.serviceNetworkId()),
                        "NETWORK",
                        "NETWORK_MEMBER",
                        "NETWORK",
                        membership.serviceNetworkId().toString(),
                        new MeContextView.MeContextScopeSummary(
                                List.of(),
                                List.of(membership.serviceNetworkId().toString()),
                                List.of()),
                        version));
            }
        }

        if (personaTypes.contains("TECHNICIAN")) {
            Optional<TechnicianProfileView> profile = networkAffiliations.findActiveTechnicianProfile(
                    actor.tenantId(), principalId);
            if (profile.isPresent()) {
                List<NetworkTechnicianMembershipView> techMemberships =
                        networkAffiliations.listActiveTechnicianMemberships(
                                actor.tenantId(), profile.get().id(), at);
                for (NetworkTechnicianMembershipView membership : techMemberships) {
                    List<String> caps = policyStore.listEffectiveCapabilityCodes(
                            actor.tenantId(), actor.principalId(),
                            "NETWORK", membership.serviceNetworkId().toString(), at);
                    if (caps.isEmpty()) {
                        continue;
                    }
                    contexts.add(new MeContextView(
                            PortalContextSupport.technicianContextId(membership.serviceNetworkId()),
                            "TECHNICIAN",
                            "TECHNICIAN",
                            "NETWORK",
                            membership.serviceNetworkId().toString(),
                            new MeContextView.MeContextScopeSummary(
                                    List.of(),
                                    List.of(membership.serviceNetworkId().toString()),
                                    List.of()),
                            version));
                }
            }
        }
        // CONSUMER Persona 可存在于 Schema/目录，但绝不产生 Portal 上下文或导航入口。
        return List.copyOf(contexts);
    }

    private void requireActivePrincipal(String tenantId, UUID principalId) {
        if (!personas.isActive(tenantId, principalId)) {
            throw new BusinessProblem(ProblemCode.ACCESS_DENIED, "主体已停用或不可用");
        }
    }

    private static String requireContextId(String contextId) {
        if (contextId == null || contextId.isBlank()) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED, "contextId 不能为空");
        }
        return contextId.trim();
    }
}
