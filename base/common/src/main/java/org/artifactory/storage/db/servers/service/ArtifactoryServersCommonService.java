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

package org.artifactory.storage.db.servers.service;

import org.artifactory.api.context.ContextHelper;
import org.artifactory.common.ConstantValues;
import org.artifactory.state.ArtifactoryServerState;
import org.artifactory.storage.db.servers.model.ArtifactoryServer;
import org.artifactory.storage.db.servers.model.ArtifactoryServerInfo;
import org.artifactory.storage.db.servers.model.ArtifactoryServerRole;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

//TODO [by shayb]: all methods should be converted to ArtifactoryServerInfo (non storage model)

/**
 * @author gidis
 */
public interface ArtifactoryServersCommonService {

    void publishServersUpdatedEvent(List<ArtifactoryServer> servers);

    /**
     * Get the current server
     */
    ArtifactoryServer getCurrentMember();

    /**
     * Get the current running primary HA member
     *
     * @return {@link org.artifactory.storage.db.servers.model.ArtifactoryServer} if exists, otherwise null
     */
    @Nullable
    List<ArtifactoryServer> getAliveAffinityNodes();

    @Nonnull
    List<ArtifactoryServer> filterAliveMembersByNodeNames(List<String> nodeNames);

    /**
     * Get all the other active (state=STARTING,RUNNING,STOPPING,CONVERTING) servers
     */
    List<ArtifactoryServer> getOtherActiveMembers();

    /**
     * Get all the active (state=STARTING,RUNNING,STOPPING,CONVERTING) servers
     *
     * @param useCache Get the information from the cache. Otherwise, go to the DB.
     */
    List<ArtifactoryServer> getActiveMembers(boolean useCache);

    /**
     * Get all the active (state=STARTING,RUNNING,STOPPING,CONVERTING) servers
     */
    List<ArtifactoryServer> getActiveMembers();

    /**
     * Get all the active (state=CONVERTING) servers
     */
    List<ArtifactoryServer> getConvertingMembers();

    boolean isConversionRunning();

    /**
     * Get all the other running HA (state=RUNNING and license=HA) servers
     */
    List<ArtifactoryServer> getOtherRunningHaMembers();

    List<ArtifactoryServer> getOtherRunningHaMembersWithOutCache();

    /**
     * Gets Artifactory server from database by serverId
     */
    ArtifactoryServer getArtifactoryServer(String serverId);

    /**
     * Returns all the Artifactory servers.
     *
     * @param useCache Get the information from the cache. Otherwise, go to the DB.
     */
    List<ArtifactoryServer> getAllArtifactoryServers(boolean useCache);

    /**
     * Returns all the Artifactory servers from the database.
     */
    List<ArtifactoryServer> getAllArtifactoryServers();

    /**
     * @return all Artifactory servers information
     */
    List<ArtifactoryServerInfo> getAllArtifactoryServersInfo();

    void updateArtifactoryServerRole(String serverId, ArtifactoryServerRole newRole);

    void updateArtifactoryJoinPort(String serverId, int port);

    void updateArtifactoryServerState(ArtifactoryServer server, ArtifactoryServerState newState);

    void createArtifactoryServer(ArtifactoryServer artifactoryServer);

    void updateArtifactoryServer(ArtifactoryServer artifactoryServer);

    boolean removeServer(String serverId);

    void updateArtifactoryServerHeartbeat(String serverId, long heartBeat, String licenseKeyHash);

    boolean updateArtifactoryLicenseHash(String serverId, long lastHeartbeat, String licenseKeyHash);

    Predicate<ArtifactoryServer> isOther =
            input -> !input.getServerId().trim().equals(ContextHelper.get().getServerId());

    Predicate<ArtifactoryServer> isRunning = server -> server.getServerState() == ArtifactoryServerState.RUNNING;

    Predicate<ArtifactoryServer> isStarting = server -> server.getServerState() == ArtifactoryServerState.STARTING;

    Predicate<ArtifactoryServer> isStopping = server -> server.getServerState() == ArtifactoryServerState.STOPPING;

    Predicate<ArtifactoryServer> isConverting = server -> server.getServerState() == ArtifactoryServerState.CONVERTING;

    Predicate<ArtifactoryServer> isTaskAffinity =
            server -> server.getServerRole() == ArtifactoryServerRole.TASK_AFFINITY;

    Predicate<ArtifactoryServer> hasHeartbeat = server ->
            TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis() - server.getLastHeartbeat())
                    <= ConstantValues.haHeartbeatStaleIntervalSecs.getInt();

    Predicate<ArtifactoryServer> qualifiesForDeletion = server ->
            // If not converting, use the dynamic system property to decide if qualified for deletion or not (180m by
            //default)
            (!isConverting.test(server) &&
                    TimeUnit.MILLISECONDS.toMinutes(System.currentTimeMillis() - server.getLastHeartbeat())
                            >= ConstantValues.haHeartbeatStaleServerCleanupPeriodMinutes.getInt()) ||
                    // If converting, allow up to 2 hours before qualifying for deletion
                    (isConverting.test(server) &&
                            TimeUnit.MILLISECONDS.toHours(System.currentTimeMillis() - server.getLastHeartbeat()) >= 2);
}
