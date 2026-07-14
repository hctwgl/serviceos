package com.serviceos.configuration;

import com.serviceos.ServiceOsApplication;
import com.serviceos.configuration.api.ConfigurationAssetType;
import com.serviceos.configuration.api.ConfigurationBundleReference;
import com.serviceos.configuration.api.ConfigurationPublicationException;
import com.serviceos.configuration.api.ConfigurationResolutionException;
import com.serviceos.configuration.api.ConfigurationService;
import com.serviceos.configuration.api.PublishConfigurationAssetCommand;
import com.serviceos.configuration.api.PublishConfigurationBundleCommand;
import com.serviceos.configuration.api.ResolveConfigurationBundleQuery;
import com.serviceos.shared.Sha256;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = ServiceOsApplication.class)
class ConfigurationPublicationPostgresIT {
    private static final String TENANT = "tenant-config-it";
    private static final String PROJECT_CODE = "BYD-OCEAN-SD-CONFIG-IT";

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("postgres:18-alpine"))
            .withDatabaseName("serviceos")
            .withUsername("serviceos_test")
            .withPassword("serviceos_test");

    @org.springframework.test.context.DynamicPropertySource
    static void properties(org.springframework.test.context.DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    ConfigurationService configurations;

    @Autowired
    JdbcClient jdbc;

    UUID projectId;
    UUID workflowVersionId;
    Instant validFrom;

    @BeforeEach
    void setUp() {
        jdbc.sql("""
                TRUNCATE TABLE wo_work_order, cfg_configuration_bundle_item,
                    cfg_configuration_bundle, cfg_configuration_asset_version,
                    prj_project CASCADE
                """).update();
        projectId = UUID.randomUUID();
        validFrom = Instant.now().minusSeconds(3600);
        jdbc.sql("""
                INSERT INTO prj_project (
                    project_id, tenant_id, project_code, client_id, project_name,
                    starts_on, ends_on, project_status, aggregate_version, created_at
                ) VALUES (
                    :projectId, :tenantId, :projectCode, 'BYD', '配置解析测试项目',
                    :startsOn, NULL, 'ACTIVE', 1, :createdAt
                )
                """)
                .param("projectId", projectId)
                .param("tenantId", TENANT)
                .param("projectCode", PROJECT_CODE)
                .param("startsOn", LocalDate.now().minusDays(1))
                .param("createdAt", OffsetDateTime.now())
                .update();
        String definition = "{\"workflowCode\":\"BYD_SURVEY_INSTALL_V1\"}";
        workflowVersionId = configurations.publishAsset(new PublishConfigurationAssetCommand(
                TENANT, ConfigurationAssetType.WORKFLOW, "BYD_SURVEY_INSTALL", "1.0.0",
                "1.0.0", definition, Sha256.digest(definition))).versionId();
    }

    @Test
    void publishesIdempotentlyAndResolvesExactImmutableVersion() {
        PublishConfigurationBundleCommand command = bundle(
                "BYD-OCEAN-SD", "1.0.0", "370000", validFrom, null);
        ConfigurationBundleReference first = configurations.publishBundle(command);
        ConfigurationBundleReference replay = configurations.publishBundle(command);

        assertThat(replay).isEqualTo(first);
        assertThat(configurations.resolve(query("370000", Instant.now())))
                .isEqualTo(first);
        assertThat(jdbc.sql("SELECT count(*) FROM cfg_configuration_bundle")
                .query(Long.class).single()).isEqualTo(1);
        assertThat(jdbc.sql("SELECT count(*) FROM cfg_configuration_bundle_item")
                .query(Long.class).single()).isEqualTo(1);

        assertThatThrownBy(() -> jdbc.sql("""
                UPDATE cfg_configuration_bundle SET bundle_version = 'mutated'
                 WHERE bundle_id = :bundleId
                """).param("bundleId", first.bundleId()).update())
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("published configuration is immutable");
    }

    @Test
    void exactProvinceOverridesGlobalBundleWithoutAmbiguity() {
        ConfigurationBundleReference global = configurations.publishBundle(bundle(
                "BYD-OCEAN-GLOBAL", "1.0.0", null, validFrom, null));
        ConfigurationBundleReference shandong = configurations.publishBundle(bundle(
                "BYD-OCEAN-SD", "1.0.0", "370000", validFrom, null));

        assertThat(configurations.resolve(query("370000", Instant.now())))
                .isEqualTo(shandong);
        assertThat(configurations.resolve(query("320000", Instant.now())))
                .isEqualTo(global);
    }

    @Test
    void rejectsOverlappingPublicationForSameResolutionScope() {
        configurations.publishBundle(bundle(
                "BYD-OCEAN-SD", "1.0.0", "370000", validFrom, null));

        assertThatThrownBy(() -> configurations.publishBundle(bundle(
                "BYD-OCEAN-SD-NEXT", "2.0.0", "370000",
                Instant.now().minusSeconds(60), null)))
                .isInstanceOf(ConfigurationPublicationException.class)
                .hasMessageContaining("overlaps");
    }

    @Test
    void rejectsImmutableAssetVersionWithDifferentContent() {
        String changed = "{\"workflowCode\":\"MUTATED\"}";

        assertThatThrownBy(() -> configurations.publishAsset(new PublishConfigurationAssetCommand(
                TENANT, ConfigurationAssetType.WORKFLOW, "BYD_SURVEY_INSTALL", "1.0.0",
                "1.0.0", changed, Sha256.digest(changed))))
                .isInstanceOf(ConfigurationPublicationException.class)
                .hasMessageContaining("different immutable content");
    }

    @Test
    void publishesOnlySchemaValidFormWithMatchingImmutableIdentity() {
        String valid = """
                {
                  "formKey": "survey.basic",
                  "version": "1.0.0",
                  "stage": "SURVEY",
                  "sections": [{
                    "sectionKey": "site",
                    "title": "现场信息",
                    "fields": [{
                      "fieldKey": "survey.conclusion",
                      "label": "勘测结论",
                      "dataType": "STRING",
                      "binding": "task.input.survey.conclusion",
                      "required": true
                    }]
                  }]
                }
                """.trim();

        var published = configurations.publishAsset(new PublishConfigurationAssetCommand(
                TENANT, ConfigurationAssetType.FORM, "survey.basic", "1.0.0",
                "1.0.0", valid, Sha256.digest(valid)));

        assertThat(published.assetKey()).isEqualTo("survey.basic");
        assertThatThrownBy(() -> configurations.publishAsset(new PublishConfigurationAssetCommand(
                TENANT, ConfigurationAssetType.FORM, "another.form", "1.0.0",
                "1.0.0", valid, Sha256.digest(valid))))
                .isInstanceOf(ConfigurationPublicationException.class)
                .hasMessageContaining("assetKey");
    }

    @Test
    void bundleLocksMultipleFormVersionsWhileSingletonReadFailsClosed() {
        UUID survey = publishForm("survey.basic", "SURVEY");
        UUID installation = publishForm("installation.basic", "INSTALLATION");
        var command = new PublishConfigurationBundleCommand(
                TENANT, projectId, "BYD-OCEAN-MULTI-FORM", "1.0.0", "BYD_OCEAN",
                "HOME_CHARGING_SURVEY_INSTALL", "370000", validFrom, null,
                List.of(workflowVersionId, survey, installation));

        ConfigurationBundleReference bundle = configurations.publishBundle(command);

        assertThat(configurations.listBundleAssets(TENANT, bundle.bundleId(),
                bundle.manifestDigest(), ConfigurationAssetType.FORM))
                .extracting(asset -> asset.assetKey())
                .containsExactly("installation.basic", "survey.basic");
        assertThatThrownBy(() -> configurations.requireBundleAsset(TENANT, bundle.bundleId(),
                bundle.manifestDigest(), ConfigurationAssetType.FORM))
                .isInstanceOfSatisfying(ConfigurationResolutionException.class,
                        exception -> assertThat(exception.reason()).isEqualTo(
                                ConfigurationResolutionException.Reason.AMBIGUOUS_MATCH));
    }

    @Test
    void rejectsMalformedOrUnknownFormSchemaBeforePersistence() {
        String malformed = """
                {"formKey":"survey.basic","version":"1.0.0","sections":[]}
                """.trim();

        assertThatThrownBy(() -> configurations.publishAsset(new PublishConfigurationAssetCommand(
                TENANT, ConfigurationAssetType.FORM, "survey.basic", "1.0.0",
                "1.0.0", malformed, Sha256.digest(malformed))))
                .isInstanceOf(ConfigurationPublicationException.class)
                .hasMessageContaining("violates schema");
        assertThatThrownBy(() -> configurations.publishAsset(new PublishConfigurationAssetCommand(
                TENANT, ConfigurationAssetType.FORM, "survey.basic", "1.0.0",
                "2.0.0", malformed, Sha256.digest(malformed))))
                .isInstanceOf(ConfigurationPublicationException.class)
                .hasMessageContaining("unsupported FORM schemaVersion");
        assertThat(jdbc.sql("SELECT count(*) FROM cfg_configuration_asset_version WHERE asset_type = 'FORM'")
                .query(Long.class).single()).isZero();
    }

    @Test
    void publishesOnlySchemaValidEvidenceTemplateWithMatchingImmutableIdentity() {
        String valid = evidenceDefinition("survey.site", "SURVEY", "site.panorama");

        var published = configurations.publishAsset(new PublishConfigurationAssetCommand(
                TENANT, ConfigurationAssetType.EVIDENCE, "survey.site", "1.0.0",
                "1.0.0", valid, Sha256.digest(valid)));

        assertThat(published.assetKey()).isEqualTo("survey.site");
        assertThatThrownBy(() -> configurations.publishAsset(new PublishConfigurationAssetCommand(
                TENANT, ConfigurationAssetType.EVIDENCE, "another.evidence", "1.0.0",
                "1.0.0", valid, Sha256.digest(valid))))
                .isInstanceOf(ConfigurationPublicationException.class)
                .hasMessageContaining("assetKey");
        assertThatThrownBy(() -> configurations.publishAsset(new PublishConfigurationAssetCommand(
                TENANT, ConfigurationAssetType.EVIDENCE, "survey.site", "2.0.0",
                "1.0.0", valid, Sha256.digest(valid))))
                .isInstanceOf(ConfigurationPublicationException.class)
                .hasMessageContaining("semanticVersion");
    }

    @Test
    void rejectsMalformedUnknownOrSemanticallyAmbiguousEvidenceBeforePersistence() {
        String malformed = """
                {"templateKey":"survey.site","version":"1.0.0","items":[]}
                """.trim();
        String duplicateEvidenceKey = """
                {
                  "templateKey":"survey.site","version":"1.0.0","stage":"SURVEY",
                  "items":[
                    {"evidenceKey":"site.panorama","name":"全景图","mediaType":"PHOTO","required":true},
                    {"evidenceKey":"site.panorama","name":"重复全景图","mediaType":"PHOTO","required":false}
                  ]
                }
                """.trim();
        String invertedCount = """
                {
                  "templateKey":"survey.site","version":"1.0.0","stage":"SURVEY",
                  "items":[
                    {"evidenceKey":"site.panorama","name":"全景图","mediaType":"PHOTO","required":true,
                     "capture":{"minCount":2,"maxCount":1}}
                  ]
                }
                """.trim();
        String requiredWithZeroCount = """
                {
                  "templateKey":"survey.site","version":"1.0.0","stage":"SURVEY",
                  "items":[
                    {"evidenceKey":"site.panorama","name":"全景图","mediaType":"PHOTO","required":true,
                     "capture":{"minCount":0,"maxCount":1}}
                  ]
                }
                """.trim();

        assertThatThrownBy(() -> publishEvidence(malformed, "1.0.0"))
                .isInstanceOf(ConfigurationPublicationException.class)
                .hasMessageContaining("violates schema");
        assertThatThrownBy(() -> publishEvidence(malformed, "2.0.0"))
                .isInstanceOf(ConfigurationPublicationException.class)
                .hasMessageContaining("unsupported EVIDENCE schemaVersion");
        assertThatThrownBy(() -> publishEvidence(duplicateEvidenceKey, "1.0.0"))
                .isInstanceOf(ConfigurationPublicationException.class)
                .hasMessageContaining("evidenceKey must be unique");
        assertThatThrownBy(() -> publishEvidence(invertedCount, "1.0.0"))
                .isInstanceOf(ConfigurationPublicationException.class)
                .hasMessageContaining("capture minCount must not exceed maxCount");
        assertThatThrownBy(() -> publishEvidence(requiredWithZeroCount, "1.0.0"))
                .isInstanceOf(ConfigurationPublicationException.class)
                .hasMessageContaining("required item minCount must be greater than zero");
        assertThat(jdbc.sql("SELECT count(*) FROM cfg_configuration_asset_version WHERE asset_type = 'EVIDENCE'")
                .query(Long.class).single()).isZero();
    }

    @Test
    void bundleLocksMultipleEvidenceTemplatesInDeterministicOrder() {
        UUID survey = publishEvidenceAsset("survey.site", "SURVEY", "site.panorama");
        UUID installation = publishEvidenceAsset(
                "installation.completion", "INSTALLATION", "charger.nameplate");
        var command = new PublishConfigurationBundleCommand(
                TENANT, projectId, "BYD-OCEAN-MULTI-EVIDENCE", "1.0.0", "BYD_OCEAN",
                "HOME_CHARGING_SURVEY_INSTALL", "370000", validFrom, null,
                List.of(workflowVersionId, survey, installation));

        ConfigurationBundleReference bundle = configurations.publishBundle(command);

        assertThat(configurations.listBundleAssets(TENANT, bundle.bundleId(),
                bundle.manifestDigest(), ConfigurationAssetType.EVIDENCE))
                .extracting(asset -> asset.assetKey())
                .containsExactly("installation.completion", "survey.site");
        assertThatThrownBy(() -> configurations.requireBundleAsset(TENANT, bundle.bundleId(),
                bundle.manifestDigest(), ConfigurationAssetType.EVIDENCE))
                .isInstanceOfSatisfying(ConfigurationResolutionException.class,
                        exception -> assertThat(exception.reason()).isEqualTo(
                                ConfigurationResolutionException.Reason.AMBIGUOUS_MATCH));
    }

    @Test
    void failsClosedWhenLegacyOrManualRowsCreateAnAmbiguousMatch() {
        insertBundleRow("AMBIGUOUS-A", "a".repeat(64));
        insertBundleRow("AMBIGUOUS-B", "b".repeat(64));

        assertThatThrownBy(() -> configurations.resolve(query("370000", Instant.now())))
                .isInstanceOfSatisfying(ConfigurationResolutionException.class,
                        exception -> assertThat(exception.reason()).isEqualTo(
                                ConfigurationResolutionException.Reason.AMBIGUOUS_MATCH));
    }

    @Test
    void failsClosedWhenProjectOrBundleDoesNotMatch() {
        configurations.publishBundle(bundle(
                "BYD-OCEAN-SD", "1.0.0", "370000", validFrom, null));

        assertThatThrownBy(() -> configurations.resolve(new ResolveConfigurationBundleQuery(
                "another-tenant", PROJECT_CODE, "BYD_OCEAN",
                "HOME_CHARGING_SURVEY_INSTALL", "370000", Instant.now())))
                .isInstanceOfSatisfying(ConfigurationResolutionException.class,
                        exception -> assertThat(exception.reason()).isEqualTo(
                                ConfigurationResolutionException.Reason.PROJECT_NOT_ACTIVE));

        assertThatThrownBy(() -> configurations.resolve(query("440000", Instant.now())))
                .isInstanceOfSatisfying(ConfigurationResolutionException.class,
                        exception -> assertThat(exception.reason()).isEqualTo(
                                ConfigurationResolutionException.Reason.NO_MATCH));
    }

    @Test
    void databaseRejectsCrossTenantBundleItems() {
        ConfigurationBundleReference bundle = configurations.publishBundle(bundle(
                "BYD-OCEAN-SD", "1.0.0", "370000", validFrom, null));
        String definition = """
                {
                  "formKey": "foreign.form",
                  "version": "1.0.0",
                  "sections": [{
                    "sectionKey": "base",
                    "title": "基础",
                    "fields": [{
                      "fieldKey": "survey.result",
                      "label": "结果",
                      "dataType": "STRING",
                      "binding": "task.input.survey.result"
                    }]
                  }]
                }
                """.trim();
        var foreignAsset = configurations.publishAsset(new PublishConfigurationAssetCommand(
                "another-tenant", ConfigurationAssetType.FORM, "foreign.form", "1.0.0",
                "1.0.0", definition, Sha256.digest(definition)));

        assertThatThrownBy(() -> jdbc.sql("""
                INSERT INTO cfg_configuration_bundle_item (
                    tenant_id, bundle_id, asset_type, asset_version_id, content_digest
                ) VALUES (
                    :tenantId, :bundleId, :assetType, :assetVersionId, :contentDigest
                )
                """)
                .param("tenantId", TENANT)
                .param("bundleId", bundle.bundleId())
                .param("assetType", foreignAsset.assetType().name())
                .param("assetVersionId", foreignAsset.versionId())
                .param("contentDigest", foreignAsset.contentDigest())
                .update())
                .isInstanceOf(DataAccessException.class);
    }

    private PublishConfigurationBundleCommand bundle(
            String code, String version, String province, Instant from, Instant until) {
        return new PublishConfigurationBundleCommand(
                TENANT, projectId, code, version, "BYD_OCEAN",
                "HOME_CHARGING_SURVEY_INSTALL", province, from, until,
                List.of(workflowVersionId));
    }

    private UUID publishForm(String formKey, String stage) {
        String definition = ("""
                {
                  "formKey":"%s","version":"1.0.0","stage":"%s",
                  "sections":[{"sectionKey":"base","title":"基础","fields":[{
                    "fieldKey":"result.value","label":"结果","dataType":"STRING",
                    "binding":"task.input.result.value"
                  }]}]
                }
                """).formatted(formKey, stage).trim();
        return configurations.publishAsset(new PublishConfigurationAssetCommand(
                TENANT, ConfigurationAssetType.FORM, formKey, "1.0.0", "1.0.0",
                definition, Sha256.digest(definition))).versionId();
    }

    private UUID publishEvidenceAsset(String assetKey, String stage, String evidenceKey) {
        String definition = evidenceDefinition(assetKey, stage, evidenceKey);
        return configurations.publishAsset(new PublishConfigurationAssetCommand(
                TENANT, ConfigurationAssetType.EVIDENCE, assetKey, "1.0.0", "1.0.0",
                definition, Sha256.digest(definition))).versionId();
    }

    private void publishEvidence(String definition, String schemaVersion) {
        configurations.publishAsset(new PublishConfigurationAssetCommand(
                TENANT, ConfigurationAssetType.EVIDENCE, "survey.site", "1.0.0", schemaVersion,
                definition, Sha256.digest(definition)));
    }

    private String evidenceDefinition(String assetKey, String stage, String evidenceKey) {
        return ("""
                {
                  "templateKey":"%s","version":"1.0.0","title":"现场资料","stage":"%s",
                  "items":[{
                    "evidenceKey":"%s","name":"现场照片","mediaType":"PHOTO","required":true,
                    "capture":{"allowCamera":true,"allowGallery":false,"requireRealtimeCapture":true,
                      "requireGps":true,"watermarkFields":["TIME","GPS","WORK_ORDER_NO"],
                      "minCount":1,"maxCount":3,"maxSizeBytes":10485760},
                    "qualityChecks":[{"checkType":"BLUR","severity":"BLOCK"}],
                    "reviewPolicy":{"reviewRequired":true,"allowItemLevelReject":true}
                  }]
                }
                """).formatted(assetKey, stage, evidenceKey).trim();
    }

    private ResolveConfigurationBundleQuery query(String province, Instant at) {
        return new ResolveConfigurationBundleQuery(
                TENANT, PROJECT_CODE, "BYD_OCEAN",
                "HOME_CHARGING_SURVEY_INSTALL", province, at);
    }

    private void insertBundleRow(String code, String digest) {
        jdbc.sql("""
                INSERT INTO cfg_configuration_bundle (
                    bundle_id, tenant_id, project_id, bundle_code, bundle_version,
                    brand_code, service_product_code, province_code, effective_from,
                    effective_until, manifest_digest, status, published_at
                ) VALUES (
                    :bundleId, :tenantId, :projectId, :bundleCode, '1.0.0',
                    'BYD_OCEAN', 'HOME_CHARGING_SURVEY_INSTALL', '370000', :effectiveFrom,
                    NULL, :manifestDigest, 'PUBLISHED', :publishedAt
                )
                """)
                .param("bundleId", UUID.randomUUID())
                .param("tenantId", TENANT)
                .param("projectId", projectId)
                .param("bundleCode", code)
                .param("effectiveFrom", OffsetDateTime.now().minusHours(1))
                .param("manifestDigest", digest)
                .param("publishedAt", OffsetDateTime.now())
                .update();
    }
}
