package com.jfrog.build.acl.bridge;

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
import com.jfrog.sysconf.SysConfig;
import jakarta.annotation.Nullable;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;
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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

public interface BuildBridgeToArtifactory {

    boolean isAdmin();

    boolean isAnonymous();

    boolean isResourceManager();

    boolean isEdgeInstance();

    boolean isColdInstance();

    void doAsSystem(@NonNull Runnable runnable);

    @NonNull
    MutableStatusHolder newStatusHolder();

    @NonNull
    String getCurrentUsername();

    @NonNull
    File getTempWorkDir();

    @NonNull
    String getDateTimeFormat();

    /**
     * Gets a next unique ID that can be used as a row ID in any of the database tables.
     */
    long getNextDatabaseId();

    /**
     * Gets a database type, which is used by Artifactory.
     */
    @NonNull
    DbType getDatabaseType();

    /**
     * Gets a component for working with the Artifactory database.
     */
    @NonNull
    JdbcHelper getJdbcHelper();

    @NonNull
    String getServletContextUrl(@NonNull HttpServletRequest httpRequest);

    @NonNull
    BuildSecurityDescriptor getSecurityDescriptor();

    /**
     * Gets a component for working with the Artifactory system.yaml configuration.
     */
    @NonNull
    SysConfig getSysConfig();

    @NonNull
    <T> T getArtifactoryProperty(
            @NonNull String key,
            @NonNull Class<T> clazz);

    void createBuildRepo(
            @NonNull String repoKey);

    @NonNull
    Optional<BuildRepoDescriptor> getRepoDescriptor(
            @NonNull String repoKey);

    @NonNull
    Optional<String> getDefaultDeploymentRepoKey(
            @Nullable String repoKey);

    @NonNull
    Set<String> getRepoKeys(
            @NonNull Set<String> repoTypes,
            @NonNull String packageType);

    @NonNull
    InputStream getResourceStream(
            @NonNull String repoPath);

    @NonNull
    List<String> getVirtualRepoKeys();

    @NonNull
    BuildFileInfo getFileInfo(
            @NonNull String repoPath);

    @NonNull
    String upload(
            @NonNull String repoPath,
            @NonNull String payload);

    boolean repoPathExists(
            @NonNull String repoPath);

    void addProperties(
           @NonNull String repoPath,
           @NonNull Map<String, Collection<String>> properties);

    void renameProperty(
            @NonNull String propertyKey,
            @NonNull String valueFrom,
            @NonNull String valueTo);

    void copy(
            @NonNull Set<String> repoPathsToCopy,
            @NonNull String targetRepoKey,
            @NonNull MutableStatusHolder status,
            boolean failFast,
            boolean dryRun);

    void move(
            @NonNull Set<String> repoPathsToMove,
            @NonNull String targetRepoKey,
            @NonNull MutableStatusHolder status,
            boolean failFast,
            boolean dryRun);

    void undeploy(
            @NonNull String repoPath,
            @NonNull MutableStatusHolder status,
            boolean triggerMetadataCalculation,
            boolean pruneEmptyFolders,
            boolean skipTrashcan,
            boolean skipBuildDeleteInterceptor,
            boolean idempotentDelete);

    boolean assertStorageQuota(
            @NonNull String repoKey,
            @NonNull Long size);

    void putEventLogHint(
            @NonNull EventHintType type,
            @NonNull String value);

    void removeEventLogHint(
            @NonNull EventHintType type);

    @NonNull
    Set<BinaryElement> findBinaries(
            @NonNull Set<String> checksums);

    @NonNull
    List<BuildNode> getNodesBySha1(
            @NonNull List<String> checksums);

    void logAccessEvent(
            @NonNull BuildAccessLogType type,
            @NonNull String buildRef);

    boolean hasPermission(
            @NonNull PermissionScopeResourceType type,
            @NonNull String permission,
            @NonNull String repoPath);

    /**
     * Returns the UI actions available on the given artifact for the current user: empty when the user cannot read the
     * path, otherwise "Download" and "ShowInTree", plus "View" when the artifact is viewable.
     */
    @NonNull
    List<String> getArtifactActions(
            @NonNull String repoKey,
            @NonNull String path);

