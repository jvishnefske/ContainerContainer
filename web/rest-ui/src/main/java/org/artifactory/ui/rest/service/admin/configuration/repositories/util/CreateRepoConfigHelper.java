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

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.p2.P2Addon;
import org.artifactory.addon.p2.P2Repo;
import org.artifactory.addon.smartrepo.EdgeSmartRepoAddon;
import org.artifactory.api.common.BasicStatusHolder;
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
import org.artifactory.repo.config.RepoConfigDefaultValues;
import org.artifactory.repo.validator.virtual.VirtualRepoPackageValidator;
import org.artifactory.rest.common.exception.RepoConfigException;
import org.artifactory.service.ConfigValidationService;
import org.artifactory.ui.rest.model.admin.configuration.repository.RepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.federation.FederatedRepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.local.LocalRepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.releasebundles.ReleaseBundlesRepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.remote.RemoteRepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.replication.local.LocalReplicationConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.P2TypeSpecificConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.TypeSpecificConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.virtual.VirtualRepositoryConfigModel;
import org.artifactory.ui.rest.service.admin.configuration.repositories.replication.ReplicationConfigService;
import org.artifactory.ui.rest.service.admin.configuration.repositories.util.builder.ReplicationConfigDescriptorBuilder;
import org.artifactory.ui.rest.service.admin.configuration.repositories.util.builder.RepoConfigDescriptorBuilder;
import org.artifactory.ui.rest.service.admin.configuration.repositories.util.validator.RemoteRepoPackageValidator;
import org.artifactory.ui.rest.service.admin.configuration.repositories.util.validator.ReplicationConfigValidator;
import org.artifactory.ui.rest.service.admin.configuration.repositories.util.validator.RepoConfigValidator;
import org.artifactory.util.InvalidNameException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.artifactory.repo.service.NugetRepoConfigValidator.validateNugetPackageTypeConfig;

/**
 * Delegate helper for creating and persisting repo config
 *
 * @author Dan Feldman
 */
@Component
public class CreateRepoConfigHelper {
    private static final Logger log = LoggerFactory.getLogger(CreateRepoConfigHelper.class);

    private static final String MSG_CREATING_DESCRIPTOR = "Creating descriptor from received model";
    private static final String CARGO_BASE_URL_ERR_MSG =
            "Custom Base URL should be defined prior to creating a Cargo repository";

    @Autowired
    void setAddonsManager(AddonsManager addonsManager) {
        this.addonsManager = addonsManager;
    }

    @Autowired
    void setRepoBuilder(RepoConfigDescriptorBuilder repoBuilder) {
        this.repoBuilder = repoBuilder;
    }

    @Autowired
    void setReplicationBuilder(ReplicationConfigDescriptorBuilder replicationBuilder) {
        this.replicationBuilder = replicationBuilder;
    }

    @Autowired
    void setReplicationConfigService(ReplicationConfigService replicationConfigService) {
        this.replicationConfigService = replicationConfigService;
    }

    @Autowired
    void setReplicationValidator(ReplicationConfigValidator replicationValidator) {
        this.replicationValidator = replicationValidator;
    }

    @Autowired
    void setRepoValidator(RepoConfigValidator repoValidator) {
        this.repoValidator = repoValidator;
    }

    @Autowired
    void setRemoteRepoPackageValidator(RemoteRepoPackageValidator remoteRepoPackageValidator) {
        this.remoteRepoPackageValidator = remoteRepoPackageValidator;
    }

    @Autowired
    void setVirtualRepoPackageValidator(VirtualRepoPackageValidator virtualRepoPackageValidator) {
        this.virtualRepoPackageValidator = virtualRepoPackageValidator;
    }

    @Autowired
    void setConfigValidationService(ConfigValidationService configValidationService) {
        this.configValidationService = configValidationService;
    }

    @Autowired
    public void setProjectsService(ProjectsService projectsService) {
        this.projectsService = projectsService;
    }

    @Autowired
    public void setRepositoryConfigService(RepositoryConfigService repositoryConfigService) {
        this.repositoryConfigService = repositoryConfigService;
    }

    private AddonsManager addonsManager;
    @Autowired
    private RepoConfigDescriptorBuilder repoBuilder;
    @Autowired
    private ReplicationConfigDescriptorBuilder replicationBuilder;
    @Autowired
    private ReplicationConfigService replicationConfigService;
    @Autowired
    private ReplicationConfigValidator replicationValidator;
    @Autowired
    private RepoConfigValidator repoValidator;
    @Autowired
    private RemoteRepoPackageValidator remoteRepoPackageValidator;
    @Autowired
    private VirtualRepoPackageValidator virtualRepoPackageValidator;
    @Autowired
    private ConfigValidationService configValidationService;
    private RepositoryConfigService repositoryConfigService;
    private ProjectsService projectsService;


