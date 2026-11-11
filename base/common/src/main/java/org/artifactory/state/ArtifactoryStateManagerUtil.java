package org.artifactory.state;

import com.jfrog.sysconf.SysConfig;
import com.jfrog.sysconf.SysConfigConstants;
import org.artifactory.addon.ArtifactoryRunningMode;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.storage.db.servers.model.ArtifactoryServer;
import org.artifactory.storage.db.servers.service.ArtifactoryServersCommonService;
import org.artifactory.util.TomcatListeningDetector;
import org.artifactory.version.CompoundVersionDetails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.jfrog.sysconf.SysConfigConstants.ARTIFACTORY_SUFFIX;
import static org.artifactory.storage.db.servers.model.ArtifactoryServerRole.STANDALONE;

/**
 * @author Mor Merhav
 */
public final class ArtifactoryStateManagerUtil {

    private static final Logger log = LoggerFactory.getLogger(ArtifactoryStateManagerUtil.class);
    private static final int INITIAL_CLUSTER_PORT = 0;

    private ArtifactoryStateManagerUtil() {
    }

    public static ArtifactoryServer createOrUpdateArtifactoryServerState(String serverId, ArtifactoryServerState state,
            ArtifactoryRunningMode runningMode, CompoundVersionDetails runningVersion,
            ArtifactoryServersCommonService serversService) {
        String contextUrl = createContextUrl();
        ArtifactoryServer prevArtifactoryServer = serversService.getArtifactoryServer(serverId);
        if (prevArtifactoryServer == null) {
            return createArtifactoryServer(serverId, contextUrl, state, runningMode, runningVersion,
                    serversService);
        } else {
            return updateArtifactoryServer(serverId, contextUrl, state, runningMode, runningVersion,
                    serversService, prevArtifactoryServer);
        }
    }

    public static String createContextUrl() {
        SysConfig sysConfig = ArtifactoryHome.get().getSysConfig();
        boolean routerEnabled = sysConfig.getBoolean(SysConfigConstants.KEY_ROUTER_ENABLED, true);
        String contextUrl = ArtifactoryHome.get().getHaNodeProperties().getIp() + ":" +
                sysConfig.helper().getRouterExternalPort() + ARTIFACTORY_SUFFIX;
        if (!routerEnabled) {
            contextUrl = "http://127.0.0.1:" + TomcatListeningDetector.detectMainListeningPort() + ARTIFACTORY_SUFFIX;
        }
        return contextUrl;
    }

    private static ArtifactoryServer createArtifactoryServer(String serverId, String serverContextUrl,
            ArtifactoryServerState serverState, ArtifactoryRunningMode runningMode,
            CompoundVersionDetails runningVersion, ArtifactoryServersCommonService serversService) {
        ArtifactoryServer artifactoryServer;
        artifactoryServer = ArtifactoryServerHelper
                .createArtifactoryServer(serverId, serverContextUrl, INITIAL_CLUSTER_PORT,
                        runningVersion, serverState, STANDALONE, runningMode);
        serversService.createArtifactoryServer(artifactoryServer);
        log.debug("Created Artifactory server: {} state: {}",
                serverId, artifactoryServer.getServerState().getPrettyName());
        return artifactoryServer;
    }

    private static ArtifactoryServer updateArtifactoryServer(String serverId, String serverContextUrl,
            ArtifactoryServerState serverState, ArtifactoryRunningMode runningMode,
            CompoundVersionDetails runningVersion, ArtifactoryServersCommonService serversService,
            ArtifactoryServer prevArtifactoryServer) {
        ArtifactoryServer artifactoryServer;
        artifactoryServer = ArtifactoryServerHelper
                .createArtifactoryServer(serverId, serverContextUrl, INITIAL_CLUSTER_PORT,
                        runningVersion, serverState, prevArtifactoryServer.getServerRole(), runningMode);
        logArtifactoryServerUpdate(serverId, prevArtifactoryServer, artifactoryServer);
        serversService.updateArtifactoryServer(artifactoryServer);

        log.debug("Updated Artifactory server: {} state: {}", serverId,
                artifactoryServer.getServerState().getPrettyName());
        return artifactoryServer;
    }

    private static void logArtifactoryServerUpdate(String serverId, ArtifactoryServer prevArtifactoryServer,
            ArtifactoryServer artifactoryServer) {
        if (artifactoryServer.getServerState() != prevArtifactoryServer.getServerState()) {
            log.debug("Changing Artifactory server: '{}' state: {} to state: {}", serverId,
                    prevArtifactoryServer.getServerState(), artifactoryServer.getServerState());
        }
        if (!artifactoryServer.getContextUrl().equals(prevArtifactoryServer.getContextUrl())) {
            log.debug("Previous Artifactory server: '{}' has different context URL {}. Updating {}", serverId,
                    prevArtifactoryServer.getContextUrl(), artifactoryServer.getContextUrl());
        }
    }
}
