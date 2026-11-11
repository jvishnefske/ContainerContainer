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

package org.artifactory.ui.rest.service.admin.configuration.repositories.replication;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.replication.ReplicationAddon;
import org.artifactory.api.common.BasicStatusHolder;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.descriptor.replication.RemoteReplicationDescriptor;
import org.artifactory.descriptor.repo.RemoteRepoDescriptor;
import org.artifactory.repo.RepoPathFactory;
import org.artifactory.rest.common.exception.RepoConfigException;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.rest.common.service.RestService;
import org.artifactory.ui.rest.model.admin.configuration.repository.RepositoryConfigModel;
import org.artifactory.ui.utils.RequestUtils;
import org.springframework.stereotype.Component;

import java.io.IOException;

import static org.apache.http.HttpStatus.SC_BAD_REQUEST;
import static org.apache.http.HttpStatus.SC_FORBIDDEN;
import static org.apache.http.HttpStatus.SC_NOT_FOUND;

/**
 * @author Dan Feldman
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExecuteRemoteReplicationService<T extends RepositoryConfigModel> implements RestService<T> {

    private final AddonsManager addonsManager;
    private final CentralConfigService configService;
    private final RepositoryService repoService;
    private final SecurityService securityService;

    @Override
    public void execute(ArtifactoryRestRequest request, RestResponse response) {
        String repoKey = RequestUtils.getRepoKeyFromRequest(request);
        RemoteReplicationDescriptor replicationDesc = configService.getDescriptor().getRemoteReplication(repoKey);
        if (replicationDesc == null || !securityService.isProjectAdmin(RepoPathFactory.create(repoKey))) {
            response.error("Repository does not exists");
            return;
        }
        BasicStatusHolder status = new BasicStatusHolder();
        try {
            runPullReplication(repoKey, status);
            if (status.hasErrors()) {
                log.debug(status.getLastError().getMessage(), status.getLastError().getException());
                response.error("Error scheduling replication task: "
                        + status.getLastError().getMessage()).responseCode(SC_BAD_REQUEST);
            } else {
                response.info("Replication task was successfully scheduled to run in the background.");
            }
        } catch (Exception e) {
            response.error(e.getMessage()).responseCode(SC_BAD_REQUEST);
        }
    }

    private void runPullReplication(String repoKey, BasicStatusHolder status) throws RepoConfigException, IOException {
        log.debug("Model resolved to remote replication, retrieving replication descriptor.");
        if (!addonsManager.isLicenseInstalled()) {
            throw new RepoConfigException("Replication is only available with a pro license and above", SC_FORBIDDEN);
        }
        RemoteRepoDescriptor remoteRepo = repoService.remoteRepoDescriptorByKey(repoKey);
        if (remoteRepo == null) {
            throw new RepoConfigException("Repository '" + repoKey + "' doesn't exist, the 'run now' button only " +
                    "works for existing repositories", SC_NOT_FOUND);
        }
        RemoteReplicationDescriptor replication = configService.getDescriptor().getRemoteReplication(repoKey);
        if (replication == null) {
            throw new RepoConfigException("Replication configuration for repository '" + repoKey + "' doesn't exist, " +
                    "the 'run now' button only works for exiting replication configurations - save the configuration " +
                    "and"
                    + " try again.", SC_NOT_FOUND);
        }
        log.info("Scheduling remote replication task for repository {}", repoKey);
        addonsManager.addonByType(ReplicationAddon.class).scheduleImmediateRemoteReplicationTask(replication, status);
    }
}