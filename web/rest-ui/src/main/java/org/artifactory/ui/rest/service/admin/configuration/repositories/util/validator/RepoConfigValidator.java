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

package org.artifactory.ui.rest.service.admin.configuration.repositories.util.validator;

import com.google.common.collect.Lists;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.plugin.pkgtype.PluginPackageTypeRegistry;
import org.artifactory.addon.plugin.pkgtype.impl.PluginPackageTypeRepoConfigValidator;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.config.RepositoryConfigService;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.entitlement.EntitlementType;
import org.artifactory.model.RepoConfig;
import org.artifactory.ph.PackageSpecification;
import org.artifactory.repo.RepoValidationConstants;
import org.artifactory.repo.remote.ContentSynchronisation;
import org.artifactory.rest.common.exception.RepoConfigException;
import org.artifactory.rest.common.validator.RepositoryNameRestValidator;
import org.artifactory.ui.rest.model.admin.configuration.propertysets.PropertySetNameModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.AdvancedRepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.BasicRepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.DownloadRedirectRepoConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.RepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.federation.FederatedRepoAdvancedRepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.federation.FederatedRepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.info.RepoConfigRequest;
import org.artifactory.ui.rest.model.admin.configuration.repository.local.LocalAdvancedRepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.local.LocalRepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.releasebundles.ReleaseBundlesRepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.remote.RemoteAdvancedRepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.remote.RemoteBasicRepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.remote.RemoteCacheRepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.remote.RemoteNetworkRepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.remote.RemoteRepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.DockerTypeSpecificConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.GoTypeSpecificConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.TypeSpecificConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.virtual.VirtualRepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.virtual.VirtualSelectedRepository;
import org.artifactory.util.CollectionUtils;
import org.artifactory.util.RemoteRepoCachePeriodValidator;
import org.artifactory.util.UrlValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static java.util.Optional.ofNullable;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.http.HttpStatus.SC_BAD_REQUEST;
import static org.apache.http.HttpStatus.SC_FORBIDDEN;
import static org.apache.http.HttpStatus.SC_NOT_FOUND;
import static org.artifactory.repo.RepoValidationConstants.DOWNLOAD_REDIRECT_AND_STORE_LOCALLY_ERROR;
import static org.artifactory.repo.RepoValidationConstants.DOWNLOAD_REDIRECT_NO_ENTERPRISE_PLUS_EDGE_ERROR;
import static org.artifactory.repo.config.RepoConfigDefaultValues.DEFAULT_ALLOW_CONTENT_BROWSING;
import static org.artifactory.repo.config.RepoConfigDefaultValues.DEFAULT_ASSUMED_OFFLINE;
import static org.artifactory.repo.config.RepoConfigDefaultValues.DEFAULT_BLACKED_OUT;
import static org.artifactory.repo.config.RepoConfigDefaultValues.DEFAULT_COOKIE_MANAGEMENT;
import static org.artifactory.repo.config.RepoConfigDefaultValues.DEFAULT_DOCKER_REMOTE_RETRIEVAL_CACHE_PERIOD;
import static org.artifactory.repo.config.RepoConfigDefaultValues.DEFAULT_GO_REMOTE_METADATA_MISSED_RETRIEVAL_CACHE_PERIOD;
import static org.artifactory.repo.config.RepoConfigDefaultValues.DEFAULT_HARD_FAIL;
import static org.artifactory.repo.config.RepoConfigDefaultValues.DEFAULT_INCLUDES_PATTERN;
import static org.artifactory.repo.config.RepoConfigDefaultValues.DEFAULT_KEEP_UNUSED_ARTIFACTS;
import static org.artifactory.repo.config.RepoConfigDefaultValues.DEFAULT_LENIENENT_HOST_AUTH;
import static org.artifactory.repo.config.RepoConfigDefaultValues.DEFAULT_METADATA_RETRIEVAL_TIMEOUT_SECS;
import static org.artifactory.repo.config.RepoConfigDefaultValues.DEFAULT_MISSED_RETRIEVAL_PERIOD;
import static org.artifactory.repo.config.RepoConfigDefaultValues.DEFAULT_OFFLINE;
import static org.artifactory.repo.config.RepoConfigDefaultValues.DEFAULT_RETRIEVAL_CACHE_PERIOD;
import static org.artifactory.repo.config.RepoConfigDefaultValues.DEFAULT_SHARE_CONFIG;
import static org.artifactory.repo.config.RepoConfigDefaultValues.DEFAULT_SOCKET_TIMEOUT;
import static org.artifactory.repo.config.RepoConfigDefaultValues.DEFAULT_STORE_ARTIFACTS_LOCALLY;
import static org.artifactory.repo.config.RepoConfigDefaultValues.DEFAULT_SYNC_PROPERTIES;
import static org.artifactory.repo.config.RepoConfigDefaultValues.DEFAULT_VIRTUAL_CAN_RETRIEVE_FROM_REMOTE;

