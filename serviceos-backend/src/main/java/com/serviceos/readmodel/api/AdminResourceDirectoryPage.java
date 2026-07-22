package com.serviceos.readmodel.api;

import java.time.Instant;
import java.util.List;

/** Admin 组织与资源域页面级读模型。 */
public record AdminResourceDirectoryPage(
        List<AdminServiceNetworkDirectoryItem> networks,
        List<AdminTechnicianDirectoryItem> technicians,
        Instant asOf
) {
    public AdminResourceDirectoryPage {
        networks = networks == null ? List.of() : List.copyOf(networks);
        technicians = technicians == null ? List.of() : List.copyOf(technicians);
    }
}
