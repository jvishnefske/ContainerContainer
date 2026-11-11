package org.artifactory.lock.util;

import org.artifactory.storage.db.servers.model.ArtifactoryServerInfo;
import org.artifactory.storage.db.servers.service.ArtifactoryServersCommonService;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author Mor Merhav
 */
public final class ReleaseLockStrategyUtils {

    private ReleaseLockStrategyUtils() {
    }

    public static Set<String> findActiveServers(ArtifactoryServersCommonService serversService,
            long staleHeartbeatSeconds) {
        return serversService.getAllArtifactoryServersInfo().stream()
                .filter(serverInfo -> serverInfo.isActive(staleHeartbeatSeconds))
                .map(ArtifactoryServerInfo::getServerId)
                .collect(Collectors.toSet());
    }
}
