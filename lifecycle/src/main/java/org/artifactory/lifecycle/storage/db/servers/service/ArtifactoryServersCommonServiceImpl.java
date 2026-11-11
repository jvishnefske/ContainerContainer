/*
 *
 * Artifactory is a binaries repository manager.
 * Copyright (C) 2018 JFrog Ltd.
 *
 * Artifactory is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 * Artifactory is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Artifactory.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package org.artifactory.lifecycle.storage.db.servers.service;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.lifecycle.storage.db.servers.event.ServersUpdatedEvent;
import org.artifactory.state.ArtifactoryServerState;
import org.artifactory.storage.db.servers.model.ArtifactoryServer;
import org.artifactory.storage.db.servers.model.ArtifactoryServerInfo;
import org.artifactory.storage.db.servers.model.ArtifactoryServerRole;
import org.artifactory.storage.db.servers.service.ArtifactoryServersCommonService;
import org.artifactory.storage.db.servers.service.ArtifactoryServersService;
import org.artifactory.storage.db.servers.service.ArtifactoryServersServiceImpl;
import org.artifactory.storage.db.util.ArtifactoryJdbcHelper;
import org.artifactory.util.CollectionUtils;
import org.jfrog.common.StreamSupportUtils;
import org.jfrog.storage.JdbcHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import jakarta.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.artifactory.common.ConstantValues.haHeartbeatMembersCacheDurationSecs;
import static org.artifactory.common.ConstantValues.haHeartbeatStaleIntervalSecs;

/**
 * @author gidis
 */
@Slf4j
@Service
public class ArtifactoryServersCommonServiceImpl implements ArtifactoryServersCommonService {

    private final ApplicationEventPublisher applicationEventPublisher;
    private final ArtifactoryServersService serversService;
    private final Cache<String, ArtifactoryServer> serversCache = CacheBuilder.newBuilder()
            .expireAfterWrite(haHeartbeatMembersCacheDurationSecs.getInt(), TimeUnit.SECONDS)
            .build();
    private final AtomicInteger serversCount = new AtomicInteger(1);

    @Autowired
    public ArtifactoryServersCommonServiceImpl(ApplicationEventPublisher applicationEventPublisher,
            ArtifactoryServersService serversService) {
        this.applicationEventPublisher = applicationEventPublisher;
        this.serversService = serversService;
    }

    public ArtifactoryServersCommonServiceImpl(JdbcHelper jdbcHelper) {
        applicationEventPublisher = null;
        serversService = new ArtifactoryServersServiceImpl(
                new ArtifactoryJdbcHelper(jdbcHelper.getDataSource())
        );
    }

    public void publishServersUpdatedEvent(List<ArtifactoryServer> servers) {
        if (applicationEventPublisher != null) {
            applicationEventPublisher.publishEvent(new ServersUpdatedEvent(this, servers));
        }
    }

    @Override
    public ArtifactoryServer getCurrentMember() {
        return getArtifactoryServer(ContextHelper.get().getServerId());
    }

    @Nonnull
    @Override
    public List<ArtifactoryServer> getAliveAffinityNodes() {
        return getAllArtifactoryServers().stream()
                .filter(isTaskAffinity)
                .filter(isRunning)
                .filter(hasHeartbeat)
                .collect(Collectors.toList());
    }

    @Nonnull
    @Override
    public List<ArtifactoryServer> filterAliveMembersByNodeNames(List<String> serverNames) {
        return CollectionUtils.notNullOrEmpty(serverNames) ?
                StreamSupportUtils.stream(getAllArtifactoryServers())
                        .filter(artifactoryServer -> serverNames.contains(artifactoryServer.getServerId()))
                        .filter(isRunning)
                        .filter(hasHeartbeat)
                        .collect(Collectors.toList()) : Collections.emptyList();
    }

    @Override
    public List<ArtifactoryServer> getOtherActiveMembers() {
        return getActiveMembers().stream()
                .filter(isOther)
                .collect(Collectors.toList());
    }