    /**
     * Resolves the package metadata (BOM) for an artifact at the given repo path, used to group build artifacts into
     * packages. Empty when the path has no resolvable metadata.
     */
    @NonNull
    Optional<BuildArtifactMetadata> getArtifactMetadata(
            @NonNull String repoPath);

    /**
     * Resolves the SHA1/MD5 checksums of the artifact at the given repo path (resolving virtual repositories to
     * their backing item). Empty when the path resolves to a folder rather than a file.
     */
    @NonNull
    Optional<BuildArtifactChecksums> getArtifactChecksums(
            @NonNull String repoKey,
            @NonNull String path);

    /**
     * Computes the effective permissions for a build-info repository path: the repository root aggregates every
     * build ACL, while a specific path is restricted to the ACLs covering it. Used by the repository Effective
     * Permissions tab for build-info repositories.
     */
    @NonNull
    BuildPermissionsModel getBuildEffectivePermissionsByRepoPath(
            @NonNull String repoKey,
            @NonNull String path);

    /**
     * Computes the effective (aggregated) permissions of a build: per-user and per-group permissions derived from the
     * build's ACLs, admin principals, and group-to-user expansion, plus the build's permission targets.
     */
    @NonNull
    BuildPermissionsModel getBuildEffectivePermissions(
            @NonNull String buildName,
            @NonNull String buildNumber,
            @NonNull String buildStarted,
            @NonNull String buildRepo);

    /**
     * Returns the names of the build's permission targets that grant the given user access (directly or via one of
     * the user's groups). Empty when the user does not exist.
     */
    @NonNull
    List<String> getBuildPermissionsByUser(
            @NonNull String buildName,
            @NonNull String buildNumber,
            @NonNull String buildStarted,
            @NonNull String buildRepo,
            @NonNull String username);

    /**
     * Returns the names of the build's permission targets that grant the given group access (a group ACE naming it).
     */
    @NonNull
    List<String> getBuildPermissionsByGroup(
            @NonNull String buildName,
            @NonNull String buildNumber,
            @NonNull String buildStarted,
            @NonNull String buildRepo,
            @NonNull String groupName);

    boolean isProjectAdmin(
            @NonNull String projectKey);

    @NonNull
    List<ProjectEntity> getAllProjects();

    @NonNull
    String getProjectForResource(
            @NonNull String resourceName,
            @NonNull ResourceType resourceType);

    void upsertProjectResource(
            @NonNull String projectKey,
            @NonNull ResourceType resourceType,
            @NonNull ProjectResourceEntity resource,
            boolean isNewResource);

    @NonNull
    Optional<ProjectSlimEntity> getProjectSpec(
            @NonNull String projectKey);

    @NonNull
    String extractProjectKeyFromReleaseBundleRepoKey(
            @NonNull String bundleRepoKey);

    void redirectBuildUpload(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull String originalServletPath) throws IOException;

    @NonNull
    List<BuildArtifactAqlRow> getBuildArtifactsByNameNumberTimestampByAql(
            @NonNull BuildWrapper buildWrapper,
            @Nullable List<String> sourceRepos,
            @Nullable List<String> excludedRepos);

    @NonNull
    List<BuildArtifactAqlRow> getBuildArtifactsByNameNumberByAql(
            @NonNull BuildWrapper buildWrapper,
            @Nullable List<String> sourceRepos,
            @Nullable List<String> excludedRepos);

    Stream<ProducedBy> getProducedBuildsByAql(
            @Nullable String sha1,
            @Nullable String md5);

    Stream<UsedBy> getDependentBuildsByAql(
            @Nullable String sha1,
            @Nullable String md5);

    void createEvidenceForBuildPromotion(
            @NonNull String buildRepo,
            @NonNull String buildName,
            @NonNull String buildNumber,
            @NonNull String started,
            @NonNull PromotionStatus promotionStatus);

    void runXrayInterceptor(
            @NonNull BuildXrayEvent type,
            @NonNull String buildName,
            @NonNull String buildNumber,
            @NonNull Long buildStarted,
            @NonNull String buildRepo);

    void runPluginInterceptor(
            @NonNull BuildPluginType type,
            @NonNull Build build,
            @NonNull String buildRepo);

    void runBuildPromotionInterceptor(
            @NonNull Promotion promotion,
            @NonNull Build build,
            @NonNull String buildRepo);

    void runWorkers(
            @NonNull BuildWorkerEventType type,
            @NonNull BuildWrapper build);
}
