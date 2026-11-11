package org.artifactory.addon.bridge;

import com.jfrog.apptrust.client.ApptrustClient;
import com.jfrog.platformfederation.client.PlatformFederationClient;
import com.jfrog.bh.acl.consumer.bridge.BundleArtifactoryConsumerBridge;
import com.jfrog.bh.acl.consumer.model.BundleArtifactoryVersion;
import com.jfrog.bh.acl.consumer.model.BundleFileInfo;
import com.jfrog.bh.acl.consumer.model.BundleRepoConfig;
import com.jfrog.bh.acl.consumer.model.BundleSourceItem;
import com.jfrog.bh.acl.consumer.model.build.BundleSourceBuild;
import com.jfrog.bh.acl.consumer.model.callhome.BundleReport;
import com.jfrog.bh.acl.consumer.model.metadata.BundlePackageMetadata;
import com.jfrog.bh.acl.error.base.BundleException;
import com.jfrog.bh.acl.producer.model.BundleInputBuild;
import com.jfrog.sysconf.SysConfig;
import jakarta.annotation.Nullable;
import lombok.NonNull;
import org.jfrog.access.client.AccessClient;
import org.jfrog.access.common.PermissionScope;
import org.jfrog.access.common.PermissionScopeResourceType;
import org.jfrog.common.concurrency.ConflictGuard;
import org.jfrog.common.concurrency.ConflictsGuard;
import org.jfrog.jfevent.event.NotificationEvent;
import org.jfrog.jfevent.model.payload.NotificationPayload;
import org.jfrog.storage.DbType;
import org.jfrog.storage.JdbcHelper;
import org.jfrog.xray.client.XrayClient;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Empty implementation of the {@link BundleArtifactoryConsumerBridge} for Artifactory OSS.
 * <p>
 * The non-empty implementation resides in the Artifactory Pro, and has a higher priority
 * than this bean (in Artifactory Pro deployments this empty implementation is suppressed).
 */
@Component
public class BundleArtifactoryConsumerBridgeEmptyImpl implements BundleArtifactoryConsumerBridge {

    private static final UnsupportedOperationException UNSUPPORTED_OPERATION =
            new UnsupportedOperationException("The 'Bundles' add-on is available in Artifactory Pro");

    @NonNull
    @Override
    public String getServiceId() {
        throw UNSUPPORTED_OPERATION;
    }

    @NonNull
    @Override
    public BundleArtifactoryVersion getRunningVersion() {
        throw UNSUPPORTED_OPERATION;
    }

    @NonNull
    @Override
    public SysConfig getSysConfig() {
        return new SysConfig.Builder().build();
    }

    @NonNull
    public String getRouterUrl() {
        throw UNSUPPORTED_OPERATION;
    }

    @NonNull
    @Override
    public JdbcHelper getJdbcHelper() {
        throw UNSUPPORTED_OPERATION;
    }

    @NonNull
    @Override
    public DbType getDatabaseType() {
        throw UNSUPPORTED_OPERATION;
    }

    @Override
    public long getNextDatabaseId() {
        throw UNSUPPORTED_OPERATION;
    }

    @NonNull
    @Override
    public AccessClient getAccessClient() {
        throw UNSUPPORTED_OPERATION;
    }

    @NonNull
    @Override
    public BundleRepoConfig getRepositoryConfig(@NonNull String repoKey) {
        throw UNSUPPORTED_OPERATION;
    }

    @Override
    public boolean repositoryExists(@NonNull String repoKey) {
        throw UNSUPPORTED_OPERATION;
    }

    @Override
    public void createRepositories(@NonNull Map<String, String> repositoryNameToTypeMap) {
        throw UNSUPPORTED_OPERATION;
    }

    @Override
    public boolean repositoryPathExists(@NonNull String repoPath) {
        throw UNSUPPORTED_OPERATION;
    }

    @Override
    public boolean hasPermission(@NonNull PermissionScopeResourceType type,
            @NonNull String permission, @NonNull String repoPath) {
        throw UNSUPPORTED_OPERATION;
    }

    @Override
    public void createBundleRepository(@NonNull String repoKey) {
        throw UNSUPPORTED_OPERATION;
    }

    @NonNull
    @Override
    public Set<BundleSourceItem> collectBundleSourceItemsByAql(@NonNull String aql) {
        throw UNSUPPORTED_OPERATION;
    }

    @NonNull
    @Override
    public Set<BundleSourceItem> collectBundleSourceItemsByFiles(@NonNull Set<String> files) {
        throw UNSUPPORTED_OPERATION;
    }

