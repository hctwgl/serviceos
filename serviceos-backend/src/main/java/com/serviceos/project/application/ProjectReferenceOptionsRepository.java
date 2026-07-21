package com.serviceos.project.application;

import com.serviceos.project.api.ProjectClientOption;
import com.serviceos.project.api.ProjectRegionOption;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ProjectReferenceOptionsRepository {
    List<ProjectClientOption> listClients(String tenantId, boolean tenantWide, Collection<UUID> projectIds);

    List<ProjectRegionOption> listRegions(String tenantId, boolean tenantWide, Collection<UUID> projectIds);
}