    @Override
    public List<ArtifactoryServer> getActiveMembers(boolean useCache) {
        return getAllArtifactoryServers(useCache).stream()
                .filter(hasHeartbeat)
                .filter(isRunning
                        .or(isStarting)
                        .or(isStopping)
                        .or(isConverting))
                .collect(Collectors.toList());
    }

    @Override
    public List<ArtifactoryServer> getActiveMembers() {
        return getActiveMembers(true);
    }

    @Override
    public List<ArtifactoryServer> getConvertingMembers() {
        return getAllArtifactoryServers().stream()
                .filter(hasHeartbeat)
                .filter(isConverting)
                .collect(Collectors.toList());
    }

    @Override
    public boolean isConversionRunning() {
        return !getConvertingMembers().isEmpty();
    }

    @Override
    public List<ArtifactoryServer> getOtherRunningHaMembers() {
        return getAllArtifactoryServers().stream()
                .filter(isOther)
                .filter(isRunning)
                .filter(hasHeartbeat)
                .collect(Collectors.toList());
    }

    @Override
    public List<ArtifactoryServer> getOtherRunningHaMembersWithOutCache() {
        return getAllArtifactoryServers(false).stream()
                .filter(isOther)
                .filter(isRunning)
                .filter(hasHeartbeat)
                .collect(Collectors.toList());
    }

    @Override
    public ArtifactoryServer getArtifactoryServer(String serverId) {
        ArtifactoryServer artifactoryServer = serversCache.getIfPresent(serverId);
        log.debug("Artifactory server info in the cache: {}", artifactoryServer);
        if (Objects.isNull(artifactoryServer)) {
            return getArtifactoryServerFromDatabase(serverId);
        }
        log.debug("Retrieving Artifactory server {} info from the cache", artifactoryServer);
        return artifactoryServer;
    }

    @NonNull
    @Override
    public List<ArtifactoryServer> getAllArtifactoryServers(boolean useCache) {
        List<ArtifactoryServer> artifactoryServers = new ArrayList<>(serversCache.asMap().values());
        log.debug("Artifactory servers info in the cache: {}, useCache: {}, serversCount: {}",
                artifactoryServers, useCache, serversCount.get());
        if (shouldGetFromDatabase(useCache, artifactoryServers)) {
            return getAllArtifactoryServersFromDatabase();
        }
        log.debug("Retrieving Artifactory servers {} info from the cache", artifactoryServers);
        return artifactoryServers;
    }

    @Override
    public List<ArtifactoryServer> getAllArtifactoryServers() {
        return getAllArtifactoryServers(true);
    }

    @Override
    public List<ArtifactoryServerInfo> getAllArtifactoryServersInfo() {
        return getAllArtifactoryServers().stream()
                .map(server -> new ArtifactoryServerInfo(server, haHeartbeatStaleIntervalSecs.getInt()))
                .collect(Collectors.toList());
    }

    @Override
    public void updateArtifactoryServerRole(String serverId, ArtifactoryServerRole newRole) {
        serversService.updateArtifactoryServerRole(serverId, newRole);
        cacheServerRole(serverId, newRole);
    }

    @Override
    public void updateArtifactoryJoinPort(String serverId, int joinPort) {
        serversService.updateArtifactoryJoinPort(serverId, joinPort);
        cacheServerMembershipPort(serverId, joinPort);
    }

    @Override
    public void updateArtifactoryServerState(ArtifactoryServer server, ArtifactoryServerState newState) {
        serversService.updateArtifactoryServerState(server.getServerId(), newState);
        cacheServerState(server, newState);
    }

    @Override
    public void createArtifactoryServer(ArtifactoryServer artifactoryServer) {
        serversService.createArtifactoryServer(artifactoryServer);
        cacheServer(artifactoryServer);
    }

