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

package org.artifactory.ui.rest.service.admin.configuration.repositories.util;

import org.apache.http.HttpStatus;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.p2.P2Repo;
import org.artifactory.addon.smartrepo.EdgeSmartRepoAddon;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.config.RepositoryConfigService;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.api.repo.UpstreamRepoKind;
import org.artifactory.descriptor.config.MutableCentralConfigDescriptor;
import org.artifactory.descriptor.replication.LocalReplicationDescriptor;
import org.artifactory.descriptor.replication.RemoteReplicationDescriptor;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.descriptor.repo.ReverseProxyDescriptor;
import org.artifactory.model.FederatedRepoConfig;
import org.artifactory.model.LocalConfig;
import org.artifactory.model.ReleaseBundleRepoConfig;
import org.artifactory.model.RemoteRepoConfig;
import org.artifactory.model.RepoConfig;
import org.artifactory.model.VirtualRepoConfig;
import org.artifactory.projects.ProjectsService;
import org.artifactory.repo.validator.virtual.VirtualRepoPackageValidator;
import org.artifactory.rest.common.exception.RepoConfigException;
import org.artifactory.ui.rest.model.admin.configuration.repository.federation.FederatedRepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.federation.MirrorMemberConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.local.LocalRepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.releasebundles.ReleaseBundlesRepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.remote.RemoteRepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.P2TypeSpecificConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.virtual.VirtualRepositoryConfigModel;
import org.artifactory.ui.rest.service.admin.configuration.repositories.replication.ReplicationConfigService;
import org.artifactory.ui.rest.service.admin.configuration.repositories.util.builder.ReplicationConfigDescriptorBuilder;
import org.artifactory.ui.rest.service.admin.configuration.repositories.util.builder.RepoConfigDescriptorBuilder;
import org.artifactory.ui.rest.service.admin.configuration.repositories.util.validator.RemoteRepoPackageValidator;
import org.artifactory.ui.rest.service.admin.configuration.repositories.util.validator.ReplicationConfigValidator;
import org.artifactory.ui.rest.service.admin.configuration.repositories.util.validator.RepoConfigValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static org.artifactory.repo.service.NugetRepoConfigValidator.validateNugetPackageTypeConfig;

/**
 * Delegate helper for creating and persisting repo config
 *
 * @author Dan Feldman
 */
@Component
public class UpdateRepoConfigHelper {
    private static final Logger log = LoggerFactory.getLogger(UpdateRepoConfigHelper.class);

    private static final String MSG_UPDATING_DESCRIPTOR = "Updating descriptor for {}.";

    @Autowired
    private RepositoryConfigService repositoryConfigService;
    @Autowired
    private RepositoryService repoService;
    @Autowired
    private RepoConfigDescriptorBuilder builder;
    @Autowired
    private RepoConfigValidator validator;
    @Autowired
    private ReplicationConfigDescriptorBuilder replicationBuilder;
    @Autowired
    private ReplicationConfigService replicationConfigService;
    @Autowired
    private ReplicationConfigValidator replicationValidator;
    @Autowired
    private RemoteRepoPackageValidator remoteRepoPackageValidator;
    @Autowired
    private VirtualRepoPackageValidator virtualValidator;
    @Autowired
    private CentralConfigService configService;
    @Autowired
    private CreateRepoConfigHelper creator;
    @Autowired
    private AddonsManager addonsManager;
    @Autowired
    private ProjectsService projectsService;
    @Autowired
    private RepositoryService repositoryService;

    public void handleLocal(LocalRepositoryConfigModel model, boolean fedAttrOptIn) throws RepoConfigException {
        MutableCentralConfigDescriptor updatedConfigDescriptor = configService.getMutableDescriptor();
        log.debug("Model resolved to local repo descriptor, updating.");
        String repoKey = model.getGeneral().getRepoKey();
        LocalConfig newLocalConfig = model.toRepoConfig(validator, builder, updatedConfigDescriptor);
        if (fedAttrOptIn) {
            builder.populateFederationValues(model, newLocalConfig);
        }
        ReverseProxyDescriptor reverseProxyDescriptor = model.getReverseProxyDescriptor(newLocalConfig, builder);
        Set<LocalReplicationDescriptor> replications = model
                .getReplicationDescriptors(replicationValidator, replicationBuilder);
        log.debug(MSG_UPDATING_DESCRIPTOR, repoKey);
        updatedConfigDescriptor.updateReverseProxy(reverseProxyDescriptor);
        replicationConfigService.updateLocalReplications(replications, repoKey, updatedConfigDescriptor);
        int numberOfDiffs = configService.findDiff(configService.getDescriptor(),
                updatedConfigDescriptor).getNumberOfDiffs();
        if (numberOfDiffs > 0) {
            configService
                    .saveEditedDescriptorAndReload(updatedConfigDescriptor, reverseProxyDescriptor, replications);
        }
        repositoryConfigService.upsertRepoConfig(newLocalConfig);
    }

