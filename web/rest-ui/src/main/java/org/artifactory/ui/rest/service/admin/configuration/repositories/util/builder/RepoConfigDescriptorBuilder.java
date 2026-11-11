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

package org.artifactory.ui.rest.service.admin.configuration.repositories.util.builder;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.addon.p2.P2Repo;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.config.RepositoryConfigService;
import org.artifactory.common.crypto.CryptoHelper;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.descriptor.config.CentralConfigDescriptor;
import org.artifactory.descriptor.config.MutableCentralConfigDescriptor;
import org.artifactory.descriptor.property.PropertySet;
import org.artifactory.descriptor.repo.KeyPairDescriptor;
import org.artifactory.descriptor.repo.ProxyDescriptor;
import org.artifactory.descriptor.repo.RepoLayout;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.descriptor.repo.ReverseProxyDescriptor;
import org.artifactory.descriptor.repo.ReverseProxyRepoConfig;
import org.artifactory.model.FederatedRepoConfig;
import org.artifactory.model.LocalConfig;
import org.artifactory.model.LocalRepoConfig;
import org.artifactory.model.ProxyRepoConfig;
import org.artifactory.model.RealRepoConfig;
import org.artifactory.model.ReleaseBundleRepoConfig;
import org.artifactory.model.RemoteRepoConfig;
import org.artifactory.model.RepoConfig;
import org.artifactory.model.VirtualRepoConfig;
import org.artifactory.model.XrayRepoConfig;
import org.artifactory.repo.CdnRedirectRepoConfig;
import org.artifactory.repo.DownloadRedirectRepoConfig;
import org.artifactory.repo.federated.FederatedMember;
import org.artifactory.repo.vcs.VcsGitProvider;
import org.artifactory.repo.vcs.VcsType;
import org.artifactory.repo.virtual.VirtualCacheConfig;
import org.artifactory.ui.rest.model.admin.configuration.propertysets.PropertySetNameModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.AdvancedRepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.BasicRepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.CdnRedirectRepoConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.DownloadRedirectRepoConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.ProxyAwareConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.RepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.federation.FederatedRepoAdvancedRepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.federation.FederatedRepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.federation.FederationConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.federation.MirrorMemberConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.local.LocalAdvancedRepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.local.LocalBasicRepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.local.LocalRepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.releasebundles.ReleaseBundlesRepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.remote.RemoteAdvancedRepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.remote.RemoteBasicRepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.remote.RemoteCacheRepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.remote.RemoteNetworkRepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.remote.RemoteRepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.AIEditorExtensionsTypeSpecificConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.AlpineTypeSpecificConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.AnsibleTypeSpecificConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.BowerTypeSpecificConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.CargoTypeSpecificConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.ChefTypeSpecificConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.CocoaPodsTypeSpecificConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.ComposerTypeSpecificConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.ConanTypeSpecificConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.CondaTypeSpecificConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.CranTypeSpecificConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.DebTypeSpecificConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.DockerTypeSpecificConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.GemsTypeSpecificConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.GenericTypeSpecificConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.GitLfsTypeSpecificConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.GoTypeSpecificConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.HelmTypeSpecificConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.HexTypeSpecificConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.HuggingFaceMLTypeSpecificConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.MavenTypeSpecificConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.NimModelTypeSpecificConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.NixTypeSpecificConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.NpmTypeSpecificConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.NugetTypeSpecificConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.OpkgTypeSpecificConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.P2TypeSpecificConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.PypiTypeSpecificConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.SwiftTypeSpecificConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.TerraformBackendTypeSpecificConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.TerraformTypeSpecificConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.TypeSpecificConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.VcsTypeSpecificConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.YumTypeSpecificConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.virtual.VirtualAdvancedRepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.virtual.VirtualBasicRepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.virtual.VirtualRepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.virtual.VirtualSelectedRepository;
import org.artifactory.ui.rest.model.admin.configuration.repository.xray.XrayRepoConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.reverseProxy.ReverseProxyRepoModel;
import org.artifactory.util.AolUtils;
import org.jfrog.client.util.PathUtils;
import org.jfrog.common.JsonUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

import static org.artifactory.repo.RepoDetailsType.FEDERATED;
import static org.artifactory.repo.RepoDetailsType.LOCAL;
import static org.artifactory.repo.config.RepoConfigDefaultValues.DEFAULT_HUGGING_FACE_ML_REGISTRY_URL;
import static org.artifactory.repo.config.RepoConfigDefaultValues.DEFAULT_NIMMODEL_REGISTRY_URL;
import static org.artifactory.util.HelmPackageTypeConfigMapper.FORCE_METADATA_NAME_VERSION;
import static org.artifactory.util.HelmPackageTypeConfigMapper.FORCE_NON_DUPLICATE_CHART;
import static org.artifactory.util.HuggingFaceMLPackageTypeConfigMapper.IS_ML_REPO_LAYOUT;
import static org.artifactory.util.PackageTypeConfigFields.AUTH_URL;
import static org.artifactory.util.PackageTypeConfigFields.BLOCK_PUSHING_SCHEMA_1;
import static org.artifactory.util.PackageTypeConfigFields.BOWER_REGISTRY_URL;
import static org.artifactory.util.PackageTypeConfigFields.CACHING_LOCAL_FOREIGN_LAYERS_ENABLED;
import static org.artifactory.util.PackageTypeConfigFields.CALCULATE_YUM_METADATA;
import static org.artifactory.util.PackageTypeConfigFields.CARGO_ANONYMOUS_ACCESS;
import static org.artifactory.util.PackageTypeConfigFields.CARGO_INTERNAL_INDEX;
import static org.artifactory.util.PackageTypeConfigFields.CHARTS_BASE_URL;
import static org.artifactory.util.PackageTypeConfigFields.COCOA_PODS_CDN_URL;
import static org.artifactory.util.PackageTypeConfigFields.COCOA_PODS_FORCE_MODIFY_INDEX;
import static org.artifactory.util.PackageTypeConfigFields.COCOA_PODS_SPECS_URL;
import static org.artifactory.util.PackageTypeConfigFields.COMPOSER_REGISTRY_URL;
import static org.artifactory.util.PackageTypeConfigFields.DDEB_SUPPORTED;
import static org.artifactory.util.PackageTypeConfigFields.DEBIAN_DEFAULT_ARCHITECTURES;
import static org.artifactory.util.PackageTypeConfigFields.DEBIAN_OPTIONAL_INDEX_COMPRESSION_FORMATS;
import static org.artifactory.util.PackageTypeConfigFields.DEBIAN_TRIVIAL_LAYOUT;
import static org.artifactory.util.PackageTypeConfigFields.DOCKER_API_VERSION;
import static org.artifactory.util.PackageTypeConfigFields.DOCKER_PROJECT_ID;
import static org.artifactory.util.PackageTypeConfigFields.DOCKER_TAG_RETENTION;
import static org.artifactory.util.PackageTypeConfigFields.DOWNLOAD_CONTEXT_PATH;
import static org.artifactory.util.PackageTypeConfigFields.ENABLE_COMPOSER_V1_INDEXING;
import static org.artifactory.util.PackageTypeConfigFields.ENABLE_FILE_LISTS_INDEXING;
import static org.artifactory.util.PackageTypeConfigFields.ENABLE_NUGET_NORMALIZED_VERSION;
import static org.artifactory.util.PackageTypeConfigFields.ENCRYPT_STATES;
import static org.artifactory.util.PackageTypeConfigFields.EXTERNAL_DEPENDENCIES_ENABLED;
import static org.artifactory.util.PackageTypeConfigFields.EXTERNAL_DEPENDENCIES_PATTERNS;
import static org.artifactory.util.PackageTypeConfigFields.EXTERNAL_DEPENDENCIES_REMOTE_REPO_REF;
import static org.artifactory.util.PackageTypeConfigFields.FEED_CONTEXT_PATH;
import static org.artifactory.util.PackageTypeConfigFields.FETCH_JARS_EAGERLY;
import static org.artifactory.util.PackageTypeConfigFields.FETCH_SOURCES_EAGERLY;
import static org.artifactory.util.PackageTypeConfigFields.FORCE_CONAN_AUTHENTICATION;
import static org.artifactory.util.PackageTypeConfigFields.FORCE_MAVEN_AUTHENTICATION;
import static org.artifactory.util.PackageTypeConfigFields.FORCE_NUGET_AUTHENTICATION;
import static org.artifactory.util.PackageTypeConfigFields.FORCE_P2_AUTHENTICATION;
import static org.artifactory.util.PackageTypeConfigFields.GITLAB_RESOLVE_SUBGROUPS;
import static org.artifactory.util.PackageTypeConfigFields.GIT_REGISTRY_URL;
import static org.artifactory.util.PackageTypeConfigFields.HEX_REGISTRY_PUBLIC_KEY;
import static org.artifactory.util.PackageTypeConfigFields.MAX_UNIQUE_TAGS;
import static org.artifactory.util.PackageTypeConfigFields.OPTIONAL_INDEX_COMPRESSION_FORMATS;
import static org.artifactory.util.PackageTypeConfigFields.P2_ORIGINAL_URL;
import static org.artifactory.util.PackageTypeConfigFields.P2_URLS;
import static org.artifactory.util.PackageTypeConfigFields.POM_REPOSITORY_REFERENCES_CLEANUP_POLICY;
import static org.artifactory.util.PackageTypeConfigFields.PYPI_REGISTRY_URL;
import static org.artifactory.util.PackageTypeConfigFields.PYPI_REPOSITORY_SUFFIX;
import static org.artifactory.util.PackageTypeConfigFields.REJECT_INVALID_JARS;
import static org.artifactory.util.PackageTypeConfigFields.RESOLVE_DOCKER_TAGS_BY_TIMESTAMP;
import static org.artifactory.util.PackageTypeConfigFields.SPECS_REPO_DOWNLOAD_PROVIDER;
import static org.artifactory.util.PackageTypeConfigFields.SUPPRESS_POM_CONSISTENCY_CHECKS;
import static org.artifactory.util.PackageTypeConfigFields.SYMBOL_SERVER_URL;
import static org.artifactory.util.PackageTypeConfigFields.TERRAFORM_PROVIDERS_URL;
import static org.artifactory.util.PackageTypeConfigFields.TERRAFORM_REGISTRY_URL;
import static org.artifactory.util.PackageTypeConfigFields.TERRAFORM_TYPE;
import static org.artifactory.util.PackageTypeConfigFields.USE_NAMESPACES;
import static org.artifactory.util.PackageTypeConfigFields.V3_FEED_URL;
import static org.artifactory.util.PackageTypeConfigFields.VCS_GIT_DOWNLOAD_URL;
import static org.artifactory.util.PackageTypeConfigFields.VCS_GIT_PROVIDER;
import static org.artifactory.util.PackageTypeConfigFields.VCS_TYPE;
import static org.artifactory.util.PackageTypeConfigFields.YUM_GROUP_FILENAMES;
import static org.artifactory.util.PackageTypeConfigFields.YUM_ROOT_DEPTH;