    @Override
    public void updateArtifactoryServer(ArtifactoryServer artifactoryServer) {
        serversService.updateArtifactoryServer(artifactoryServer);
        cacheServer(artifactoryServer);
    }

    @Override
    public boolean removeServer(String serverId) {
        boolean deleted = serversService.removeServer(serverId);
        invalidateCache(serverId);
        return deleted;
    }

    @Override
    public void updateArtifactoryServerHeartbeat(String serverId, long heartBeat, String licenseKeyHash) {
        serversService.updateArtifactoryServerHeartbeat(serverId, heartBeat, licenseKeyHash);
        cacheServerHeartbeat(serverId, heartBeat, licenseKeyHash);
    }

    @Override
    public boolean updateArtifactoryLicenseHash(String serverId, long lastHeartbeat, String licenseKeyHash) {
        List<ArtifactoryServer> allArtifactoryServers = getAllArtifactoryServers(false);
        Optional<ArtifactoryServer> oldMe = allArtifactoryServers.stream()
                .filter(server -> server.getServerId().equals(serverId))
                .findFirst();
        //TODO [shayb] should return with false if not exist?
        String oldLicenseHash = oldMe.isPresent() ? oldMe.get().getLicenseKeyHash() : "";
        List<ArtifactoryServer> otherActiveMembers = allArtifactoryServers.stream().filter(hasHeartbeat)
                .filter(isRunning.or(isStarting).or(isStopping).or(isConverting))
                .filter(isOther)
                .collect(Collectors.toList());

        // If there is a active (heartbeat of less than now - 30 seconds) server with this license, we shouldn't
        //update the DB that we own the license.
        boolean skipUpdate = otherActiveMembers.stream()
                .anyMatch(server -> server.getLicenseKeyHash().equals(licenseKeyHash));
        log.debug("{} should skip update: {}", serverId, skipUpdate);
        if (!skipUpdate) {
            int affectedRows = serversService.updateArtifactoryServerHeartbeat(serverId, lastHeartbeat, licenseKeyHash);
            if (affectedRows > 0) {
                otherActiveMembers = getOtherActiveMembers();
                boolean duplicateLicenseFound = otherActiveMembers.stream()
                        .anyMatch(server -> server.getLicenseKeyHash().equals(licenseKeyHash));
                log.debug("{} found duplicate license: {}", serverId, skipUpdate);
                if (duplicateLicenseFound) {
                    updateArtifactoryServerHeartbeat(serverId, lastHeartbeat, oldLicenseHash);
                } else {
                    log.debug("{} returning true, license was activated", serverId);
                    return true;
                }
            }
        }
        return false;
    }

    private ArtifactoryServer getArtifactoryServerFromDatabase(String serverId) {
        ArtifactoryServer artifactoryServer = serversService.getArtifactoryServer(serverId);
        log.debug("Retrieving Artifactory server {} info from the database", artifactoryServer);
        if (Objects.nonNull(artifactoryServer)) {
            cacheServer(artifactoryServer);
        }
        return artifactoryServer;
    }

    private boolean shouldGetFromDatabase(boolean useCache, List<ArtifactoryServer> artifactoryServers) {
        return !useCache || artifactoryServers.size() < serversCount.get();
    }

    private List<ArtifactoryServer> getAllArtifactoryServersFromDatabase() {
        List<ArtifactoryServer> artifactoryServers = serversService.getAllArtifactoryServers();
        log.debug("Retrieving Artifactory servers {} info from the database", artifactoryServers);
        artifactoryServers.forEach(this::cacheServer);
        serversCount.set(artifactoryServers.size());
        log.debug("Artifactory servers count: {}", serversCount.get());
        return artifactoryServers;
    }

