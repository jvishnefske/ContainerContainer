package com.jfrog.build.test.bridge;

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
import com.jfrog.build.acl.model.BuildsSearchFilter;
import com.jfrog.build.acl.model.GeneralBuild;
import com.jfrog.build.acl.model.ProducedBy;
import com.jfrog.build.acl.model.UsedBy;
import com.jfrog.build.acl.model.api.permission.BuildPermissionsModel;
import com.jfrog.build.test.database.TestDatabaseProvider;
import com.jfrog.sysconf.SysConfig;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import org.artifactory.common.status.MutableStatusHolder;
import org.artifactory.event.EventHintType;
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
import org.jfrog.storage.binstore.ifc.model.BinaryElement;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.io.InputStream;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

import static com.jfrog.build.service.BuildSysConfigService.SYS_BUILD_ARTIFACTS_QUERY_BY_BUILD_DATE_ENABLED;
import static com.jfrog.build.service.BuildSysConfigService.SYS_BUILD_ARTIFACTS_QUERY_DEDUPLICATION_ENABLED;
import static com.jfrog.build.service.BuildSysConfigService.SYS_BUILD_FORCED_DELETE_ARTIFACTS;
import static com.jfrog.build.service.BuildSysConfigService.SYS_BUILD_RETENTION_ENABLED;
import static com.jfrog.build.service.BuildSysConfigService.SYS_BUILD_SEARCH_MAX_BATCH_SIZE;
import static com.jfrog.build.service.BuildSysConfigService.SYS_DB_OPERATIONS_BULK_SIZE;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TestBuildBridgeToArtifactory implements BuildBridgeToArtifactory {

    /**
     * Adding an initial shift to avoid conflicts with setup scripts.
     */
    public static final long INITIAL_BUILD_ID = 1000L;

    private AtomicLong nextDatabaseId;

    @Getter
    @Setter
    private SysConfig sysConfig;

    @Setter
    private Set<BinaryElement> binaries;

    @Setter
    private Map<String, String> resourcesToProjectKeys;

    public TestBuildBridgeToArtifactory() {
        reset();
    }

    public void reset() {
        nextDatabaseId = new AtomicLong(INITIAL_BUILD_ID);
        initDefaultSysConfig();
        setBinaries(Set.of());
    }

    private void initDefaultSysConfig() {
        sysConfig = mock(SysConfig.class);
        when(sysConfig.getInt(eq(SYS_DB_OPERATIONS_BULK_SIZE), anyInt())).thenReturn(500);
        when(sysConfig.getBoolean(eq(SYS_BUILD_ARTIFACTS_QUERY_BY_BUILD_DATE_ENABLED), anyBoolean())).thenReturn(true);
    }

    @Override
    public boolean isAdmin() {
        return false;
    }

    @Override
    public boolean isAnonymous() {
        return false;
    }

    @Override
    public boolean isResourceManager() {
        return false;
    }

    @Override
    public boolean isEdgeInstance() {
        return false;
    }

    @Override
    public boolean isColdInstance() {
        return false;
    }

    @Override
    public void doAsSystem(@NonNull Runnable runnable) {
        runnable.run();
    }

    @NonNull
    @Override
    public MutableStatusHolder newStatusHolder() {
        return null;
    }

    @NonNull
    @Override
    public String getCurrentUsername() {
        return "";
    }

    @NonNull
    @Override
    public File getTempWorkDir() {
        return null;
    }

    @NonNull
    @Override
    public String getDateTimeFormat() {
        return "dd-MM-yy HH:mm:ss z";
    }

    @Override
    public long getNextDatabaseId() {
        return nextDatabaseId.incrementAndGet();
    }

    @NonNull
    @Override
    public DbType getDatabaseType() {
        return TestDatabaseProvider.getDatabaseType();
    }

    @NonNull
    @Override
    public JdbcHelper getJdbcHelper() {
        return TestDatabaseProvider.getJdbcHelper();
    }

    @NonNull
    @Override
    public String getServletContextUrl(@NonNull HttpServletRequest httpRequest) {
        return "";
    }

    @NonNull
    @Override
    public BuildSecurityDescriptor getSecurityDescriptor() {
        return null;
    }

    @NonNull
    @Override
    @SuppressWarnings("unchecked")
    public <T> T getArtifactoryProperty(
            @NonNull String key,
            @NonNull Class<T> clazz) {
        return switch (key) {
            case SYS_DB_OPERATIONS_BULK_SIZE -> (T) (Integer) 500;
            case SYS_BUILD_SEARCH_MAX_BATCH_SIZE -> (T) (Integer) 5000;
            case SYS_BUILD_RETENTION_ENABLED, SYS_BUILD_FORCED_DELETE_ARTIFACTS,
                 SYS_BUILD_ARTIFACTS_QUERY_BY_BUILD_DATE_ENABLED -> (T) (Boolean) true;
            case SYS_BUILD_ARTIFACTS_QUERY_DEDUPLICATION_ENABLED -> (T) (Boolean) false;
            default -> throw new IllegalArgumentException("Unexpected value: " + key);
        };
    }

    @Override
    public void createBuildRepo(
            @NonNull String repoKey) {

    }

    @NonNull
    @Override
    public Optional<BuildRepoDescriptor> getRepoDescriptor(
            @NonNull String repoKey) {
        return Optional.empty();
    }

    @NonNull
    @Override
    public Optional<String> getDefaultDeploymentRepoKey(
            @NonNull String repoKey) {
        return Optional.empty();
    }

    @NonNull
    @Override
    public Set<String> getRepoKeys(
            @NonNull Set<String> repoTypes,
            @NonNull String packageType) {
        return Set.of();
    }

    @NonNull
    @Override
    public InputStream getResourceStream(
            @NonNull String repoPath) {
        return null;
    }

    @NonNull
    @Override
    public List<String> getVirtualRepoKeys() {
        return List.of();
    }

    @NonNull
    @Override
    public BuildFileInfo getFileInfo(
            @NonNull String repoPath) {
        return null;
    }

    @NonNull
    @Override
    public String upload(
            @NonNull String repoPath,
            @NonNull String payload) {
        return "";
    }

    @Override
    public boolean repoPathExists(@NonNull String repoPath) {
        return false;
    }

    @Override
    public void addProperties(
            @NonNull String repoPath,
            @NonNull Map<String, Collection<String>> properties) {
    }

    @Override
    public void renameProperty(
            @NonNull String propertyKey,
            @NonNull String valueFrom,
            @NonNull String valueTo) {
    }

    @Override
    public void copy(
            @NonNull Set<String> repoPathsToCopy,
            @NonNull String targetRepoKey,
            @NonNull MutableStatusHolder status,
            boolean failFast,
            boolean dryRun) {
    }

    @Override
    public void move(
            @NonNull Set<String> repoPathsToMove,
            @NonNull String targetRepoKey,
            @NonNull MutableStatusHolder status,
            boolean failFast,
            boolean dryRun) {
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
    }

    @Override
    public boolean assertStorageQuota(
            @NonNull String repoKey,
            @NonNull Long size) {
        return false;
    }

    @Override
    public void putEventLogHint(
            @NonNull EventHintType type,
            @NonNull String value) {
    }

    @Override
    public void removeEventLogHint(
            @NonNull EventHintType type) {
    }

    @NonNull
    @Override
    public Set<BinaryElement> findBinaries(
            @NonNull Set<String> checksums) {
        return binaries;
    }

    @NonNull
    @Override
    public List<BuildNode> getNodesBySha1(
            @NonNull List<String> checksums) {
        return List.of();
    }

    @Override
    public void logAccessEvent(
            @NonNull BuildAccessLogType type,
            @NonNull String buildRef) {
    }

    @Override
    public boolean hasPermission(
            @NonNull PermissionScopeResourceType type,
            @NonNull String permission,
            @NonNull String repoPath) {
        return true;
    }

    @NonNull
    @Override
    public List<String> getArtifactActions(
            @NonNull String repoKey,
            @NonNull String path) {
        return List.of();
    }

    @NonNull
    @Override
    public Optional<BuildArtifactMetadata> getArtifactMetadata(@NonNull String repoPath) {
        return Optional.empty();
    }

    @NonNull
    @Override
    public Optional<BuildArtifactChecksums> getArtifactChecksums(
            @NonNull String repoKey,
            @NonNull String path) {
        return Optional.empty();
    }

    @NonNull
    @Override
    public BuildPermissionsModel getBuildEffectivePermissions(
            @NonNull String buildName,
            @NonNull String buildNumber,
            @NonNull String buildStarted,
            @NonNull String buildRepo) {
        return new BuildPermissionsModel(List.of(), List.of(), List.of());
    }

    @NonNull
    @Override
    public BuildPermissionsModel getBuildEffectivePermissionsByRepoPath(
            @NonNull String repoKey,
            @NonNull String path) {
        return new BuildPermissionsModel(List.of(), List.of(), List.of());
    }

    @NonNull
    @Override
    public List<String> getBuildPermissionsByUser(
            @NonNull String buildName,
            @NonNull String buildNumber,
            @NonNull String buildStarted,
            @NonNull String buildRepo,
            @NonNull String username) {
        return List.of();
    }

    @NonNull
    @Override
    public List<String> getBuildPermissionsByGroup(
            @NonNull String buildName,
            @NonNull String buildNumber,
            @NonNull String buildStarted,
            @NonNull String buildRepo,
            @NonNull String groupName) {
        return List.of();
    }

    @Override
    public boolean isProjectAdmin(@NonNull String projectKey) {
        return true;
    }

    @NonNull
    @Override
    public List<ProjectEntity> getAllProjects() {
        return List.of();
    }

    @NonNull
    @Override
    public String getProjectForResource(
            @NonNull String resourceName,
            @NonNull ResourceType resourceType) {
        return resourcesToProjectKeys.get(resourceName);
    }

    @Override
    public void upsertProjectResource(
            @NonNull String projectKey,
            @NonNull ResourceType resourceType,
            @NonNull ProjectResourceEntity resource,
            boolean isNewResource) {
    }

    @NonNull
    @Override
    public Optional<ProjectSlimEntity> getProjectSpec(
            @NonNull String projectKey) {
        return Optional.empty();
    }

    @NonNull
    @Override
    public String extractProjectKeyFromReleaseBundleRepoKey(@NonNull String bundleRepoKey) {
        return "";
    }

    @Override
    public void redirectBuildUpload(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull String originalServletPath) {
    }

    @NonNull
    @Override
    public List<BuildArtifactAqlRow> getBuildArtifactsByNameNumberTimestampByAql(
            @NonNull BuildWrapper buildWrapper,
            @Nullable List<String> sourceRepos,
            @Nullable List<String> excludedRepos) {
        return List.of();
    }

    @NonNull
    @Override
    public List<BuildArtifactAqlRow> getBuildArtifactsByNameNumberByAql(
            @NonNull BuildWrapper buildWrapper,
            @Nullable List<String> sourceRepos,
            @Nullable List<String> excludedRepos) {
        return List.of();
    }

    @Override
    public Stream<ProducedBy> getProducedBuildsByAql(
            @Nullable String sha1,
            @Nullable String md5) {
        return Stream.empty();
    }

    @Override
    public Stream<UsedBy> getDependentBuildsByAql(
            @Nullable String sha1,
            @Nullable String md5) {
        return Stream.empty();
    }

    @Override
    public void createEvidenceForBuildPromotion(
            @NonNull String buildRepo,
            @NonNull String buildName,
            @NonNull String buildNumber,
            @NonNull String started,
            @NonNull PromotionStatus promotionStatus) {
    }

    @Override
    public void runXrayInterceptor(
            @NonNull BuildXrayEvent type,
            @NonNull String buildName,
            @NonNull String buildNumber,
            @NonNull Long buildStarted,
            @NonNull String buildRepo) {
    }

    @Override
    public void runPluginInterceptor(
            @NonNull BuildPluginType type,
            @NonNull Build build,
            @NonNull String buildRepo) {
    }

    @Override
    public void runBuildPromotionInterceptor(
            @NonNull Promotion promotion,
            @NonNull Build build,
            @NonNull String buildRepo) {
    }

    @Override
    public void runWorkers(
            @NonNull BuildWorkerEventType type,
            @NonNull BuildWrapper build) {
    }
}