/**
 * Utility class for converting model to repo config
 *
 * @author Dan Feldman
 */
@Slf4j
@Component
public class RepoConfigDescriptorBuilder {
    private CentralConfigService centralConfig;
    private RepositoryConfigService repositoryConfigService;

    static String getUrlWithoutSubpath(String url) {
        int slashslash = url.indexOf("//") + 2;
        int nextSlash = url.indexOf('/', slashslash);
        return nextSlash < 0 ? url : PathUtils.trimSlashes(url.substring(0, nextSlash));
    }

    @Autowired
    public void setCentralConfig(CentralConfigService centralConfig) {
        this.centralConfig = centralConfig;
    }

    @Autowired
    public void setRepositoryConfigService(RepositoryConfigService repositoryConfigService) {
        this.repositoryConfigService = repositoryConfigService;
    }

    public LocalConfig buildLocalConfig(LocalRepositoryConfigModel model) {
        LocalConfig localConfig =
                LocalRepoConfig.create(model.getGeneral().getRepoKey(), model.getTypeSpecific().getRepoType());
        populateSharedBasicConfigValues(model.getBasic(), localConfig);
        buildAndSetXrayConfig(localConfig, model.getBasic());
        buildAndSetCdnRedirectConfig(localConfig, model);
        buildAndSetDownloadRedirectConfig(localConfig, model.getAdvanced());
        populateSharedAdvancedDescriptorValues(model.getAdvanced(), localConfig);
        populateSharedTypeSpecificDescriptorValues(model.getTypeSpecific(), localConfig);
        populateLocalTypeSpecificDescriptorValues(model.getTypeSpecific(), localConfig);
        setDefaultGpgKeyIfNeeded(localConfig);
        return localConfig;
    }

    public void populateFederationValues(RepositoryConfigModel<?, ?, ?> model, RepoConfig repoConfig) {
        FederationConfigModel federation = model.getFederation();
        if (federation == null || federation.getMirrors() == null || federation.getMirrors().isEmpty()) {
            return;
        }
        repoConfig.getBaseConfig().getFederationConfig()
                .setMembers(federation.getMirrors().stream().map(this::toFederatedMember).toList());
    }

    public FederatedRepoConfig buildFederatedRepoConfig(FederatedRepositoryConfigModel model) {
        FederatedRepoConfig federatedRepoConfig =
                FederatedRepoConfig.create(model.getGeneral().getRepoKey(), model.getTypeSpecific().getRepoType());
        populateSharedBasicConfigValues(model.getBasic(), federatedRepoConfig);
        populateFederationValues(model, federatedRepoConfig);
        buildAndSetXrayConfig(federatedRepoConfig, model.getBasic());
        buildAndSetCdnRedirectConfig(federatedRepoConfig, model);
        buildAndSetDownloadRedirectConfig(federatedRepoConfig, model.getAdvanced());
        populateSharedAdvancedDescriptorValues(model.getAdvanced(), federatedRepoConfig);
        populateSharedTypeSpecificDescriptorValues(model.getTypeSpecific(), federatedRepoConfig);
        populateFederatedRepoAdvancedDescriptorValues(model.getAdvanced(), federatedRepoConfig);
        populateLocalTypeSpecificDescriptorValues(model.getTypeSpecific(), federatedRepoConfig);
        setDefaultGpgKeyIfNeeded(federatedRepoConfig);
        return federatedRepoConfig;
    }

    public RemoteRepoConfig buildRemoteRepoConfig(RemoteRepositoryConfigModel model) {
        RemoteRepoConfig remoteRepoConfig =
                RemoteRepoConfig.create(model.getGeneral().getRepoKey(), model.getTypeSpecific().getRepoType());
        populateSharedBasicConfigValues(model.getBasic(), remoteRepoConfig);
        populateRemoteBasicRepoConfigValues(model.getBasic(), remoteRepoConfig);
        buildAndSetXrayConfig(remoteRepoConfig, model.getBasic());
        buildAndSetCdnRedirectConfig(remoteRepoConfig, model);
        buildAndSetDownloadRedirectConfig(remoteRepoConfig, model.getAdvanced());
        populateSharedAdvancedDescriptorValues(model.getAdvanced(), remoteRepoConfig);
        populateRemoteAdvancedDescriptorValues(model.getAdvanced(), remoteRepoConfig,
                model.getTypeSpecific());
        populateSharedTypeSpecificDescriptorValues(model.getTypeSpecific(), remoteRepoConfig);
        populateRemoteTypeSpecificDescriptorValues(model.getTypeSpecific(), remoteRepoConfig);
        return remoteRepoConfig;
    }

    public VirtualRepoConfig buildVirtualRepoConfig(VirtualRepositoryConfigModel model) {
        VirtualRepoConfig virtualRepoConfig =
                VirtualRepoConfig.create(model.getGeneral().getRepoKey(), model.getTypeSpecific().getRepoType());
        populateSharedBasicConfigValues(model.getBasic(), virtualRepoConfig);
        // if not validate resolve virtual values from config descriptor (instead of config service)
        populateVirtualBasicConfigValues(model.getBasic(), virtualRepoConfig);
        populateVirtualAdvancedConfigValues(model.getAdvanced(), virtualRepoConfig);
        populateVirtualTypeSpecific(model.getTypeSpecific(), virtualRepoConfig);
        setDefaultGpgKeyIfNeeded(virtualRepoConfig);
        return virtualRepoConfig;
    }

    public ReleaseBundleRepoConfig buildReleaseBundleRepoConfig(ReleaseBundlesRepositoryConfigModel model) {
        ReleaseBundleRepoConfig releaseBundleRepoConfig =
                ReleaseBundleRepoConfig.create(model.getGeneral().getRepoKey());
        populateSharedBasicConfigValues(model.getBasic(), releaseBundleRepoConfig);
        buildAndSetXrayConfig(releaseBundleRepoConfig, model.getBasic());
        populateSharedAdvancedDescriptorValues(model.getAdvanced(), releaseBundleRepoConfig);
        populateSharedTypeSpecificDescriptorValues(model.getTypeSpecific(), releaseBundleRepoConfig);
        populateLocalTypeSpecificDescriptorValues(model.getTypeSpecific(), releaseBundleRepoConfig);
        return releaseBundleRepoConfig;
    }

    public ReverseProxyDescriptor buildReverseProxyDescriptor(AdvancedRepositoryConfigModel model,
            RepoConfig repoConfig) {
        ReverseProxyRepoModel reverseProxy = model.getReverseProxy();
        CentralConfigDescriptor mutableDescriptor = centralConfig.getMutableDescriptor();
        ReverseProxyDescriptor reverseProxyDescriptor = mutableDescriptor.getCurrentReverseProxy();
        if (reverseProxy != null && reverseProxyDescriptor != null) {
            if (reverseProxy.getServerPort() == null) {
                reverseProxyDescriptor.deleteReverseProxyConfig(repoConfig.getKey());
            } else {
                ReverseProxyRepoConfig reverseProxyRepoConfig = new ReverseProxyRepoConfig();
                reverseProxyRepoConfig.setServerName(reverseProxy.getServerName());
                reverseProxyRepoConfig.setPort(reverseProxy.getServerPort());
                reverseProxyRepoConfig.setRepoRef(repoConfig.getKey());
                reverseProxyDescriptor.addReverseProxyRepoConfig(reverseProxyRepoConfig);
            }
        }
        return reverseProxyDescriptor;
    }

    void populateVirtualBasicConfigValues(VirtualBasicRepositoryConfigModel model,
            VirtualRepoConfig virtualRepoConfig) {
        virtualRepoConfig.getRepoTypeConfig().setRepositoryRefs(model.getSelectedRepositories().stream()
                .map(VirtualSelectedRepository::getRepoName)
                .filter(repoName -> repositoryConfigService.existsByRepoKey(repoName))
                .filter(Objects::nonNull)
                .toList());
        if (StringUtils.isNotBlank(model.getDefaultDeploymentRepo())) {
            Stream.of(repositoryConfigService.getRepoConfigsByType(LOCAL),
                            repositoryConfigService.getRepoConfigsByType(FEDERATED))
                    .flatMap(Collection::stream)
                    .filter(repoConfig -> repoConfig.getKey().equals(model.getDefaultDeploymentRepo()))
                    .map(LocalConfig.class::cast)
                    .findAny()
                    .ifPresent(repoConfig -> virtualRepoConfig.getRepoTypeConfig().setDefaultDeploymentRepoRef(
                            repoConfig.getKey()));

        }
    }

    private FederatedMember toFederatedMember(MirrorMemberConfigModel mirrorMemberConfigModel) {
        FederatedMember federatedMember = new FederatedMember();
        federatedMember.setUrl(mirrorMemberConfigModel.getUrl());
        federatedMember.setMode(mirrorMemberConfigModel.getMode());
        federatedMember.setEnabled(mirrorMemberConfigModel.isEnabled());
        return federatedMember;
    }

    private void populateFederatedRepoAdvancedDescriptorValues(FederatedRepoAdvancedRepositoryConfigModel advanced,
            FederatedRepoConfig federatedRepoConfig) {
        populateProxyValues(advanced, federatedRepoConfig);
    }

