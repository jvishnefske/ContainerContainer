package org.artifactory.storage.db.locks.service;

import lombok.experimental.Delegate;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.descriptor.config.CentralConfigDescriptor;
import org.artifactory.storage.db.servers.service.ArtifactoryServersCommonService;
import org.artifactory.storage.db.servers.service.ArtifactoryServersService;
import org.artifactory.version.ArtifactoryServiceVersion;
import org.artifactory.version.ArtifactoryServiceVersionProvider;
import org.jfrog.common.config.diff.DataDiff;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * <p>
 * This wrapper's purpose is to toggle from: {@link JFrogTableBasedDbLocksService} to: {@link NativeDbLocksService}
 * when all servers in the cluster support native locking (check is done via: {@link ArtifactoryServersService}).
 * Since it is not possible to determine ahead of time in which state of the application's lifecycle this will occur,
 * the wrapper checks the condition during each lock acquisition.
 * </p>
 *
 * @see JFrogTableBasedDbLocksService
 * @see NativeDbLocksService
 * @see ArtifactoryServersService
 *
 * @author Mor Merhav
 */
@Slf4j
class AutoDbLocksService implements DbLocksService {

    private final NativeDbLocksService nativeDbLocksService;
    private final JFrogTableBasedDbLocksService tableBasedDbLocksService;
    private final ArtifactoryServersCommonService artifactoryServersCommonService;
    private final CentralConfigService centralConfigService;
    @Delegate(excludes = ExcludedMethods.class)
    private DbLocksService dbLocksService;

    public AutoDbLocksService(JFrogTableBasedDbLocksService tableBasedDbLocksService,
            NativeDbLocksService nativeDbLocksService,
            ArtifactoryServersCommonService artifactoryServersCommonService,
            CentralConfigService centralConfigService) {
        this.nativeDbLocksService = nativeDbLocksService;
        this.tableBasedDbLocksService = tableBasedDbLocksService;
        this.artifactoryServersCommonService = artifactoryServersCommonService;
        this.dbLocksService = tableBasedDbLocksService;
        this.centralConfigService = centralConfigService;
    }

    @Override
    public void acquireLock(String category, String key, String owner, long timeout, TimeUnit timeUnit)
            throws TimeoutException {
        if (isForceNativeInAutoMode() || checkIfAllServersSupportNative()) {
            if (dbLocksService != nativeDbLocksService) {
                log.info(
                        "Criteria for native DB locks met. All servers support native locks: '{}', force native in " +
                                "auto mode: '{}'. Switching.",
                        checkIfAllServersSupportNative(), isForceNativeInAutoMode());
                dbLocksService = nativeDbLocksService;
            }
        } else {
            if (dbLocksService != tableBasedDbLocksService) {
                log.info("Criteria for native DB locks not met. Falling back to table based mechanism.");
                dbLocksService = tableBasedDbLocksService;
            }
        }
        dbLocksService.acquireLock(category, key, owner, timeout, timeUnit);
    }

    private boolean isForceNativeInAutoMode() {
        return centralConfigService.getDescriptor().isForceNativeDbLocksMechanismInAutoMode();
    }

    private boolean checkIfAllServersSupportNative() {
        return artifactoryServersCommonService.getAllArtifactoryServers().stream()
                .allMatch(artifactoryServer -> {
                    ArtifactoryServiceVersion serviceVersion =
                            ArtifactoryServiceVersionProvider.get(artifactoryServer.getArtifactoryVersion());
                    ArtifactoryServiceVersion supportedVersion =
                            ArtifactoryServiceVersionProvider.get(nativeDbLocksService.getSupportedVersion());

                    if (serviceVersion != null && supportedVersion != null) {
                        return serviceVersion.afterOrEqual(supportedVersion);
                    } else {
                        return false;
                    }
                });
    }

    @SuppressWarnings("unused")
    private interface ExcludedMethods {

        void acquireLock(String category, String key, String owner, long timeout, TimeUnit timeUnit)
                throws TimeoutException;

        void init();

        void reload(CentralConfigDescriptor oldDescriptor, List<DataDiff<?>> configDiff);

        void destroy();

        boolean isInterested();

        void convert();
    }
}