    public void handleFederated(FederatedRepositoryConfigModel model) throws RepoConfigException {
        MutableCentralConfigDescriptor updatedConfigDescriptor = configService.getMutableDescriptor();
        String repoKey = model.getGeneral().getRepoKey();
        FederatedRepoConfig newFederatedRepoConfig = model.toRepoConfig(validator, builder, updatedConfigDescriptor);
        log.debug("Model resolved to federated repo descriptor, updating.");
        ReverseProxyDescriptor reverseProxyDescriptor =
                model.getReverseProxyDescriptor(newFederatedRepoConfig, builder);
        List<MirrorMemberConfigModel> mirrors = model.getFederation() != null
                ? model.getFederation().getMirrors() : null;
        log.debug(MSG_UPDATING_DESCRIPTOR, repoKey);
        updatedConfigDescriptor.updateReverseProxy(reverseProxyDescriptor);
        // TODO check this out
        newFederatedRepoConfig.getRepoTypeConfig()
                .setModificationDate(
                        repositoryConfigService.getFederatedRepoConfigByKey(newFederatedRepoConfig.getKey())
                                .getRepoTypeConfig().getModificationDate());
        int numberOfDiffs = configService.findDiff(configService.getDescriptor(),
                updatedConfigDescriptor).getNumberOfDiffs();
        if (numberOfDiffs > 0) {
            configService
                    .saveEditedDescriptorAndReload(updatedConfigDescriptor, reverseProxyDescriptor, mirrors);
        }
        repositoryConfigService.upsertRepoConfig(newFederatedRepoConfig);
    }

    public void handleRemote(RemoteRepositoryConfigModel model, boolean fedAttrOptIn) throws RepoConfigException {
        log.debug("Model resolved to remote repo descriptor, updating.");
        MutableCentralConfigDescriptor updatedConfigDescriptor = configService.getMutableDescriptor();
        RemoteRepoConfig newRemoteRepoConfig = model.toRepoConfig(validator, builder, updatedConfigDescriptor);
        if (fedAttrOptIn) {
            builder.populateFederationValues(model, newRemoteRepoConfig);
        }
        validateSmartRemoteRepoOnEdge(newRemoteRepoConfig);
        validateTypeSpecificRemoteRepo(newRemoteRepoConfig, updatedConfigDescriptor, model);
        ReverseProxyDescriptor reverseProxyDescriptor = model.getReverseProxyDescriptor(newRemoteRepoConfig, builder);
        updatedConfigDescriptor.updateReverseProxy(reverseProxyDescriptor);
        RemoteReplicationDescriptor replication =
                model.getReplicationDescriptor(replicationValidator, replicationBuilder);
        log.debug(MSG_UPDATING_DESCRIPTOR, newRemoteRepoConfig.getKey());
        if (replication != null) {
            replicationConfigService.updateRemoteReplication(replication, newRemoteRepoConfig, updatedConfigDescriptor);
        } else {
            replicationConfigService.removeRemoteReplication(newRemoteRepoConfig, updatedConfigDescriptor);
        }
        int numberOfDiffs = configService.findDiff(configService.getDescriptor(),
                updatedConfigDescriptor).getNumberOfDiffs();
        if (numberOfDiffs > 0) {
            configService.saveEditedDescriptorAndReload(updatedConfigDescriptor, reverseProxyDescriptor, replication);
        }
        repositoryConfigService.upsertRepoConfig(newRemoteRepoConfig);
    }

    private void validateTypeSpecificRemoteRepo(RemoteRepoConfig repoDescriptor,
            MutableCentralConfigDescriptor updatedConfigDescriptor, RemoteRepositoryConfigModel model)
            throws RepoConfigException {
        if (remoteRepoPackageValidator.getRemoteRepoUpdateValidationEnabledPackages()
                .contains(repoDescriptor.getPackageType().getType().toLowerCase())) {
            remoteRepoPackageValidator.validate(repoDescriptor, updatedConfigDescriptor, model);
        }
    }