    /**
     * populate config model's proxy values to repository config
     * <p>
     * Note: default proxy must not be set as reference but to be handled as part of system config change event
     * mechanism
     *
     * @see org.artifactory.repo.confighandler.ProxyConfigChangeEventHandler
     */
    private void populateProxyValues(ProxyAwareConfigModel model, ProxyRepoConfig proxyRepoConfig) {
        boolean isDisabledProxy = Boolean.TRUE.equals(model.isDisableProxy());
        proxyRepoConfig.setDisableProxy(isDisabledProxy);
        if (isDisabledProxy) {
            return;
        }
        String proxyRef = model.getProxy();
        if (StringUtils.isNotBlank(proxyRef)) {
            ProxyDescriptor proxyDescriptor = centralConfig.getDescriptor().getProxy(proxyRef);
            if (proxyDescriptor == null) {
                log.debug("Proxy '{}' does not exist in the config descriptor", proxyRef);
            } else if (!proxyDescriptor.isPlatformDefault()) {
                proxyRepoConfig.setProxyRef(proxyRef);
            }
        }
    }

    /**
     * Populates basic descriptor values that are shared between local and remote repos
     */
    private void populateSharedBasicConfigValues(BasicRepositoryConfigModel model,
            RepoConfig repoConfig) {
        MutableCentralConfigDescriptor mutableDescriptor = centralConfig.getMutableDescriptor();
        repoConfig.getBaseConfig().setDescription(model.getPublicDescription());
        repoConfig.getBaseConfig().setNotes(model.getInternalDescription());
        repoConfig.getBaseConfig().setIncludesPattern(model.getIncludesPattern());
        repoConfig.getBaseConfig().setExcludesPattern(model.getExcludesPattern());
        if (StringUtils.isNotBlank(model.getLayout())) { //Don't enforce on virtual
            repoConfig.getBaseConfig()
                    .setRepoLayoutRef(mutableDescriptor.getRepoLayout(model.getLayout()).getName());
        }
        KeyPairDescriptor keyPair = mutableDescriptor
                .getKeyPair(model.getKeypair());
        KeyPairDescriptor primaryKeyPair = mutableDescriptor.getKeyPair(model.getPrimaryKeyPair());
        if (StringUtils.isNotBlank(model.getKeypair()) && keyPair != null) {
            repoConfig.getSecurityConfig().setPrimaryKeyPairRef(keyPair.getKey());
        } else if (StringUtils.isNotBlank(model.getPrimaryKeyPair()) && primaryKeyPair != null) {
            repoConfig.getSecurityConfig().setPrimaryKeyPairRef(primaryKeyPair.getKey());
        } else {
            repoConfig.getSecurityConfig().setPrimaryKeyPairRef(null);
        }
        KeyPairDescriptor secondaryKeyPair = mutableDescriptor
                .getKeyPair(model.getSecondaryKeyPair());
        if (StringUtils.isNotBlank(model.getSecondaryKeyPair()) && secondaryKeyPair != null) {
            repoConfig.getSecurityConfig().setSecondaryKeyPairRef(secondaryKeyPair.getKey());
        } else {
            repoConfig.getSecurityConfig().setSecondaryKeyPairRef(null);
        }
    }

    private void setDefaultGpgKeyIfNeeded(RepoConfig localConfig) {
        if (KeyPairDescriptor.GPG_REPO_TYPES.contains(localConfig.getPackageType())
                && Objects.isNull(localConfig.getSecurityConfig().getPrimaryKeyPairRef())) {
            KeyPairDescriptor keyPair = centralConfig.getDescriptor().getKeyPair(KeyPairDescriptor.DEFAULT_GPG_KEY);
            if (Objects.nonNull(keyPair)) {
                localConfig.getSecurityConfig().setPrimaryKeyPairRef(keyPair.getKey());
            }
        }
    }

    /**
     * Populates remote basic repo config values
     */
    private void populateRemoteBasicRepoConfigValues(RemoteBasicRepositoryConfigModel model,
            RemoteRepoConfig remoteRepoConfig) {
        remoteRepoConfig.getRepoTypeConfig().setUrl(model.getUrl());
        if (model.getRemoteLayoutMapping() != null) {
            RepoLayout repoLayout = centralConfig.getDescriptor().getRepoLayout(model.getRemoteLayoutMapping());
            remoteRepoConfig.getRepoTypeConfig()
                    .setRemoteRepoLayoutRef(repoLayout != null ? repoLayout.getName() : null);
        }
        remoteRepoConfig.getRepoTypeConfig().setOffline(model.isOffline());
        remoteRepoConfig.getRepoTypeConfig().setSendContext(model.isSendContext());
        remoteRepoConfig.getRepoTypeConfig().setPassThrough(model.isPassThrough());
        remoteRepoConfig.getRepoTypeConfig().setCurated(model.isCurated());
        remoteRepoConfig.getRepoTypeConfig().setContentSynchronisation(model.getContentSynchronisation());
    }

    private void populateVirtualAdvancedConfigValues(VirtualAdvancedRepositoryConfigModel model,
            VirtualRepoConfig virtualRepoConfig) {
        virtualRepoConfig.getRepoTypeConfig()
                .setArtifactoryRequestsCanRetrieveRemoteArtifacts(model.getRetrieveRemoteArtifacts());
        virtualRepoConfig.getSecurityConfig().setSignedUrlTtl(model.getSignedUrlTtl());
        buildReverseProxyDescriptor(model, virtualRepoConfig);
        virtualRepoConfig.getSecurityConfig().setHideUnauthorizedResources(model.getHideUnauthorizedResources());
    }

    /**
     * Populates advanced repo config values that are shared between local and remote repos
     */
    private void populateSharedAdvancedDescriptorValues(AdvancedRepositoryConfigModel model,
            RealRepoConfig localConfig) {
        localConfig.getRepoTypeConfig().setBlackedOut(model.isBlackedOut());
        localConfig.getRepoTypeConfig().setArchiveBrowsingEnabled(model.getAllowContentBrowsing());
        localConfig.getSecurityConfig().setSignedUrlTtl(model.getSignedUrlTtl());
        List<PropertySetNameModel> propertySetNameModels = model.getPropertySets();
        if (propertySetNameModels != null) {
            List<PropertySet> propertySets = model.getPropertySets().stream()
                    .map(propSet -> getPropSetByName(propSet.getName()))
                    .filter(Objects::nonNull)
                    .toList();
            localConfig.getRepoTypeConfig().setPropertySetRefs(propertySets.stream().map(PropertySet::getName)
                    .toList());
        }
        buildReverseProxyDescriptor(model, localConfig);
        localConfig.getRepoTypeConfig().setPriorityResolution(Boolean.TRUE.equals(model.getPriorityResolution()));
    }

    /**
     * Populates remote advanced repo config values
     */
    private void populateRemoteAdvancedDescriptorValues(RemoteAdvancedRepositoryConfigModel model,
            RemoteRepoConfig remoteRepoConfig, TypeSpecificConfigModel typeSpecificConfigModel) {
        //network
        RemoteNetworkRepositoryConfigModel network = model.getNetwork();
        if (network != null) {
            populateProxyValues(network, remoteRepoConfig);
            remoteRepoConfig.getRepoTypeConfig().setLocalAddress(network.getLocalAddress());
            remoteRepoConfig.getRepoTypeConfig().setUsername(network.getUsername());
            remoteRepoConfig.getRepoTypeConfig()
                    .setPassword(CryptoHelper.encryptIfNeeded(ArtifactoryHome.get(), network.getPassword()));
            remoteRepoConfig.getRepoTypeConfig()
                    .setEnableTokenAuthentication(typeSpecificConfigModel.isEnableTokenAuthentication());
            remoteRepoConfig.getRepoTypeConfig().setSocketTimeoutMillis(network.getSocketTimeout());
            remoteRepoConfig.getRepoTypeConfig().setAllowAnyHostAuth(network.getLenientHostAuth());
            remoteRepoConfig.getRepoTypeConfig().setEnableCookieManagement(network.getCookieManagement());
            remoteRepoConfig.getRepoTypeConfig().setClientTlsCertificate(network.getSelectedInstalledCertificate());
        }
        //cache
        RemoteCacheRepositoryConfigModel cache = model.getCache();
        if (cache != null) {
            remoteRepoConfig.getRepoTypeConfig()
                    .setUnusedArtifactsCleanupPeriodHours(cache.getKeepUnusedArtifactsHours());
            remoteRepoConfig.getRepoTypeConfig().setRetrievalCachePeriodSecs(cache.getRetrievalCachePeriodSecs());
            remoteRepoConfig.getRepoTypeConfig().setAssumedOfflinePeriodSecs(cache.getAssumedOfflineLimitSecs());
            remoteRepoConfig.getRepoTypeConfig()
                    .setMissedRetrievalCachePeriodSecs(cache.getMissedRetrievalCachePeriodSecs());
            remoteRepoConfig.getRepoTypeConfig()
                    .setMetadataRetrievalTimeoutSecs(cache.getMetadataRetrievalTimeoutSecs());
        }
        //other
        remoteRepoConfig.getRepoTypeConfig().setQueryParams(model.getQueryParams());
        remoteRepoConfig.getRepoTypeConfig().setPropagateQueryParams(model.isPropagateQueryParams());
        remoteRepoConfig.getRepoTypeConfig().setHardFail(model.getHardFail());
        remoteRepoConfig.getRepoTypeConfig().setSynchronizeProperties(model.getSynchronizeArtifactProperties());
        remoteRepoConfig.getRepoTypeConfig().setShareConfiguration(model.isShareConfiguration());
        remoteRepoConfig.getRepoTypeConfig().setBlockMismatchingMimeTypes(model.isBlockMismatchingMimeTypes());
        remoteRepoConfig.getRepoTypeConfig().setMismatchingMimeTypesOverrideList(
                PathUtils.collectionToDelimitedString(model.getMismatchingMimeTypesOverrideList()));
        remoteRepoConfig.getRepoTypeConfig().setBypassHeadRequests(model.isBypassHeadRequests());
        remoteRepoConfig.getRepoTypeConfig().setDisableUrlNormalization(model.isDisableUrlNormalization());
        remoteRepoConfig.getRepoTypeConfig().setRetrieveSha256FromServer(model.isRetrieveSha256FromServer());

        assertStoreArtifactsLocally(model, typeSpecificConfigModel.getRepoType());
        remoteRepoConfig.getRepoTypeConfig().setStoreArtifactsLocally(model.isStoreArtifactsLocally());
    }

