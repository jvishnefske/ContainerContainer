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
import com.jfrog.build.acl.model.BuildSecurityDescriptor;
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
import jakarta.annotation.Nullable;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.retention.ArtifactRetentionAddon;
import org.artifactory.api.common.BasicStatusHolder;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.events.EventLogHintManager;
import org.artifactory.api.repo.DeployService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.common.status.MutableStatusHolder;
import org.artifactory.descriptor.security.SecurityDescriptor;
import org.artifactory.event.EventHintType;
import org.artifactory.platform.PlatformConfigService;
import org.artifactory.projects.ProjectsService;
import org.artifactory.repo.RepoPathFactory;
import org.artifactory.repo.interceptor.BuildPromotionInterceptors;
import org.artifactory.repo.system.SystemRepoConfigFactory;
import org.artifactory.request.BuildArtifactoryRequestImpl;
import org.artifactory.storage.db.DbService;
import org.artifactory.storage.spring.ArtifactoryStorageContext;
import org.artifactory.storage.spring.StorageContextHelper;
import org.artifactory.util.HttpUtils;
import org.artifactory.util.bundles.BundleConfigurationUtil;
import org.artifactory.webapp.servlet.HttpArtifactoryResponse;
import org.jfrog.access.client.project.ProjectEntity;
import org.jfrog.access.client.project.ProjectResourceEntity;
import org.jfrog.access.client.project.ProjectSlimEntity;
import org.jfrog.access.common.PermissionScopeResourceType;
import org.jfrog.access.common.ResourceType;
import org.jfrog.build.api.Build;
import org.jfrog.build.api.release.Promotion;
import org.jfrog.build.api.release.PromotionStatus;
import org.jfrog.storage.DbType;
import org.jfrog.storage.JdbcHelper;
import org.jfrog.storage.binstore.ifc.BinaryService;
import org.jfrog.storage.binstore.ifc.model.BinaryElement;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import static java.util.Optional.ofNullable;
import static org.artifactory.entitlement.EntitlementType.EDGE;
import static org.artifactory.repo.system.SystemRepos.SystemRepoConfig.BUILDINFO;

@RequiredArgsConstructor
public class BuildBridgeToArtifactoryImpl implements BuildBridgeToArtifactory {

    private final DbService dbService;
    private final JdbcHelper jdbcHelper;
    private final AddonsManager addonsManager;

    private final BinaryService binaryService;
    private final DeployService deployService;
    private final CentralConfigService configService;
    private final PlatformConfigService platformConfigService;
    private final ProjectsService projectsService;
    private final SecurityService securityService;
    private final SystemRepoConfigFactory systemRepoConfigFactory;

    private final BuildAccessGateway accessGateway;
    private final BuildAqlGateway aqlGateway;
    private final BuildConfigGateway configGateway;
    private final BuildEvidenceGateway evidenceGateway;
    private final BuildFileGateway fileGateway;
    private final BuildNodeGateway nodeGateway;
    private final BuildPermissionGateway permissionGateway;
    private final BuildPluginGateway pluginGateway;
    private final BuildPropertyGateway propertyGateway;
    private final BuildRepoGateway repoGateway;
    private final BuildUploadGateway uploadGateway;
    private final BuildWorkerGateway workerGateway;
    private final BuildXrayGateway xrayGateway;

    @Override
    public boolean isAdmin() {
        return securityService.isAdmin();
    }

    @Override
    public boolean isAnonymous() {
        return securityService.isAnonymous();
    }

    @Override
    public boolean isResourceManager() {
        return securityService.isResourceManager();
    }

    @Override
    public boolean isEdgeInstance() {
        return addonsManager.isEntitled(EDGE);
    }

    @Override
    public boolean isColdInstance() {
        return addonsManager.addonByType(ArtifactRetentionAddon.class).isColdInstanceV1orV2();
    }

    @Override
    public void doAsSystem(@NonNull Runnable runnable) {
        securityService.doAsSystem(runnable);
    }

    @NonNull
    @Override
    public MutableStatusHolder newStatusHolder() {
        return new BasicStatusHolder();
    }

    @NonNull
    @Override
    public String getCurrentUsername() {
        return securityService.currentUsername();
    }