    public RepoConfig handleLocal(LocalRepositoryConfigModel model,
            MutableCentralConfigDescriptor configDescriptor, boolean fedAttrOptIn) throws RepoConfigException {
        log.debug("Model resolved to local repo descriptor, adding.");
        log.debug(MSG_CREATING_DESCRIPTOR);
        LocalConfig localConfig = model.toRepoConfig(repoValidator, repoBuilder, configDescriptor);
        if (fedAttrOptIn) {
            repoBuilder.populateFederationValues(model, localConfig);
        }
        this.configValidationService.validate(localConfig);
        checkIfKeyLowerCase(localConfig);
        validateTypeSpecificLocalRepo(localConfig, configDescriptor);
        ReverseProxyDescriptor reverseProxyDescriptor = getReverseProxyDescriptor(localConfig, model);
        this.configValidationService.validate(reverseProxyDescriptor);
        configDescriptor.updateReverseProxy(reverseProxyDescriptor);
        List<LocalReplicationConfigModel> replicationList = model.getReplications();
        if (replicationList != null && !replicationList.isEmpty()) {
            log.debug("Creating push replication descriptor(s) from received model");
            Set<LocalReplicationDescriptor> replications =
                    model.getReplicationDescriptors(replicationValidator, replicationBuilder);
            this.configValidationService.validateCollection(replications);
            replicationConfigService
                    .addLocalReplications(replications, localConfig, configDescriptor);
        }
        String pkgType = Optional.of(model).map(LocalRepositoryConfigModel::getTypeSpecific).map(
                TypeSpecificConfigModel::getRepoType).map(RepoType::getType).orElse("unknown");
        String messageSuccess = "Successfully created local repository config " + localConfig +
                " with package type " + pkgType;
        log.info(messageSuccess);
        return localConfig;
    }

    public RepoConfig handleFederated(FederatedRepositoryConfigModel model,
            MutableCentralConfigDescriptor configDescriptor) throws RepoConfigException {
        log.debug("Model resolved to federated repo descriptor, adding.");
        log.debug(MSG_CREATING_DESCRIPTOR);
        FederatedRepoConfig federatedRepoConfig = model.toRepoConfig(repoValidator, repoBuilder, configDescriptor);
        this.configValidationService.validate(federatedRepoConfig);
        checkIfKeyLowerCase(federatedRepoConfig);
        repositoryConfigService.upsertRepoConfig(federatedRepoConfig);
        federatedRepoConfig = repositoryConfigService.getFederatedRepoConfigByKey(federatedRepoConfig.getKey());
        configDescriptor.conditionallyAddToBackups(federatedRepoConfig.getKey());
        ReverseProxyDescriptor reverseProxyDescriptor = getReverseProxyDescriptor(federatedRepoConfig, model);
        this.configValidationService.validate(reverseProxyDescriptor);
        configDescriptor.updateReverseProxy(reverseProxyDescriptor);
        return federatedRepoConfig;
    }

    public RepoConfig handleRemote(RemoteRepositoryConfigModel model,
            MutableCentralConfigDescriptor configDescriptor, boolean fedAttrOptIn) throws RepoConfigException {
        log.debug("Model resolved to remote repo descriptor, adding.");
        log.debug(MSG_CREATING_DESCRIPTOR);
        RemoteRepoConfig remoteRepoConfig = model.toRepoConfig(repoValidator, repoBuilder, configDescriptor);
        if (fedAttrOptIn) {
            repoBuilder.populateFederationValues(model, remoteRepoConfig);
        }
        this.configValidationService.validate(remoteRepoConfig);
        validateSmartRemoteRepoOnEdge(remoteRepoConfig);
        validateTypeSpecificRemoteRepo(remoteRepoConfig, configDescriptor, model);
        ReverseProxyDescriptor reverseProxyDescriptor = getReverseProxyDescriptor(remoteRepoConfig, model);
        this.configValidationService.validate(reverseProxyDescriptor);
        configDescriptor.updateReverseProxy(reverseProxyDescriptor);
        checkIfKeyLowerCase(remoteRepoConfig);
        repositoryConfigService.upsertRepoConfig(remoteRepoConfig);
        if (model.getReplications() != null) {
            log.debug("Creating pull replication descriptor from received model");
            RemoteReplicationDescriptor replicationDescriptor =
                    model.getReplicationDescriptor(replicationValidator, replicationBuilder);
            this.configValidationService.validate(replicationDescriptor);
            replicationConfigService.addRemoteReplication(replicationDescriptor, remoteRepoConfig, configDescriptor);
        }
        String pkgType = Optional.of(model).map(RemoteRepositoryConfigModel::getTypeSpecific).map(
                TypeSpecificConfigModel::getRepoType).map(RepoType::getType).orElse("unknown");
        String messageSuccess = "Successfully created remote repository config " + remoteRepoConfig +
                " with package type " + pkgType;
        log.info(messageSuccess);
        return remoteRepoConfig;
    }