    private static void assertStoreArtifactsLocally(RemoteAdvancedRepositoryConfigModel model, RepoType repoType) {
        boolean requiresStorageLocally = repoType.isTerraformRegistry() || repoType.isDockerGroup();
        if (requiresStorageLocally && Boolean.FALSE.equals(model.isStoreArtifactsLocally())) {
            throw new IllegalArgumentException(
                    repoType.getDisplayName() + " prerequisite - artifacts must be stored locally in cache");
        }
    }

    /**
     * Populates type specific values shared by local and remote repos
     */
    private void populateSharedTypeSpecificDescriptorValues(TypeSpecificConfigModel type,
            RealRepoConfig localConfig) {
        switch (type.getRepoType()) {
            case Maven, Gradle, Ivy, SBT -> {
                MavenTypeSpecificConfigModel maven = (MavenTypeSpecificConfigModel) type;
                localConfig.getRepoTypeConfig().setMaxUniqueSnapshots(maven.getMaxUniqueSnapshots());
                localConfig.getRepoTypeConfig().setHandleReleases(maven.getHandleReleases());
                localConfig.getRepoTypeConfig().setHandleSnapshots(maven.getHandleSnapshots());
                localConfig.getPackageTypeConfig()
                        .put(SUPPRESS_POM_CONSISTENCY_CHECKS.getKey(),
                                String.valueOf(maven.getSuppressPomConsistencyChecks()));
            }
            case P2 -> localConfig.getPackageTypeConfig().put(FORCE_P2_AUTHENTICATION.getKey(),
                    String.valueOf(((P2TypeSpecificConfigModel) type).isForceP2Authentication()));
            case NuGet -> localConfig.getPackageTypeConfig().put(FORCE_NUGET_AUTHENTICATION.getKey(),
                    String.valueOf(((NugetTypeSpecificConfigModel) type).isForceNugetAuthentication()));
            case Conan -> localConfig.getPackageTypeConfig().put(FORCE_CONAN_AUTHENTICATION.getKey(),
                    String.valueOf(((ConanTypeSpecificConfigModel) type).getForceConanAuthentication()));
            case Hex -> localConfig.getPackageTypeConfig().put(HEX_REGISTRY_PUBLIC_KEY.getKey(),
                    String.valueOf(((HexTypeSpecificConfigModel) type).getHexPublicKey()));
            default -> log.trace("No Shared type specific for {}", type.getRepoType());
        }
    }

    /**
     * Populates type specific values for local repos
     */
    private void populateLocalTypeSpecificDescriptorValues(TypeSpecificConfigModel type,
            LocalConfig localConfig) {
        final Map<String, String> packageTypeConfig = localConfig.getPackageTypeConfig();
        switch (type.getRepoType()) {
            case Maven, Gradle, Ivy, SBT -> {
                MavenTypeSpecificConfigModel maven = (MavenTypeSpecificConfigModel) type;
                localConfig.getRepoTypeConfig().setSnapshotVersionBehavior(maven.getSnapshotVersionBehavior());
                localConfig.getRepoTypeConfig().setChecksumPolicyType(maven.getLocalChecksumPolicy());
            }
            case YUM -> populateLocalYumDescriptor((YumTypeSpecificConfigModel) type, packageTypeConfig);
            case Docker, OCI, HelmOCI ->
                    populateLocalOciOrDockerDescriptor((DockerTypeSpecificConfigModel) type, packageTypeConfig);
            case Debian -> populateLocalDebianDescriptor((DebTypeSpecificConfigModel) type, packageTypeConfig);
            case NuGet -> {
                NugetTypeSpecificConfigModel nuget = (NugetTypeSpecificConfigModel) type;
                localConfig.getRepoTypeConfig().setMaxUniqueSnapshots(nuget.getMaxUniqueSnapshots());
                boolean globalNormalization = centralConfig.getDescriptor()
                        .getRepositoriesGeneralConfig().getNuget().isEnableGlobalNormalization();
                packageTypeConfig.put(ENABLE_NUGET_NORMALIZED_VERSION.getKey(),
                        String.valueOf(globalNormalization || nuget.isEnableNormalizedVersion()));
            }
            case Cargo -> populateLocalCargoDescriptor((CargoTypeSpecificConfigModel) type, packageTypeConfig);
            case Composer -> {
                ComposerTypeSpecificConfigModel composer = (ComposerTypeSpecificConfigModel) type;
                packageTypeConfig.put(ENABLE_COMPOSER_V1_INDEXING.getKey(),
                        String.valueOf(composer.isEnableComposerV1Indexing()));
            }
            case Terraform -> {
                TerraformTypeSpecificConfigModel terraform = (TerraformTypeSpecificConfigModel) type;
                packageTypeConfig.put(TERRAFORM_TYPE.getKey(), terraform.getTerraformType());
            }
            case TerraformBackend -> {
                TerraformBackendTypeSpecificConfigModel terraformBackend =
                        (TerraformBackendTypeSpecificConfigModel) type;
                packageTypeConfig.put(ENCRYPT_STATES.getKey(), String.valueOf(terraformBackend.isEncryptStates()));
            }
            case Helm -> {
                HelmTypeSpecificConfigModel helm = (HelmTypeSpecificConfigModel) type;
                packageTypeConfig.put(FORCE_METADATA_NAME_VERSION, String.valueOf(helm.isForceMetadataNameVersion()));
                packageTypeConfig.put(FORCE_NON_DUPLICATE_CHART, String.valueOf(helm.isForceNonDuplicateChart()));
            }
            case HuggingFaceML -> {
                HuggingFaceMLTypeSpecificConfigModel huggingFace = (HuggingFaceMLTypeSpecificConfigModel) type;
                packageTypeConfig.put(IS_ML_REPO_LAYOUT, String.valueOf(huggingFace.isMlRepoLayout()));
            }
            default -> log.trace("No populate rules for {}", type.getRepoType());
        }
    }

    private static void populateLocalCargoDescriptor(CargoTypeSpecificConfigModel type,
            Map<String, String> packageTypeConfig) {
        packageTypeConfig.put(CARGO_ANONYMOUS_ACCESS.getKey(), String.valueOf(type.isAnonymousAccess()));
        packageTypeConfig.put(CARGO_INTERNAL_INDEX.getKey(), String.valueOf(type.isInternalIndex()));
    }

    private static void populateLocalDebianDescriptor(DebTypeSpecificConfigModel type,
            Map<String, String> packageTypeConfig) {
        packageTypeConfig.put(DEBIAN_TRIVIAL_LAYOUT.getKey(), String.valueOf(type.getTrivialLayout()));
        packageTypeConfig.put(OPTIONAL_INDEX_COMPRESSION_FORMATS.getKey(),
                JsonUtils.getInstance().writeValueAsString(type.getOptionalIndexCompressionFormats()));
        packageTypeConfig.put(DDEB_SUPPORTED.getKey(), String.valueOf(type.isDdebSupported()));
    }

    private static void populateLocalOciOrDockerDescriptor(DockerTypeSpecificConfigModel type,
            Map<String, String> packageTypeConfig) {
        packageTypeConfig.put(DOCKER_API_VERSION.getKey(), type.getDockerApiVersion().toString());
        packageTypeConfig.put(MAX_UNIQUE_TAGS.getKey(), String.valueOf(type.getMaxUniqueTags()));
        packageTypeConfig.put(BLOCK_PUSHING_SCHEMA_1.getKey(), String.valueOf(type.isBlockPushingSchema1()));
        packageTypeConfig.put(DOCKER_TAG_RETENTION.getKey(), String.valueOf(type.getDockerTagRetention()));
    }

    private static void populateLocalYumDescriptor(YumTypeSpecificConfigModel type,
            Map<String, String> packageTypeConfig) {
        packageTypeConfig
                .put(YUM_GROUP_FILENAMES.getKey(), type.getGroupFileNames());
        packageTypeConfig
                .put(YUM_ROOT_DEPTH.getKey(),
                        String.valueOf(type.getMetadataFolderDepth()));
        packageTypeConfig
                .put(CALCULATE_YUM_METADATA.getKey(),
                        String.valueOf(type.isAutoCalculateYumMetadata()));
        packageTypeConfig
                .put(ENABLE_FILE_LISTS_INDEXING.getKey(), String.valueOf(type.isEnableFileListsIndexing()));
    }