    @NonNull
    //@Override
    public Set<BundleSourceItem> collectBundleSourceItemsByPackage(@NonNull String packageName,
            @NonNull String packageVersion, @NonNull String packageType, @NonNull String repository) {
        throw UNSUPPORTED_OPERATION;
    }

    /**
     * @deprecated Use {@link #copyFile(String, String, boolean)} instead.
     * Will be removed when the interface drops this overload.
     */
    @Deprecated
    @Override
    public void copyFile(@NonNull String repoPathFrom, @NonNull String repoPathTo) {
        copyFile(repoPathFrom, repoPathTo, false);
    }

    public void copyFile(@NonNull String repoPathFrom, @NonNull String repoPathTo, boolean conditionalInsert) {
        throw UNSUPPORTED_OPERATION;
    }

    @NonNull
    @Override
    public InputStream readFile(@NonNull String repoPath) {
        throw UNSUPPORTED_OPERATION;
    }

    @Override
    public void storeFile(@NonNull String repoPath, byte @NonNull [] payload) throws BundleException {
        throw UNSUPPORTED_OPERATION;
    }

    @Override
    public void deleteFile(@NonNull String repoPath) {
        throw UNSUPPORTED_OPERATION;
    }

    //@Override
    public void deleteFiles(@NonNull Set<String> reposPaths, boolean pruneEmptyFolders) throws BundleException {
        throw UNSUPPORTED_OPERATION;
    }

    @Override
    public void deleteFiles(@NonNull Set<String> reposPaths) {
        throw UNSUPPORTED_OPERATION;
    }

    @NonNull
    @Override
    public BundleFileInfo getFileInfo(@NonNull String repoPath) {
        throw UNSUPPORTED_OPERATION;
    }

    @NonNull
    @Override
    public List<BundleFileInfo> getChildFileInfos(@NonNull String repoPath) {
        throw UNSUPPORTED_OPERATION;
    }

    @Override
    public long getFilesCount(@NonNull String repoPath) {
        throw UNSUPPORTED_OPERATION;
    }

    @NonNull
    @Override
    public Map<String, Collection<String>> getProperties(@NonNull String repoPath) {
        throw UNSUPPORTED_OPERATION;
    }

    @Override
    public void addProperties(@NonNull String repoPath, @NonNull Map<String, List<String>> properties) {
        throw UNSUPPORTED_OPERATION;
    }

    @NonNull
    @Override
    public BundleSourceBuild getBuild(
            @NonNull String buildName, @NonNull String buildNumber,
            @Nullable String buildRepo, @Nullable String buildStarted,
            boolean includeDependencies) {
        throw UNSUPPORTED_OPERATION;
    }

    @NonNull
    @Override
    public Optional<String> getBuildProperty(
            @NonNull String buildPropertyName,
            @NonNull String buildName, @NonNull String buildNumber,
            @Nullable String buildRepo, @Nullable String buildStarted) {
        throw UNSUPPORTED_OPERATION;
    }

    @NonNull
    @Override
    public String scheduleBundleCleanup(@NonNull String cronExpression) {
        throw UNSUPPORTED_OPERATION;
    }

    @Override
    public void deScheduleBundleCleanup(@NonNull String taskToken) {
        throw UNSUPPORTED_OPERATION;
    }

    @Override
    public void scheduleBundleXrayScan(@NonNull String cronExpression) throws BundleException {
        throw UNSUPPORTED_OPERATION;
    }

    @Override
    public void scheduleBundlePromotionXray(@NonNull String cronExpression) throws BundleException {
        throw UNSUPPORTED_OPERATION;
    }

    @Override
    public void scheduleBundleMigrations() throws BundleException {
        throw UNSUPPORTED_OPERATION;
    }

    @Override
    public void processAsync(
            @NonNull Supplier<? extends NotificationEvent<? extends NotificationPayload>> eventSupplier) {
        throw UNSUPPORTED_OPERATION;
    }

    @Override
    public void submitCallHomeReport(@NonNull BundleReport report) {
        throw UNSUPPORTED_OPERATION;
    }

    @NonNull
    @Override
    public String getLicenseType() {
        throw UNSUPPORTED_OPERATION;
    }

    @NonNull
    @Override
    public Optional<XrayClient> getXrayClient() {
        throw UNSUPPORTED_OPERATION;
    }

    @Override
    public boolean isXrayEnabled() {
        throw UNSUPPORTED_OPERATION;
    }

    @NonNull
    @Override
    public Optional<String> getXrayVersion() {
        throw UNSUPPORTED_OPERATION;
    }

    @Override
    public boolean isAllowPromoteWhenXrayUnavailable() {
        throw UNSUPPORTED_OPERATION;
    }