    @NonNull
    @Override
    public File getTempWorkDir() {
        return ContextHelper.get().getArtifactoryHome().getTempWorkDir();
    }

    @NonNull
    @Override
    public String getDateTimeFormat() {
        return platformConfigService.getPlatformGeneralConfig().getDateFormat();
    }

    @Override
    public long getNextDatabaseId() {
        return dbService.nextId();
    }

    @NonNull
    @Override
    public DbType getDatabaseType() {
        return dbService.getDatabaseType();
    }

    @NonNull
    @Override
    public JdbcHelper getJdbcHelper() {
        return jdbcHelper;
    }

    @NonNull
    @Override
    public String getServletContextUrl(@NonNull HttpServletRequest request) {
        return HttpUtils.getServletContextUrl(ContextHelper.get(), request);
    }

    @NonNull
    @Override
    public BuildSecurityDescriptor getSecurityDescriptor() {
        SecurityDescriptor desc = configService.getDescriptor().getSecurity();
        return BuildSecurityDescriptor.builder()
                .buildGlobalBasicReadAllowed(desc.isBuildGlobalBasicReadAllowed())
                .buildGlobalBasicReadForAnonymous(desc.isBuildGlobalBasicReadForAnonymous())
                .hideUnauthorizedResources(desc.isHideUnauthorizedResources())
                .build();
    }

    @NonNull
    @Override
    public SysConfig getSysConfig() {
        return configService.getSystemConfiguration().getSysConfig();
    }

    @NonNull
    @Override
    public <T> T getArtifactoryProperty(
            @NonNull String key,
            @NonNull Class<T> clazz) {
        return configGateway.getArtifactoryConfig(key, clazz);
    }

    @Override
    public void createBuildRepo(
            @NonNull String repoKey) {
        systemRepoConfigFactory.buildSystemRepo(BUILDINFO, repoKey);
    }

    @NonNull
    @Override
    public Optional<BuildRepoDescriptor> getRepoDescriptor(
            @NonNull String repoKey) {
        return repoGateway.getRepoDescriptor(repoKey);
    }

    @NonNull
    @Override
    public Optional<String> getDefaultDeploymentRepoKey(@Nullable String repoKey) {
        return repoGateway.getDefaultDeploymentRepoKey(repoKey);
    }

    @NonNull
    @Override
    public Set<String> getRepoKeys(
            @NonNull Set<String> repoTypes,
            @NonNull String packageType) {
        return repoGateway.getRepoKeys(repoTypes, packageType);
    }

    @NonNull
    @Override
    public InputStream getResourceStream(
            @NonNull String repoPath) {
        return repoGateway.getResourceStream(repoPath);
    }

    @NonNull
    @Override
    public List<String> getVirtualRepoKeys() {
        return repoGateway.getVirtualRepoKeys();
    }

    @NonNull
    @Override
    public BuildFileInfo getFileInfo(
            @NonNull String repoPath) {
        return fileGateway.getFileInfo(repoPath);
    }

    @NonNull
    @Override
    public String upload(
            @NonNull String repoPath,
            @NonNull String payload) {
        return uploadGateway.upload(repoPath, payload);
    }

    @Override
    public boolean repoPathExists(
            @NonNull String repoPath) {
        return repoGateway.repoPathExists(repoPath);
    }

    @Override
    public void addProperties(
            @NonNull String repoPath,
            @NonNull Map<String, Collection<String>> properties) {
        propertyGateway.addProperties(repoPath, properties);
    }

    @Override
    public void renameProperty(
            @NonNull String propertyKey,
            @NonNull String valueFrom,
            @NonNull String valueTo) {
        propertyGateway.renameBuildNameProperty(propertyKey, valueFrom, valueTo);
    }

    @Override
    public void copy(
            @NonNull Set<String> repoPathsToCopy,
            @NonNull String targetRepoKey,
            @NonNull MutableStatusHolder status,
            boolean failFast,
            boolean dryRun) {
        repoGateway.copy(repoPathsToCopy, targetRepoKey, status, failFast, dryRun);
    }

