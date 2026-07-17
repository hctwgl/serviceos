package com.serviceos.readmodel.application;

import com.serviceos.authorization.api.AuthorizationDecision;
import com.serviceos.authorization.api.AuthorizationRequest;
import com.serviceos.authorization.api.AuthorizationService;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.identity.api.SecurityPrincipalQueryService;
import com.serviceos.identity.api.SecurityPrincipalView;
import com.serviceos.network.api.NetworkQueryService;
import com.serviceos.network.api.ServiceNetworkView;
import com.serviceos.network.api.TechnicianProfileView;
import com.serviceos.readmodel.api.ControlledSearchHit;
import com.serviceos.readmodel.api.ControlledSearchMeta;
import com.serviceos.readmodel.api.ControlledSearchQueryService;
import com.serviceos.readmodel.api.ControlledSearchResult;
import com.serviceos.readmodel.api.ControlledSearchType;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.ProblemCode;
import com.serviceos.shared.Sha256;
import com.serviceos.workorder.api.WorkOrderQuery;
import com.serviceos.workorder.api.WorkOrderQueryService;
import com.serviceos.workorder.api.WorkOrderView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * M192 Admin 受控搜索：经既有授权端口 fan-in。
 * <p>
 * 事务边界：只读编排，不写业务事实。幂等键：无（只读）。失败关闭：未支持 type、过宽手机号、
 * 缺 search.read、超时。跨租户依赖各端口自身 tenant/scope 收敛。
 */
@Service
final class DefaultControlledSearchQueryService implements ControlledSearchQueryService {
    private static final Logger log = LoggerFactory.getLogger(DefaultControlledSearchQueryService.class);
    private static final String SEARCH_READ = "search.read";
    private static final String WORK_ORDER_READ = "workOrder.read";
    private static final String NETWORK_READ = "network.read";
    private static final String IDENTITY_READ = "identity.read";
    private static final int PER_TYPE_LIMIT = 10;
    private static final int TOTAL_CAP = 40;
    private static final Duration BUDGET = Duration.ofSeconds(3);
    private static final Pattern PHONE_FULL = Pattern.compile("^1\\d{10}$");
    private static final Pattern DIGITS_ONLY = Pattern.compile("^\\d+$");
    private static final Pattern UUID_PATTERN = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    private final AuthorizationService authorization;
    private final WorkOrderQueryService workOrders;
    private final NetworkQueryService networks;
    private final SecurityPrincipalQueryService principals;
    private final Clock clock;

    DefaultControlledSearchQueryService(
            AuthorizationService authorization,
            WorkOrderQueryService workOrders,
            NetworkQueryService networks,
            SecurityPrincipalQueryService principals,
            Clock clock
    ) {
        this.authorization = authorization;
        this.workOrders = workOrders;
        this.networks = networks;
        this.principals = principals;
        this.clock = clock;
    }

    @Override
    // 不包外层事务：各授权端口自带只读事务；避免底层 get 抛 BusinessProblem 污染编排层。
    public ControlledSearchResult search(
            CurrentPrincipal actor, String correlationId, String q, String typesCsv
    ) {
        Objects.requireNonNull(actor, "actor must not be null");
        Instant deadline = clock.instant().plus(BUDGET);
        String normalized = normalizeQuery(q);
        String qDigest = Sha256.digest(normalized);
        // 入口能力：缺 search.read 整请求失败，不降级为空结果伪装成功。
        authorization.require(actor, AuthorizationRequest.tenantCapability(
                SEARCH_READ, actor.tenantId(), "Search", "admin-search"), correlationId);

        List<ControlledSearchType> requested = parseTypes(typesCsv);
        validatePhoneRule(normalized);

        List<ControlledSearchType> searched = new ArrayList<>();
        List<ControlledSearchType> omitted = new ArrayList<>();
        List<ControlledSearchHit> items = new ArrayList<>();

        for (ControlledSearchType type : requested) {
            ensureBudget(deadline, qDigest, correlationId);
            if (!hasTypeCapability(actor, type, correlationId)) {
                omitted.add(type);
                continue;
            }
            searched.add(type);
            List<ControlledSearchHit> hits = switch (type) {
                case WORK_ORDER -> searchWorkOrders(actor, correlationId, normalized, false);
                case EXTERNAL_ORDER -> searchWorkOrders(actor, correlationId, normalized, true);
                case NETWORK -> searchNetworks(actor, correlationId, normalized);
                case TECHNICIAN -> searchTechnicians(actor, correlationId, normalized);
            };
            for (ControlledSearchHit hit : hits) {
                if (items.size() >= TOTAL_CAP) {
                    break;
                }
                items.add(hit);
            }
        }

        log.info("受控搜索完成 tenant={} principal={} qDigest={} searched={} omitted={} hits={} corr={}",
                actor.tenantId(), actor.principalId(), qDigest, searched, omitted, items.size(), correlationId);

        return new ControlledSearchResult(
                List.copyOf(items),
                new ControlledSearchMeta(qDigest, List.copyOf(requested), List.copyOf(searched), List.copyOf(omitted)),
                clock.instant());
    }