    /**
     * Populates type specific values for remote repos
     */
    private void populateRemoteTypeSpecificDescriptorValues(TypeSpecificConfigModel type,
            RemoteRepoConfig remoteRepoConfig) {
        final Map<String, String> packageTypeConfig = remoteRepoConfig.getPackageTypeConfig();
        switch (type.getRepoType()) {
            case P2, Maven, Gradle, Ivy, SBT -> populateRemoteMavenGroupDescriptor((MavenTypeSpecificConfigModel) type,
                    remoteRepoConfig, packageTypeConfig);
            case Bower -> populateRemoteBowerDescriptor((BowerTypeSpecificConfigModel) type, remoteRepoConfig);
            case CocoaPods -> populateRemoteCocoaPodsDescriptor((CocoaPodsTypeSpecificConfigModel) type,
                    remoteRepoConfig, packageTypeConfig);
            case VCS -> populateRemoteVcsDescriptor((VcsTypeSpecificConfigModel) type, remoteRepoConfig);
            case Docker, OCI, HelmOCI -> populateRemoteOciOrDockerDescriptor((DockerTypeSpecificConfigModel) type,
                    remoteRepoConfig, packageTypeConfig);
            case Debian -> populateRemoteDebianDescriptor((DebTypeSpecificConfigModel) type, remoteRepoConfig,
                    packageTypeConfig);
            case NuGet -> populateRemoteNuGetDescriptor((NugetTypeSpecificConfigModel) type, remoteRepoConfig);
            case Generic -> populateRemoteGenericDescriptor((GenericTypeSpecificConfigModel) type, remoteRepoConfig);
            case Go -> buildAndSetGoConfig(remoteRepoConfig, (GoTypeSpecificConfigModel) type);
            case YUM -> {
                YumTypeSpecificConfigModel yum = (YumTypeSpecificConfigModel) type;
                remoteRepoConfig.getRepoTypeConfig().setListRemoteFolderItems(yum.isListRemoteFolderItems());
            }
            case Npm -> {
                NpmTypeSpecificConfigModel npm = (NpmTypeSpecificConfigModel) type;
                remoteRepoConfig.getRepoTypeConfig().setListRemoteFolderItems(npm.isListRemoteFolderItems());
            }
            case Gems -> {
                GemsTypeSpecificConfigModel gems = (GemsTypeSpecificConfigModel) type;
                remoteRepoConfig.getRepoTypeConfig().setListRemoteFolderItems(gems.isListRemoteFolderItems());
            }
            case Pypi -> {
                PypiTypeSpecificConfigModel pypi = (PypiTypeSpecificConfigModel) type;
                buildAndSetPyPIConfig(remoteRepoConfig, pypi);
                remoteRepoConfig.getRepoTypeConfig().setListRemoteFolderItems(pypi.isListRemoteFolderItems());
            }
            case GitLfs -> {
                GitLfsTypeSpecificConfigModel gitlfs = (GitLfsTypeSpecificConfigModel) type;
                remoteRepoConfig.getRepoTypeConfig().setListRemoteFolderItems(gitlfs.isListRemoteFolderItems());
            }
            case Composer -> populateRemoteComposerDescriptor((ComposerTypeSpecificConfigModel) type, remoteRepoConfig);
            case Chef -> {
                ChefTypeSpecificConfigModel chef = (ChefTypeSpecificConfigModel) type;
                remoteRepoConfig.getRepoTypeConfig().setListRemoteFolderItems(chef.isListRemoteFolderItems());
            }
            case Conan -> {
                ConanTypeSpecificConfigModel conan = (ConanTypeSpecificConfigModel) type;
                remoteRepoConfig.getRepoTypeConfig().setListRemoteFolderItems(conan.getListRemoteFolderItems());
            }
            case Conda -> {
                CondaTypeSpecificConfigModel conda = (CondaTypeSpecificConfigModel) type;
                remoteRepoConfig.getRepoTypeConfig().setListRemoteFolderItems(conda.getListRemoteFolderItems());
            }
            case Helm -> {
                HelmTypeSpecificConfigModel helm = (HelmTypeSpecificConfigModel) type;
                buildAndSetHelmConfig(remoteRepoConfig, helm);
                remoteRepoConfig.getRepoTypeConfig().setListRemoteFolderItems(helm.isListRemoteFolderItems());
            }
            case Cargo -> buildAndSetCargoConfig(remoteRepoConfig, (CargoTypeSpecificConfigModel) type);
            case Terraform ->
                    populateRemoteTerraformDescriptor((TerraformTypeSpecificConfigModel) type, remoteRepoConfig);
            case Swift -> populateRemoteSwiftDescriptor((SwiftTypeSpecificConfigModel) type, remoteRepoConfig,
                    packageTypeConfig);
            case HuggingFaceML ->
                    populateRemoteHuggingFaceDescriptor((HuggingFaceMLTypeSpecificConfigModel) type, remoteRepoConfig,
                            packageTypeConfig);
            case NimModel -> populateRemoteNimModelDescriptor((NimModelTypeSpecificConfigModel) type, remoteRepoConfig);
            case Ansible -> buildAndSetAnsibleConfig(
                    remoteRepoConfig, (AnsibleTypeSpecificConfigModel) type);
            case Opkg -> {
                OpkgTypeSpecificConfigModel opkg = (OpkgTypeSpecificConfigModel) type;
                remoteRepoConfig.getRepoTypeConfig().setListRemoteFolderItems(opkg.isListRemoteFolderItems());
            }
            case Alpine -> {
                AlpineTypeSpecificConfigModel alpine = (AlpineTypeSpecificConfigModel) type;
                remoteRepoConfig.getRepoTypeConfig().setListRemoteFolderItems(alpine.isListRemoteFolderItems());
            }
            case AIEditorExtensions -> {
                AIEditorExtensionsTypeSpecificConfigModel vscode = (AIEditorExtensionsTypeSpecificConfigModel) type;
                remoteRepoConfig.getRepoTypeConfig().setListRemoteFolderItems(vscode.isListRemoteFolderItems());
                populateRemoteVSCodeDescriptor(packageTypeConfig, vscode);
            }
            default -> log.trace("No remote type specific rules for {}", type.getRepoType());
        }
    }

    private void populateRemoteComposerDescriptor(ComposerTypeSpecificConfigModel type,
            RemoteRepoConfig remoteRepoConfig) {
        buildAndSetComposerConfig(remoteRepoConfig, type);
        remoteRepoConfig.getRepoTypeConfig().setListRemoteFolderItems(type.isListRemoteFolderItems());
    }

    private void populateRemoteTerraformDescriptor(TerraformTypeSpecificConfigModel type,
            RemoteRepoConfig remoteRepoConfig) {
        buildAndSetTerraformRemoteConfig(remoteRepoConfig, type);
        remoteRepoConfig.getRepoTypeConfig().setListRemoteFolderItems(type.isListRemoteFolderItems());
    }

    private void populateRemoteVSCodeDescriptor(Map<String, String> packageTypeConfig,
            AIEditorExtensionsTypeSpecificConfigModel type) {
        RemoteRepoConfig vscodeRemoteRepoConfig =
                repositoryConfigService.getRemoteRepoConfigByKey(type.getExternalRemoteRepo());
        populateExternalDependencies(packageTypeConfig, type.getEnableExternalDependencies(),
                type.getExternalPatterns(), vscodeRemoteRepoConfig);
    }

    private static void populateRemoteHuggingFaceDescriptor(HuggingFaceMLTypeSpecificConfigModel type,
            RemoteRepoConfig remoteRepoConfig, Map<String, String> packageTypeConfig) {
        remoteRepoConfig.getRepoTypeConfig().setListRemoteFolderItems(type.getListRemoteFolderItems());
        remoteRepoConfig.getRepoTypeConfig().setEnableTokenAuthentication(
                DEFAULT_HUGGING_FACE_ML_REGISTRY_URL.equals(
                        PathUtils.trimTrailingSlashes(remoteRepoConfig.getRepoTypeConfig().getUrl())));

        packageTypeConfig.put(IS_ML_REPO_LAYOUT, String.valueOf(type.isMlRepoLayout()));
    }

    private static void populateRemoteNimModelDescriptor(NimModelTypeSpecificConfigModel type,
            RemoteRepoConfig remoteRepoConfig) {
        remoteRepoConfig.getRepoTypeConfig().setListRemoteFolderItems(type.getListRemoteFolderItems());
        remoteRepoConfig.getRepoTypeConfig().setEnableTokenAuthentication(
                DEFAULT_NIMMODEL_REGISTRY_URL.equals(
                        PathUtils.trimTrailingSlashes(remoteRepoConfig.getRepoTypeConfig().getUrl())));
    }

    private static void populateRemoteSwiftDescriptor(SwiftTypeSpecificConfigModel type,
            RemoteRepoConfig remoteRepoConfig,
            Map<String, String> packageTypeConfig) {

        packageTypeConfig.put(EXTERNAL_DEPENDENCIES_ENABLED.getKey(),
                String.valueOf(type.getEnableExternalDependencies()));
        packageTypeConfig.put(EXTERNAL_DEPENDENCIES_PATTERNS.getKey(),
                JsonUtils.getInstance().writeValueAsString(type.getExternalPatterns()));
        packageTypeConfig.put(EXTERNAL_DEPENDENCIES_REMOTE_REPO_REF.getKey(), null); //not used

        remoteRepoConfig.getRepoTypeConfig().setListRemoteFolderItems(type.getListRemoteFolderItems());
    }

    private static void populateRemoteMavenGroupDescriptor(MavenTypeSpecificConfigModel type,
            RemoteRepoConfig remoteRepoConfig, Map<String, String> packageTypeConfig) {

        packageTypeConfig.put(FETCH_JARS_EAGERLY.getKey(), String.valueOf(type.getEagerlyFetchJars()));
        packageTypeConfig.put(FETCH_SOURCES_EAGERLY.getKey(), String.valueOf(type.getEagerlyFetchSources()));
        packageTypeConfig.put(REJECT_INVALID_JARS.getKey(), String.valueOf(type.getRejectInvalidJars()));
        remoteRepoConfig.getRepoTypeConfig().setListRemoteFolderItems(type.isListRemoteFolderItems());
        remoteRepoConfig.getRepoTypeConfig().setChecksumPolicyType(type.getRemoteChecksumPolicy());

        //Set p2 url for supporting repos
        if (remoteRepoConfig.getRepoTypeConfig().getUrl() !=
                null) { //Should always be true, but this is to avoid accidental nulls
            packageTypeConfig.put(P2_ORIGINAL_URL.getKey(), remoteRepoConfig.getRepoTypeConfig().getUrl());
        }
    }

    private static void populateRemoteGenericDescriptor(GenericTypeSpecificConfigModel type,
            RemoteRepoConfig remoteRepoConfig) {
        remoteRepoConfig.getRepoTypeConfig().setListRemoteFolderItems(type.isListRemoteFolderItems());
    }

    private void populateRemoteNuGetDescriptor(NugetTypeSpecificConfigModel type, RemoteRepoConfig remoteRepoConfig) {
        remoteRepoConfig.getRepoTypeConfig().setListRemoteFolderItems(type.isListRemoteFolderItems());
        buildAndSetNugetConfig(remoteRepoConfig, type);
    }

    private static void populateRemoteDebianDescriptor(DebTypeSpecificConfigModel type,
            RemoteRepoConfig remoteRepoConfig,
            Map<String, String> packageTypeConfig) {
        remoteRepoConfig.getRepoTypeConfig().setListRemoteFolderItems(type.isListRemoteFolderItems());
        packageTypeConfig.put(DDEB_SUPPORTED.getKey(), String.valueOf(type.isDdebSupported()));
    }