/**
 * Service validates values in the model and sets default values as needed.
 *
 * @author Dan Feldman
 */
@Component
public class RepoConfigValidator {
    private static final Logger log = LoggerFactory.getLogger(RepoConfigValidator.class);

    private final UrlValidator urlValidator = new UrlValidator("http", "https");

    private CentralConfigService centralConfig;
    private AddonsManager addonsManager;
    private RepositoryConfigService repositoryConfigService;
    private RepositoryService repositoryService;
    @Setter(onMethod_ = {@Autowired})
    private PackageSpecification packageSpecification;
    private PluginPackageTypeRegistry pluginPackageTypeRegistry;

    @Autowired
    public void setPluginPackageTypeRegistry(PluginPackageTypeRegistry pluginPackageTypeRegistry) {
        this.pluginPackageTypeRegistry = pluginPackageTypeRegistry;
    }

    /**
     * Rejects a configuration naming an unregistered plugin package type with 400 and a message naming the type
     * (requirement R2). Keeping the type name already stored in {@code existing} is allowed even when that type is
     * not registered, so a repository whose plugin was removed stays editable. Delegates the decision to
     * {@link PluginPackageTypeRepoConfigValidator#check}; with no registry wired, no plugin type is registered.
     *
     * @param existing the stored configuration, {@code null} on create
     */
    public void validatePluginPackageType(RepoConfig repoConfig, RepoConfig existing) throws RepoConfigException {
        Optional<String> error = PluginPackageTypeRepoConfigValidator.check(repoConfig, existing,
                name -> pluginPackageTypeRegistry != null && pluginPackageTypeRegistry.find(name).isPresent());
        if (error.isPresent()) {
            throw new RepoConfigException(error.get(), HttpStatus.SC_BAD_REQUEST);
        }
    }

    @Autowired
    public void setCentralConfig(CentralConfigService centralConfig) {
        this.centralConfig = centralConfig;
    }

    @Autowired
    public void setAddonsManager(AddonsManager addonsManager) {
        this.addonsManager = addonsManager;
    }

    @Autowired
    public void setRepositoryService(RepositoryService repositoryService) {
        this.repositoryService = repositoryService;
    }

    @Autowired
    public void setRepositoryConfigService(RepositoryConfigService repositoryConfigService) {
        this.repositoryConfigService = repositoryConfigService;
    }

    public void validateLocal(LocalRepositoryConfigModel model) throws RepoConfigException {
        verifyAllSectionsExist(model);

        validateSharedBasic(model.getBasic());

        LocalAdvancedRepositoryConfigModel advanced = model.getAdvanced();
        validateSharedAdvanced(advanced);
        validateDownloadRedirectLicenseType(advanced, model.getGeneral().getRepoKey());

        TypeSpecificConfigModel typeSpecific = model.getTypeSpecific();
        typeSpecific.validateSharedTypeSpecific();
        typeSpecific.validateLocalTypeSpecific();
    }