    @Override
    public void move(
            @NonNull Set<String> repoPathsToMove,
            @NonNull String targetRepoKey,
            @NonNull MutableStatusHolder status,
            boolean failFast,
            boolean dryRun) {
        repoGateway.move(repoPathsToMove, targetRepoKey, status, failFast, dryRun);
    }

    @Override
    public void undeploy(
            @NonNull String repoPath,
            @NonNull MutableStatusHolder status,
            boolean triggerMetadataCalculation,
            boolean pruneEmptyFolders,
            boolean skipTrashcan,
            boolean skipBuildDeleteInterceptor,
            boolean idempotentDelete) {
        repoGateway.undeploy(
                repoPath,
                status,
                triggerMetadataCalculation,
                pruneEmptyFolders,
                skipTrashcan,
                skipBuildDeleteInterceptor,
                idempotentDelete
        );
    }

    @Override
    public boolean assertStorageQuota(
            @NonNull String repoKey,
            @NonNull Long size) {
        return repoGateway.assertStorageQuota(repoKey, size);
    }

    @Override
    public void putEventLogHint(
            @NonNull EventHintType type,
            @NonNull String value) {
        EventLogHintManager.put(type, value);
    }

    @Override
    public void removeEventLogHint(
            @NonNull EventHintType type) {
        EventLogHintManager.remove(type);
    }

    @NonNull
    @Override
    public Set<BinaryElement> findBinaries(
            @NonNull Set<String> checksums) {
        return binaryService.findBinaries(checksums);
    }

    @NonNull
    @Override
    public List<BuildNode> getNodesBySha1(
            @NonNull List<String> checksums) {
        return nodeGateway.getNodesBySha1(checksums);
    }

    @Override
    public void logAccessEvent(
            @NonNull BuildAccessLogType type,
            @NonNull String buildRef) {
        accessGateway.logAccessEvent(type, buildRef);
    }

    @Override
    public boolean hasPermission(
            @NonNull PermissionScopeResourceType type,
            @NonNull String permission,
            @NonNull String repoPath) {
        return permissionGateway.hasPermission(type, permission, repoPath);
    }

    @NonNull
    @Override
    public List<String> getArtifactActions(
            @NonNull String repoKey,
            @NonNull String path) {
        return permissionGateway.getArtifactActions(repoKey, path);
    }

    @NonNull
    @Override
    public Optional<BuildArtifactMetadata> getArtifactMetadata(@NonNull String repoPath) {
        return repoGateway.getArtifactMetadata(repoPath);
    }

    @NonNull
    @Override
    public Optional<BuildArtifactChecksums> getArtifactChecksums(
            @NonNull String repoKey,
            @NonNull String path) {
        return repoGateway.getArtifactChecksums(repoKey, path);
    }

    @NonNull
    @Override
    public BuildPermissionsModel getBuildEffectivePermissionsByRepoPath(
            @NonNull String repoKey,
            @NonNull String path) {
        return permissionGateway.getBuildEffectivePermissionsByRepoPath(repoKey, path);
    }

    @NonNull
    @Override
    public BuildPermissionsModel getBuildEffectivePermissions(
            @NonNull String buildName,
            @NonNull String buildNumber,
            @NonNull String buildStarted,
            @NonNull String buildRepo) {
        return permissionGateway.getBuildEffectivePermissions(buildName, buildNumber, buildStarted, buildRepo);
    }

    @NonNull
    @Override
    public List<String> getBuildPermissionsByUser(
            @NonNull String buildName,
            @NonNull String buildNumber,
            @NonNull String buildStarted,
            @NonNull String buildRepo,
            @NonNull String username) {
        return permissionGateway.getBuildPermissionsByUser(buildName, buildNumber, buildStarted, buildRepo, username);
    }

    @NonNull
    @Override
    public List<String> getBuildPermissionsByGroup(
            @NonNull String buildName,
            @NonNull String buildNumber,
            @NonNull String buildStarted,
            @NonNull String buildRepo,
            @NonNull String groupName) {
        return permissionGateway.getBuildPermissionsByGroup(buildName, buildNumber, buildStarted, buildRepo, groupName);
    }

    @Override
    public boolean isProjectAdmin(@NonNull String projectKey) {
        return projectsService.isProjectAdmin(projectKey);
    }

