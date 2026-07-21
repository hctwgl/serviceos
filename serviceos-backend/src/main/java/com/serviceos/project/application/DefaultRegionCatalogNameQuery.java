package com.serviceos.project.application;

import com.serviceos.project.api.RegionCatalogNameQuery;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Map;

@Service
final class DefaultRegionCatalogNameQuery implements RegionCatalogNameQuery {
    private final ProjectCatalogRepository catalogs;

    DefaultRegionCatalogNameQuery(ProjectCatalogRepository catalogs) {
        this.catalogs = catalogs;
    }

    @Override
    public Map<String, String> findNames(Collection<String> regionCodes) {
        return catalogs.findRegionNames(regionCodes);
    }
}