    public RepoConfig handleVirtual(VirtualRepositoryConfigModel model,
            MutableCentralConfigDescriptor configDescriptor,
            RepositoryService repositoryService, boolean fedAttrOptIn) throws RepoConfigException {
        log.debug("Model resolved to virtual repo descriptor, adding.");
        log.debug(MSG_CREATING_DESCRIPTOR);

        VirtualRepoConfig virtualRepoConfig = model.toRepoConfig(repoValidator, repoBuilder, configDescriptor);
        if (fedAttrOptIn) {
            repoBuilder.populateFederationValues(model, virtualRepoConfig);
        }
        this.configValidationService.validate(virtualRepoConfig);
        ReverseProxyDescriptor reverseProxyDescriptor = getReverseProxyDescriptor(virtualRepoConfig, model);
        this.configValidationService.validate(reverseProxyDescriptor);
        configDescriptor.updateReverseProxy(reverseProxyDescriptor);
        if (virtualRepoConfig.getPackageType() == RepoType.P2) {
            List<RemoteRepoConfig> remoteRepoConfigs = repositoryService.getRemoteRepoConfigs();
            log.debug("Creating P2 config for repo {}", virtualRepoConfig.getKey());
            virtualRepoConfig.getRepoTypeConfig().setRepositoryRefs(validateAndCreateP2ConfigRepos(configDescriptor,
                    virtualRepoConfig,
                    ((P2TypeSpecificConfigModel) model.getTypeSpecific()).getP2Repos(), remoteRepoConfigs));
        }
        validateNugetPackageTypeConfig(virtualRepoConfig, repositoryConfigService);
        virtualRepoPackageValidator.validate(virtualRepoConfig);
        checkIfKeyLowerCase(virtualRepoConfig);
        repositoryConfigService.upsertRepoConfig(virtualRepoConfig);
        String pkgType = Optional.of(model).map(VirtualRepositoryConfigModel::getTypeSpecific).map(
                TypeSpecificConfigModel::getRepoType).map(RepoType::getType).orElse("unknown");
        String messageSuccess = "Successfully created virtual repository config" + virtualRepoConfig +
                " with package type " + pkgType;
        log.info(messageSuccess);
        return virtualRepoConfig;
    }

    public RepoConfig handleReleaseBundles(ReleaseBundlesRepositoryConfigModel model,
            MutableCentralConfigDescriptor configDescriptor) throws RepoConfigException {
        log.debug("Model resolved to release bundle repo descriptor, adding.");
        log.debug(MSG_CREATING_DESCRIPTOR);
        ReleaseBundleRepoConfig releaseBundleRepoConfig =
                model.toRepoConfig(repoValidator, repoBuilder, configDescriptor);
        this.configValidationService.validate(releaseBundleRepoConfig);
        checkIfKeyLowerCase(releaseBundleRepoConfig);
        repositoryConfigService.upsertRepoConfig(releaseBundleRepoConfig);
        configDescriptor.conditionallyAddToBackups(releaseBundleRepoConfig.getKey());
        ReverseProxyDescriptor reverseProxyDescriptor = getReverseProxyDescriptor(releaseBundleRepoConfig, model);
        this.configValidationService.validate(reverseProxyDescriptor);
        configDescriptor.updateReverseProxy(reverseProxyDescriptor);
        return releaseBundleRepoConfig;
    }

    private void validateTypeSpecificLocalRepo(LocalConfig repoDescriptor,
            MutableCentralConfigDescriptor configDescriptor) throws RepoConfigException {
        validateCargoRepo(repoDescriptor, configDescriptor);
    }