    public void validateFederated(FederatedRepositoryConfigModel model) throws RepoConfigException {
        verifyAllSectionsExist(model);

        validateSharedBasic(model.getBasic());

        FederatedRepoAdvancedRepositoryConfigModel advanced = model.getAdvanced();
        validateSharedAdvanced(advanced);
        validateDownloadRedirectLicenseType(advanced, model.getGeneral().getRepoKey());

        TypeSpecificConfigModel typeSpecific = model.getTypeSpecific();
        typeSpecific.validateSharedTypeSpecific();
    }

    /**
     * Currently, there is no a single unified place for descriptor validation and each module (UI, REST, xml)
     * has it's own validation.
     * Here we are printing a warning in case the repo is configured for download redirect, though not failing it.
     * When a single unified validation will be made, it should go into it.
     */
    private void validateDownloadRedirectLicenseType(LocalAdvancedRepositoryConfigModel advanced, String repoKey) {
        if (addonsManager.isEntitled(EntitlementType.BINARY_PROVIDER_DOWNLOAD_REDIRECT)) {
            return;
        }
        // License is not Enterprise+, check if one of the repos is configured for it
        DownloadRedirectRepoConfigModel downloadRedirectConfig = advanced.getDownloadRedirectConfig();
        if (downloadRedirectConfig != null && downloadRedirectConfig.isEnabled() && log.isWarnEnabled()) {
            log.warn("{}", String.format(DOWNLOAD_REDIRECT_NO_ENTERPRISE_PLUS_EDGE_ERROR, repoKey));
        }
    }

    public void validateRemote(RemoteRepositoryConfigModel model) throws RepoConfigException {
        verifyAllSectionsExist(model);

        //basic
        RemoteBasicRepositoryConfigModel basic = model.getBasic();
        validateSharedBasic(basic);
        if (isBlank(basic.getUrl())) {
            throw new RepoConfigException("URL cannot be empty", SC_BAD_REQUEST);
        }
        try {
            urlValidator.validate(basic.getUrl());
        } catch (UrlValidator.UrlValidationException e) {
            throw new RepoConfigException("Invalid URL: " + e.getMessage(), SC_BAD_REQUEST, e);
        }
        if (basic.getRemoteLayoutMapping() != null
                && centralConfig.getDescriptor().getRepoLayout(basic.getLayout()) == null) {
            throw new RepoConfigException("Invalid remote repository layout", SC_BAD_REQUEST);
        }
        basic.setOffline(ofNullable(basic.isOffline()).orElse(DEFAULT_OFFLINE));

        //advanced
        RemoteAdvancedRepositoryConfigModel advanced = model.getAdvanced();
        validateSharedAdvanced(advanced);
        advanced.setHardFail(ofNullable(advanced.getHardFail()).orElse(DEFAULT_HARD_FAIL));
        advanced.setStoreArtifactsLocally(
                ofNullable(advanced.isStoreArtifactsLocally()).orElse(DEFAULT_STORE_ARTIFACTS_LOCALLY));
        validateDownloadRedirectLicenseType(advanced, model.getGeneral().getRepoKey());
        DownloadRedirectRepoConfigModel downloadRedirectConfig = advanced.getDownloadRedirectConfig();
        if (downloadRedirectConfig != null && downloadRedirectConfig.isEnabled() &&
                Boolean.TRUE.equals(!advanced.isStoreArtifactsLocally())) {
            throw new RepoConfigException(DOWNLOAD_REDIRECT_AND_STORE_LOCALLY_ERROR, SC_BAD_REQUEST);
        }
        advanced.setSynchronizeArtifactProperties(
                ofNullable(advanced.getSynchronizeArtifactProperties()).orElse(DEFAULT_SYNC_PROPERTIES));
        advanced.setShareConfiguration(
                ofNullable(advanced.isShareConfiguration()).orElse(DEFAULT_SHARE_CONFIG));

        //network
        RemoteNetworkRepositoryConfigModel network = advanced.getNetwork();
        if (network != null) {
            if (StringUtils.isNotBlank(network.getProxy()) &&
                    centralConfig.getDescriptor().getProxy(network.getProxy()) == null) {
                throw new RepoConfigException("Invalid proxy configuration", SC_BAD_REQUEST);
            }
            network.setSocketTimeout(ofNullable(network.getSocketTimeout()).orElse(DEFAULT_SOCKET_TIMEOUT));
            network.setSyncProperties(ofNullable(network.isSyncProperties()).orElse(DEFAULT_SYNC_PROPERTIES));
            network.setCookieManagement(
                    ofNullable(network.getCookieManagement()).orElse(DEFAULT_COOKIE_MANAGEMENT));
            network.setLenientHostAuth(
                    ofNullable(network.getLenientHostAuth()).orElse(DEFAULT_LENIENENT_HOST_AUTH));
        }

        //cache
        RemoteCacheRepositoryConfigModel cache = advanced.getCache();
        if (cache != null) {
            cache.setKeepUnusedArtifactsHours(
                    ofNullable(cache.getKeepUnusedArtifactsHours()).orElse(DEFAULT_KEEP_UNUSED_ARTIFACTS));
            setRetrievalCachePeriodSecsOfCacheModel(model, cache);
            cache.setAssumedOfflineLimitSecs(
                    ofNullable(cache.getAssumedOfflineLimitSecs()).orElse(DEFAULT_ASSUMED_OFFLINE));
            validateMetadataTimeIsValid(cache);
            validateMinimumCachePeriod(model, cache);
            cache.setMetadataRetrievalTimeoutSecs(ofNullable(cache.getMetadataRetrievalTimeoutSecs()).orElse(
                    DEFAULT_METADATA_RETRIEVAL_TIMEOUT_SECS));
        }

        TypeSpecificConfigModel typeSpecific = model.getTypeSpecific();
        typeSpecific.validateSharedTypeSpecific();
        typeSpecific.validateRemoteTypeSpecific();
    }

