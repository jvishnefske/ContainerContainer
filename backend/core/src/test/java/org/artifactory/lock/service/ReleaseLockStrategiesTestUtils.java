package org.artifactory.lock.service;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.artifactory.concurrent.NonStandardLockCategory;
import org.artifactory.storage.db.servers.model.ArtifactoryServerInfo;
import org.mockito.Mockito;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * @author Mor Merhav
 */
final class ReleaseLockStrategiesTestUtils {

    private ReleaseLockStrategiesTestUtils() {

    }

    public static List<ArtifactoryServerInfo> getArtifactoryServerInfoList(int numServers, boolean isActive) {
        List<ArtifactoryServerInfo> serverInfoList = Lists.newArrayList();
        for (int i = 0; i < numServers; i++) {
            ArtifactoryServerInfo serverInfo = getArtifactoryServerInfo(isActive, String.valueOf(i));
            serverInfoList.add(serverInfo);
        }
        return serverInfoList;
    }

    public static List<ArtifactoryServerInfo> getArtifactoryServerInfoList(int numServers, int stableHeartBeatSecs) {
        List<ArtifactoryServerInfo> serverInfoList = Lists.newArrayList();
        for (int i = 0; i < numServers; i++) {
            ArtifactoryServerInfo serverInfo = getArtifactoryServerInfo(stableHeartBeatSecs, String.valueOf(i));
            serverInfoList.add(serverInfo);
        }
        return serverInfoList;
    }

    public static ArtifactoryServerInfo getArtifactoryServerInfo(boolean isActive, String serverId) {
        ArtifactoryServerInfo serverInfo = Mockito.mock(ArtifactoryServerInfo.class);
        when(serverInfo.isActive(anyLong())).thenReturn(isActive);
        when(serverInfo.getServerId()).thenReturn(serverId);
        return serverInfo;
    }

    public static ArtifactoryServerInfo getArtifactoryServerInfo(int staleHeartBeatSecs, String serverId) {
        ArtifactoryServerInfo serverInfo = Mockito.mock(ArtifactoryServerInfo.class);
        when(serverInfo.isActive(eq(staleHeartBeatSecs))).thenReturn(true);
        when(serverInfo.getServerId()).thenReturn(serverId);
        return serverInfo;
    }

    public static Set<String> getNonStandardCategories() {
        return Sets.newHashSet(NonStandardLockCategory.values()).stream()
                .map(NonStandardLockCategory::getName)
                .collect(Collectors.toSet());
    }
}