    private void validateSmartRemoteRepoOnEdge(RemoteRepoConfig remoteRepoConfig) throws RepoConfigException {
        UpstreamRepoKind upstreamRepoKind =
                addonsManager.addonByType(EdgeSmartRepoAddon.class).getUpstreamRepoKind(remoteRepoConfig);
        if (upstreamRepoKind.notSmart()) {
            throw new RepoConfigException(upstreamRepoKind.getValidationMessage(), HttpStatus.SC_BAD_REQUEST);
        }
    }

    private void validateTypeSpecificRemoteRepo(RemoteRepoConfig repoDescriptor,
            MutableCentralConfigDescriptor configDescriptor, RemoteRepositoryConfigModel model)
            throws RepoConfigException {
        if (remoteRepoPackageValidator.getRemoteRepoCreateValidationEnabledPackages()
                .contains(repoDescriptor.getPackageType().getType().toLowerCase())) {
            remoteRepoPackageValidator.validate(repoDescriptor, configDescriptor, model);
        }
    }

    private void checkIfKeyLowerCase(RepoConfig repoConfig) {
        //docker repository key must be lower case in order it will work with reverse proxy
        if (repoConfig.getPackageType() == RepoType.Docker) {
            String repoKey = repoConfig.getKey();
            if (!repoKey.equals(repoKey.toLowerCase())) {
                throw new InvalidNameException("Docker Repository " + repoKey + " must be in lower case");
            }
        }
    }

    private void validateCargoRepo(RepoConfig repoConfig,
            MutableCentralConfigDescriptor configDescriptor) throws RepoConfigException {
        if (RepoType.Cargo == repoConfig.getPackageType() &&
                StringUtils.isBlank(configDescriptor.getUrlBase())) {
            throw new RepoConfigException(CARGO_BASE_URL_ERR_MSG, HttpStatus.SC_BAD_REQUEST);
        }
    }

    /**
     * Validate and create P2 config repos
     *
     * @return Set of P2 config repo keys
     */
    List<String> validateAndCreateP2ConfigRepos(MutableCentralConfigDescriptor configDescriptor,
            VirtualRepoConfig virtualRepoConfig, List<P2Repo> requestedRepos,
            List<RemoteRepoConfig> remoteRepoConfigs) {
        BasicStatusHolder status = new BasicStatusHolder();
        Map<String, List<String>> subCompositeUrls = Maps.newHashMap();
        //Addon verifies each url and crunches out a list of local and remote repositories that should be aggregated
        //In this virtual based on the URLs passed to it
        List<P2Repo> p2Repos = addonsManager.addonByType(P2Addon.class)
                .verifyRemoteRepositories(remoteRepoConfigs, configDescriptor, virtualRepoConfig, null,
                        requestedRepos, subCompositeUrls, status);
        List<RepoConfig> repoConfigsToAdd = Lists.newArrayList();
        for (P2Repo repo : p2Repos) {
            RepoConfig repoConfig = repo.getRepoConfig();
            if (!repositoryConfigService.existsByRepoKey(repo.getRepoKey())) {
                if (repo.getRepoConfig() instanceof RemoteRepoConfig newRemoteRepoConfig) {
                    //Set default proxy for automatically created new remote repos
                    if (configDescriptor.getDefaultProxy() != null) {
                        newRemoteRepoConfig.getRepoTypeConfig()
                                .setProxyRef(configDescriptor.getDefaultProxy().getKey());
                    }
                    //P2 remote repos list remote items by default, as we're not creating through the normal
                    //model chain we have to set it here for new repos
                    newRemoteRepoConfig.getRepoTypeConfig().setListRemoteFolderItems(
                            RepoConfigDefaultValues.DEFAULT_LIST_REMOTE_ITEMS_UNSUPPORTED_TYPE);
                    checkIfKeyLowerCase(newRemoteRepoConfig);
                    repositoryConfigService.upsertRepoConfig(newRemoteRepoConfig);
                }
                log.info("Remote repository {} is being created based on {}'s P2 config",
                        repoConfig.getKey(), virtualRepoConfig.getKey());
            }
            repoConfigsToAdd.add(repoConfig);
        }
        return repoConfigsToAdd.stream().distinct().map(RepoConfig::getKey)
                .collect(Collectors.toList());
    }

    private ReverseProxyDescriptor getReverseProxyDescriptor(RepoConfig repoConfig,
            RepositoryConfigModel<?, ?, ?> model) {
        return model.getReverseProxyDescriptor(repoConfig, repoBuilder);
    }
}