    /**
     * Validates the metadata timeout is valid.
     * The metadata timeout need to be lower the retrieval cache period (unless the retrieval is set to zero)
     * Also the metadata timeout can't be set to zero.
     *
     * @param cache - The remote repo cache model
     */
    void validateMetadataTimeIsValid(RemoteCacheRepositoryConfigModel cache) throws RepoConfigException {
        long metadataRetrievalTimeoutSecs = cache.getMetadataRetrievalTimeoutSecs();
        long retrievalCachePeriodSecs = cache.getRetrievalCachePeriodSecs();
        if (metadataRetrievalTimeoutSecs > retrievalCachePeriodSecs && retrievalCachePeriodSecs > 0) {
            String errorMessage =
                    "The Metadata retrieval timeout not allowed to be bigger then Metadata Retrieval cache field.";
            log.error(errorMessage);
            throw new RepoConfigException(errorMessage, HttpStatus.SC_BAD_REQUEST);
        }
        if (metadataRetrievalTimeoutSecs <= 0) {
            String errorMessage = "The Metadata retrieval timeout can't be zero or negative";
            log.error(errorMessage);
            throw new RepoConfigException(errorMessage, HttpStatus.SC_BAD_REQUEST);
        }
    }

    /**
     * Validates that the metadata retrieval cache period meets the minimum requirement of 30 seconds.
     * Exclusions:
     * - Feature flag is disabled (ConstantValues.remoteRepoEnforceMinimumMetadataCachePeriod)
     * - Generic package type ({@link org.artifactory.descriptor.repo.RepoType#Generic})
     * - Smart repositories (content synchronisation enabled on the model)
     * - URLs matching the whitelist prefix (ConstantValues.remoteRepoMinMetadataCachePeriodWhitelistUrls)
     *
     * @param model - The remote repository config model
     * @param cache - The remote repo cache model
     * @throws RepoConfigException if validation fails
     */
    void validateMinimumCachePeriod(RemoteRepositoryConfigModel model, RemoteCacheRepositoryConfigModel cache)
            throws RepoConfigException {

        try {
            RemoteRepoCachePeriodValidator.assertValidValue(
                    model.getBasic().getUrl(),
                    cache.getRetrievalCachePeriodSecs(),
                    isSmartRemoteRepository(model),
                    model.getTypeSpecific().getRepoType()
            );
        } catch (IllegalArgumentException e) {
            throw new RepoConfigException(e.getMessage(), HttpStatus.SC_BAD_REQUEST, e);
        }
    }