    private static void populateRemoteOciOrDockerDescriptor(DockerTypeSpecificConfigModel type,
            RemoteRepoConfig remoteRepoConfig, Map<String, String> packageTypeConfig) {
        remoteRepoConfig.getRepoTypeConfig()
                .setEnableTokenAuthentication(type.getEnableTokenAuthentication());
        remoteRepoConfig.getRepoTypeConfig().setListRemoteFolderItems(type.getListRemoteFolderItems());
        packageTypeConfig.put(EXTERNAL_DEPENDENCIES_ENABLED.getKey(),
                String.valueOf(type.getEnableForeignLayersCaching()));
        packageTypeConfig.put(EXTERNAL_DEPENDENCIES_PATTERNS.getKey(),
                JsonUtils.getInstance().writeValueAsString(type.getExternalPatterns()));
        packageTypeConfig.put(EXTERNAL_DEPENDENCIES_REMOTE_REPO_REF.getKey(), null); //not used
        packageTypeConfig.put(BLOCK_PUSHING_SCHEMA_1.getKey(), String.valueOf(type.isBlockPushingSchema1()));
        packageTypeConfig.put(DOCKER_PROJECT_ID.getKey(), type.getDockerProjectId());
        remoteRepoConfig.getRepoTypeConfig().setRetrievalCachePeriodSecs(type.getRetrievalCachePeriodSecs());
    }

    private void populateRemoteVcsDescriptor(VcsTypeSpecificConfigModel type, RemoteRepoConfig remoteRepoConfig) {
        buildAndSetVcsConfig(remoteRepoConfig, type);
        remoteRepoConfig.getRepoTypeConfig().setListRemoteFolderItems(type.isListRemoteFolderItems());
    }

    private void populateRemoteBowerDescriptor(BowerTypeSpecificConfigModel type, RemoteRepoConfig remoteRepoConfig) {
        buildAndSetBowerConfig(remoteRepoConfig, type);
        remoteRepoConfig.getRepoTypeConfig().setListRemoteFolderItems(type.isListRemoteFolderItems());
    }

    private void populateRemoteCocoaPodsDescriptor(CocoaPodsTypeSpecificConfigModel pods,
            RemoteRepoConfig remoteRepoConfig, Map<String, String> packageTypeConfig) {
        buildAndSetPodsConfig(remoteRepoConfig, pods);
        remoteRepoConfig.getRepoTypeConfig().setListRemoteFolderItems(pods.isListRemoteFolderItems());
        packageTypeConfig.put(EXTERNAL_DEPENDENCIES_ENABLED.getKey(),
                String.valueOf(pods.getEnableExternalDependencies()));
        packageTypeConfig.put(EXTERNAL_DEPENDENCIES_PATTERNS.getKey(),
                JsonUtils.getInstance().writeValueAsString(pods.getExternalPatterns()));
        packageTypeConfig.put(EXTERNAL_DEPENDENCIES_REMOTE_REPO_REF.getKey(), null);
        packageTypeConfig.put(COCOA_PODS_FORCE_MODIFY_INDEX.getKey(),
                String.valueOf(pods.getForceModifyIndex()));
    }

    private void populateVirtualTypeSpecific(TypeSpecificConfigModel type, VirtualRepoConfig virtualRepoConfig) {
        Map<String, String> packageTypeConfig = virtualRepoConfig.getPackageTypeConfig();
        switch (type.getRepoType()) {
            case P2 -> buildAndSetVirtualP2Config(virtualRepoConfig, (P2TypeSpecificConfigModel) type);
            case NuGet -> {
                boolean isGlobalNormalised = centralConfig.getDescriptor()
                        .getRepositoriesGeneralConfig().getNuget().isEnableGlobalNormalization();
                populateVirtualNuGetDescriptor((NugetTypeSpecificConfigModel) type, packageTypeConfig,
                        isGlobalNormalised);
            }
            case Bower -> populateVirtualBowerDescriptor((BowerTypeSpecificConfigModel) type, packageTypeConfig);
            case Npm -> populateVirtualNpmDescriptor((NpmTypeSpecificConfigModel) type, virtualRepoConfig,
                    packageTypeConfig);
            case Go -> populateVirtualGoDescriptor((GoTypeSpecificConfigModel) type, packageTypeConfig);
            case YUM -> populateVirtualYumDescriptor((YumTypeSpecificConfigModel) type, virtualRepoConfig);
            case Helm -> populateVirtualHelmDescriptor((HelmTypeSpecificConfigModel) type, virtualRepoConfig,
                    packageTypeConfig);
            case Alpine -> populateVirtualAlpineDescriptor((AlpineTypeSpecificConfigModel) type, virtualRepoConfig);
            case Cargo -> populateVirtualCargoDescriptor((CargoTypeSpecificConfigModel) type, virtualRepoConfig,
                    packageTypeConfig);
            case CRAN -> populateVirtualCranDescriptor((CranTypeSpecificConfigModel) type, virtualRepoConfig);
            case Conan -> populateVirtualConanDescriptor((ConanTypeSpecificConfigModel) type, virtualRepoConfig,
                    packageTypeConfig);
            case Conda -> populateVirtualCondaDescriptor((CondaTypeSpecificConfigModel) type, virtualRepoConfig);
            case Chef -> populateVirtualChefDescriptor((ChefTypeSpecificConfigModel) type, virtualRepoConfig);
            case Debian -> populateVirtualDebianDescriptor((DebTypeSpecificConfigModel) type, virtualRepoConfig,
                    packageTypeConfig);
            case Maven, Gradle, Ivy, SBT -> //P2 reaches maven also
                    populateVirtualMavenGroupDescriptor(type, virtualRepoConfig, packageTypeConfig);
            case Docker, OCI, HelmOCI ->
                    populateVirtualOciOrDockerDescriptor((DockerTypeSpecificConfigModel) type, packageTypeConfig);
            case Composer ->
                    populateVirtualComposerDescriptor((ComposerTypeSpecificConfigModel) type, virtualRepoConfig);
            case HuggingFaceML -> {
                packageTypeConfig.put(IS_ML_REPO_LAYOUT, String.valueOf(true));
            }
            case Nix -> populateVirtualNixDescriptor((NixTypeSpecificConfigModel) type, virtualRepoConfig);
            default -> log.trace("No virtual type specific for {}", type.getRepoType());
        }
    }

    private static void populateVirtualNuGetDescriptor(NugetTypeSpecificConfigModel type,
            Map<String, String> packageTypeConfig, boolean isGlobalNormalised) {
        packageTypeConfig.put(FORCE_NUGET_AUTHENTICATION.getKey(),
                String.valueOf(type.isForceNugetAuthentication()));
        packageTypeConfig.put(ENABLE_NUGET_NORMALIZED_VERSION.getKey(),
                String.valueOf(isGlobalNormalised || type.isEnableNormalizedVersion()));
    }

    private static void populateVirtualComposerDescriptor(ComposerTypeSpecificConfigModel type,
            VirtualRepoConfig virtualRepoConfig) {
        VirtualCacheConfig virtualCacheConfig = virtualRepoConfig.getRepoTypeConfig().getVirtualCacheConfig();
        virtualCacheConfig = virtualCacheConfig != null ? virtualCacheConfig : new VirtualCacheConfig();
        virtualCacheConfig.setVirtualRetrievalCachePeriodSecs(type.getVirtualRetrievalCachePeriodSecs());
        virtualRepoConfig.getRepoTypeConfig().setVirtualCacheConfig(virtualCacheConfig);
    }

    private static void populateVirtualOciOrDockerDescriptor(DockerTypeSpecificConfigModel type,
            Map<String, String> packageTypeConfig) {
        packageTypeConfig.put(RESOLVE_DOCKER_TAGS_BY_TIMESTAMP.getKey(),
                String.valueOf(type.getResolveDockerTagsByTimestamp()));
        packageTypeConfig.put(CACHING_LOCAL_FOREIGN_LAYERS_ENABLED.getKey(),
                String.valueOf(type.getCachingLocalForeignLayersEnabled()));
    }

    private static void populateVirtualMavenGroupDescriptor(TypeSpecificConfigModel type,
            VirtualRepoConfig virtualRepoConfig,
            Map<String, String> packageTypeConfig) {
        if (type.getRepoType() == RepoType.Maven || type.getRepoType() == RepoType.P2) {
            MavenTypeSpecificConfigModel maven = (MavenTypeSpecificConfigModel) type;
            packageTypeConfig.put(FORCE_MAVEN_AUTHENTICATION.getKey(),
                    String.valueOf(maven.isForceMavenAuthentication()));
        }
        MavenTypeSpecificConfigModel mavenTypes = (MavenTypeSpecificConfigModel) type;
        virtualRepoConfig.getSecurityConfig().setKeyPair(mavenTypes.getKeyPair());
        packageTypeConfig.put(POM_REPOSITORY_REFERENCES_CLEANUP_POLICY.getKey(),
                mavenTypes.getPomCleanupPolicy().getType());
    }

    private static void populateVirtualDebianDescriptor(DebTypeSpecificConfigModel type,
            VirtualRepoConfig virtualRepoConfig,
            Map<String, String> packageTypeConfig) {
        VirtualCacheConfig virtualCacheConfig;
        virtualCacheConfig = virtualRepoConfig.getRepoTypeConfig().getVirtualCacheConfig();
        virtualCacheConfig = virtualCacheConfig != null ? virtualCacheConfig : new VirtualCacheConfig();
        virtualCacheConfig.setVirtualRetrievalCachePeriodSecs(type.getVirtualRetrievalCachePeriodSecs());
        virtualRepoConfig.getRepoTypeConfig().setVirtualCacheConfig(virtualCacheConfig);

        packageTypeConfig.put(DEBIAN_DEFAULT_ARCHITECTURES.getKey(),
                String.join(",", type.getVirtualArchitectures()));
        packageTypeConfig.put(DEBIAN_OPTIONAL_INDEX_COMPRESSION_FORMATS.getKey(),
                JsonUtils.getInstance().writeValueAsString(type.getOptionalIndexCompressionFormats()));
        packageTypeConfig.put(DDEB_SUPPORTED.getKey(), String.valueOf(type.isDdebSupported()));
    }

