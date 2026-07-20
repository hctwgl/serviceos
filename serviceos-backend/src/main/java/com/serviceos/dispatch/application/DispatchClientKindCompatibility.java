package com.serviceos.dispatch.application;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** ADR-088：师傅声明与 Bundle 有效目标的匹配规则（自动池与人工指派共用）。 */
final class DispatchClientKindCompatibility {
    private DispatchClientKindCompatibility() {
    }

    /**
     * 师傅声明与有效目标非空交集才可通过；未声明在定向任务上失败关闭。
     */
    static boolean matchesDeclaredClientKinds(List<String> declared, List<String> targetKinds) {
        if (declared == null || declared.isEmpty()) {
            return false;
        }
        if (targetKinds == null || targetKinds.isEmpty()) {
            return false;
        }
        Set<String> targets = new HashSet<>(targetKinds);
        for (String kind : declared) {
            if (kind != null && targets.contains(kind)) {
                return true;
            }
        }
        return false;
    }
}