    private List<ControlledSearchHit> searchWorkOrders(
            CurrentPrincipal actor, String correlationId, String q, boolean asExternalOrder
    ) {
        List<ControlledSearchHit> hits = new ArrayList<>();
        if (UUID_PATTERN.matcher(q).matches() && !asExternalOrder) {
            try {
                WorkOrderView view = workOrders.get(actor, correlationId, UUID.fromString(q)).workOrder();
                hits.add(workOrderHit(view, asExternalOrder, "WORK_ORDER_ID"));
            } catch (BusinessProblem problem) {
                if (problem.code() != ProblemCode.RESOURCE_NOT_FOUND
                        && problem.code() != ProblemCode.ACCESS_DENIED) {
                    throw problem;
                }
            }
            return hits;
        }
        if (UUID_PATTERN.matcher(q).matches() && asExternalOrder) {
            return hits;
        }
        // 精确外部单号：走授权列表的 externalOrderCode 筛选，避免跨模块 raw SQL。
        var page = workOrders.list(actor, correlationId,
                new WorkOrderQuery(null, null, null, q, null, PER_TYPE_LIMIT));
        for (WorkOrderView view : page.items()) {
            if (hits.size() >= PER_TYPE_LIMIT) {
                break;
            }
            hits.add(workOrderHit(view, asExternalOrder, "EXTERNAL_ORDER_CODE"));
        }
        return hits;
    }

    private static ControlledSearchHit workOrderHit(
            WorkOrderView view, boolean asExternalOrder, String reason
    ) {
        ControlledSearchType type = asExternalOrder
                ? ControlledSearchType.EXTERNAL_ORDER
                : ControlledSearchType.WORK_ORDER;
        String primary = asExternalOrder ? view.externalOrderCode() : view.id().toString();
        String secondary = asExternalOrder ? maskId(view.id().toString()) : maskCode(view.externalOrderCode());
        return new ControlledSearchHit(
                view.id().toString(),
                type,
                primary,
                secondary,
                reason,
                "/work-orders/" + view.id());
    }

    private List<ControlledSearchHit> searchNetworks(
            CurrentPrincipal actor, String correlationId, String q
    ) {
        String prefix = q.toLowerCase(Locale.ROOT);
        List<ControlledSearchHit> hits = new ArrayList<>();
        for (ServiceNetworkView network : networks.listServiceNetworks(actor, correlationId, null).items()) {
            if (hits.size() >= PER_TYPE_LIMIT) {
                break;
            }
            boolean codeMatch = network.networkCode() != null
                    && network.networkCode().toLowerCase(Locale.ROOT).startsWith(prefix);
            boolean nameMatch = network.networkName() != null
                    && network.networkName().toLowerCase(Locale.ROOT).startsWith(prefix);
            if (!codeMatch && !nameMatch) {
                continue;
            }
            hits.add(new ControlledSearchHit(
                    network.id().toString(),
                    ControlledSearchType.NETWORK,
                    network.networkName(),
                    maskCode(network.networkCode()),
                    codeMatch ? "NETWORK_CODE_PREFIX" : "NETWORK_NAME_PREFIX",
                    "/networks/" + network.id()));
        }
        return hits;
    }