    private static void populateVirtualChefDescriptor(ChefTypeSpecificConfigModel type,
            VirtualRepoConfig virtualRepoConfig) {
        VirtualCacheConfig virtualCacheConfig;
        virtualCacheConfig = virtualRepoConfig.getRepoTypeConfig().getVirtualCacheConfig();
        virtualCacheConfig = virtualCacheConfig != null ? virtualCacheConfig : new VirtualCacheConfig();
        virtualCacheConfig.setVirtualRetrievalCachePeriodSecs(type.getVirtualRetrievalCachePeriodSecs());
        virtualRepoConfig.getRepoTypeConfig().setVirtualCacheConfig(virtualCacheConfig);
    }

    private static void populateVirtualCondaDescriptor(CondaTypeSpecificConfigModel type,
            VirtualRepoConfig virtualRepoConfig) {
        VirtualCacheConfig virtualCacheConfig;
        virtualCacheConfig = virtualRepoConfig.getRepoTypeConfig().getVirtualCacheConfig();
        virtualCacheConfig = virtualCacheConfig != null ? virtualCacheConfig : new VirtualCacheConfig();
        virtualCacheConfig.setVirtualRetrievalCachePeriodSecs(type.getVirtualRetrievalCachePeriodSecs());
        virtualRepoConfig.getRepoTypeConfig().setVirtualCacheConfig(virtualCacheConfig);
    }

    private static void populateVirtualConanDescriptor(ConanTypeSpecificConfigModel type,
            VirtualRepoConfig virtualRepoConfig,
            Map<String, String> packageTypeConfig) {
        VirtualCacheConfig virtualCacheConfig;
        virtualCacheConfig = virtualRepoConfig.getRepoTypeConfig().getVirtualCacheConfig();
        packageTypeConfig.put(FORCE_CONAN_AUTHENTICATION.getKey(),
                String.valueOf(type.getForceConanAuthentication()));
        virtualCacheConfig = virtualCacheConfig != null ? virtualCacheConfig : new VirtualCacheConfig();
        virtualCacheConfig.setVirtualRetrievalCachePeriodSecs(type.getVirtualRetrievalCachePeriodSecs());
        virtualRepoConfig.getRepoTypeConfig().setVirtualCacheConfig(virtualCacheConfig);
    }

    private static void populateVirtualCranDescriptor(CranTypeSpecificConfigModel type,
            VirtualRepoConfig virtualRepoConfig) {
        VirtualCacheConfig virtualCacheConfig;
        virtualCacheConfig = virtualRepoConfig.getRepoTypeConfig().getVirtualCacheConfig();
        virtualCacheConfig = virtualCacheConfig != null ? virtualCacheConfig : new VirtualCacheConfig();
        virtualCacheConfig.setVirtualRetrievalCachePeriodSecs(type.getVirtualRetrievalCachePeriodSecs());
        virtualRepoConfig.getRepoTypeConfig().setVirtualCacheConfig(virtualCacheConfig);
    }

    private static void populateVirtualCargoDescriptor(CargoTypeSpecificConfigModel type,
            VirtualRepoConfig virtualRepoConfig,
            Map<String, String> packageTypeConfig) {
        VirtualCacheConfig virtualCacheConfig;
        virtualCacheConfig = virtualRepoConfig.getRepoTypeConfig().getVirtualCacheConfig();
        virtualCacheConfig = virtualCacheConfig != null ? virtualCacheConfig : new VirtualCacheConfig();
        virtualCacheConfig.setVirtualRetrievalCachePeriodSecs(type.getVirtualRetrievalCachePeriodSecs());
        virtualRepoConfig.getRepoTypeConfig().setVirtualCacheConfig(virtualCacheConfig);
        packageTypeConfig.put(CARGO_INTERNAL_INDEX.getKey(), Boolean.TRUE.toString());
    }

    private static void populateVirtualAlpineDescriptor(AlpineTypeSpecificConfigModel type,
            VirtualRepoConfig virtualRepoConfig) {
        VirtualCacheConfig virtualCacheConfig;
        virtualCacheConfig = virtualRepoConfig.getRepoTypeConfig().getVirtualCacheConfig();
        virtualCacheConfig = virtualCacheConfig != null ? virtualCacheConfig : new VirtualCacheConfig();
        virtualCacheConfig.setVirtualRetrievalCachePeriodSecs(type.getVirtualRetrievalCachePeriodSecs());
        virtualRepoConfig.getRepoTypeConfig().setVirtualCacheConfig(virtualCacheConfig);
    }

    private static void populateVirtualNixDescriptor(NixTypeSpecificConfigModel type,
            VirtualRepoConfig virtualRepoConfig) {
        VirtualCacheConfig virtualCacheConfig;
        virtualCacheConfig = virtualRepoConfig.getRepoTypeConfig().getVirtualCacheConfig();
        virtualCacheConfig = virtualCacheConfig != null ? virtualCacheConfig : new VirtualCacheConfig();
        virtualCacheConfig.setVirtualRetrievalCachePeriodSecs(type.getVirtualRetrievalCachePeriodSecs());
        virtualRepoConfig.getRepoTypeConfig().setVirtualCacheConfig(virtualCacheConfig);
    }

    private static void populateVirtualHelmDescriptor(HelmTypeSpecificConfigModel type,
            VirtualRepoConfig virtualRepoConfig,
            Map<String, String> packageTypeConfig) {
        VirtualCacheConfig virtualCacheConfig;
        virtualCacheConfig = virtualRepoConfig.getRepoTypeConfig().getVirtualCacheConfig();
        virtualCacheConfig = virtualCacheConfig != null ? virtualCacheConfig : new VirtualCacheConfig();
        virtualCacheConfig.setVirtualRetrievalCachePeriodSecs(type.getVirtualRetrievalCachePeriodSecs());
        virtualRepoConfig.getRepoTypeConfig().setVirtualCacheConfig(virtualCacheConfig);

        packageTypeConfig.put(USE_NAMESPACES.getKey(), String.valueOf(type.getUseNamespaces()));
    }

    private static void populateVirtualYumDescriptor(YumTypeSpecificConfigModel type,
            VirtualRepoConfig virtualRepoConfig) {
        VirtualCacheConfig virtualCacheConfig;
        virtualCacheConfig = virtualRepoConfig.getRepoTypeConfig().getVirtualCacheConfig();
        virtualCacheConfig = virtualCacheConfig != null ? virtualCacheConfig : new VirtualCacheConfig();
        virtualCacheConfig.setVirtualRetrievalCachePeriodSecs(type.getVirtualRetrievalCachePeriodSecs());
        virtualRepoConfig.getRepoTypeConfig().setVirtualCacheConfig(virtualCacheConfig);
    }

    private static void populateVirtualGoDescriptor(GoTypeSpecificConfigModel type,
            Map<String, String> packageTypeConfig) {
        packageTypeConfig.put(EXTERNAL_DEPENDENCIES_ENABLED.getKey(),
                String.valueOf(type.getEnableExternalDependencies()));
        packageTypeConfig.put(EXTERNAL_DEPENDENCIES_PATTERNS.getKey(),
                JsonUtils.getInstance().writeValueAsString(type.getExternalPatterns()));
        packageTypeConfig.put(EXTERNAL_DEPENDENCIES_REMOTE_REPO_REF.getKey(), null); // Not used by go
    }

    private void populateVirtualNpmDescriptor(NpmTypeSpecificConfigModel type, VirtualRepoConfig virtualRepoConfig,
            Map<String, String> packageTypeConfig) {
        VirtualCacheConfig virtualCacheConfig;
        RemoteRepoConfig npmRemoteRepoConfig =
                repositoryConfigService.getRemoteRepoConfigByKey(type.getExternalRemoteRepo());
        populateExternalDependencies(packageTypeConfig, type.getEnableExternalDependencies(),
                type.getExternalPatterns(), npmRemoteRepoConfig);
        virtualCacheConfig = virtualRepoConfig.getRepoTypeConfig().getVirtualCacheConfig();
        virtualCacheConfig = virtualCacheConfig != null ? virtualCacheConfig : new VirtualCacheConfig();
        virtualCacheConfig.setVirtualRetrievalCachePeriodSecs(type.getVirtualRetrievalCachePeriodSecs());
        virtualRepoConfig.getRepoTypeConfig().setVirtualCacheConfig(virtualCacheConfig);
    }

    private static void populateExternalDependencies(Map<String, String> packageTypeConfig, Boolean type,
            List<String> type1, RemoteRepoConfig remoteRepoConfig) {
        packageTypeConfig.put(EXTERNAL_DEPENDENCIES_ENABLED.getKey(),
                String.valueOf(type));
        packageTypeConfig.put(EXTERNAL_DEPENDENCIES_PATTERNS.getKey(),
                JsonUtils.getInstance().writeValueAsString(type1));
        Optional.ofNullable(remoteRepoConfig).ifPresent(repoConfig ->
                packageTypeConfig.put(EXTERNAL_DEPENDENCIES_REMOTE_REPO_REF.getKey(),
                        repoConfig.getKey()));
    }

    private void populateVirtualBowerDescriptor(BowerTypeSpecificConfigModel type,
            Map<String, String> packageTypeConfig) {
        RemoteRepoConfig bowerRemoteRepoConfig =
                repositoryConfigService.getRemoteRepoConfigByKey(type.getExternalRemoteRepo());
        populateExternalDependencies(packageTypeConfig, type.getEnableExternalDependencies(),
                type.getExternalPatterns(), bowerRemoteRepoConfig);
    }

    private void buildAndSetNugetConfig(RemoteRepoConfig remoteRepoConfig, NugetTypeSpecificConfigModel nuGet) {
        Map<String, String> packageTypeConfig = remoteRepoConfig.getPackageTypeConfig();
        packageTypeConfig.put(DOWNLOAD_CONTEXT_PATH.getKey(), nuGet.getDownloadContextPath());
        packageTypeConfig.put(FEED_CONTEXT_PATH.getKey(), nuGet.getFeedContextPath());
        packageTypeConfig.put(V3_FEED_URL.getKey(), nuGet.getV3FeedUrl());
        packageTypeConfig.put(SYMBOL_SERVER_URL.getKey(), nuGet.getSymbolServerUrl());
        boolean enableGlobalNormalization = centralConfig.getDescriptor()
                .getRepositoriesGeneralConfig().getNuget().isEnableGlobalNormalization();
        packageTypeConfig.put(ENABLE_NUGET_NORMALIZED_VERSION.getKey(),
                String.valueOf(enableGlobalNormalization || nuGet.isEnableNormalizedVersion()));
    }