    private void cacheServerRole(String serverId, ArtifactoryServerRole newRole) {
        ArtifactoryServer cachedServer = serversCache.getIfPresent(serverId);
        if (Objects.nonNull(cachedServer)) {
            ArtifactoryServer updatedServer =
                    new ArtifactoryServer(cachedServer.getServerId(), cachedServer.getStartTime(),
                            cachedServer.getContextUrl(), cachedServer.getMembershipPort(),
                            cachedServer.getServerState(), newRole, cachedServer.getLastHeartbeat(),
                            cachedServer.getArtifactoryVersion(), cachedServer.getArtifactoryRevision(),
                            cachedServer.getArtifactoryRelease(), cachedServer.getArtifactoryRunningMode(),
                            cachedServer.getLicenseKeyHash());
            serversCache.put(serverId, updatedServer);
            log.debug("Caching Artifactory server {} with new role: {}", updatedServer, newRole);
        }
    }

    private void cacheServerMembershipPort(String serverId, int newMembershipPort) {
        ArtifactoryServer cachedServer = serversCache.getIfPresent(serverId);
        if (Objects.nonNull(cachedServer)) {
            ArtifactoryServer updatedServer =
                    new ArtifactoryServer(cachedServer.getServerId(), cachedServer.getStartTime(),
                            cachedServer.getContextUrl(), newMembershipPort, cachedServer.getServerState(),
                            cachedServer.getServerRole(), cachedServer.getLastHeartbeat(),
                            cachedServer.getArtifactoryVersion(), cachedServer.getArtifactoryRevision(),
                            cachedServer.getArtifactoryRelease(), cachedServer.getArtifactoryRunningMode(),
                            cachedServer.getLicenseKeyHash());
            serversCache.put(serverId, updatedServer);
            log.debug("Caching Artifactory server {} with new membership port: {}", updatedServer, newMembershipPort);
        }
    }

    private void cacheServerState(ArtifactoryServer server, ArtifactoryServerState newState) {
        ArtifactoryServer cachedServer = serversCache.getIfPresent(server.getServerId());
        if (Objects.nonNull(cachedServer)) {
            ArtifactoryServer updatedServer =
                    new ArtifactoryServer(cachedServer.getServerId(), cachedServer.getStartTime(),
                            cachedServer.getContextUrl(), cachedServer.getMembershipPort(), newState,
                            cachedServer.getServerRole(), cachedServer.getLastHeartbeat(),
                            cachedServer.getArtifactoryVersion(), cachedServer.getArtifactoryRevision(),
                            cachedServer.getArtifactoryRelease(), cachedServer.getArtifactoryRunningMode(),
                            cachedServer.getLicenseKeyHash());
            serversCache.put(server.getServerId(), updatedServer);
            log.debug("Caching Artifactory server {} with new state: {}", updatedServer, newState);
        }
    }

    private void cacheServer(ArtifactoryServer artifactoryServer) {
        serversCache.put(artifactoryServer.getServerId(), artifactoryServer);
        log.debug("Caching Artifactory server {} info", artifactoryServer);
    }

    private void invalidateCache(String serverId) {
        serversCache.invalidate(serverId);
        log.debug("Removing Artifactory server {} info from the cache", serverId);
    }

    private void cacheServerHeartbeat(String serverId, long heartBeat, String licenseKeyHash) {
        ArtifactoryServer cachedServer = serversCache.getIfPresent(serverId);
        if (Objects.nonNull(cachedServer)) {
            ArtifactoryServer updatedServer =
                    new ArtifactoryServer(serverId, cachedServer.getStartTime(), cachedServer.getContextUrl(),
                            cachedServer.getMembershipPort(), cachedServer.getServerState(),
                            cachedServer.getServerRole(), heartBeat, cachedServer.getArtifactoryVersion(),
                            cachedServer.getArtifactoryRevision(), cachedServer.getArtifactoryRelease(),
                            cachedServer.getArtifactoryRunningMode(), licenseKeyHash);
            serversCache.put(serverId, updatedServer);
            log.debug("Caching Artifactory server {} info with new heartbeat: {}", updatedServer, heartBeat);
        }
    }
}
