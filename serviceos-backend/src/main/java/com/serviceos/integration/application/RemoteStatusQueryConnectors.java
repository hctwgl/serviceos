package com.serviceos.integration.application;

import com.serviceos.integration.spi.RemoteStatusQueryConnector;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.ProblemCode;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

/** 远端状态查询连接器注册表：按 connectorVersion 唯一解析。 */
@Component
public class RemoteStatusQueryConnectors {
    private final List<RemoteStatusQueryConnector> connectors;

    public RemoteStatusQueryConnectors(List<RemoteStatusQueryConnector> connectors) {
        this.connectors = List.copyOf(Objects.requireNonNullElse(connectors, List.of()));
    }

    public RemoteStatusQueryConnector requireForConnectorVersion(String connectorVersionId) {
        List<RemoteStatusQueryConnector> matches = connectors.stream()
                .filter(connector -> connector.supportsConnectorVersion(connectorVersionId))
                .toList();
        if (matches.isEmpty()) {
            throw new BusinessProblem(ProblemCode.RESOURCE_NOT_FOUND,
                    "No RemoteStatusQueryConnector for connectorVersionId: " + connectorVersionId);
        }
        if (matches.size() > 1) {
            throw new BusinessProblem(ProblemCode.INTERNAL_ERROR,
                    "Multiple RemoteStatusQueryConnectors matched connectorVersionId");
        }
        return matches.getFirst();
    }
}