    private static boolean isSmartRemoteRepository(RemoteRepositoryConfigModel model) {
        RemoteBasicRepositoryConfigModel basic = model.getBasic();
        if (basic == null) {
            return false;
        }
        ContentSynchronisation cs = basic.getContentSynchronisation();
        return cs != null && cs.isEnabled();
    }

    void setRetrievalCachePeriodSecsOfCacheModel(RemoteRepositoryConfigModel model,
            RemoteCacheRepositoryConfigModel cache) {
        final long missedRetrievalCachePeriod;
        final long metadataRetrievalCachePeriod;
        switch (model.getTypeSpecific().getRepoType()) {
            case Docker, OCI, HelmOCI -> {
                missedRetrievalCachePeriod = ofNullable(((DockerTypeSpecificConfigModel) model.getTypeSpecific())
                        .getRetrievalCachePeriodSecs()).orElse(DEFAULT_DOCKER_REMOTE_RETRIEVAL_CACHE_PERIOD);
                metadataRetrievalCachePeriod = ofNullable(cache.getMissedRetrievalCachePeriodSecs()).orElse(
                        DEFAULT_MISSED_RETRIEVAL_PERIOD);
            }
            case Go -> {
                metadataRetrievalCachePeriod = ofNullable((GoTypeSpecificConfigModel) model.getTypeSpecific())
                        .map(GoTypeSpecificConfigModel::getMissedRetrievalCachePeriodSecs)
                        .orElse(DEFAULT_GO_REMOTE_METADATA_MISSED_RETRIEVAL_CACHE_PERIOD);
                missedRetrievalCachePeriod =
                        ofNullable(cache.getRetrievalCachePeriodSecs()).orElse(DEFAULT_RETRIEVAL_CACHE_PERIOD);
            }
            default -> {
                missedRetrievalCachePeriod =
                        ofNullable(cache.getRetrievalCachePeriodSecs()).orElse(DEFAULT_RETRIEVAL_CACHE_PERIOD);
                metadataRetrievalCachePeriod = ofNullable(cache.getMissedRetrievalCachePeriodSecs()).orElse(
                        DEFAULT_MISSED_RETRIEVAL_PERIOD);
            }
        }
        cache.setRetrievalCachePeriodSecs(missedRetrievalCachePeriod);
        cache.setMissedRetrievalCachePeriodSecs(metadataRetrievalCachePeriod);
    }

    public void validateVirtual(VirtualRepositoryConfigModel model)
            throws RepoConfigException {
        //Sections and aggregated repos validation
        verifyAllSectionsExist(model);
        validateAggregatedReposExistAndTypesMatch(model);

        //basic
        model.getBasic().setIncludesPattern(ofNullable(model.getBasic().getIncludesPattern())
                .orElse(DEFAULT_INCLUDES_PATTERN));

        //advanced
        model.getAdvanced().setRetrieveRemoteArtifacts(ofNullable(model.getAdvanced()
                .getRetrieveRemoteArtifacts()).orElse(DEFAULT_VIRTUAL_CAN_RETRIEVE_FROM_REMOTE));

        //type specific
        model.getTypeSpecific().validateVirtualTypeSpecific(addonsManager);
    }

    public void validateReleaseBundle(ReleaseBundlesRepositoryConfigModel model) throws RepoConfigException {
        verifyAllSectionsExist(model);
    }

    /**
     * Validates all given repo keys exist - throws an error for the first not found one.
     *
     * @param repoKeys - Keys to check if existing
     */
    public void validateSelectedReposInVirtualExist(List<VirtualSelectedRepository> repoKeys)
            throws RepoConfigException {
        String nonExistentKey = repoKeys.stream()
                .map(VirtualSelectedRepository::getRepoName)
                .filter(repoKey -> !repositoryService.repoExists(repoKey))
                .findAny()
                .orElse(null);
        if (StringUtils.isNotBlank(nonExistentKey)) {
            throw new RepoConfigException("Repository '" + nonExistentKey + "' does not exist", SC_NOT_FOUND);
        }
    }

