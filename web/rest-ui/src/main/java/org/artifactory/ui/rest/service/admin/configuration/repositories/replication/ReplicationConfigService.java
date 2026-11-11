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

import com.google.common.collect.Lists;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.addon.AddonsManager;
import org.artifactory.descriptor.config.MutableCentralConfigDescriptor;
import org.artifactory.descriptor.replication.LocalReplicationDescriptor;
import org.artifactory.descriptor.replication.RemoteReplicationDescriptor;
import org.artifactory.descriptor.replication.ReplicationBaseDescriptor;
import org.artifactory.entitlement.EntitlementType;
import org.artifactory.model.LocalConfig;
import org.artifactory.model.RemoteRepoConfig;
import org.artifactory.rest.common.exception.RepoConfigException;
import org.artifactory.ui.rest.service.admin.configuration.repositories.util.validator.ReplicationConfigValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.apache.http.HttpStatus.SC_FORBIDDEN;

/**
 * Service to handle replication specific operations for the repo config services.
 *
 * @author Dan Feldman
 */
@Component
public class ReplicationConfigService {
    private static final Logger log = LoggerFactory.getLogger(ReplicationConfigService.class);

    @Autowired
    private AddonsManager addonsManager;

    @Autowired
    private ReplicationConfigValidator validator;

    public static final String IS_REPLICATION_QUERY_PARAM = "isReplication";

    public void addLocalReplications(Set<LocalReplicationDescriptor> replications, LocalConfig repo,
            MutableCentralConfigDescriptor configDescriptor) throws RepoConfigException {
        String repoKey = repo.getKey();
        if (replications.size() == 0) {
            log.debug("No replication config received for repo {} creation", repoKey);
        } else if (replications.size() > 1) {   //multi-push
            addMultiPushReplications(replications, repo, configDescriptor);
        } else {
            log.info("Adding push replication config for repo {}", repoKey);
            LocalReplicationDescriptor replication = replications.iterator().next();
            configDescriptor.addLocalReplication(replication);
        }
    }

    private void addMultiPushReplications(Set<LocalReplicationDescriptor> replications, LocalConfig repo,
            MutableCentralConfigDescriptor configDescriptor) throws RepoConfigException {
        if (!addonsManager.isEntitled(EntitlementType.MULTIPUSH_REPLICATION)) {
            throw new RepoConfigException(
                    "Multi-push replication is only available with an Enterprise license.", SC_FORBIDDEN);
        }
        String repoKey = repo.getKey();
        validator.validateAllTargetReplicationLicenses(repo, Lists.newArrayList(replications));
        log.info("Adding multi-push replication configurations for repo {}", repoKey);
        replications.forEach(configDescriptor::addLocalReplication);
    }

    public void addRemoteReplication(RemoteReplicationDescriptor replication, RemoteRepoConfig repo,
            MutableCentralConfigDescriptor configDescriptor) throws RepoConfigException {
        log.info("Adding pull replication config for repo {}", repo.getKey());
        configDescriptor.addRemoteReplication(replication);
    }

    public void updateLocalReplications(Set<LocalReplicationDescriptor> replications, String repoKey,
            MutableCentralConfigDescriptor configDescriptor) throws RepoConfigException {
        if (replications.size() == 0) {
            log.debug("No replication config received for repo {}, deleting existing ones", repoKey);
            List<LocalReplicationDescriptor> currentReplications = configDescriptor.getMultiLocalReplications(repoKey);
            currentReplications.forEach(configDescriptor::removeLocalReplication);
            updateAddedAndRemovedReplications(replications, repoKey, configDescriptor);
        } else {
            LocalReplicationDescriptor replicationDescriptor = replications.iterator().next();
            setRepoKey(replicationDescriptor, repoKey);
            updateAddedAndRemovedReplications(replications, repoKey, configDescriptor);
        }
    }

    public void updateRemoteReplication(RemoteReplicationDescriptor replication, RemoteRepoConfig repo,
            MutableCentralConfigDescriptor configDescriptor) {
        String repoKey = repo.getKey();
        log.info("Updating remote replication config for repo {}", repoKey);
        RemoteReplicationDescriptor remoteReplication = configDescriptor.getRemoteReplication(repoKey);
        replication.setCheckBinaryExistenceInFilestore(
                remoteReplication != null && remoteReplication.isCheckBinaryExistenceInFilestore());
        configDescriptor.removeRemoteReplication(remoteReplication);
        configDescriptor.addRemoteReplication(replication);
    }

    public void removeRemoteReplication(RemoteRepoConfig repo, MutableCentralConfigDescriptor configDescriptor) {
        String repoKey = repo.getKey();
        log.info("Removing remote replication config for repo {}", repoKey);
        RemoteReplicationDescriptor remoteReplication = configDescriptor.getRemoteReplication(repoKey);
        if (remoteReplication != null) {
            configDescriptor.removeRemoteReplication(remoteReplication);
            return;
        }
        log.warn("No remote replication found for removal - repo {}", repoKey);
    }

    private void updateAddedAndRemovedReplications(Set<LocalReplicationDescriptor> replications, String repoKey,
            MutableCentralConfigDescriptor configDescriptor) {
        log.info("Updating replication configurations for repo {}", repoKey);
        List<LocalReplicationDescriptor> currentLocalReplications = configDescriptor.getMultiLocalReplications(repoKey);
        updateCheckBinaryExistenceInFileStoreFromConfigDescriptor(currentLocalReplications, replications);
        currentLocalReplications.forEach(configDescriptor::removeLocalReplication);
        replications.forEach(configDescriptor::addLocalReplication);
    }

    private void updateCheckBinaryExistenceInFileStoreFromConfigDescriptor(
            List<LocalReplicationDescriptor> currentLocalReplications,
            Set<LocalReplicationDescriptor> replications) {
        Map<String, Boolean> currentReplicationsMap = currentLocalReplications.stream()
                .collect(Collectors.toMap(LocalReplicationDescriptor::getUrl,
                        LocalReplicationDescriptor::isCheckBinaryExistenceInFilestore));
        replications.forEach(replication ->
                replication.setCheckBinaryExistenceInFilestore(
                        currentReplicationsMap.getOrDefault(replication.getUrl(), false)));
    }

    private void setRepoKey(ReplicationBaseDescriptor replication, String repoKey) {
        if (StringUtils.isBlank(replication.getRepoKey())) {
            replication.setRepoKey(repoKey);
        }
    }
}
