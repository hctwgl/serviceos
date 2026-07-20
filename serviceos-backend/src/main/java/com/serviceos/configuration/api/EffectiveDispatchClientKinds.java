package com.serviceos.configuration.api;

import java.util.List;

/**
 * 派单级有效目标客户端（ADR-088 A4-R）。
 *
 * <p>{@code applyFilter=false}：相关 FORM/EVIDENCE 均未定向，不施加派单 kind 过滤。
 * {@code applyFilter=true}：取定向资产交集；{@code targetKinds} 可为 empty（交集为空 → MANUAL）。</p>
 */
public record EffectiveDispatchClientKinds(boolean applyFilter, List<String> targetKinds) {
    public EffectiveDispatchClientKinds {
        targetKinds = targetKinds == null ? List.of() : List.copyOf(targetKinds);
    }

    public static EffectiveDispatchClientKinds unfiltered() {
        return new EffectiveDispatchClientKinds(false, List.of());
    }

    public static EffectiveDispatchClientKinds directed(List<String> targetKinds) {
        return new EffectiveDispatchClientKinds(true, targetKinds);
    }
}
