package org.artifactory.addon.bridge;

import com.jfrog.distribution.handler.acl.bridge.DistributionHandlerBridgeToArtifactory;
import com.jfrog.distribution.handler.acl.exception.TargetBundleActionsException;
import com.jfrog.distribution.handler.acl.exception.TargetBundleRetentionException;
import com.jfrog.distribution.handler.acl.model.ArtifactoryDetails;
import com.jfrog.sysconf.SysConfig;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.jfrog.access.client.AccessClient;
import org.jfrog.access.common.PermissionScope;
import org.jfrog.storage.DbType;
import org.jfrog.storage.JdbcHelper;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Empty implementation of the {@link DistributionHandlerBridgeToArtifactory} for Artifactory OSS.
 * <p>
 * The non-empty implementation resides in the Artifactory Pro, and has a higher priority
 * than this bean (in Artifactory Pro deployments this empty implementation is suppressed).
 */
@Slf4j
@Component
public class DistributionHandlerBridgeToArtifactoryEmptyImpl implements DistributionHandlerBridgeToArtifactory {

    private static final UnsupportedOperationException UNSUPPORTED_OPERATION =
            new UnsupportedOperationException("Distribution Handler add-on is available in Artifactory Pro");


    @Override
    public @NonNull ArtifactoryDetails getArtifactoryDetails() {
        throw UNSUPPORTED_OPERATION;
    }

    @Override
    public @NonNull DbType getDatabaseType() {
        throw UNSUPPORTED_OPERATION;
    }

    @Override
    public @NonNull SysConfig getSysConfig() {
        throw UNSUPPORTED_OPERATION;
    }

    @Override
    public @NonNull JdbcHelper getJdbcHelper() {
        throw UNSUPPORTED_OPERATION;
    }

    @Override
    public @NonNull AccessClient getAccessClient() {
        throw UNSUPPORTED_OPERATION;
    }

    @Override
    public long getNextDatabaseId() {
        throw UNSUPPORTED_OPERATION;
    }

    @Override
    public void deleteTargetBundle(@NonNull String bundleName, @NonNull String bundleVersion) {
        throw UNSUPPORTED_OPERATION;
    }

    @Override
    public boolean isTargetBundleMarkedAsKeep(@NonNull String bundleName, @NonNull String bundleVersion) {
        throw UNSUPPORTED_OPERATION;
    }

    @Override
    public boolean markTargetBundleKeep(@NonNull String bundleName, @NonNull String bundleVersion, boolean keep) {
        throw UNSUPPORTED_OPERATION;
    }

    @Override
    public @NonNull String scheduleRetentionCleanup(@NonNull String cronExpression)
            throws TargetBundleRetentionException {
        throw UNSUPPORTED_OPERATION;
    }

    @Override
    public @NonNull String scheduleTargetBundleActionsCleanup(@NonNull String cronExpression)
            throws TargetBundleActionsException {
        throw UNSUPPORTED_OPERATION;
    }

    @Override
    public void cancelScheduledRetentionCleanup(@NonNull String taskToken) throws TargetBundleRetentionException {
        throw UNSUPPORTED_OPERATION;
    }

    @Override
    public @NonNull Map<String, String> propagateToNeighbours(@NonNull String data) {
        throw UNSUPPORTED_OPERATION;
    }

    @Override
    public boolean isGrantedByAccessToken(@NonNull PermissionScope scope) {
        throw UNSUPPORTED_OPERATION;
    }
}
