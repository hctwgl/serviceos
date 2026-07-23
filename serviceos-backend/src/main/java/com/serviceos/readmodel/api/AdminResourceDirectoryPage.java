package com.serviceos.readmodel.api;

import java.time.Instant;
import java.util.List;

/** Admin 组织与资源域页面级读模型。 */
public record AdminResourceDirectoryPage(
        List<AdminPartnerOrganizationDirectoryItem> partners,
        List<AdminServiceNetworkDirectoryItem> networks,
        List<AdminTechnicianDirectoryItem> technicians,
        List<String> allowedActions,
        Instant asOf
) {
    public AdminResourceDirectoryPage {
        partners = partners == null ? List.of() : List.copyOf(partners);
        networks = networks == null ? List.of() : List.copyOf(networks);
        technicians = technicians == null ? List.of() : List.copyOf(technicians);
        allowedActions = allowedActions == null ? List.of() : List.copyOf(allowedActions);
    }
}