    private void validateAggregatedReposExistAndTypesMatch(VirtualRepositoryConfigModel model)
            throws RepoConfigException {
        List<VirtualSelectedRepository> virtualSelectedRepositories =
                ofNullable(model.getBasic().getSelectedRepositories()).orElse(Lists.newArrayList());
        if (CollectionUtils.isNullOrEmpty(virtualSelectedRepositories)) {
            model.getBasic().setSelectedRepositories(virtualSelectedRepositories);
        } else {
            validateSelectedReposInVirtualExist(virtualSelectedRepositories);

            RepoConfig invalidTypeDescriptor = virtualSelectedRepositories.stream()
                    .map(VirtualSelectedRepository::getRepoName)
                    .map(repositoryService::repoConfigByKey)
                    .filter(repoConfig -> !filterByType(model.getTypeSpecific().getRepoType(), repoConfig))
                    .findAny().orElse(null);
            if (invalidTypeDescriptor != null) {
                throw new RepoConfigException("Repository '" + model.getGeneral().getRepoKey()
                        + "' aggregates another repository '" + invalidTypeDescriptor.getKey() +
                        "' that has a "
                        + "mismatching package type " + invalidTypeDescriptor.getRepoType().name(),
                        SC_FORBIDDEN);
            }

        }
    }

    private boolean filterByType(RepoType type, RepoConfig repoConfig) {
        if (repoConfig == null) {
            return false;
        }
        if (type == RepoType.P2) {
            return true;
        }

        return packageSpecification.isVirtualCanContainRepoWithType(type, repoConfig.getPackageType());
    }

    private void validateSharedBasic(BasicRepositoryConfigModel basic) throws RepoConfigException {
        basic.setIncludesPattern(ofNullable(basic.getIncludesPattern()).orElse(DEFAULT_INCLUDES_PATTERN));
        if (basic.getLayout() == null || centralConfig.getDescriptor().getRepoLayout(basic.getLayout()) == null) {
            throw new RepoConfigException("Invalid repository layout", SC_BAD_REQUEST);
        }
        if (StringUtils.isNotBlank(basic.getPrimaryKeyPair()) &&
                centralConfig.getDescriptor().getKeyPair(basic.getPrimaryKeyPair()) == null) {
            throw new RepoConfigException("Invalid repository primary keypair", SC_BAD_REQUEST);
        }
        if (StringUtils.isNotBlank(basic.getSecondaryKeyPair()) &&
                centralConfig.getDescriptor().getKeyPair(basic.getSecondaryKeyPair()) == null) {
            throw new RepoConfigException("Invalid repository secondary keypair", SC_BAD_REQUEST);
        }
        if (StringUtils.isNotBlank(basic.getKeypair()) &&
                centralConfig.getDescriptor().getKeyPair(basic.getKeypair()) == null) {
            throw new RepoConfigException("Invalid repository keypair", SC_BAD_REQUEST);
        }
        if (StringUtils.isBlank(basic.getPrimaryKeyPair()) &&
                StringUtils.isNotBlank(basic.getSecondaryKeyPair())) {
            throw new RepoConfigException("Could not define secondary key pair without defining " +
                    "a primary", SC_BAD_REQUEST);
        }
        if (StringUtils.isNotBlank(basic.getPrimaryKeyPair()) &&
                StringUtils.isNotBlank(basic.getSecondaryKeyPair()) &&
                basic.getPrimaryKeyPair().equals(basic.getSecondaryKeyPair())) {
            throw new RepoConfigException("primary key pair could not be equals to secondary",
                    SC_BAD_REQUEST);
        }
    }