    @NonNull
    @Override
    public List<ProjectEntity> getAllProjects() {
        return projectsService.getAllProjectsEntities();
    }

    @NonNull
    @Override
    public String getProjectForResource(
            @NonNull String resourceName,
            @NonNull ResourceType resourceType) {
        return projectsService.getProjectForResource(resourceName, resourceType);
    }

    @Override
    public void upsertProjectResource(
            @NonNull String projectKey,
            @NonNull ResourceType resourceType,
            @NonNull ProjectResourceEntity resource,
            boolean isNewResource) {
        projectsService.upsertProjectResource(projectKey, resourceType, resource, isNewResource);
    }

    @NonNull
    @Override
    public Optional<ProjectSlimEntity> getProjectSpec(
            @NonNull String projectKey) {
        return ofNullable(projectsService.getProjectSpec(projectKey));
    }

    @NonNull
    @Override
    public String extractProjectKeyFromReleaseBundleRepoKey(@NonNull String bundleRepoKey) {
        return BundleConfigurationUtil.extractProjectKey(bundleRepoKey);
    }

    @Override
    public void redirectBuildUpload(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull String originalServletPath) throws IOException {
        deployService.handleBuildUploadRedirect(
                new BuildArtifactoryRequestImpl(request),
                new HttpArtifactoryResponse(response),
                RepoPathFactory.create(originalServletPath).getRepoKey()
        );
    }

    @NonNull
    @Override
    public List<BuildArtifactAqlRow> getBuildArtifactsByNameNumberTimestampByAql(
            @NonNull BuildWrapper buildWrapper,
            @Nullable List<String> sourceRepos,
            @Nullable List<String> excludedRepos) {
        return aqlGateway.getBuildArtifactsByNameNumberTimestamp(buildWrapper, sourceRepos, excludedRepos);
    }

    @NonNull
    @Override
    public List<BuildArtifactAqlRow> getBuildArtifactsByNameNumberByAql(
            @NonNull BuildWrapper buildWrapper,
            @Nullable List<String> sourceRepos,
            @Nullable List<String> excludedRepos) {
        return aqlGateway.getBuildArtifactsByNameNumber(buildWrapper, sourceRepos, excludedRepos);
    }

    @Override
    public Stream<ProducedBy> getProducedBuildsByAql(
            @Nullable String sha1,
            @Nullable String md5) {
        return aqlGateway.getProducedBuilds(sha1, md5);
    }

    @Override
    public Stream<UsedBy> getDependentBuildsByAql(
            @Nullable String sha1,
            @Nullable String md5) {
        return aqlGateway.getDependentBuilds(sha1, md5);
    }

    @Override
    public void createEvidenceForBuildPromotion(
            @NonNull String buildRepo,
            @NonNull String buildName,
            @NonNull String buildNumber,
            @NonNull String started,
            @NonNull PromotionStatus promotionStatus) {
        evidenceGateway.createEvidenceForBuildPromotion(
                buildRepo, buildName, buildNumber, started, promotionStatus);
    }

    @Override
    public void runXrayInterceptor(
            @NonNull BuildXrayEvent type,
            @NonNull String buildName,
            @NonNull String buildNumber,
            @NonNull Long buildStarted,
            @NonNull String buildRepo) {
        xrayGateway.runXrayInterceptor(type, buildName, buildNumber, buildStarted, buildRepo);
    }

    @Override
    public void runPluginInterceptor(
            @NonNull BuildPluginType type,
            @NonNull Build build,
            @NonNull String buildRepo) {
        pluginGateway.runPluginInterceptor(type, build, buildRepo);
    }

    @Override
    public void runBuildPromotionInterceptor(
            @NonNull Promotion promotion,
            @NonNull Build build,
            @NonNull String buildRepo) {
        ArtifactoryStorageContext context = StorageContextHelper.get();
        BuildPromotionInterceptors interceptors = context.beanForType(BuildPromotionInterceptors.class);
        interceptors.afterPromote(build, buildRepo, promotion);
    }

    @Override
    public void runWorkers(
            @NonNull BuildWorkerEventType type,
            @NonNull BuildWrapper build) {
        workerGateway.runWorkers(build, type);
    }
}
