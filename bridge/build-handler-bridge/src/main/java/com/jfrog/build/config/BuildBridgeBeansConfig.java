package com.jfrog.build.config;

import com.jfrog.build.acl.bridge.BuildBridgeToArtifactory;
import com.jfrog.build.aql.BuildAqlArtifacts;
import com.jfrog.build.aql.BuildAqlProducedBy;
import com.jfrog.build.aql.BuildAqlUsedBy;
import com.jfrog.build.bridge.BuildBridgeToArtifactoryImpl;
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
import jakarta.annotation.Nullable;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
import org.artifactory.build.Builds;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class BuildBridgeBeansConfig {

    private final NodesDao nodesDao;
    private final DbService dbService;
    private final JdbcHelper jdbcHelper;
    private final AqlService aqlService;
    private final FileService fileService;
    private final AddonsManager addonsManager;
    private final BinaryService binaryService;
    private final DeployService deployService;
    private final SearchService searchService;
    private final ProjectsService projectsService;
    private final CentralConfigService configService;
    private final AuthorizationService authorizationService;
    private final UserGroupService userGroupService;
    private final PlatformConfigService platformConfigService;
    private final InternalRepositoryService repositoryService;
    private final RepositoryConfigService repositoryConfigService;
    private final MetadataEntityFacade metadataEntityFacade;
    private final SystemRepoConfigFactory systemRepoConfigFactory;
    private final SecurityService securityService;
    private final UploadService uploadService;

    private Builds builds;

    /**
     * Available only for Self-Hosted deployments.
     */
    @Autowired(required = false)
    public void setBuilds(@Nullable Builds builds) {
        this.builds = builds;
    }

    @Bean
    public BuildAqlArtifacts buildAqlArtifacts() {
        return new BuildAqlArtifacts(aqlService);
    }

    @Bean
    public BuildAqlProducedBy buildAqlProducedBy() {
        return new BuildAqlProducedBy(aqlService);
    }

    @Bean
    public BuildAqlUsedBy buildAqlUsedBy() {
        return new BuildAqlUsedBy(aqlService);
    }

    @Bean
    public BuildAccessGateway buildAccessGateway() {
        return new BuildAccessGateway();
    }

    @Bean
    public BuildAqlGateway buildAqlGateway(
            @NonNull BuildAqlArtifacts buildAqlArtifacts,
            @NonNull BuildAqlProducedBy buildAqlProducedBy,
            @NonNull BuildAqlUsedBy buildAqlUsedBy) {
        return new BuildAqlGateway(
                buildAqlArtifacts,
                buildAqlProducedBy,
                buildAqlUsedBy
        );
    }

    @Bean
    public BuildConfigGateway buildConfigGateway() {
        return new BuildConfigGateway();
    }

    @Bean
    public BuildEvidenceGateway buildEvidenceGateway() {
        return new BuildEvidenceGateway();
    }

    @Bean
    public BuildFileGateway buildFileGateway() {
        return new BuildFileGateway(fileService);
    }

    @Bean
    public BuildNodeGateway buildNodeGateway() {
        return new BuildNodeGateway(nodesDao);
    }

    @Bean
    public BuildPermissionGateway buildPermissionGateway() {
        return new BuildPermissionGateway(authorizationService, userGroupService);
    }

    @Bean
    public BuildPluginGateway buildPluginGateway() {
        return new BuildPluginGateway(builds, addonsManager);
    }

    @Bean
    public BuildPropertyGateway buildPropertyGateway() {
        return new BuildPropertyGateway(addonsManager, searchService);
    }

    @Bean
    public BuildRepoGateway buildRepoGateway() {
        return new BuildRepoGateway(repositoryConfigService, repositoryService, metadataEntityFacade);
    }

    @Bean
    public BuildUploadGateway buildUploadGateway() {
        return new BuildUploadGateway(uploadService);
    }

    @Bean
    public BuildWorkerGateway buildWorkerGateway() {
        return new BuildWorkerGateway(addonsManager);
    }

    @Bean
    public BuildXrayGateway buildXrayGateway() {
        return new BuildXrayGateway(addonsManager);
    }

    @Bean
    @Primary
    public BuildBridgeToArtifactory buildBridgeToArtifactory(
            @NonNull BuildAccessGateway buildAccessGateway,
            @NonNull BuildAqlGateway buildAqlGateway,
            @NonNull BuildConfigGateway buildConfigGateway,
            @NonNull BuildEvidenceGateway buildEvidenceGateway,
            @NonNull BuildFileGateway buildFileGateway,
            @NonNull BuildNodeGateway buildNodeGateway,
            @NonNull BuildPermissionGateway buildPermissionGateway,
            @NonNull BuildPluginGateway buildPluginGateway,
            @NonNull BuildPropertyGateway buildPropertyGateway,
            @NonNull BuildRepoGateway buildRepoGateway,
            @NonNull BuildUploadGateway buildUploadGateway,
            @NonNull BuildWorkerGateway buildWorkerGateway,
            @NonNull BuildXrayGateway buildXrayGateway) {
        return new BuildBridgeToArtifactoryImpl(
                dbService,
                jdbcHelper,
                addonsManager,
                binaryService,
                deployService,
                configService,
                platformConfigService,
                projectsService,
                securityService,
                systemRepoConfigFactory,
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
    }
}
