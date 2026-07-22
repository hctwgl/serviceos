package com.serviceos.readmodel.application;

import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.project.api.ProjectClientBrandItem;
import com.serviceos.project.api.ProjectClientBrandPage;
import com.serviceos.project.api.ProjectClientDirectoryItem;
import com.serviceos.project.api.ProjectClientDirectoryPage;
import com.serviceos.project.api.ProjectPage;
import com.serviceos.project.api.ProjectQuery;
import com.serviceos.project.api.ProjectQueryService;
import com.serviceos.project.api.ProjectReferenceOptions;
import com.serviceos.project.api.ProjectRegionOption;
import com.serviceos.project.api.ProjectView;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DefaultAdminClientProjectDirectoryQueryServiceTest {
    private static final Instant NOW = Instant.parse("2026-07-22T08:00:00Z");

    @Test
    @DisplayName("客户项目目录应返回客户品牌、区域名称和履约配置状态")
    void shouldComposeClientAndProjectFacts() {
        ProjectQueryService projects = mock(ProjectQueryService.class);
        CurrentPrincipal actor = new CurrentPrincipal(
                "admin-1", "tenant-1", CurrentPrincipal.PrincipalType.USER, "admin-web", Set.of());
        ProjectView project = new ProjectView(
                UUID.randomUUID(), "tenant-1", "BYD-SD-HOME", "BYD", "比亚迪山东家充服务项目",
                LocalDate.parse("2026-01-01"), null, List.of("370100"), List.of("network-1"),
                "ACTIVE", 1, NOW, 1, 1);

        when(projects.list(any(), any(), any(ProjectQuery.class)))
                .thenReturn(new ProjectPage(List.of(project), null, NOW));
        when(projects.listClientDirectory(actor, "corr-projects", "ALL"))
                .thenReturn(new ProjectClientDirectoryPage(
                        List.of(new ProjectClientDirectoryItem("BYD", "比亚迪汽车", "ACTIVE")), NOW));
        when(projects.referenceOptions(actor, "corr-projects"))
                .thenReturn(new ProjectReferenceOptions(
                        List.of(), List.of(new ProjectRegionOption("370100", "济南市", 1)), NOW));
        when(projects.listClientBrands(actor, "corr-projects", "BYD", "ALL"))
                .thenReturn(new ProjectClientBrandPage(List.of(
                        new ProjectClientBrandItem("BYD", "OCEAN", "海洋网", "ACTIVE", 10),
                        new ProjectClientBrandItem("BYD", "DYNASTY", "王朝网", "ACTIVE", 20)), NOW));

        var service = new DefaultAdminClientProjectDirectoryQueryService(
                projects, Clock.fixed(NOW, ZoneOffset.UTC));
        var result = service.load(actor, "corr-projects");

        assertThat(result.clients()).singleElement().satisfies(client -> {
            assertThat(client.clientName()).isEqualTo("比亚迪汽车");
            assertThat(client.brandNames()).containsExactly("海洋网", "王朝网");
            assertThat(client.projectCount()).isEqualTo(1);
        });
        assertThat(result.projects()).singleElement().satisfies(item -> {
            assertThat(item.projectName()).isEqualTo("比亚迪山东家充服务项目");
            assertThat(item.clientName()).isEqualTo("比亚迪汽车");
            assertThat(item.regionNames()).containsExactly("济南市");
            assertThat(item.configurationStatus()).isEqualTo("UNPUBLISHED_CHANGES");
            assertThat(item.dataComplete()).isTrue();
        });
    }
}