    private List<ControlledSearchHit> searchTechnicians(
            CurrentPrincipal actor, String correlationId, String q
    ) {
        List<ControlledSearchHit> hits = new ArrayList<>();
        String prefix = q.toLowerCase(Locale.ROOT);
        Set<UUID> matchedPrincipals = new LinkedHashSet<>();

        // 工号精确：经 identity 授权目录；缺 identity.read 时跳过工号支路，不抛 403 整失败。
        if (hasCapability(actor, IDENTITY_READ, "SecurityPrincipal", "search", correlationId)) {
            for (SecurityPrincipalView principal : principals.list(
                    actor, correlationId, q, "ACTIVE", null, PER_TYPE_LIMIT).items()) {
                if (principal.employeeNumber() != null && principal.employeeNumber().equals(q)) {
                    matchedPrincipals.add(principal.id());
                }
            }
        }

        for (TechnicianProfileView profile : networks.listTechnicianProfiles(actor, correlationId).items()) {
            if (hits.size() >= PER_TYPE_LIMIT) {
                break;
            }
            boolean nameMatch = profile.displayName() != null
                    && profile.displayName().toLowerCase(Locale.ROOT).startsWith(prefix);
            boolean employeeMatch = matchedPrincipals.contains(profile.principalId());
            if (!nameMatch && !employeeMatch) {
                continue;
            }
            hits.add(new ControlledSearchHit(
                    profile.id().toString(),
                    ControlledSearchType.TECHNICIAN,
                    profile.displayName(),
                    maskId(profile.principalId().toString()),
                    employeeMatch ? "EMPLOYEE_NUMBER_EXACT" : "DISPLAY_NAME_PREFIX",
                    "/technicians/" + profile.id()));
        }
        return hits;
    }

    private boolean hasTypeCapability(CurrentPrincipal actor, ControlledSearchType type, String correlationId) {
        return switch (type) {
            case WORK_ORDER, EXTERNAL_ORDER -> hasCapability(
                    actor, WORK_ORDER_READ, "WorkOrder", "search", correlationId);
            case NETWORK, TECHNICIAN -> hasCapability(
                    actor, NETWORK_READ, "Network", "search", correlationId);
        };
    }

    private boolean hasCapability(
            CurrentPrincipal actor, String capability, String resourceType, String resourceId, String correlationId
    ) {
        AuthorizationDecision decision = authorization.authorize(
                actor,
                AuthorizationRequest.tenantCapability(capability, actor.tenantId(), resourceType, resourceId),
                correlationId);
        return decision.effect() == AuthorizationDecision.Effect.ALLOW;
    }

    private static List<ControlledSearchType> parseTypes(String typesCsv) {
        if (typesCsv == null || typesCsv.isBlank()) {
            return List.copyOf(EnumSet.allOf(ControlledSearchType.class));
        }
        Set<ControlledSearchType> types = EnumSet.noneOf(ControlledSearchType.class);
        for (String raw : typesCsv.split(",")) {
            String token = raw.trim();
            if (token.isEmpty()) {
                continue;
            }
            try {
                types.add(ControlledSearchType.valueOf(token));
            } catch (IllegalArgumentException ex) {
                throw new BusinessProblem(ProblemCode.SEARCH_TERM_NOT_ALLOWED,
                        "不支持的搜索类型: " + token);
            }
        }
        if (types.isEmpty()) {
            throw new BusinessProblem(ProblemCode.SEARCH_TERM_NOT_ALLOWED, "types 不能为空");
        }
        return List.copyOf(types);
    }

    private static String normalizeQuery(String q) {
        if (q == null || q.isBlank()) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED, "q 不能为空");
        }
        String trimmed = q.trim();
        if (trimmed.length() > 200) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED, "q 过长");
        }
        boolean uuid = UUID_PATTERN.matcher(trimmed).matches();
        boolean phoneLast4 = DIGITS_ONLY.matcher(trimmed).matches() && trimmed.length() == 4;
        if (!uuid && !phoneLast4 && trimmed.length() < 2) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED, "q 长度不足");
        }
        return trimmed;
    }

    /**
     * 完整手机号形态失败关闭；仅允许末四位。不把原文写入异常 detail 之外的日志字段。
     */
    private static void validatePhoneRule(String q) {
        String digits = q.replaceAll("[\\s\\-()]", "");
        if (PHONE_FULL.matcher(digits).matches()
                || (DIGITS_ONLY.matcher(digits).matches() && digits.length() > 4 && digits.length() >= 8)) {
            throw new BusinessProblem(ProblemCode.SEARCH_TERM_NOT_ALLOWED,
                    "手机号仅允许末四位精确搜索");
        }
    }

    private static void ensureBudget(Instant deadline, String qDigest, String correlationId) {
        if (Instant.now().isAfter(deadline)) {
            log.warn("受控搜索超时失败关闭 qDigest={} corr={}", qDigest, correlationId);
            throw new BusinessProblem(ProblemCode.INTERNAL_ERROR, "搜索超时，请缩小范围后重试");
        }
    }

    private static String maskCode(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        if (value.length() <= 4) {
            return "****";
        }
        return "****" + value.substring(value.length() - 4);
    }

    private static String maskId(String value) {
        if (value == null || value.length() < 8) {
            return "********";
        }
        return value.substring(0, 4) + "…" + value.substring(value.length() - 4);
    }
}
