package com.jfrog.build.bridge;

import com.jfrog.build.acl.bridge.BuildBridgeToArtifactory;
import com.jfrog.build.acl.model.BuildAccessLogType;
import com.jfrog.build.acl.model.BuildArtifactAqlRow;
import com.jfrog.build.acl.model.BuildArtifactChecksums;
import com.jfrog.build.acl.model.BuildArtifactMetadata;
import com.jfrog.build.acl.model.BuildFileInfo;
import com.jfrog.build.acl.model.BuildNode;
import com.jfrog.build.acl.model.BuildPluginType;
import com.jfrog.build.acl.model.BuildRepoDescriptor;
import com.jfrog.build.acl.model.BuildWorkerEventType;
import com.jfrog.build.acl.model.BuildWrapper;
import com.jfrog.build.acl.model.BuildXrayEvent;
import com.jfrog.build.acl.model.ProducedBy;
import com.jfrog.build.acl.model.UsedBy;
import com.jfrog.build.acl.model.api.permission.BuildPermissionsModel;
import com.jfrog.build.gateway.BuildAccessGateway;
import com.jfrog.build.gateway.BuildAqlGateway;
import com.jfrog.build.gateway.BuildConfigGateway;
import com.jfrog.build.gateway.BuildEvidenceGateway;
import com.jfrog.build.gateway.BuildFileGateway;
import com.jfrog.build.gateway.BuildNodeGateway;
import com.jfrog.build.gateway.BuildPermissionGateway;
import com.jfrog.build.gateway.BuildPluginGateway;
import com.jfrog.build.gateway.BuildPropertyGateway;
import com.jfrog.build.gateway.BuildRepoGateway;
import com.jfrog.build.gateway.BuildUploadGateway;
import com.jfrog.build.gateway.BuildWorkerGateway;
import com.jfrog.build.gateway.BuildXrayGateway;
import com.jfrog.sysconf.SysConfig;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.retention.ArtifactRetentionAddon;
import org.artifactory.api.config.ArtifactorySystemConfig;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.repo.DeployService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.common.status.MutableStatusHolder;
import org.artifactory.entitlement.EntitlementType;
import org.artifactory.platform.PlatformConfigService;
import org.artifactory.projects.ProjectsService;
import org.artifactory.repo.system.SystemRepoConfigFactory;
import org.artifactory.storage.db.DbService;
import org.jfrog.access.client.project.ProjectEntity;
import org.jfrog.access.client.project.ProjectResourceEntity;
import org.jfrog.access.client.project.ProjectSlimEntity;
import org.jfrog.build.api.Build;
import org.jfrog.build.api.release.PromotionStatus;
import org.jfrog.storage.JdbcHelper;
import org.jfrog.storage.binstore.ifc.BinaryService;
import org.jfrog.storage.binstore.ifc.model.BinaryElement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import static org.jfrog.access.common.PermissionScopeResourceType.BUILD;
import static org.jfrog.access.common.ResourceType.REPO;
import static org.jfrog.storage.DbType.POSTGRESQL;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BuildBridgeToArtifactoryImplTest {

    private BuildBridgeToArtifactory buildBridgeToArtifactory;

    private DbService dbService;
    private JdbcHelper jdbcHelper;
    private AddonsManager addonsManager;
    private BinaryService binaryService;
    private CentralConfigService configService;
    private PlatformConfigService platformConfigService;
    private ProjectsService projectsService;
    private SecurityService securityService;
    private SystemRepoConfigFactory systemRepoConfigFactory;
    private BuildAccessGateway accessGateway;
    private BuildAqlGateway aqlGateway;
    private BuildConfigGateway configGateway;
    private BuildEvidenceGateway evidenceGateway;
    private BuildFileGateway fileGateway;
    private BuildNodeGateway nodeGateway;
    private BuildPermissionGateway permissionGateway;
    private BuildPluginGateway pluginGateway;
    private BuildPropertyGateway propertyGateway;
    private BuildRepoGateway repoGateway;
    private BuildUploadGateway uploadGateway;
    private BuildWorkerGateway workerGateway;
    private BuildXrayGateway xrayGateway;

    @BeforeEach
    void setUp() {
        dbService = mock(DbService.class);
        jdbcHelper = mock(JdbcHelper.class);
        addonsManager = mock(AddonsManager.class);
        binaryService = mock(BinaryService.class);
        configService = mock(CentralConfigService.class, RETURNS_DEEP_STUBS);
        platformConfigService = mock(PlatformConfigService.class, RETURNS_DEEP_STUBS);
        projectsService = mock(ProjectsService.class);
        securityService = mock(SecurityService.class);
        systemRepoConfigFactory = mock(SystemRepoConfigFactory.class);
        accessGateway = mock(BuildAccessGateway.class);
        aqlGateway = mock(BuildAqlGateway.class);
        configGateway = mock(BuildConfigGateway.class);
        evidenceGateway = mock(BuildEvidenceGateway.class);
        fileGateway = mock(BuildFileGateway.class);
        nodeGateway = mock(BuildNodeGateway.class);
        permissionGateway = mock(BuildPermissionGateway.class);
        pluginGateway = mock(BuildPluginGateway.class);
        propertyGateway = mock(BuildPropertyGateway.class);
        repoGateway = mock(BuildRepoGateway.class);
        uploadGateway = mock(BuildUploadGateway.class);
        workerGateway = mock(BuildWorkerGateway.class);
        xrayGateway = mock(BuildXrayGateway.class);
        buildBridgeToArtifactory = new BuildBridgeToArtifactoryImpl(
                dbService, jdbcHelper, addonsManager, binaryService, mock(DeployService.class), configService,
                platformConfigService, projectsService, securityService, systemRepoConfigFactory,
                accessGateway, aqlGateway, configGateway, evidenceGateway, fileGateway, nodeGateway,
                permissionGateway, pluginGateway, propertyGateway, repoGateway, uploadGateway, workerGateway,
                xrayGateway);
    }

    @Test
    void isAdmin_delegatesToSecurityService() {
        when(securityService.isAdmin()).thenReturn(true);
        assertTrue(buildBridgeToArtifactory.isAdmin());
    }

    @Test
    void isAnonymous_delegatesToSecurityService() {
        when(securityService.isAnonymous()).thenReturn(true);
        assertTrue(buildBridgeToArtifactory.isAnonymous());
    }

    @Test
    void isResourceManager_delegatesToSecurityService() {
        when(securityService.isResourceManager()).thenReturn(true);
        assertTrue(buildBridgeToArtifactory.isResourceManager());
    }

    @Test
    void isEdgeInstance_delegatesToAddonsManager() {
        when(addonsManager.isEntitled(EntitlementType.EDGE)).thenReturn(true);
        assertTrue(buildBridgeToArtifactory.isEdgeInstance());
    }

    @Test
    void isColdInstance_delegatesToRetentionAddon() {
        ArtifactRetentionAddon retentionAddon = mock(ArtifactRetentionAddon.class);
        when(addonsManager.addonByType(ArtifactRetentionAddon.class)).thenReturn(retentionAddon);
        when(retentionAddon.isColdInstanceV1orV2()).thenReturn(true);
        assertTrue(buildBridgeToArtifactory.isColdInstance());
    }

    @Test
    void doAsSystem_delegatesToSecurityService() {
        Runnable runnable = mock(Runnable.class);
        buildBridgeToArtifactory.doAsSystem(runnable);
        verify(securityService).doAsSystem(runnable);
    }

    @Test
    void newStatusHolder_returnsNonNull() {
        assertNotNull(buildBridgeToArtifactory.newStatusHolder());
    }

    @Test
    void getCurrentUsername_delegatesToSecurityService() {
        when(securityService.currentUsername()).thenReturn("alice");
        assertEquals("alice", buildBridgeToArtifactory.getCurrentUsername());
    }

    @Test
    void getDateTimeFormat_delegatesToPlatformConfig() {
        when(platformConfigService.getPlatformGeneralConfig().getDateFormat()).thenReturn("dd-MM-yy");
        assertEquals("dd-MM-yy", buildBridgeToArtifactory.getDateTimeFormat());
    }

    @Test
    void getSecurityDescriptor_mapsCentralConfigSecurity() {
        when(configService.getDescriptor().getSecurity().isBuildGlobalBasicReadAllowed()).thenReturn(true);
        when(configService.getDescriptor().getSecurity().isBuildGlobalBasicReadForAnonymous()).thenReturn(false);
        when(configService.getDescriptor().getSecurity().isHideUnauthorizedResources()).thenReturn(true);

        assertTrue(buildBridgeToArtifactory.getSecurityDescriptor().isBuildGlobalBasicReadAllowed());
        assertFalse(buildBridgeToArtifactory.getSecurityDescriptor().isBuildGlobalBasicReadForAnonymous());
    }

    @Test
    void getNextDatabaseId_Success() {
        when(dbService.nextId()).thenReturn(1L);
        assertEquals(1L, buildBridgeToArtifactory.getNextDatabaseId());
        verify(dbService).nextId();
    }

    @Test
    void getDatabaseType_Success() {
        when(dbService.getDatabaseType()).thenReturn(POSTGRESQL);
        assertEquals(POSTGRESQL, buildBridgeToArtifactory.getDatabaseType());
    }

    @Test
    void getJdbcHelper_Success() {
        assertEquals(jdbcHelper, buildBridgeToArtifactory.getJdbcHelper());
    }

    @Test
    void getSysConfig_Success() {
        SysConfig sysConfig = mock(SysConfig.class);
        ArtifactorySystemConfig systemConfig = mock(ArtifactorySystemConfig.class);
        when(configService.getSystemConfiguration()).thenReturn(systemConfig);
        when(systemConfig.getSysConfig()).thenReturn(sysConfig);
        assertEquals(sysConfig, buildBridgeToArtifactory.getSysConfig());
    }

    @Test
    void getArtifactoryProperty_delegatesToConfigGateway() {
        when(configGateway.getArtifactoryConfig("key", String.class)).thenReturn("value");
        assertEquals("value", buildBridgeToArtifactory.getArtifactoryProperty("key", String.class));
    }

    @Test
    void createBuildRepo_delegatesToSystemRepoConfigFactory() {
        buildBridgeToArtifactory.createBuildRepo("proj-build-info");
        verify(systemRepoConfigFactory).buildSystemRepo(any(), eq("proj-build-info"));
    }

    @Test
    void getRepoDescriptor_delegatesToRepoGateway() {
        Optional<BuildRepoDescriptor> expected = Optional.of(BuildRepoDescriptor.builder().key("k").build());
        when(repoGateway.getRepoDescriptor("k")).thenReturn(expected);
        assertEquals(expected, buildBridgeToArtifactory.getRepoDescriptor("k"));
    }

    @Test
    void getDefaultDeploymentRepoKey_delegatesToRepoGateway() {
        when(repoGateway.getDefaultDeploymentRepoKey("k")).thenReturn(Optional.of("k"));
        assertEquals(Optional.of("k"), buildBridgeToArtifactory.getDefaultDeploymentRepoKey("k"));
    }

    @Test
    void getRepoKeys_delegatesToRepoGateway() {
        when(repoGateway.getRepoKeys(Set.of("LOCAL"), "build")).thenReturn(Set.of("a-build-info"));
        assertEquals(Set.of("a-build-info"), buildBridgeToArtifactory.getRepoKeys(Set.of("LOCAL"), "build"));
    }

    @Test
    void getVirtualRepoKeys_delegatesToRepoGateway() {
        when(repoGateway.getVirtualRepoKeys()).thenReturn(List.of("virt"));
        assertEquals(List.of("virt"), buildBridgeToArtifactory.getVirtualRepoKeys());
    }

    @Test
    void repoPathExists_delegatesToRepoGateway() {
        when(repoGateway.repoPathExists("repo/path")).thenReturn(true);
        assertTrue(buildBridgeToArtifactory.repoPathExists("repo/path"));
    }

    @Test
    void assertStorageQuota_delegatesToRepoGateway() {
        when(repoGateway.assertStorageQuota("repo", 10L)).thenReturn(true);
        assertTrue(buildBridgeToArtifactory.assertStorageQuota("repo", 10L));
    }

    @Test
    void getResourceStream_delegatesToRepoGateway() {
        when(repoGateway.getResourceStream("repo/path")).thenReturn(null);
        buildBridgeToArtifactory.getResourceStream("repo/path");
        verify(repoGateway).getResourceStream("repo/path");
    }

    @Test
    void copy_delegatesToRepoGateway() {
        MutableStatusHolder status = mock(MutableStatusHolder.class);
        buildBridgeToArtifactory.copy(Set.of("a"), "target", status, true, false);
        verify(repoGateway).copy(Set.of("a"), "target", status, true, false);
    }

    @Test
    void move_delegatesToRepoGateway() {
        MutableStatusHolder status = mock(MutableStatusHolder.class);
        buildBridgeToArtifactory.move(Set.of("a"), "target", status, true, false);
        verify(repoGateway).move(Set.of("a"), "target", status, true, false);
    }

    @Test
    void undeploy_delegatesToRepoGateway() {
        MutableStatusHolder status = mock(MutableStatusHolder.class);
        buildBridgeToArtifactory.undeploy("repo/path", status, true, false, true, false, true);
        verify(repoGateway).undeploy("repo/path", status, true, false, true, false, true);
    }

    @Test
    void getArtifactMetadata_delegatesToRepoGateway() {
        Optional<BuildArtifactMetadata> expected = Optional.of(BuildArtifactMetadata.builder().name("n").build());
        when(repoGateway.getArtifactMetadata("repo/path")).thenReturn(expected);
        assertEquals(expected, buildBridgeToArtifactory.getArtifactMetadata("repo/path"));
    }

    @Test
    void getArtifactChecksums_delegatesToRepoGateway() {
        Optional<BuildArtifactChecksums> expected = Optional.of(new BuildArtifactChecksums("sha1", "md5"));
        when(repoGateway.getArtifactChecksums("repo", "path")).thenReturn(expected);
        assertEquals(expected, buildBridgeToArtifactory.getArtifactChecksums("repo", "path"));
    }

    @Test
    void getFileInfo_delegatesToFileGateway() {
        BuildFileInfo info = BuildFileInfo.builder().build();
        when(fileGateway.getFileInfo("repo/path")).thenReturn(info);
        assertEquals(info, buildBridgeToArtifactory.getFileInfo("repo/path"));
    }

    @Test
    void upload_delegatesToUploadGateway() {
        when(uploadGateway.upload("repo/path", "payload")).thenReturn("created");
        assertEquals("created", buildBridgeToArtifactory.upload("repo/path", "payload"));
    }

    @Test
    void addProperties_delegatesToPropertyGateway() {
        buildBridgeToArtifactory.addProperties("repo/path", Map.of());
        verify(propertyGateway).addProperties("repo/path", Map.of());
    }

    @Test
    void renameProperty_delegatesToPropertyGateway() {
        buildBridgeToArtifactory.renameProperty("build.name", "old", "new");
        verify(propertyGateway).renameBuildNameProperty("build.name", "old", "new");
    }

    @Test
    void getNodesBySha1_delegatesToNodeGateway() {
        List<BuildNode> nodes = List.of(mock(BuildNode.class));
        when(nodeGateway.getNodesBySha1(List.of("sha1"))).thenReturn(nodes);
        assertEquals(nodes, buildBridgeToArtifactory.getNodesBySha1(List.of("sha1")));
    }

    @Test
    void logAccessEvent_delegatesToAccessGateway() {
        buildBridgeToArtifactory.logAccessEvent(BuildAccessLogType.BUILD_CREATE, "ref");
        verify(accessGateway).logAccessEvent(BuildAccessLogType.BUILD_CREATE, "ref");
    }

    @Test
    void findBinaries_delegatesToBinaryService() {
        Set<BinaryElement> expected = Set.of(mock(BinaryElement.class));
        when(binaryService.findBinaries(Set.of("sha1"))).thenReturn(expected);
        assertEquals(expected, buildBridgeToArtifactory.findBinaries(Set.of("sha1")));
    }

    @Test
    void hasPermission_delegatesToPermissionGateway() {
        when(permissionGateway.hasPermission(BUILD, "read", "repo/path")).thenReturn(true);
        assertTrue(buildBridgeToArtifactory.hasPermission(BUILD, "read", "repo/path"));
    }

    @Test
    void getArtifactActions_delegatesToPermissionGateway() {
        when(permissionGateway.getArtifactActions("repo", "path")).thenReturn(List.of("Download"));
        assertEquals(List.of("Download"), buildBridgeToArtifactory.getArtifactActions("repo", "path"));
    }

    @Test
    void getBuildEffectivePermissions_delegatesToPermissionGateway() {
        BuildPermissionsModel model = new BuildPermissionsModel(List.of(), List.of(), List.of());
        when(permissionGateway.getBuildEffectivePermissions("b", "1", "s", "r")).thenReturn(model);
        assertEquals(model, buildBridgeToArtifactory.getBuildEffectivePermissions("b", "1", "s", "r"));
    }

    @Test
    void getBuildEffectivePermissionsByRepoPath_delegatesToPermissionGateway() {
        BuildPermissionsModel model = new BuildPermissionsModel(List.of(), List.of(), List.of());
        when(permissionGateway.getBuildEffectivePermissionsByRepoPath("r", "p")).thenReturn(model);
        assertEquals(model, buildBridgeToArtifactory.getBuildEffectivePermissionsByRepoPath("r", "p"));
    }

    @Test
    void getBuildPermissionsByUser_delegatesToPermissionGateway() {
        when(permissionGateway.getBuildPermissionsByUser("b", "1", "s", "r", "alice")).thenReturn(List.of("pt"));
        assertEquals(List.of("pt"), buildBridgeToArtifactory.getBuildPermissionsByUser("b", "1", "s", "r", "alice"));
    }

    @Test
    void getBuildPermissionsByGroup_delegatesToPermissionGateway() {
        when(permissionGateway.getBuildPermissionsByGroup("b", "1", "s", "r", "devs")).thenReturn(List.of("pt"));
        assertEquals(List.of("pt"), buildBridgeToArtifactory.getBuildPermissionsByGroup("b", "1", "s", "r", "devs"));
    }

    @Test
    void isProjectAdmin_delegatesToProjectsService() {
        when(projectsService.isProjectAdmin("proj")).thenReturn(true);
        assertTrue(buildBridgeToArtifactory.isProjectAdmin("proj"));
    }

    @Test
    void getAllProjects_delegatesToProjectsService() {
        List<ProjectEntity> projects = List.of(mock(ProjectEntity.class));
        when(projectsService.getAllProjectsEntities()).thenReturn(projects);
        assertEquals(projects, buildBridgeToArtifactory.getAllProjects());
    }

    @Test
    void getProjectForResource_delegatesToProjectsService() {
        when(projectsService.getProjectForResource("repo-key", REPO)).thenReturn("default");
        assertEquals("default", buildBridgeToArtifactory.getProjectForResource("repo-key", REPO));
    }

    @Test
    void upsertProjectResource_delegatesToProjectsService() {
        ProjectResourceEntity resource = mock(ProjectResourceEntity.class);
        buildBridgeToArtifactory.upsertProjectResource("proj", REPO, resource, true);
        verify(projectsService).upsertProjectResource("proj", REPO, resource, true);
    }

    @Test
    void getProjectSpec_shouldReturnPresent_whenServiceReturnsValue() {
        ProjectSlimEntity entity = mock(ProjectSlimEntity.class);
        when(projectsService.getProjectSpec("proj")).thenReturn(entity);
        assertSame(entity, buildBridgeToArtifactory.getProjectSpec("proj").orElseThrow());
    }

    @Test
    void getProjectSpec_shouldReturnEmpty_whenServiceReturnsNull() {
        when(projectsService.getProjectSpec("proj")).thenReturn(null);
        assertTrue(buildBridgeToArtifactory.getProjectSpec("proj").isEmpty());
    }

    @Test
    void getBuildArtifactsByNameNumberTimestampByAql_delegatesToAqlGateway() {
        BuildWrapper wrapper = mock(BuildWrapper.class);
        List<BuildArtifactAqlRow> rows = List.of(mock(BuildArtifactAqlRow.class));
        when(aqlGateway.getBuildArtifactsByNameNumberTimestamp(wrapper, null, null)).thenReturn(rows);
        assertEquals(rows, buildBridgeToArtifactory.getBuildArtifactsByNameNumberTimestampByAql(wrapper, null, null));
    }

    @Test
    void getBuildArtifactsByNameNumberByAql_delegatesToAqlGateway() {
        BuildWrapper wrapper = mock(BuildWrapper.class);
        List<BuildArtifactAqlRow> rows = List.of(mock(BuildArtifactAqlRow.class));
        when(aqlGateway.getBuildArtifactsByNameNumber(wrapper, null, null)).thenReturn(rows);
        assertEquals(rows, buildBridgeToArtifactory.getBuildArtifactsByNameNumberByAql(wrapper, null, null));
    }

    @Test
    void getProducedBuildsByAql_delegatesToAqlGateway() {
        Stream<ProducedBy> stream = Stream.of(mock(ProducedBy.class));
        when(aqlGateway.getProducedBuilds("sha1", "md5")).thenReturn(stream);
        assertSame(stream, buildBridgeToArtifactory.getProducedBuildsByAql("sha1", "md5"));
    }

    @Test
    void getDependentBuildsByAql_delegatesToAqlGateway() {
        Stream<UsedBy> stream = Stream.of(mock(UsedBy.class));
        when(aqlGateway.getDependentBuilds("sha1", "md5")).thenReturn(stream);
        assertSame(stream, buildBridgeToArtifactory.getDependentBuildsByAql("sha1", "md5"));
    }

    @Test
    void createEvidenceForBuildPromotion_delegatesToEvidenceGateway() {
        PromotionStatus status = mock(PromotionStatus.class);
        buildBridgeToArtifactory.createEvidenceForBuildPromotion("r", "b", "1", "s", status);
        verify(evidenceGateway).createEvidenceForBuildPromotion("r", "b", "1", "s", status);
    }

    @Test
    void runXrayInterceptor_delegatesToXrayGateway() {
        buildBridgeToArtifactory.runXrayInterceptor(BuildXrayEvent.CREATE_BUILD, "b", "1", 1700000000000L, "r");
        verify(xrayGateway).runXrayInterceptor(BuildXrayEvent.CREATE_BUILD, "b", "1", 1700000000000L, "r");
    }

    @Test
    void runPluginInterceptor_delegatesToPluginGateway() {
        Build build = mock(Build.class);
        buildBridgeToArtifactory.runPluginInterceptor(BuildPluginType.BEFORE_CREATE, build, "r");
        verify(pluginGateway).runPluginInterceptor(BuildPluginType.BEFORE_CREATE, build, "r");
    }

    @Test
    void runWorkers_delegatesToWorkerGatewayWithSwappedArgs() {
        BuildWrapper wrapper = mock(BuildWrapper.class);
        buildBridgeToArtifactory.runWorkers(BuildWorkerEventType.AFTER_BUILD_INFO_SAVE, wrapper);
        verify(workerGateway).runWorkers(wrapper, BuildWorkerEventType.AFTER_BUILD_INFO_SAVE);
    }
}