    @Override
    public int getBlockUnfinishedScansTimeoutSeconds() {
        throw UNSUPPORTED_OPERATION;
    }

    @NonNull
    @Override
    public String getArtifactoryId() {
        throw UNSUPPORTED_OPERATION;
    }

    @NonNull
    @Override
    public Optional<String> getBaseUrl() {
        throw UNSUPPORTED_OPERATION;
    }

    @Override
    public boolean hasPermissionScopeToken(@NonNull PermissionScope permissionScope) {
        return false;
    }

    @Override
    public boolean isLeadArtifact(@NonNull String repoPath) {
        throw UNSUPPORTED_OPERATION;
    }

    @NonNull
    @Override
    public Optional<BundlePackageMetadata> getRepositoryPackageMetadata(@NonNull String repoPath) {
        throw UNSUPPORTED_OPERATION;
    }

    @Override
    public void attachReleaseBundleToBuild(
            @NonNull BundleInputBuild build,
            @NonNull String repoKey,
            @NonNull String name,
            @NonNull String version) {
        throw UNSUPPORTED_OPERATION;
    }

    public void detachReleaseBundleFromBuilds(
            @NonNull String repoKey,
            @NonNull String name,
            @NonNull String version) {
        throw UNSUPPORTED_OPERATION;
    }

    @NonNull
    @Override
    public String getBuildInfoRepoPath(
            @NonNull String buildName,
            @NonNull String buildNumber,
            @Nullable String buildStarted,
            @Nullable String buildRepo) {
        throw UNSUPPORTED_OPERATION;
    }

    @Override
    public byte[] searchEvidence(@NonNull String repoKey, @NonNull String path, @NonNull String name) {
        throw UNSUPPORTED_OPERATION;
    }

    @Override
    public void createEvidenceBySubject(@NonNull String subjectRepoPath, @NonNull InputStream content) {
        throw UNSUPPORTED_OPERATION;
    }

    @NonNull
    @Override
    public Optional<String> getPackageId(@NonNull String repoPath) {
        throw UNSUPPORTED_OPERATION;
    }

    @NonNull
    @Override
    public Optional<String> getDefaultDeploymentRepoKey(@NonNull String repoKey) {
        throw UNSUPPORTED_OPERATION;
    }

    @NonNull
    @Override
    public Optional<String> getArtifactSourceRepoKeyFromVirtualRepo(
            @NonNull String repoKey, @NonNull String artifactPath) {
        throw UNSUPPORTED_OPERATION;
    }

    @Override
    public boolean isProjectAdmin(@NonNull String projectKey) {
        return false;
    }

    @Override
    public boolean isVirtualRepo(@NonNull String repoKey) {
        throw UNSUPPORTED_OPERATION;
    }

    @Override
    public void logMetrics(@NonNull Map<String, String> labels, @NonNull String metricName, long metricValue) {
        throw UNSUPPORTED_OPERATION;
    }

    @Override
    public void reportBundleLocallyDeletedFromTarget(@NonNull String storingRepo, @NonNull String releaseBundleName,
            @NonNull String releaseBundleVersion, @NonNull String sourceServiceId, @NonNull String signature) {
        throw UNSUPPORTED_OPERATION;
    }

    @Override
    public void doAsSystem(@NonNull Runnable runnable){
        throw UNSUPPORTED_OPERATION;
    }

    @NonNull
    @Override
    public Optional<ApptrustClient> getApptrustClient() {
        throw UNSUPPORTED_OPERATION;
    }

    @NonNull
    public Optional<PlatformFederationClient> getPlatformFederationClient() {
        throw UNSUPPORTED_OPERATION;
    }

    //@Override
    public boolean hasEvidence(@NonNull String repositoryPath, @NonNull String sha256) {
        throw UNSUPPORTED_OPERATION;
    }

    //@Override
    @NonNull
    public ConflictsGuard<Object> getConflictsGuard(@NonNull String category) {
        return dummyConflictsGuard();
    }

    @NonNull
    private static ConflictsGuard<Object> dummyConflictsGuard() {
        return new ConflictsGuard<>() {

            @Override
            public ConflictGuard getLock(Object key) {
                return null;
            }

            @Override
            public boolean tryToLock(Object key, long timeout, TimeUnit timeUnit) throws InterruptedException {
                return true;
            }

            @Override
            public void unlock(Object key) {
                // no-op
            }

            @Override
            public void forceUnlock(Object key) {
                // no-op
            }

            @Override
            public boolean isLocked(Object key) {
                return false;
            }

            @Override
            public int size() {
                return 0;
            }

            @Override
            public Set<Object> keySet() {
                return Set.of();
            }
        };
    }
}
