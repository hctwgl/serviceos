package com.serviceos.configuration.api;

import java.util.List;

/**
 * FORM/EVIDENCE 配置相对生产师傅端的客户端能力兼容报告。
 *
 * <p>blockingErrors 非空表示不得进入 VALIDATED/PUBLISHED；
 * clientReports 记录分端缺口，供设计器展示与审计，不参与授权。</p>
 */
public record ClientCompatibilityReport(
        List<String> requiredCapabilities,
        List<String> blockingErrors,
        List<ClientReport> clientReports
) {
    public ClientCompatibilityReport {
        requiredCapabilities = requiredCapabilities == null
                ? List.of() : List.copyOf(requiredCapabilities);
        blockingErrors = blockingErrors == null ? List.of() : List.copyOf(blockingErrors);
        clientReports = clientReports == null ? List.of() : List.copyOf(clientReports);
    }

    public boolean blocking() {
        return !blockingErrors.isEmpty();
    }

    public record ClientReport(
            String clientKind,
            boolean compatible,
            List<String> missingCapabilities,
            List<String> notes
    ) {
        public ClientReport {
            missingCapabilities = missingCapabilities == null
                    ? List.of() : List.copyOf(missingCapabilities);
            notes = notes == null ? List.of() : List.copyOf(notes);
        }
    }
}