    private void buildAndSetAnsibleConfig(RemoteRepoConfig remoteRepoConfig, AnsibleTypeSpecificConfigModel ansible) {
        if (StringUtils.isNotBlank(ansible.getAuthUrl())) {
            if (StringUtils.isNotBlank(remoteRepoConfig.getRepoTypeConfig().getUsername())) {
                throw new IllegalArgumentException(
                        "Ansible remote repository cannot have both authUrl and username configured");
            }
            if (StringUtils.isBlank(remoteRepoConfig.getRepoTypeConfig().getPassword())) {
                throw new IllegalArgumentException(
                        "Ansible remote repository with authUrl configured must also have a password configured");
            }
        }
        Map<String, String> packageTypeConfig = remoteRepoConfig.getPackageTypeConfig();
        packageTypeConfig.put(AUTH_URL.getKey(),
                StringUtils.defaultString(ansible.getAuthUrl()));
    }

    private void buildAndSetBowerConfig(RemoteRepoConfig remoteRepoConfig, BowerTypeSpecificConfigModel bower) {
        buildAndSetVcsConfig(remoteRepoConfig, bower);
        remoteRepoConfig.getPackageTypeConfig().put(BOWER_REGISTRY_URL.getKey(), bower.getRegistryUrl());
    }

    private void buildAndSetVcsConfig(RemoteRepoConfig remoteRepoConfig, VcsTypeSpecificConfigModel vcs) {
        Map<String, String> packageTypeConfig = remoteRepoConfig.getPackageTypeConfig();
        packageTypeConfig.put(VCS_GIT_PROVIDER.getKey(), vcs.getGitProvider().name());
        packageTypeConfig.put(VCS_GIT_DOWNLOAD_URL.getKey(), vcs.getDownloadUrl());
        packageTypeConfig.put(VCS_TYPE.getKey(), vcs.getVcsType().name());
        remoteRepoConfig.getRepoTypeConfig().setMaxUniqueSnapshots(vcs.getMaxUniqueSnapshots());
        remoteRepoConfig.getRepoTypeConfig().setListRemoteFolderItems(vcs.isListRemoteFolderItems());
    }

    private void buildAndSetGoConfig(RemoteRepoConfig descriptor, GoTypeSpecificConfigModel go) {
        buildAndSetVcsConfig(descriptor, go);

        // Go type-specific config
        descriptor.getPackageTypeConfig()
                .put(GITLAB_RESOLVE_SUBGROUPS.getKey(), String.valueOf(go.getGitLabResolveSubgroups()));
    }

    private void buildAndSetPyPIConfig(RemoteRepoConfig remoteRepoConfig, PypiTypeSpecificConfigModel pyPI) {
        Map<String, String> packageTypeConfig = remoteRepoConfig.getPackageTypeConfig();
        packageTypeConfig.put(PYPI_REGISTRY_URL.getKey(), pyPI.getRegistryUrl());
        packageTypeConfig.put(PYPI_REPOSITORY_SUFFIX.getKey(), pyPI.getRepositorySuffix());
    }

    private void buildAndSetComposerConfig(RemoteRepoConfig remoteRepoConfig,
            ComposerTypeSpecificConfigModel composer) {
        buildAndSetVcsConfig(remoteRepoConfig, composer);
        remoteRepoConfig.getPackageTypeConfig().put(COMPOSER_REGISTRY_URL.getKey(), composer.getRegistryUrl());
    }

    private void buildAndSetTerraformRemoteConfig(RemoteRepoConfig remoteRepoConfig,
            TerraformTypeSpecificConfigModel terraform) {
        buildAndSetVcsConfig(remoteRepoConfig, terraform);

        Map<String, String> packageTypeConfig = remoteRepoConfig.getPackageTypeConfig();
        packageTypeConfig.put(TERRAFORM_REGISTRY_URL.getKey(), terraform.getRegistryUrl());
        packageTypeConfig.put(TERRAFORM_PROVIDERS_URL.getKey(), terraform.getProvidersUrl());
        populateExternalDependencies(packageTypeConfig, terraform.getEnableExternalDependencies(),
                terraform.getExternalPatterns(), null);
    }

    private void buildAndSetPodsConfig(RemoteRepoConfig remoteRepoConfig, CocoaPodsTypeSpecificConfigModel pods) {
        buildAndSetVcsConfig(remoteRepoConfig, pods);
        Map<String, String> packageTypeConfig = remoteRepoConfig.getPackageTypeConfig();

        boolean isSmartRepo = remoteRepoConfig.getRepoTypeConfig().getContentSynchronisation().isEnabled();
        packageTypeConfig.put(COCOA_PODS_SPECS_URL.getKey(), isSmartRepo ? StringUtils.EMPTY : pods.getSpecsRepoUrl());
        //Specs repo provider must be Artifactory or the same as the remote's Git provider - from UI it should always
        // match in any case, users can change it from the descriptor manually.
        packageTypeConfig.put(SPECS_REPO_DOWNLOAD_PROVIDER.getKey(),
                isSmartRepo ? VcsGitProvider.ARTIFACTORY.name() : pods.getGitProvider().name());
        packageTypeConfig.put(COCOA_PODS_CDN_URL.getKey(), isSmartRepo ? StringUtils.EMPTY : pods.getSpecsCdnUrl());

        if (isSmartRepo) {
            packageTypeConfig.put(VCS_TYPE.getKey(), VcsType.GIT.name());
            packageTypeConfig.put(VCS_GIT_PROVIDER.getKey(), VcsGitProvider.ARTIFACTORY.name());
        }
    }

    private void buildAndSetVirtualP2Config(VirtualRepoConfig virtualRepoConfig, P2TypeSpecificConfigModel p2) {
        List<String> p2Repos = p2.getP2Repos().stream()
                .map(P2Repo::getRepoUrl)
                .distinct()
                .toList();

        //This just adds the selected urls - creating repos etc is done at the service
        Map<String, String> packageTypeConfig = virtualRepoConfig.getPackageTypeConfig();
        packageTypeConfig.put(P2_URLS.getKey(), JsonUtils.getInstance().writeValueAsString(p2Repos));
        packageTypeConfig.put(FORCE_P2_AUTHENTICATION.getKey(), String.valueOf(p2.isForceP2Authentication()));
    }

    private void buildAndSetXrayConfig(RealRepoConfig realRepoConfig, LocalBasicRepositoryConfigModel basic) {
        XrayRepoConfig xrayConfig = null;
        XrayRepoConfigModel xrayConfigModel = basic.getXrayConfig();
        if (xrayConfigModel != null) {
            xrayConfig = new XrayRepoConfig();
            xrayConfig.setEnabled(xrayConfigModel.isEnabled());
            xrayConfig.setDataTtl(xrayConfigModel.getDataTtl());
        }
        realRepoConfig.getSecurityConfig().setXrayConfig(xrayConfig);
    }

    /**
     * Reason for entire model: currently only one field - 'enabled', but will expand in the near future
     */
    private void buildAndSetDownloadRedirectConfig(RealRepoConfig localConfig,
            LocalAdvancedRepositoryConfigModel advanced) {
        DownloadRedirectRepoConfig downloadRedirectConfig = null;
        DownloadRedirectRepoConfigModel downloadRedirectConfigModel = advanced.getDownloadRedirectConfig();
        if (downloadRedirectConfigModel != null && !AolUtils.isAol()) {
            downloadRedirectConfig = new DownloadRedirectRepoConfig();
            downloadRedirectConfig.setEnabled(downloadRedirectConfigModel.isEnabled());
        }
        localConfig.getRepoTypeConfig().setDownloadRedirectConfig(downloadRedirectConfig);
    }

    private void buildAndSetCdnRedirectConfig(RealRepoConfig realRepoConfig,
            RepositoryConfigModel<?, ?, ?> model) {
        CdnRedirectRepoConfigModel cdnRedirectConfigModel = model.getAdvanced().getCdnRedirectRepoConfig();
        if (cdnRedirectConfigModel != null) {
            CdnRedirectRepoConfig cdnRedirectConfig = new CdnRedirectRepoConfig();
            cdnRedirectConfig.setEnabled(cdnRedirectConfigModel.isEnabled());
            realRepoConfig.getRepoTypeConfig().setCdnRedirectRepoConfig(cdnRedirectConfig);
        }
    }

    private void buildAndSetHelmConfig(RemoteRepoConfig remoteRepoConfig, HelmTypeSpecificConfigModel helm) {
        Map<String, String> packageTypeConfig = remoteRepoConfig.getPackageTypeConfig();
        packageTypeConfig.put(CHARTS_BASE_URL.getKey(), helm.getChartsBaseUrl());
        packageTypeConfig.put(EXTERNAL_DEPENDENCIES_ENABLED.getKey(),
                String.valueOf(helm.getEnableExternalDependencies()));
        packageTypeConfig.put(EXTERNAL_DEPENDENCIES_PATTERNS.getKey(),
                JsonUtils.getInstance().writeValueAsString(helm.getExternalPatterns()));
        packageTypeConfig.put(EXTERNAL_DEPENDENCIES_REMOTE_REPO_REF.getKey(), null);
    }

    private void buildAndSetCargoConfig(RemoteRepoConfig remoteRepoConfig, CargoTypeSpecificConfigModel cargo) {
        Map<String, String> packageTypeConfig = remoteRepoConfig.getPackageTypeConfig();
        packageTypeConfig.put(GIT_REGISTRY_URL.getKey(), cargo.getRegistryUrl());
        packageTypeConfig.put(CARGO_ANONYMOUS_ACCESS.getKey(), String.valueOf(cargo.isAnonymousAccess()));
        packageTypeConfig.put(CARGO_INTERNAL_INDEX.getKey(), String.valueOf(cargo.isInternalIndex()));
    }


    private PropertySet getPropSetByName(String name) {
        return centralConfig.getDescriptor().getPropertySets().stream()
                .filter(propertySet -> propertySet.getName().equals(name))
                .findFirst()
                .orElse(null);
    }
}
