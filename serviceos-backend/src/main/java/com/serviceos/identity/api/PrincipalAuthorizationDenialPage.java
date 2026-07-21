package com.serviceos.identity.api;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * 主体授权拒绝安全活动页。
 *
 * <p>{@code omitted=true} 表示调用方缺 authorization.read，条目被诚实省略（非空成功）。</p>
 */
public record PrincipalAuthorizationDenialPage(
        List<PrincipalAuthorizationDenialItem> items,
        boolean omitted,
        Instant asOf
) {
    public PrincipalAuthorizationDenialPage {
        items = List.copyOf(Objects.requireNonNull(items, "items"));
        Objects.requireNonNull(asOf, "asOf");
    }
}