    private void validateSharedAdvanced(AdvancedRepositoryConfigModel model) throws RepoConfigException {
        if (model.getPropertySets() == null) {
            return;
        }
        String invalidPropSet = model.getPropertySets().stream()
                .map(PropertySetNameModel::getName)
                .filter(propSetName -> !centralConfig.getMutableDescriptor().isPropertySetExists(propSetName))
                .findAny().orElse(null);
        if (StringUtils.isNotBlank(invalidPropSet)) {
            throw new RepoConfigException("Property set " + invalidPropSet + " doesn't exist", SC_NOT_FOUND);
        }
        model.setAllowContentBrowsing(
                ofNullable(model.getAllowContentBrowsing()).orElse(DEFAULT_ALLOW_CONTENT_BROWSING));
        model.setBlackedOut(ofNullable(model.isBlackedOut()).orElse(DEFAULT_BLACKED_OUT));
    }

    private void verifyAllSectionsExist(RepositoryConfigModel<?, ?, ?> model) throws RepoConfigException {
        if (model.getGeneral() == null) {
            throw new RepoConfigException("Repository Key cannot be empty", SC_BAD_REQUEST);
        } else if (model.getBasic() == null) {
            throw new RepoConfigException("Basic configuration cannot be empty", SC_BAD_REQUEST);
        }
        if (model.getAdvanced() == null) {
            throw new RepoConfigException("Advanced configuration cannot be empty", SC_BAD_REQUEST);
        }
        if (model.getTypeSpecific() == null) {
            throw new RepoConfigException("Package type configuration cannot be empty", SC_BAD_REQUEST);
        }
    }

    /**
     * Rejects assigning env/stage to build-info or release-bundle repositories on create, whose permissions are
     * environment-agnostic in Access. Nothing is assigned yet on create, so any non-empty environments set is rejected.
     */
    public void validateEnvironmentsForRepoType(RepositoryConfigModel<?, ?, ?> model) throws RepoConfigException {
        validateEnvironmentsForRepoType(model, Set.of());
    }

    /**
     * Rejects assigning or changing env/stage on build-info or release-bundle repositories, whose permissions are
     * environment-agnostic in Access. Re-sending the environments that are already assigned is allowed, so a
     * read -> modify (non-env field) -> update round trip does not fail because the existing env value is re-sent.
     */
    public void validateEnvironmentsForRepoType(RepositoryConfigModel<?, ?, ?> model,
            Collection<String> currentEnvironments) throws RepoConfigException {
        if (model.getGeneral() == null || model.getTypeSpecific() == null) {
            return;
        }
        RepoType repoType = model.getTypeSpecific().getRepoType();
        if (repoType == null || !repoType.isEnvironmentAgnostic()) {
            return;
        }
        Set<String> requested = new HashSet<>(model.getGeneral().getEnvironments());
        // An empty/omitted env set is not an assignment or change (the env is re-resolved/defaulted downstream),
        // so only a non-empty set that differs from what is currently assigned is rejected.
        if (requested.isEmpty()) {
            return;
        }
        Set<String> current = currentEnvironments == null ? Set.of() : new HashSet<>(currentEnvironments);
        if (!requested.equals(current)) {
            throw new RepoConfigException(RepoValidationConstants.ENV_NOT_ALLOWED_FOR_REPO_TYPE, SC_BAD_REQUEST);
        }
    }

    public void validateRepoNameForCreate(String repoKey, String repoType) throws RepoConfigException {
        RepositoryNameRestValidator.validateRepoNameForCreate(repoKey, repoType, repositoryConfigService);
    }

    public void validateRepoNameForUpdate(String repoKey) throws RepoConfigException {
        RepositoryNameRestValidator.validateRepoNameForUpdate(repoKey);
    }

    public void validateProjectContext(RepoConfigRequest repoConfigRequest) throws RepoConfigException {
        if (repoConfigRequest.isProjectContext() &&
                (CollectionUtils.isNullOrEmpty(repoConfigRequest.getProjectKeys())
                        || repoConfigRequest.getProjectKeys().size() != 1)) {
            throw new RepoConfigException("Only a single project key is allowed in project context", SC_BAD_REQUEST);
        }
    }
}
