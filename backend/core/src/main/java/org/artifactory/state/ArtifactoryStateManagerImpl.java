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

package org.artifactory.state;

import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.ArtifactoryRunningMode;
import org.artifactory.addon.HaAddon;
import org.artifactory.addon.license.VerificationResult;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.config.CentralConfigKey;
import org.artifactory.descriptor.config.CentralConfigDescriptor;
import org.artifactory.security.access.AccessService;
import org.artifactory.spring.ContextReadinessListener;
import org.artifactory.spring.Reloadable;
import org.artifactory.spring.ReloadableBean;
import org.artifactory.spring.StateChangeListener;
import org.artifactory.state.model.ArtifactoryStateManager;
import org.artifactory.storage.db.DbService;
import org.artifactory.storage.db.servers.model.ArtifactoryServer;
import org.artifactory.storage.db.servers.service.ArtifactoryServersCommonService;
import org.artifactory.version.CompoundVersionDetails;
import org.jfrog.common.config.diff.DataDiff;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.artifactory.state.ArtifactoryServerState.CONVERTING;
import static org.artifactory.state.ArtifactoryServerState.OFFLINE;
import static org.artifactory.state.ArtifactoryServerState.RUNNING;
import static org.artifactory.state.ArtifactoryServerState.STARTING;
import static org.artifactory.state.ArtifactoryServerState.STOPPED;
import static org.artifactory.state.ArtifactoryServerState.STOPPING;

/**
 * @author Gidi Shabat
 */
@Reloadable(beanClass = ArtifactoryStateManager.class, initAfter = DbService.class, listenOn = CentralConfigKey.none)
public class ArtifactoryStateManagerImpl implements ArtifactoryStateManager, ReloadableBean, ContextReadinessListener {
    private static final Logger log = LoggerFactory.getLogger(ArtifactoryStateManagerImpl.class);

    @Autowired
    ArtifactoryServersCommonService serversService;

    @Autowired
    private AddonsManager addonsManager;

    @Autowired
    private AccessService accessService;

    @Override
    public void init() {
        ArtifactoryRunningMode runningMode = addonsManager.getArtifactoryRunningMode();
        log.debug("Starting Artifactory in running mode: {}", runningMode.name());
        // If the verify result is invalid, it's also set the server to offline mode
        VerificationResult result = addonsManager.verifyAllArtifactoryServers(false);
        switch (result) {
            case valid:
                initHa();
                break;
            case converting:
            case notSameVersion:
            case duplicateServerIds:
            case runningModeConflict:
                handleError(result);
                break;
            default: {
                // TODO do we really need to check if HA? the nonHA will never get here anyway.
                if (runningMode.isHa()) {
                    // Invalid/missing license on HA should no longer be offline, we use basicLockdown instead until
                    // the node will get it's license from the cluster license pool
                    initHa();
                } else {
                    createOrUpdateArtifactoryServerState(OFFLINE);
                }
                if (log.isDebugEnabled()) {
                    log.debug("{}", result.showMassage());
                }
            }
        }
    }

    @Override
    public void reload(CentralConfigDescriptor oldDescriptor, List<DataDiff<?>> configDiff) {
        //nop
    }

    @Override
    public void destroy() {
        accessService.unregisterFromRouterIfNeeded();
        createOrUpdateArtifactoryServerState(STOPPED);
    }

    void handleError(VerificationResult result) {
        ArtifactoryServer currentMember = serversService.getCurrentMember();
        long lastHeartbeat = 0;
        if (currentMember != null) {
            lastHeartbeat = currentMember.getLastHeartbeat();
        }
        throw new RuntimeException("Current Artifactory node last heartbeat is: " + lastHeartbeat + ". " +
                result.showMassage());
    }

    private void initHa() {
        boolean converting = ContextHelper.get().getConverterManager().isConverting();
        createOrUpdateArtifactoryServerState(converting ? CONVERTING : STARTING);
        addonsManager.addonByType(HaAddon.class).init();
    }

    @Override
    public void onContextReady() {
        ArtifactoryServer artifactoryServer = serversService.getArtifactoryServer(ContextHelper.get().getServerId());
        if (artifactoryServer == null) {
            throw new IllegalStateException("Artifactory server could not be found [" +
                    ContextHelper.get().getServerId() + "]");
        }
        // Allow to change the state only if not offline and the previous state was STARTING
        boolean allowedToPromote =
                STARTING == artifactoryServer.getServerState() || CONVERTING == artifactoryServer.getServerState();
        if (!ContextHelper.get().isOffline() && allowedToPromote) {
            createOrUpdateArtifactoryServerState(RUNNING);
        } else {
            log.debug("Changing from {} to {} is allowed only if the previous step was {}",
                    artifactoryServer.getServerState(), RUNNING.getPrettyName(), STARTING.getPrettyName());
        }
    }

    @Override
    public boolean forceState(ArtifactoryServerState state) {
        // Currently we do not support to change state during offline
        if (ContextHelper.get().isOffline() && ArtifactoryServerState.OFFLINE != state) {
            log.warn("The change in Artifactory state to {} will take effect only after restart",
                    OFFLINE.getPrettyName());
            return false;
        }
        if (ArtifactoryServerState.OFFLINE == state) {
            ContextHelper.get().setOffline();
        } else if (ArtifactoryServerState.RUNNING == state) {
            addonsManager.addonByType(HaAddon.class).updateArtifactoryServerRole();
        }
        createOrUpdateArtifactoryServerState(state);

        ArtifactoryServer artifactoryServer = serversService.getCurrentMember();
        log.debug("Artifactory server '{}' state is {}", artifactoryServer.getServerId(),
                artifactoryServer.getServerState().getPrettyName());
        return true;
    }

    @Override
    public ArtifactoryServer createOrUpdateArtifactoryServerState(ArtifactoryServerState state) {
        ArtifactoryServer artifactoryServer = ArtifactoryStateManagerUtil
                .createOrUpdateArtifactoryServerState(ContextHelper.get().getServerId(), state,
                        addonsManager.getArtifactoryRunningMode(),
                        ContextHelper.get().getVersionProvider().getRunningServiceVersion(), serversService);

        log.debug("Artifactory server '{}' state is {}", serversService.getCurrentMember().getServerId(),
                state.getPrettyName());
        var stateChangeListeners = ContextHelper.get().beansForType(StateChangeListener.class);
        stateChangeListeners.values()
                .forEach(stateChangeListener -> stateChangeListener.onStateChange(state));
        return artifactoryServer;
    }

    @Override
    public void beforeDestroy() {
        createOrUpdateArtifactoryServerState(STOPPING);
    }

    @Override
    public void convert(CompoundVersionDetails source, CompoundVersionDetails target) {
        //nop
    }
}