    private void validateSmartRemoteRepoOnEdge(RemoteRepoConfig remoteRepoConfig) throws RepoConfigException {
        UpstreamRepoKind upstreamRepoKind =
                addonsManager.addonByType(EdgeSmartRepoAddon.class).getUpstreamRepoKind(remoteRepoConfig);
        if (upstreamRepoKind.notSmart()) {
            throw new RepoConfigException(upstreamRepoKind.getValidationMessage(), HttpStatus.SC_BAD_REQUEST);
        }
    }

    public void handleVirtual(VirtualRepositoryConfigModel model, boolean fedAttrOptIn) throws RepoConfigException {
        log.debug("Model resolved to virtual repo descriptor, updating.");

        MutableCentralConfigDescriptor updatedConfigDescriptor = configService.getMutableDescriptor();
        VirtualRepoConfig virtualRepoConfig = model.toRepoConfig(validator, builder, updatedConfigDescriptor);
        if (fedAttrOptIn) {
            builder.populateFederationValues(model, virtualRepoConfig);
        }
        ReverseProxyDescriptor reverseProxyDescriptor = model.getReverseProxyDescriptor(virtualRepoConfig, builder);
        updatedConfigDescriptor.updateReverseProxy(reverseProxyDescriptor);
        if (virtualRepoConfig.getPackageType() == RepoType.P2) {
            updateP2Config(updatedConfigDescriptor, virtualRepoConfig,
                    ((P2TypeSpecificConfigModel) model.getTypeSpecific()).getP2Repos());
        }
        validateNugetPackageTypeConfig(virtualRepoConfig, repositoryConfigService);
        virtualValidator.validate(virtualRepoConfig);
        virtualRepoConfig.getSecurityConfig()
                .setHideUnauthorizedResources(model.getAdvanced().getHideUnauthorizedResources());
        log.debug(MSG_UPDATING_DESCRIPTOR, virtualRepoConfig.getKey());
        int numberOfDiffs = configService.findDiff(configService.getDescriptor(),
                updatedConfigDescriptor).getNumberOfDiffs();
        if (numberOfDiffs > 0) {
            configService.saveEditedDescriptorAndReload(updatedConfigDescriptor, reverseProxyDescriptor);
        }
        repositoryConfigService.upsertRepoConfig(virtualRepoConfig);
    }

    public void handleReleaseBundles(ReleaseBundlesRepositoryConfigModel model) throws RepoConfigException {
        MutableCentralConfigDescriptor updatedConfigDescriptor = configService.getMutableDescriptor();
        log.debug("Model resolved to release bundle repo descriptor, updating.");
        String repoKey = model.getGeneral().getRepoKey();
        ReleaseBundleRepoConfig releaseBundleRepoConfig =
                model.toRepoConfig(validator, builder, updatedConfigDescriptor);
        ReverseProxyDescriptor reverseProxyDescriptor =
                model.getReverseProxyDescriptor(releaseBundleRepoConfig, builder);
        log.debug(MSG_UPDATING_DESCRIPTOR, repoKey);
        updatedConfigDescriptor.updateReverseProxy(reverseProxyDescriptor);
        int numberOfDiffs = configService.findDiff(configService.getDescriptor(),
                updatedConfigDescriptor).getNumberOfDiffs();
        if (numberOfDiffs > 0) {
            configService.saveEditedDescriptorAndReload(updatedConfigDescriptor, reverseProxyDescriptor);
        }
        repositoryConfigService.upsertRepoConfig(releaseBundleRepoConfig);
    }

    private void updateP2Config(MutableCentralConfigDescriptor configDescriptor, VirtualRepoConfig virtualRepoConfig,
            List<P2Repo> requestedRepos) {
        log.debug("Updating P2 config for repo {}", virtualRepoConfig.getKey());
        //Preserve all already-aggregated virtual repos this repo might have for backwards compatibility although
        // we don't allow adding new ones anymore, and append to the ones returned by the P2 config logic
        List<String> reposToAdd = repoService.virtualRepoConfigByKey(virtualRepoConfig.getKey())
                .getRepoTypeConfig().getRepositoryRefs().stream()
                .map(repoService::repoConfigByKey)
                .filter(Objects::nonNull)
                .filter(aggregatedRepo -> !aggregatedRepo.isReal())
                .map(RepoConfig::getKey)
                .collect(Collectors.toList());
        List<RemoteRepoConfig> remoteRepoConfigs = repositoryService.getRemoteRepoConfigs();
        reposToAdd.addAll(creator
                .validateAndCreateP2ConfigRepos(configDescriptor, virtualRepoConfig, requestedRepos,
                        remoteRepoConfigs));
        virtualRepoConfig.getRepoTypeConfig().setRepositoryRefs(reposToAdd);
    }
}
