package com.jfrog.build.config;

import com.jfrog.build.acl.bridge.BuildBridgeToArtifactory;
import com.jfrog.build.aql.BuildAqlArtifacts;
import com.jfrog.build.aql.BuildAqlProducedBy;
import com.jfrog.build.aql.BuildAqlUsedBy;
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
import org.artifactory.addon.AddonsManager;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.config.RepositoryConfigService;
import org.artifactory.api.repo.DeployService;
import org.artifactory.api.request.UploadService;
import org.artifactory.api.search.SearchService;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.api.security.UserGroupService;
import org.artifactory.aql.AqlService;
import org.artifactory.metadata.service.MetadataEntityFacade;
import org.artifactory.platform.PlatformConfigService;
import org.artifactory.projects.ProjectsService;
import org.artifactory.repo.service.InternalRepositoryService;
import org.artifactory.repo.system.SystemRepoConfigFactory;
import org.artifactory.storage.db.DbService;
import org.artifactory.storage.db.fs.dao.NodesDao;
import org.artifactory.storage.fs.service.FileService;
import org.jfrog.storage.JdbcHelper;
import org.jfrog.storage.binstore.ifc.BinaryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

class BuildBridgeBeansConfigTest {

    private BuildBridgeBeansConfig beansConfig;

    @BeforeEach
    void setUp() {
        beansConfig = new BuildBridgeBeansConfig(
                mock(NodesDao.class),
                mock(DbService.class),
                mock(JdbcHelper.class),
                mock(AqlService.class),
                mock(FileService.class),
                mock(AddonsManager.class),
                mock(BinaryService.class),
                mock(DeployService.class),
                mock(SearchService.class),
                mock(ProjectsService.class),
                mock(CentralConfigService.class),
                mock(AuthorizationService.class),
                mock(UserGroupService.class),
                mock(PlatformConfigService.class),
                mock(InternalRepositoryService.class),
                mock(RepositoryConfigService.class),
                mock(MetadataEntityFacade.class),
                mock(SystemRepoConfigFactory.class),
                mock(SecurityService.class),
                mock(UploadService.class)
        );
    }

    @Test
    void buildAqlGateway_Success() {
        BuildAqlArtifacts buildAqlArtifacts = mock(BuildAqlArtifacts.class);
        BuildAqlProducedBy buildAqlProducedBy = mock(BuildAqlProducedBy.class);
        BuildAqlUsedBy buildAqlUsedBy = mock(BuildAqlUsedBy.class);
        BuildAqlGateway gateway = beansConfig.buildAqlGateway(
                buildAqlArtifacts,
                buildAqlProducedBy,
                buildAqlUsedBy
        );
        assertNotNull(gateway);
    }

    @Test
    void buildBridgeToArtifactory_Success() {
        BuildAccessGateway buildAccessGateway = mock(BuildAccessGateway.class);
        BuildAqlGateway buildAqlGateway = mock(BuildAqlGateway.class);
        BuildConfigGateway buildConfigGateway = mock(BuildConfigGateway.class);
        BuildEvidenceGateway buildEvidenceGateway = mock(BuildEvidenceGateway.class);
        BuildFileGateway buildFileGateway = mock(BuildFileGateway.class);
        BuildNodeGateway buildNodeGateway = mock(BuildNodeGateway.class);
        BuildPermissionGateway buildPermissionGateway = mock(BuildPermissionGateway.class);
        BuildPluginGateway buildPluginGateway = mock(BuildPluginGateway.class);
        BuildPropertyGateway buildPropertyGateway = mock(BuildPropertyGateway.class);
        BuildRepoGateway buildRepoGateway = mock(BuildRepoGateway.class);
        BuildUploadGateway buildUploadGateway = mock(BuildUploadGateway.class);
        BuildWorkerGateway buildWorkerGateway = mock(BuildWorkerGateway.class);
        BuildXrayGateway buildXrayGateway = mock(BuildXrayGateway.class);
        BuildBridgeToArtifactory bridge = beansConfig.buildBridgeToArtifactory(
                buildAccessGateway,
                buildAqlGateway,
                buildConfigGateway,
                buildEvidenceGateway,
                buildFileGateway,
                buildNodeGateway,
                buildPermissionGateway,
                buildPluginGateway,
                buildPropertyGateway,
                buildRepoGateway,
                buildUploadGateway,
                buildWorkerGateway,
                buildXrayGateway
        );
        assertNotNull(bridge);
    }
}
