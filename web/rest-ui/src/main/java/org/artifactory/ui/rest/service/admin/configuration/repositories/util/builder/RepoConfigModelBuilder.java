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

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import jakarta.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.p2.P2Repo;
import org.artifactory.addon.webstart.ArtifactWebstartAddon;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.config.RepositoryConfigService;
import org.artifactory.descriptor.config.CentralConfigDescriptor;
import org.artifactory.descriptor.property.PropertySet;
import org.artifactory.descriptor.replication.LocalReplicationDescriptor;
import org.artifactory.descriptor.replication.RemoteReplicationDescriptor;
import org.artifactory.descriptor.repo.ProxyDescriptor;
import org.artifactory.descriptor.repo.RepoDescriptor;
import org.artifactory.descriptor.repo.RepoLayout;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.descriptor.repo.ReverseProxyDescriptor;
import org.artifactory.descriptor.repo.ReverseProxyRepoConfig;
import org.artifactory.model.FederatedRepoConfig;
import org.artifactory.model.FederationConfig;
import org.artifactory.model.LocalConfig;
import org.artifactory.model.ProxyRepoConfig;
import org.artifactory.model.RealRepoConfig;
import org.artifactory.model.ReleaseBundleRepoConfig;
import org.artifactory.model.RemoteRepoConfig;
import org.artifactory.model.RepoConfig;
import org.artifactory.model.VirtualRepoConfig;
import org.artifactory.model.XrayRepoConfig;
import org.artifactory.projects.ProjectsService;
import org.artifactory.repo.CdnRedirectRepoConfig;
import org.artifactory.repo.DownloadRedirectRepoConfig;
import org.artifactory.repo.FederatedMemberConfiguration;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.RepoPathFactory;
import org.artifactory.repo.federated.FederatedMember;
import org.artifactory.repo.packagetype.DockerApiVersion;
import org.artifactory.repo.packagetype.ExternalDependenciesConfig;
import org.artifactory.repo.packagetype.configuration.AnsibleConfiguration;
import org.artifactory.repo.packagetype.configuration.BowerConfiguration;
import org.artifactory.repo.packagetype.configuration.CargoConfiguration;
import org.artifactory.repo.packagetype.configuration.CocoaPodsConfiguration;
import org.artifactory.repo.packagetype.configuration.ComposerConfiguration;
import org.artifactory.repo.packagetype.configuration.ConanConfiguration;
import org.artifactory.repo.packagetype.configuration.DebianConfiguration;
import org.artifactory.repo.packagetype.configuration.DockerConfiguration;
import org.artifactory.repo.packagetype.configuration.GitConfiguration;
import org.artifactory.repo.packagetype.configuration.GoConfiguration;
import org.artifactory.repo.packagetype.configuration.HelmConfiguration;
import org.artifactory.repo.packagetype.configuration.HexConfiguration;
import org.artifactory.repo.packagetype.configuration.HuggingFaceMLConfiguration;
import org.artifactory.repo.packagetype.configuration.MavenConfiguration;
import org.artifactory.repo.packagetype.configuration.NpmConfiguration;
import org.artifactory.repo.packagetype.configuration.NuGetConfiguration;
import org.artifactory.repo.packagetype.configuration.P2Configuration;
import org.artifactory.repo.packagetype.configuration.PypiConfiguration;
import org.artifactory.repo.packagetype.configuration.TerraformBackendConfiguration;
import org.artifactory.repo.packagetype.configuration.TerraformConfiguration;
import org.artifactory.repo.packagetype.configuration.VcsConfiguration;
import org.artifactory.repo.packagetype.configuration.VcsGitConfiguration;
import org.artifactory.repo.packagetype.configuration.YumConfiguration;
import org.artifactory.repo.virtual.VirtualRepositoryConfigResolver;
import org.artifactory.ui.rest.model.admin.configuration.propertysets.PropertySetNameModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.AdvancedRepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.BasicRepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.CdnRedirectRepoConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.DownloadRedirectRepoConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.GeneralRepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.ProxyAwareConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.federation.FederatedRepoAdvancedRepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.federation.FederatedRepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.federation.FederationConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.federation.MirrorMemberConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.local.LocalAdvancedRepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.local.LocalBasicRepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.local.LocalRepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.releasebundles.ReleaseBundlesAdvancedRepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.releasebundles.ReleaseBundlesBasicRepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.releasebundles.ReleaseBundlesRepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.remote.RemoteAdvancedRepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.remote.RemoteBasicRepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.remote.RemoteCacheRepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.remote.RemoteNetworkRepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.remote.RemoteRepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.replication.local.LocalReplicationConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.replication.remote.RemoteReplicationConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.AIEditorExtensionsTypeSpecificConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.AgentPluginsTypeSpecificConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.AgentPackagesTypeSpecificConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.AlpineTypeSpecificConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.AnsibleTypeSpecificConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.BazelModulesTypeSpecificConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.BowerTypeSpecificConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.BuildInfoTypeSpecificConfigModel;
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
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.GradleTypeSpecificConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.HelmOciTypeSpecificConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.HelmTypeSpecificConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.HexTypeSpecificConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.HuggingFaceMLTypeSpecificConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.IvyTypeSpecificConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.JemTypeSpecificConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.JetBrainsPluginsTypeSpecificConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.LuaRocksTypeSpecificConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.MLTypeSpecificConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.MavenTypeSpecificConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.NimModelTypeSpecificConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.NixTypeSpecificConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.NpmTypeSpecificConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.NugetTypeSpecificConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.OciTypeSpecificConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.OpkgTypeSpecificConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.P2TypeSpecificConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.PubTypeSpecificConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.PuppetTypeSpecificConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.PypiTypeSpecificConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.ReleaseBundlesRepoTypeSpecificConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.SbtTypeSpecificConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.SkillsTypeSpecificConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.SwiftTypeSpecificConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.TerraformBackendTypeSpecificConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.TerraformTypeSpecificConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.TypeSpecificConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.VagrantTypeSpecificConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.VcsTypeSpecificConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.YumTypeSpecificConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.virtual.VirtualAdvancedRepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.virtual.VirtualBasicRepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.virtual.VirtualRepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.virtual.VirtualSelectedRepository;
import org.artifactory.ui.rest.model.admin.configuration.repository.xray.XrayRepoConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.reverseProxy.ReverseProxyRepoModel;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.nodes.RepositoryType;
import org.artifactory.util.AnsiblePackageTypeConfigMapper;
import org.artifactory.util.BowerPackageTypeConfigMapper;
import org.artifactory.util.CargoPackageTypeConfigMapper;
import org.artifactory.util.CocoaPodsPackageTypeConfigMapper;
import org.artifactory.util.CollectionUtils;
import org.artifactory.util.ComposerPackageTypeConfigMapper;
import org.artifactory.util.ConanPackageTypeConfigMapper;
import org.artifactory.util.DebianPackageTypeConfigMapper;
import org.artifactory.util.DockerPackageTypeConfigMapper;
import org.artifactory.util.ExternalDependenciesPackageTypeConfigMapper;
import org.artifactory.util.GoPackageTypeConfigMapper;
import org.artifactory.util.HelmPackageTypeConfigMapper;
import org.artifactory.util.HexPackageTypeConfigMapper;
import org.artifactory.util.HuggingFaceMLPackageTypeConfigMapper;
import org.artifactory.util.MavenPackageTypeConfigMapper;
import org.artifactory.util.NpmPackageTypeConfigMapper;
import org.artifactory.util.NuGetPackageTypeConfigMapper;
import org.artifactory.util.P2PackageTypeConfigMapper;
import org.artifactory.util.PypiPackageTypeConfigMapper;
import org.artifactory.util.TerraformBackendPackageTypeConfigMapper;
import org.artifactory.util.TerraformPackageTypeConfigMapper;
import org.artifactory.util.VcsPackageTypeConfigMapper;
import org.artifactory.util.YumPackageTypeConfigMapper;
import org.jfrog.common.config.validation.NotUnicode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

import static org.artifactory.descriptor.repo.RepoType.Docker;
import static org.artifactory.descriptor.repo.RepoType.OCI;
import static org.artifactory.descriptor.repo.RepoType.ReleaseBundles;
import static org.artifactory.util.FederatedMemberUtils.resolveUrl;
import static org.artifactory.util.PackageTypeConfigFields.ENABLE_NUGET_NORMALIZED_VERSION;
import static org.artifactory.util.PackageTypeConfigFields.FORCE_CONAN_AUTHENTICATION;
import static org.artifactory.util.PackageTypeConfigFields.FORCE_MAVEN_AUTHENTICATION;

/**
 * Utility class for converting descriptor to model
 *
 * @author Aviad Shikloshi
 */
@Slf4j
@Component
public class RepoConfigModelBuilder {
    private VirtualRepositoryConfigResolver resolver;
    private RepositoryConfigService repositoryConfigService;
    private CentralConfigService centralConfig;
    private AddonsManager addonsManager;
    private ProjectsService projectsService;

    @Autowired
    public void setProjectsService(ProjectsService projectsService) {
        this.projectsService = projectsService;
    }

    @Autowired
    public void setResolver(VirtualRepositoryConfigResolver resolver) {
        this.resolver = resolver;
    }

    @Autowired
    public void setRepositoryConfigService(RepositoryConfigService repositoryConfigService) {
        this.repositoryConfigService = repositoryConfigService;
    }

    @Autowired
    public void setCentralConfig(CentralConfigService centralConfig) {
        this.centralConfig = centralConfig;
    }

    @Autowired
    public void setAddonsManager(AddonsManager addonsManager) {
        this.addonsManager = addonsManager;
    }

    /**
     * Populate model configuration from local repository config
     */
    public void populateLocalDescriptorValuesToModel(
            LocalConfig localConfig, LocalRepositoryConfigModel model) {
        GeneralRepositoryConfigModel general = createGeneralConfig(localConfig);
        LocalBasicRepositoryConfigModel basic = createLocalBasicConfig(localConfig);
        LocalAdvancedRepositoryConfigModel advanced = createLocalAdvancedConfig(localConfig);
        TypeSpecificConfigModel typeSpecific =
                createLocalTypeSpecific(localConfig.getPackageType(), localConfig);
        model.setGeneral(general);
        model.setBasic(basic);
        model.setAdvanced(advanced);
        model.setTypeSpecific(typeSpecific);

        //the same behaviour as release bundle v1
        if (localConfig.getPackageType() != ReleaseBundles) {
            List<LocalReplicationDescriptor> replicationDescriptors = centralConfig.getDescriptor()
                    .getMultiLocalReplications(localConfig.getKey());
            List<LocalReplicationConfigModel> replications = replicationDescriptors.stream()
                    .map(this::createLocalReplicationConfig)
                    .toList();
            model.setReplications(replications);
        }
    }

    /**
     * Populate model configuration from federated repository config
     */
    public void populateFederatedDescriptorValuesToModel(FederatedRepoConfig federatedRepoConfig,
            FederatedRepositoryConfigModel model) {
        GeneralRepositoryConfigModel general = createGeneralConfig(federatedRepoConfig);
        LocalBasicRepositoryConfigModel basic = createLocalBasicConfig(federatedRepoConfig);
        FederatedRepoAdvancedRepositoryConfigModel advanced = createFederatedRepoAdvancedConfig(federatedRepoConfig);
        TypeSpecificConfigModel typeSpecific =
                createLocalTypeSpecific(federatedRepoConfig.getPackageType(), federatedRepoConfig);
        FederationConfigModel federationConfigModel = createFederatedConfig(federatedRepoConfig);

        model.setFederation(federationConfigModel);
        model.setGeneral(general);
        model.setBasic(basic);
        model.setAdvanced(advanced);
        model.setTypeSpecific(typeSpecific);
    }

    public FederationConfigModel createFederatedConfig(FederatedRepoConfig federatedRepoConfig) {
        FederationConfigModel federationConfigModel = new FederationConfigModel();
        List<FederatedMember> members = federatedRepoConfig.getFederatedMembers();
        if (members == null) {
            return federationConfigModel;
        }
        List<MirrorMemberConfigModel> mirrorMemberConfigModels = members.stream()
                .map(federatedMember -> new FederatedMemberConfiguration(resolveUrl(federatedMember),
                        federatedMember.getMode(), federatedMember.isEnabled()))
                .map(this::toMemberConfigModel)
                .toList();
        federationConfigModel.setMirrors(mirrorMemberConfigModels);
        return federationConfigModel;
    }

    public FederationConfigModel createFederationConfigFromBaseConfig(RepoConfig repoConfig) {
        FederationConfig federationConfig = repoConfig.getBaseConfig().getFederationConfig();
        if (federationConfig == null) {
            return null;
        }
        List<MirrorMemberConfigModel> mirrorMemberConfigModels =
                federationConfig.getMembers().stream()
                        .map(federatedMember -> new FederatedMemberConfiguration(resolveUrl(federatedMember),
                                federatedMember.getMode(), federatedMember.isEnabled()))
                        .map(this::toMemberConfigModel)
                        .toList();
        if (mirrorMemberConfigModels.isEmpty()) {
            return null;
        }
        FederationConfigModel federationConfigModel = new FederationConfigModel();
        federationConfigModel.setMirrors(mirrorMemberConfigModels);
        return federationConfigModel;
    }

    private MirrorMemberConfigModel toMemberConfigModel(FederatedMemberConfiguration federatedMemberConfiguration) {
        MirrorMemberConfigModel memberConfigModel = new MirrorMemberConfigModel();
        memberConfigModel.setUrl(federatedMemberConfiguration.getUrl());
        memberConfigModel.setMode(federatedMemberConfiguration.getMode());
        memberConfigModel.setEnabled(federatedMemberConfiguration.getEnabled());
        return memberConfigModel;
    }

    /**
     * Populate model configuration from remote repository config
     */
    public void populateRemoteRepositoryConfigValuesToModel(RemoteRepoConfig remoteRepoConfig,
            RemoteRepositoryConfigModel model) {
        GeneralRepositoryConfigModel general = createGeneralConfig(remoteRepoConfig);
        model.setGeneral(general);

        RemoteBasicRepositoryConfigModel basic = createRemoteBasicConfig(remoteRepoConfig);
        model.setBasic(basic);

        RemoteReplicationDescriptor replicationDescriptor = centralConfig.getDescriptor().getRemoteReplication(
                remoteRepoConfig.getKey());
        if (replicationDescriptor != null) {
            RemoteReplicationConfigModel replication = createRemoteReplicationConfigModel(replicationDescriptor);
            model.setReplications(Lists.newArrayList(replication));
        }
        RemoteAdvancedRepositoryConfigModel advanced = createRemoteAdvancedConfig(remoteRepoConfig);
        model.setAdvanced(advanced);
        TypeSpecificConfigModel typeSpecific =
                createRemoteTypeSpecific(remoteRepoConfig.getPackageType(), remoteRepoConfig);
        model.setTypeSpecific(typeSpecific);
    }

    /**
     * Populate model configuration from virtual repository config
     */
    public void populateVirtualRepositoryConfigValuesToModel(VirtualRepoConfig virtualRepoConfig,
            VirtualRepositoryConfigModel model) {
        GeneralRepositoryConfigModel general = createGeneralConfig(virtualRepoConfig);
        model.setGeneral(general);

        VirtualBasicRepositoryConfigModel basic = createVirtualBasicConfig(virtualRepoConfig);
        model.setBasic(basic);

        VirtualAdvancedRepositoryConfigModel advanced = createVirtualAdvancedConfig(virtualRepoConfig);
        model.setAdvanced(advanced);

        TypeSpecificConfigModel typeSpecific =
                createVirtualTypeSpecific(virtualRepoConfig.getPackageType(), virtualRepoConfig);
        model.setTypeSpecific(typeSpecific);
    }

    private VirtualAdvancedRepositoryConfigModel createVirtualAdvancedConfig(VirtualRepoConfig virtualRepoConfig) {
        VirtualAdvancedRepositoryConfigModel advanced = new VirtualAdvancedRepositoryConfigModel();
        advanced.setRetrieveRemoteArtifacts(
                virtualRepoConfig.getRepoTypeConfig().isArtifactoryRequestsCanRetrieveRemoteArtifacts());
        addReverseProxyConfig(advanced, virtualRepoConfig.getKey());
        advanced.setHideUnauthorizedResources(virtualRepoConfig.getSecurityConfig().isHideUnauthorizedResources());
        return advanced;
    }

    private VirtualBasicRepositoryConfigModel createVirtualBasicConfig(VirtualRepoConfig virtualRepoConfig) {
        VirtualBasicRepositoryConfigModel basic = new VirtualBasicRepositoryConfigModel();
        Optional.ofNullable(virtualRepoConfig.getBaseConfig().getRepoLayoutRef())
                .ifPresent(basic::setLayout);
        Optional.ofNullable(virtualRepoConfig.getSecurityConfig().getPrimaryKeyPairRef())
                .ifPresent(primaryKeyRef -> {
                    basic.setKeypair(primaryKeyRef);
                    basic.setPrimaryKeyPair(primaryKeyRef);
                });
        Optional.ofNullable(virtualRepoConfig.getSecurityConfig().getSecondaryKeyPairRef())
                .ifPresent(basic::setSecondaryKeyPair);
        basic.setPublicDescription(virtualRepoConfig.getBaseConfig().getDescription());
        basic.setInternalDescription(virtualRepoConfig.getBaseConfig().getNotes());
        basic.setExcludesPattern(virtualRepoConfig.getBaseConfig().getExcludesPattern());
        basic.setIncludesPattern(virtualRepoConfig.getBaseConfig().getIncludesPattern());
        basic.setResolvedRepositories(
                resolver.getOrderedRepos(virtualRepoConfig.getKey()).stream()
                        .map(VirtualSelectedRepository::new).toList());
        basic.setSelectedRepositories(
                repositoryConfigService.getRepoConfigsByKeysInSpecificOrder(virtualRepoConfig).stream()
                        .map(VirtualSelectedRepository::new).toList());
        Optional.ofNullable(virtualRepoConfig.getRepoTypeConfig().getDefaultDeploymentRepoRef())
                .ifPresent(basic::setDefaultDeploymentRepo);
        String projectKey = projectsService.getProjectKeyOfRepository(virtualRepoConfig.getKey());
        Set<RepoDescriptor> ineligibleRepositories =
                projectsService.getIneligibleRepos(virtualRepoConfig.getKey(), projectKey);
        basic.setIneligibleRepositories(ineligibleRepositories.stream()
                .map(ineligibleRepo -> new VirtualSelectedRepository(ineligibleRepo.getKey(),
                        RepositoryType.byConfig(ineligibleRepo).getTypeNameLoweCase()))
                .collect(Collectors.toSet()));
        return basic;
    }

    /**
     * Populate model configuration from local repository config
     */
    public void populateReleaseBundlesDescriptorValuesToModel(ReleaseBundleRepoConfig releaseBundleRepoConfig,
            ReleaseBundlesRepositoryConfigModel model) {
        GeneralRepositoryConfigModel general = createGeneralConfig(releaseBundleRepoConfig);
        ReleaseBundlesBasicRepositoryConfigModel basic = createReleaseBundlesBasicConfig(releaseBundleRepoConfig);
        ReleaseBundlesAdvancedRepositoryConfigModel advanced =
                createReleaseBundlesAdvancedConfig(releaseBundleRepoConfig);
        ReleaseBundlesRepoTypeSpecificConfigModel typeSpecific = new ReleaseBundlesRepoTypeSpecificConfigModel();
        model.setGeneral(general);
        model.setBasic(basic);
        model.setAdvanced(advanced);
        model.setTypeSpecific(typeSpecific);
    }

    private LocalReplicationConfigModel createLocalReplicationConfig(LocalReplicationDescriptor replicationDescriptor) {
        LocalReplicationConfigModel replication = new LocalReplicationConfigModel();
        replication.setUrl(replicationDescriptor.getUrl());
        replication.setCronExp(replicationDescriptor.getCronExp());
        replication.setEnableEventReplication(replicationDescriptor.isEnableEventReplication());
        replication.setEnabled(replicationDescriptor.isEnabled());
        replication.setDisableProxy(replicationDescriptor.isDisableProxy());
        if (!replicationDescriptor.isDisableProxy()) {
            ProxyDescriptor proxyDescriptor = replicationDescriptor.getProxy();
            if (proxyDescriptor != null) {
                replication.setProxy(proxyDescriptor.getKey());
            }
        }
        replication.setSocketTimeout(replicationDescriptor.getSocketTimeoutMillis());
        replication.setSyncDeletes(replicationDescriptor.isSyncDeletes());
        replication.setSyncProperties(replicationDescriptor.isSyncProperties());
        replication.setSyncStatistics(replicationDescriptor.isSyncStatistics());
        replication.setCheckBinaryExistenceInFilestore(replicationDescriptor.isCheckBinaryExistenceInFilestore());
        replication.setUsername(replicationDescriptor.getUsername());
        replication.setPassword(replicationDescriptor.getPassword());
        replication.setIncludePathPrefixPattern(replicationDescriptor.getIncludePathPrefixPattern());
        replication.setExcludePathPrefixPattern(replicationDescriptor.getExcludePathPrefixPattern());
        return replication;
    }

    private RemoteReplicationConfigModel createRemoteReplicationConfigModel(
            RemoteReplicationDescriptor replicationDescriptor) {
        RemoteReplicationConfigModel replication = new RemoteReplicationConfigModel();
        replication.setEnabled(replicationDescriptor.isEnabled());
        replication.setCronExp(replicationDescriptor.getCronExp());
        replication.setSyncDeletes(replicationDescriptor.isSyncDeletes());
        replication.setSyncProperties(replicationDescriptor.isSyncProperties());
        replication.setEnableEventReplication(replicationDescriptor.isEnableEventReplication());
        replication.setIncludePathPrefixPattern(replicationDescriptor.getIncludePathPrefixPattern());
        replication.setExcludePathPrefixPattern(replicationDescriptor.getExcludePathPrefixPattern());
        return replication;
    }

    private GeneralRepositoryConfigModel createGeneralConfig(RepoConfig localConfig) {
        GeneralRepositoryConfigModel general = new GeneralRepositoryConfigModel();
        general.setRepoKey(localConfig.getKey());
        return general;
    }

    private LocalBasicRepositoryConfigModel createLocalBasicConfig(LocalConfig localConfig) {
        LocalBasicRepositoryConfigModel basic = new LocalBasicRepositoryConfigModel();
        addSharedBasicConfigModel(basic, localConfig);
        basic.setXrayConfig(getXrayConfig(localConfig.getSecurityConfig().getXrayConfig()));
        return basic;
    }

    private RemoteBasicRepositoryConfigModel createRemoteBasicConfig(RemoteRepoConfig remoteRepoConfig) {
        RemoteBasicRepositoryConfigModel basic = new RemoteBasicRepositoryConfigModel();
        addSharedBasicConfigModel(basic, remoteRepoConfig);
        basic.setUrl(remoteRepoConfig.getRepoTypeConfig().getUrl());
        basic.setXrayConfig(getXrayConfig(remoteRepoConfig.getSecurityConfig().getXrayConfig()));
        basic.setContentSynchronisation(remoteRepoConfig.getRepoTypeConfig().getContentSynchronisation());
        String remoteRepoLayoutRef = remoteRepoConfig.getRepoTypeConfig().getRemoteRepoLayoutRef();
        RepoLayout remoteRepoLayout = centralConfig.getDescriptor().getRepoLayout(remoteRepoLayoutRef);
        if (remoteRepoLayout != null) {
            basic.setRemoteLayoutMapping(remoteRepoLayout.getName());
        }
        basic.setOffline(remoteRepoConfig.getRepoTypeConfig().isOffline());
        basic.setSendContext(remoteRepoConfig.getRepoTypeConfig().isSendContext());
        basic.setPassThrough(remoteRepoConfig.getRepoTypeConfig().isPassThrough());
        basic.setCurated(remoteRepoConfig.getRepoTypeConfig().isCurated());
        return basic;
    }

    private ReleaseBundlesBasicRepositoryConfigModel createReleaseBundlesBasicConfig(
            ReleaseBundleRepoConfig descriptor) {
        ReleaseBundlesBasicRepositoryConfigModel basic = new ReleaseBundlesBasicRepositoryConfigModel();
        addSharedBasicConfigModel(basic, descriptor);
        return basic;
    }

    private void addSharedBasicConfigModel(BasicRepositoryConfigModel basic, RepoConfig repoConfig) {
        basic.setPublicDescription(repoConfig.getBaseConfig().getDescription());
        basic.setInternalDescription(repoConfig.getBaseConfig().getNotes());
        basic.setIncludesPattern(repoConfig.getBaseConfig().getIncludesPattern());
        basic.setExcludesPattern(repoConfig.getBaseConfig().getExcludesPattern());
        Optional.ofNullable(repoConfig.getBaseConfig().getRepoLayoutRef()).ifPresent(basic::setLayout);
        Optional.ofNullable(repoConfig.getSecurityConfig().getPrimaryKeyPairRef())
                .ifPresent(primaryKeyPairRef -> {
                    basic.setPrimaryKeyPair(primaryKeyPairRef);
                    basic.setKeypair(primaryKeyPairRef);
                });
        Optional.ofNullable(repoConfig.getSecurityConfig().getSecondaryKeyPairRef())
                .ifPresent(basic::setSecondaryKeyPair);
    }

    private LocalAdvancedRepositoryConfigModel createLocalAdvancedConfig(LocalConfig localConfig) {
        LocalAdvancedRepositoryConfigModel advanced = new LocalAdvancedRepositoryConfigModel();
        if (localConfig.getPackageType() == ReleaseBundles) {
            //The same behaviour as release bundle v1.
            addSharedAdvancedConfigModel(advanced, localConfig);
        } else {
            populateRepoAdvancedConfig(localConfig, advanced);
        }
        return advanced;
    }

    private void populateRepoAdvancedConfig(LocalConfig localConfig,
            LocalAdvancedRepositoryConfigModel advanced) {
        addSharedAdvancedConfigModel(advanced, localConfig);
        advanced.setDownloadRedirectConfig(
                getDownloadRedirectConfig(localConfig.getRepoTypeConfig().getDownloadRedirectConfig()));

        if (localConfig.getRepoTypeConfig().getCdnRedirectRepoConfig() != null) {
            final CdnRedirectRepoConfigModel cdnRedirectRepoConfigModel = new CdnRedirectRepoConfigModel();
            cdnRedirectRepoConfigModel.setEnabled(
                    localConfig.getRepoTypeConfig().getCdnRedirectRepoConfig().isEnabled());
            advanced.setCdnRedirectRepoConfig(cdnRedirectRepoConfigModel);
        }
        advanced.setPriorityResolution(localConfig.getRepoTypeConfig().isPriorityResolution());
    }

    private FederatedRepoAdvancedRepositoryConfigModel createFederatedRepoAdvancedConfig(
            FederatedRepoConfig federatedRepoConfig) {
        FederatedRepoAdvancedRepositoryConfigModel advanced = new FederatedRepoAdvancedRepositoryConfigModel();
        populateRepoAdvancedConfig(federatedRepoConfig, advanced);
        populateProxyValues(federatedRepoConfig, advanced);
        return advanced;
    }


    private RemoteAdvancedRepositoryConfigModel createRemoteAdvancedConfig(RemoteRepoConfig remoteRepoConfig) {
        RemoteAdvancedRepositoryConfigModel advanced = new RemoteAdvancedRepositoryConfigModel();
        addSharedAdvancedConfigModel(advanced, remoteRepoConfig);
        advanced.setDownloadRedirectConfig(
                getDownloadRedirectConfig(remoteRepoConfig.getRepoTypeConfig().getDownloadRedirectConfig()));
        advanced.setPriorityResolution(remoteRepoConfig.getRepoTypeConfig().isPriorityResolution());
        advanced.setSignedUrlTtl(remoteRepoConfig.getSecurityConfig().getSignedUrlTtl());
        RemoteNetworkRepositoryConfigModel networkModel = createNetworkConfig(remoteRepoConfig);
        advanced.setNetwork(networkModel);
        RemoteCacheRepositoryConfigModel cacheConfig = createCacheConfig(remoteRepoConfig);
        advanced.setCache(cacheConfig);
        advanced.setStoreArtifactsLocally(remoteRepoConfig.getRepoTypeConfig().isStoreArtifactsLocally());
        advanced.setSynchronizeArtifactProperties(remoteRepoConfig.getRepoTypeConfig().isSynchronizeProperties());
        advanced.setHardFail(remoteRepoConfig.getRepoTypeConfig().isHardFail());
        advanced.setQueryParams(remoteRepoConfig.getRepoTypeConfig().getQueryParams());
        advanced.setShareConfiguration(remoteRepoConfig.getRepoTypeConfig().isShareConfiguration());
        advanced.setBlockMismatchingMimeTypes(remoteRepoConfig.getRepoTypeConfig().isBlockMismatchingMimeTypes());
        advanced.setBypassHeadRequests(remoteRepoConfig.getRepoTypeConfig().isBypassHeadRequests());
        advanced.setDisableUrlNormalization(remoteRepoConfig.getRepoTypeConfig().isDisableUrlNormalization());
        advanced.setPropagateQueryParams(remoteRepoConfig.getRepoTypeConfig().isPropagateQueryParams());
        createRemoteCdnRedirectConfig(remoteRepoConfig, advanced);
        advanced.setRetrieveSha256FromServer(remoteRepoConfig.getRepoTypeConfig().isRetrieveSha256FromServer());
        if (StringUtils.isNotBlank(remoteRepoConfig.getRepoTypeConfig().getMismatchingMimeTypesOverrideList())) {
            advanced.setMismatchingMimeTypesOverrideList(
                    List.of(remoteRepoConfig.getRepoTypeConfig().getMismatchingMimeTypesOverrideList().split(",")));
        }
        return advanced;
    }

    private static void createRemoteCdnRedirectConfig(RemoteRepoConfig remoteRepoConfig,
            RemoteAdvancedRepositoryConfigModel advanced) {
        CdnRedirectRepoConfig cdnRedirectRepoConfig = remoteRepoConfig.getRepoTypeConfig().getCdnRedirectRepoConfig();
        if (cdnRedirectRepoConfig != null) {
            CdnRedirectRepoConfigModel cdnRedirectRepoConfigModel = new CdnRedirectRepoConfigModel();
            cdnRedirectRepoConfigModel.setEnabled(cdnRedirectRepoConfig.isEnabled());
            advanced.setCdnRedirectRepoConfig(cdnRedirectRepoConfigModel);
        }
    }

    private ReleaseBundlesAdvancedRepositoryConfigModel createReleaseBundlesAdvancedConfig(
            ReleaseBundleRepoConfig releaseBundleRepoConfig) {
        ReleaseBundlesAdvancedRepositoryConfigModel advanced = new ReleaseBundlesAdvancedRepositoryConfigModel();
        addSharedAdvancedConfigModel(advanced, releaseBundleRepoConfig);
        return advanced;
    }

    private void addSharedAdvancedConfigModel(AdvancedRepositoryConfigModel advanced,
            RealRepoConfig realRepoConfig) {
        advanced.setAllowContentBrowsing(realRepoConfig.getRepoTypeConfig().isArchiveBrowsingEnabled());
        advanced.setBlackedOut(realRepoConfig.getRepoTypeConfig().isBlackedOut());
        advanced.setSignedUrlTtl(realRepoConfig.getSecurityConfig().getSignedUrlTtl());
        List<@NotUnicode String> propertySetRefs = realRepoConfig.getRepoTypeConfig().getPropertySetRefs();
        List<PropertySet> propertySetsList = centralConfig.getDescriptor().getPropertySets().stream()
                .filter(propertySet -> propertySetRefs.contains(propertySet.getName())).toList();
        List<PropertySetNameModel> propertySetNameModelList = collectPropertySets(propertySetsList);
        advanced.setPropertySets(propertySetNameModelList);
        addReverseProxyConfig(advanced, realRepoConfig.getKey());
    }

    RemoteCacheRepositoryConfigModel createCacheConfig(RemoteRepoConfig remoteRepoConfig) {
        RemoteCacheRepositoryConfigModel cacheConfig = new RemoteCacheRepositoryConfigModel();
        cacheConfig.setKeepUnusedArtifactsHours(
                remoteRepoConfig.getRepoTypeConfig().getUnusedArtifactsCleanupPeriodHours());
        cacheConfig.setRetrievalCachePeriodSecs(remoteRepoConfig.getRepoTypeConfig().getRetrievalCachePeriodSecs());
        cacheConfig.setAssumedOfflineLimitSecs(remoteRepoConfig.getRepoTypeConfig().getAssumedOfflinePeriodSecs());
        cacheConfig.setMissedRetrievalCachePeriodSecs(
                remoteRepoConfig.getRepoTypeConfig().getMissedRetrievalCachePeriodSecs());
        cacheConfig.setMetadataRetrievalTimeoutSecs(
                remoteRepoConfig.getRepoTypeConfig().getMetadataRetrievalTimeoutSecs());
        return cacheConfig;
    }

    private RemoteNetworkRepositoryConfigModel createNetworkConfig(RemoteRepoConfig remoteRepoConfig) {
        RemoteNetworkRepositoryConfigModel networkModel = new RemoteNetworkRepositoryConfigModel();
        populateProxyValues(remoteRepoConfig, networkModel);
        networkModel.setLocalAddress(remoteRepoConfig.getRepoTypeConfig().getLocalAddress());
        networkModel.setUsername(remoteRepoConfig.getRepoTypeConfig().getUsername());
        networkModel.setPassword(remoteRepoConfig.getRepoTypeConfig().getPassword());
        networkModel.setSocketTimeout(remoteRepoConfig.getRepoTypeConfig().getSocketTimeoutMillis());
        networkModel.setLenientHostAuth(remoteRepoConfig.getRepoTypeConfig().isAllowAnyHostAuth());
        networkModel.setCookieManagement(remoteRepoConfig.getRepoTypeConfig().isEnableCookieManagement());
        networkModel.setSyncProperties(remoteRepoConfig.getRepoTypeConfig().isSynchronizeProperties());
        String clientTleCertificate = remoteRepoConfig.getRepoTypeConfig().getClientTlsCertificate();
        if (StringUtils.isNotBlank(clientTleCertificate) &&
                clientTleCertificate.contains(ArtifactWebstartAddon.SSL_CERT_ALIAS_PREFIX)) {
            clientTleCertificate = clientTleCertificate.substring(ArtifactWebstartAddon.SSL_CERT_ALIAS_PREFIX.length());
        }
        networkModel.setSelectedInstalledCertificate(clientTleCertificate);
        ArtifactWebstartAddon webstartAddon = addonsManager.addonByType(ArtifactWebstartAddon.class);
        List<String> certAliases = webstartAddon.getSslCertNames();
        if (CollectionUtils.notNullOrEmpty(certAliases)) {
            networkModel.setInstalledCertificatesList(certAliases);
        }
        return networkModel;
    }

    private void populateProxyValues(ProxyRepoConfig proxyRepoConfig, ProxyAwareConfigModel model) {
        boolean isDisabledProxy = proxyRepoConfig.isProxyDisabled();
        model.setDisableProxy(isDisabledProxy);
        if (isDisabledProxy) {
            return;
        }
        String proxyRef = proxyRepoConfig.getProxyRef();
        if (StringUtils.isBlank(proxyRef)) {
            ProxyDescriptor defaultProxy = centralConfig.getDescriptor().getDefaultProxy();
            if (defaultProxy != null) {
                model.setProxy(defaultProxy.getKey());
            }
        } else if (centralConfig.getDescriptor().getProxy(proxyRef) != null) {
            model.setProxy(proxyRef);
        } else {
            log.debug("Repository '{}' contains a reference to proxy '{}' which does not exist on the config " +
                    "descriptor", proxyRepoConfig.getKey(), proxyRef);
        }
    }

    private TypeSpecificConfigModel createLocalTypeSpecific(RepoType type, LocalConfig localConfig) {
        MavenTypeSpecificConfigModel mavenModel = null;
        TypeSpecificConfigModel model = null;
        switch (type) {
            case ReleaseBundles -> model = new ReleaseBundlesRepoTypeSpecificConfigModel();
            case Bower -> model = new BowerTypeSpecificConfigModel();
            case Cargo -> model = createCargoLocalTypeSpecific(localConfig);
            case Chef -> model = new ChefTypeSpecificConfigModel();
            case CocoaPods -> model = new CocoaPodsTypeSpecificConfigModel();
            case Composer -> model = createComposerLocalTypeSpecific(localConfig);
            case Conan -> model = createConanLocalTypeSpecific(localConfig);
            case Docker, OCI, HelmOCI -> model = createOciOrDockerLocalTypeSpecific(type, localConfig);
            case Helm -> model = createLocalHelmTypeSpecific(localConfig);
            case Alpine -> model = new AlpineTypeSpecificConfigModel();
            case Go -> model = new GoTypeSpecificConfigModel();
            case CRAN -> model = new CranTypeSpecificConfigModel();
            case Conda -> model = new CondaTypeSpecificConfigModel();
            case NuGet -> model = createNugetLocalTypeSpecific(localConfig);
            case Npm -> model = new NpmTypeSpecificConfigModel();
            case Pypi -> model = new PypiTypeSpecificConfigModel();
            case Puppet -> model = new PuppetTypeSpecificConfigModel();
            case Vagrant -> model = new VagrantTypeSpecificConfigModel();
            case GitLfs -> model = new GitLfsTypeSpecificConfigModel();
            case Debian -> model = createDebianLocalTypeSpecific(localConfig);
            case Opkg -> model = new OpkgTypeSpecificConfigModel();
            case YUM -> model = createYumLocalTypeSpecific(localConfig);
            case Gems -> model = new GemsTypeSpecificConfigModel();
            case Generic -> model = new GenericTypeSpecificConfigModel();
            case Maven -> mavenModel = new MavenTypeSpecificConfigModel();
            case Gradle -> mavenModel = new GradleTypeSpecificConfigModel();
            case Ivy -> mavenModel = new IvyTypeSpecificConfigModel();
            case Pub -> model = new PubTypeSpecificConfigModel();
            case Swift -> model = new SwiftTypeSpecificConfigModel();
            case SBT -> mavenModel = new SbtTypeSpecificConfigModel();
            case Terraform -> model = createTerraformLocalTypeSpecific(localConfig);
            case TerraformBackend -> model = createTerraformBackendLocalTypeSpecific(localConfig);
            case HuggingFaceML -> model = createLocalHuggingFaceTypeSpecific(localConfig);
            case MachineLearning -> model = new MLTypeSpecificConfigModel();
            case Ansible -> model = new AnsibleTypeSpecificConfigModel();
            case Hex -> model = new HexTypeSpecificConfigModel();
            case BuildInfo -> model = new BuildInfoTypeSpecificConfigModel();
            case Jem -> model = new JemTypeSpecificConfigModel();
            case Nix -> model = new NixTypeSpecificConfigModel();
            case LuaRocks -> model = new LuaRocksTypeSpecificConfigModel();
            case Skills -> model = new SkillsTypeSpecificConfigModel();
            case AgentPlugins -> model = new AgentPluginsTypeSpecificConfigModel();
            case AgentPackages -> model = new AgentPackagesTypeSpecificConfigModel();
            default -> log.trace("No local type specific set for {}", type);
        }
        if (model != null) {
            return model;
        }
        // We will get here only if our model is maven / gradle / ivy / sbt and we populate the values
        populateMavenLocalValues(mavenModel, localConfig);
        return mavenModel;
    }

    @Nonnull
    private static TypeSpecificConfigModel createTerraformLocalTypeSpecific(LocalConfig localConfig) {
        TypeSpecificConfigModel model;
        TerraformTypeSpecificConfigModel typeSpecificConfigModel = new TerraformTypeSpecificConfigModel();
        TerraformConfiguration terraformConfiguration =
                TerraformPackageTypeConfigMapper.INSTANCE.map(localConfig.getPackageTypeConfig());
        typeSpecificConfigModel.setTerraformType(terraformConfiguration.getTerraformType().getName());
        model = typeSpecificConfigModel;
        return model;
    }

    @Nonnull
    private static TypeSpecificConfigModel createTerraformBackendLocalTypeSpecific(LocalConfig localConfig) {
        TypeSpecificConfigModel model;
        TerraformBackendTypeSpecificConfigModel typeSpecificConfigModel = new TerraformBackendTypeSpecificConfigModel();
        TerraformBackendConfiguration terraformBackendConfiguration =
                TerraformBackendPackageTypeConfigMapper.INSTANCE.map(localConfig.getPackageTypeConfig());
        typeSpecificConfigModel.setEncryptStates(terraformBackendConfiguration.isEncryptStates());
        model = typeSpecificConfigModel;
        return model;
    }

    @Nonnull
    private static TypeSpecificConfigModel createYumLocalTypeSpecific(LocalConfig localConfig) {
        TypeSpecificConfigModel model;
        YumTypeSpecificConfigModel yumType = new YumTypeSpecificConfigModel();
        YumConfiguration yumConfiguration =
                YumPackageTypeConfigMapper.INSTANCE.map(localConfig.getPackageTypeConfig());
        yumType.setMetadataFolderDepth(yumConfiguration.getYumRootDepth());
        yumType.setGroupFileNames(yumConfiguration.getYumGroupFileNames());
        yumType.setAutoCalculateYumMetadata(yumConfiguration.isCalculateYumMetadata());
        yumType.setEnableFileListsIndexing(yumConfiguration.isEnableFileListsIndexing());
        model = yumType;
        return model;
    }

    @Nonnull
    private static TypeSpecificConfigModel createDebianLocalTypeSpecific(LocalConfig localConfig) {
        TypeSpecificConfigModel model;
        DebTypeSpecificConfigModel debType = new DebTypeSpecificConfigModel();
        DebianConfiguration debianConfiguration =
                DebianPackageTypeConfigMapper.INSTANCE.map(localConfig.getPackageTypeConfig());
        debType.setTrivialLayout(debianConfiguration.isDebianTrivialLayout());
        debType.setOptionalIndexCompressionFormats(debianConfiguration.getOptionalIndexCompressionFormats());
        debType.setDdebSupported(debianConfiguration.isDdebSupported());
        model = debType;
        return model;
    }

    @Nonnull
    private TypeSpecificConfigModel createNugetLocalTypeSpecific(LocalConfig localConfig) {
        TypeSpecificConfigModel model;
        NugetTypeSpecificConfigModel nugetType = new NugetTypeSpecificConfigModel();
        populateSharedNuGetValues(nugetType, localConfig);
        nugetType.setMaxUniqueSnapshots(localConfig.getRepoTypeConfig().getMaxUniqueSnapshots());
        populateEnbledNugetNormalizedVersion(localConfig, nugetType);

        model = nugetType;
        return model;
    }

    private void populateEnbledNugetNormalizedVersion(LocalConfig localConfig, NugetTypeSpecificConfigModel nugetType) {
        Optional.ofNullable(localConfig.getPackageTypeConfig())
                .map(config -> config.get(ENABLE_NUGET_NORMALIZED_VERSION.getKey()))
                .map(Boolean::parseBoolean)
                .ifPresent(nugetType::setEnableNormalizedVersion);
    }

    @Nonnull
    private TypeSpecificConfigModel createOciOrDockerLocalTypeSpecific(RepoType type, LocalConfig localConfig) {
        TypeSpecificConfigModel model;
        DockerTypeSpecificConfigModel dockerType = createDockerTypeSpecificConfigModel(type);
        DockerConfiguration dockerConfiguration =
                DockerPackageTypeConfigMapper.INSTANCE.map(localConfig.getPackageTypeConfig());
        DockerApiVersion dockerApiVersion = dockerConfiguration.getDockerApiVersion();
        if (dockerApiVersion != null) {
            dockerType.setDockerApiVersion(dockerApiVersion);
        }
        dockerType.setMaxUniqueTags(dockerConfiguration.getMaxUniqueTags());
        dockerType.setBlockPushingSchema1(dockerConfiguration.isBlockPushingSchema1());
        dockerType.setDockerTagRetention(dockerConfiguration.getDockerTagRetention());
        model = dockerType;
        return model;
    }

    @Nonnull
    private TypeSpecificConfigModel createConanLocalTypeSpecific(LocalConfig localConfig) {
        TypeSpecificConfigModel model;
        ConanTypeSpecificConfigModel conanType = new ConanTypeSpecificConfigModel();
        populateSharedConanValues(conanType, localConfig);
        model = conanType;
        return model;
    }

    @Nonnull
    private static TypeSpecificConfigModel createComposerLocalTypeSpecific(LocalConfig localConfig) {
        TypeSpecificConfigModel model;
        ComposerTypeSpecificConfigModel composer = new ComposerTypeSpecificConfigModel();
        ComposerConfiguration composerConfiguration =
                ComposerPackageTypeConfigMapper.INSTANCE.map(localConfig.getPackageTypeConfig());
        composer.setEnableComposerV1Indexing(composerConfiguration.isEnableComposerV1Indexing());
        model = composer;
        return model;
    }

    @Nonnull
    private static TypeSpecificConfigModel createCargoLocalTypeSpecific(LocalConfig localConfig) {
        TypeSpecificConfigModel model;
        CargoTypeSpecificConfigModel cargo = new CargoTypeSpecificConfigModel();
        CargoConfiguration cargoConfiguration =
                CargoPackageTypeConfigMapper.INSTANCE.map(localConfig.getPackageTypeConfig());
        cargo.setAnonymousAccess(cargoConfiguration.isCargoAnonymousAccess());
        cargo.setInternalIndex(cargoConfiguration.isCargoInternalIndex());
        model = cargo;
        return model;
    }

    TypeSpecificConfigModel createRemoteTypeSpecific(RepoType type, RemoteRepoConfig remoteRepoConfig) {
        MavenTypeSpecificConfigModel mavenModel = null;
        TypeSpecificConfigModel model = null;
        Map<String, String> packageTypeConfig = remoteRepoConfig.getPackageTypeConfig();
        switch (type) {
            case BazelModules -> model = new BazelModulesTypeSpecificConfigModel();
            case Nix -> model = new NixTypeSpecificConfigModel();
            case AgentPlugins -> model = new AgentPluginsTypeSpecificConfigModel();
            case Maven -> mavenModel = new MavenTypeSpecificConfigModel();
            case Gradle -> mavenModel = new GradleTypeSpecificConfigModel();
            case Ivy -> mavenModel = new IvyTypeSpecificConfigModel();
            case SBT -> mavenModel = new SbtTypeSpecificConfigModel();
            case P2 -> mavenModel = new P2TypeSpecificConfigModel();
            case Debian -> model = createDebianRemoteTypeSpecific(remoteRepoConfig, packageTypeConfig);
            case Opkg -> model = createOpkgRemoteTypeSpecific(remoteRepoConfig);
            case Docker, OCI, HelmOCI ->
                    model = createOciOrDockerRemoteTypeSpecific(type, remoteRepoConfig, packageTypeConfig);
            case NuGet -> model = createNugetRemoteTypeSpecific(remoteRepoConfig, packageTypeConfig);
            case Npm -> model = createNpmRemoteTypeSpecific(remoteRepoConfig);
            case Pypi -> model = createPypiRemoteTypeSpecific(remoteRepoConfig, packageTypeConfig);
            case Puppet -> model = createPuppetRemoteTypeSpecific(remoteRepoConfig);
            case Chef -> model = createChefRemoteTypeSpecific(remoteRepoConfig);
            case VCS -> model = createVcsRemoteTypeSpecific(remoteRepoConfig);
            case Bower -> model = createBowerRemoteTypeSpecific(remoteRepoConfig, packageTypeConfig);
            case Go -> model = createGoRemoteTypeSpecific(remoteRepoConfig);
            case CRAN -> model = new CranTypeSpecificConfigModel();
            case Conan -> model = createConanRemoteTypeSpecific(remoteRepoConfig, packageTypeConfig);
            case Conda -> model = createCondaRemoteTypeSpecific(remoteRepoConfig);
            case CocoaPods -> model = createPodsRemoteTypeSpecific(remoteRepoConfig, packageTypeConfig);
            case Gems -> model = createGemsRemoteTypeSpecific(remoteRepoConfig);
            case Generic -> model = createGenericRemoteTypeSpecific(remoteRepoConfig);
            case YUM -> model = createYumRemoteTypeSpecific(remoteRepoConfig);
            case GitLfs -> model = createLfsRemoteTypeSpecific(remoteRepoConfig);
            case Helm -> model = createHelmRemoteTypeSpecific(remoteRepoConfig, packageTypeConfig);
            case Alpine -> model = createAlpineRemoteTypeSpecific(remoteRepoConfig);
            case Composer -> model = createComposerRemoteTypeSpecific(remoteRepoConfig, packageTypeConfig);
            case Cargo -> model = createCargoRemoteTypeSpecific(packageTypeConfig);
            case Pub -> model = new PubTypeSpecificConfigModel();
            case Terraform -> model = createTerraformRemoteTypeSpecific(remoteRepoConfig, packageTypeConfig);
            case Swift -> model = createSwiftRemoteTypeSpecific(remoteRepoConfig, packageTypeConfig);
            case HuggingFaceML -> model = createHuggingFaceRemoteTypeSpecific(remoteRepoConfig, packageTypeConfig);
            case NimModel -> model = createNimModelRemoteTypeSpecific(remoteRepoConfig);
            case Ansible -> model = createAnsibleRemoteTypeSpecific(packageTypeConfig);
            case Hex -> model = createHexRemoteTypeSpecific(packageTypeConfig);
            case AIEditorExtensions -> model = createVSCodeRemoteTypeSpecific(remoteRepoConfig, packageTypeConfig);
            case JetBrainsPlugins -> model = new JetBrainsPluginsTypeSpecificConfigModel();
            case LuaRocks -> model = new LuaRocksTypeSpecificConfigModel();
            case Skills -> model = new SkillsTypeSpecificConfigModel();
            default -> log.trace("No type specific model set for {}", type);
        }
        if (model != null) {
            model.setEnableTokenAuthentication(remoteRepoConfig.getRepoTypeConfig().isEnableTokenAuthentication());
            return model;
        }
        populateMavenRemoteValues(mavenModel, remoteRepoConfig);
        mavenModel.setEnableTokenAuthentication(remoteRepoConfig.getRepoTypeConfig().isEnableTokenAuthentication());
        return mavenModel;
    }

    @Nonnull
    private static TypeSpecificConfigModel createHuggingFaceRemoteTypeSpecific(RemoteRepoConfig remoteRepoConfig,
            Map<String, String> packageTypeConfig) {
        HuggingFaceMLTypeSpecificConfigModel hfType = new HuggingFaceMLTypeSpecificConfigModel();
        hfType.setListRemoteFolderItems(remoteRepoConfig.getRepoTypeConfig().isListRemoteFolderItems());

        if (!packageTypeConfig.isEmpty()) {
            final HuggingFaceMLConfiguration huggingFaceConfiguration =
                    HuggingFaceMLPackageTypeConfigMapper.INSTANCE.map(packageTypeConfig);
            hfType.setMlRepoLayout(huggingFaceConfiguration.isMlRepoLayout());
        }
        return hfType;
    }

    @Nonnull
    private static TypeSpecificConfigModel createNimModelRemoteTypeSpecific(RemoteRepoConfig remoteRepoConfig) {
        TypeSpecificConfigModel model;
        NimModelTypeSpecificConfigModel nimModelType = new NimModelTypeSpecificConfigModel();
        nimModelType.setListRemoteFolderItems(remoteRepoConfig.getRepoTypeConfig().isListRemoteFolderItems());
        model = nimModelType;
        return model;
    }

    @Nonnull
    private static TypeSpecificConfigModel createAlpineRemoteTypeSpecific(RemoteRepoConfig remoteRepoConfig) {
        AlpineTypeSpecificConfigModel alpineType = new AlpineTypeSpecificConfigModel();
        alpineType.setListRemoteFolderItems(
                remoteRepoConfig.getRepoTypeConfig().isListRemoteFolderItems());
        return alpineType;
    }

    @Nonnull
    private static TypeSpecificConfigModel createSwiftRemoteTypeSpecific(RemoteRepoConfig remoteRepoConfig,
            Map<String, String> packageTypeConfig) {
        TypeSpecificConfigModel model;
        SwiftTypeSpecificConfigModel swiftType = new SwiftTypeSpecificConfigModel();
        ExternalDependenciesConfig externalDependencies =
                ExternalDependenciesPackageTypeConfigMapper.INSTANCE.map(
                        packageTypeConfig);
        if (externalDependencies != null) {
            swiftType.setEnableExternalDependencies(externalDependencies.isEnabled());
            swiftType.setExternalPatterns(externalDependencies.getPatterns());
        }
        swiftType.setListRemoteFolderItems(remoteRepoConfig.getRepoTypeConfig().isListRemoteFolderItems());
        model = swiftType;
        return model;
    }

    @Nonnull
    private TypeSpecificConfigModel createTerraformRemoteTypeSpecific(RemoteRepoConfig remoteRepoConfig,
            Map<String, String> packageTypeConfig) {
        TypeSpecificConfigModel model;
        TerraformTypeSpecificConfigModel terraformTypeSpecific = new TerraformTypeSpecificConfigModel();
        populateVcsValues(terraformTypeSpecific, remoteRepoConfig);
        TerraformConfiguration terraformConfiguration =
                TerraformPackageTypeConfigMapper.INSTANCE.map(packageTypeConfig);
        if (terraformConfiguration != null) {
            terraformTypeSpecific.setRegistryUrl(terraformConfiguration.getTerraformRegistryUrl());
            terraformTypeSpecific.setProvidersUrl(terraformConfiguration.getTerraformProvidersUrl());

            ExternalDependenciesConfig tfExternalDependencies = terraformConfiguration.getExternalDependencies();
            if (tfExternalDependencies != null) {
                terraformTypeSpecific.setEnableExternalDependencies(tfExternalDependencies.isEnabled());
                terraformTypeSpecific.setExternalPatterns(tfExternalDependencies.getPatterns());
            }
        }
        model = terraformTypeSpecific;
        return model;
    }

    @Nonnull
    private static TypeSpecificConfigModel createCargoRemoteTypeSpecific(Map<String, String> packageTypeConfig) {
        TypeSpecificConfigModel model;
        CargoTypeSpecificConfigModel cargoType = new CargoTypeSpecificConfigModel();
        CargoConfiguration cargoConfiguration =
                CargoPackageTypeConfigMapper.INSTANCE.map(packageTypeConfig);
        GitConfiguration gitConfiguration = cargoConfiguration.getGit();
        if (gitConfiguration != null) {
            cargoType.setRegistryUrl(gitConfiguration.getRegistryUrl());
        }
        cargoType.setAnonymousAccess(cargoConfiguration.isCargoAnonymousAccess());
        cargoType.setInternalIndex(cargoConfiguration.isCargoInternalIndex());
        model = cargoType;
        return model;
    }

    @Nonnull
    private TypeSpecificConfigModel createComposerRemoteTypeSpecific(RemoteRepoConfig remoteRepoConfig,
            Map<String, String> packageTypeConfig) {
        TypeSpecificConfigModel model;
        ComposerTypeSpecificConfigModel composerType = new ComposerTypeSpecificConfigModel();
        populateVcsValues(composerType, remoteRepoConfig);
        ComposerConfiguration composerConfiguration =
                ComposerPackageTypeConfigMapper.INSTANCE.map(packageTypeConfig);
        if (composerConfiguration != null) {
            composerType.setRegistryUrl(composerConfiguration.getComposerRegistryUrl());
        }
        model = composerType;
        return model;
    }

    @Nonnull
    private static TypeSpecificConfigModel createHelmRemoteTypeSpecific(RemoteRepoConfig remoteRepoConfig,
            Map<String, String> packageTypeConfig) {
        TypeSpecificConfigModel model;
        HelmTypeSpecificConfigModel helmType = new HelmTypeSpecificConfigModel();
        helmType.setListRemoteFolderItems(remoteRepoConfig.getRepoTypeConfig().isListRemoteFolderItems());

        if (!packageTypeConfig.isEmpty()) {
            final HelmConfiguration helmConfiguration =
                    HelmPackageTypeConfigMapper.INSTANCE.map(packageTypeConfig);
            helmType.setChartsBaseUrl(helmConfiguration.getChartsBaseUrl());
            ExternalDependenciesConfig helmExternalDependencies = helmConfiguration.getExternalDependencies();
            if (helmExternalDependencies != null) {
                helmType.setEnableExternalDependencies(helmExternalDependencies.isEnabled());
                helmType.setExternalPatterns(helmExternalDependencies.getPatterns());
            }
        }
        model = helmType;
        return model;
    }

    @Nonnull
    private static TypeSpecificConfigModel createVSCodeRemoteTypeSpecific(RemoteRepoConfig remoteRepoConfig,
            Map<String, String> packageTypeConfig) {
        TypeSpecificConfigModel model;
        AIEditorExtensionsTypeSpecificConfigModel vscodeType = new AIEditorExtensionsTypeSpecificConfigModel();
        vscodeType.setListRemoteFolderItems(remoteRepoConfig.getRepoTypeConfig().isListRemoteFolderItems());
        ExternalDependenciesConfig externalDependencies =
                ExternalDependenciesPackageTypeConfigMapper.INSTANCE.map(packageTypeConfig);
        if (externalDependencies != null) {
            vscodeType.setEnableExternalDependencies(externalDependencies.isEnabled());
            vscodeType.setExternalPatterns(externalDependencies.getPatterns());
        }
        model = vscodeType;
        return model;
    }

    @Nonnull
    private static TypeSpecificConfigModel createLfsRemoteTypeSpecific(RemoteRepoConfig remoteRepoConfig) {
        TypeSpecificConfigModel model;
        GitLfsTypeSpecificConfigModel gitLfsType = new GitLfsTypeSpecificConfigModel();
        gitLfsType.setListRemoteFolderItems(remoteRepoConfig.getRepoTypeConfig().isListRemoteFolderItems());
        model = gitLfsType;
        return model;
    }

    @Nonnull
    private static TypeSpecificConfigModel createYumRemoteTypeSpecific(RemoteRepoConfig remoteRepoConfig) {
        TypeSpecificConfigModel model;
        YumTypeSpecificConfigModel yumType = new YumTypeSpecificConfigModel();
        yumType.setListRemoteFolderItems(remoteRepoConfig.getRepoTypeConfig().isListRemoteFolderItems());
        yumType.setAutoCalculateYumMetadata(null);
        yumType.setMetadataFolderDepth(null);
        yumType.setGroupFileNames(null);
        model = yumType;
        return model;
    }

    @Nonnull
    private static TypeSpecificConfigModel createGenericRemoteTypeSpecific(RemoteRepoConfig remoteRepoConfig) {
        TypeSpecificConfigModel model;
        GenericTypeSpecificConfigModel genericType = new GenericTypeSpecificConfigModel();
        genericType.setListRemoteFolderItems(remoteRepoConfig.getRepoTypeConfig().isListRemoteFolderItems());
        model = genericType;
        return model;
    }

    @Nonnull
    private static TypeSpecificConfigModel createGemsRemoteTypeSpecific(RemoteRepoConfig remoteRepoConfig) {
        TypeSpecificConfigModel model;
        GemsTypeSpecificConfigModel gemsType = new GemsTypeSpecificConfigModel();
        gemsType.setListRemoteFolderItems(remoteRepoConfig.getRepoTypeConfig().isListRemoteFolderItems());
        model = gemsType;
        return model;
    }

    @Nonnull
    private TypeSpecificConfigModel createPodsRemoteTypeSpecific(RemoteRepoConfig remoteRepoConfig,
            Map<String, String> packageTypeConfig) {
        TypeSpecificConfigModel model;
        CocoaPodsTypeSpecificConfigModel podsType = new CocoaPodsTypeSpecificConfigModel();
        populateVcsValues(podsType, remoteRepoConfig);
        CocoaPodsConfiguration podsConfiguration =
                CocoaPodsPackageTypeConfigMapper.INSTANCE.map(packageTypeConfig);
        ExternalDependenciesConfig podsExternalDependencies = podsConfiguration.getExternalDependencies();
        if (podsExternalDependencies != null) {
            podsType.setEnableExternalDependencies(podsExternalDependencies.isEnabled());
            podsType.setExternalPatterns(podsExternalDependencies.getPatterns());
        }
        podsType.setForceModifyIndex(podsConfiguration.isCocoaPodsForceModifyIndex());
        podsType.setSpecsRepoUrl(podsConfiguration.getCocoaPodsSpecsRepoUrl());
        podsType.setSpecsCdnUrl(podsConfiguration.getCocoaPodsCdnUrl());
        podsType.setSpecsRepoProvider(podsConfiguration.getSpecRepoProvider());
        model = podsType;
        return model;
    }

    @Nonnull
    private static TypeSpecificConfigModel createAnsibleRemoteTypeSpecific(Map<String, String> packageTypeConfig) {
        AnsibleTypeSpecificConfigModel ansibleType = new AnsibleTypeSpecificConfigModel();
        AnsibleConfiguration ansibleConfiguration = AnsiblePackageTypeConfigMapper.INSTANCE.map(packageTypeConfig);
        ansibleType.setAuthUrl(ansibleConfiguration.getAuthUrl());
        return ansibleType;
    }

    @Nonnull
    private TypeSpecificConfigModel createHexRemoteTypeSpecific(Map<String, String> packageTypeConfig) {
        TypeSpecificConfigModel model;
        HexTypeSpecificConfigModel hexType = new HexTypeSpecificConfigModel();
        HexConfiguration hexConfiguration = HexPackageTypeConfigMapper.INSTANCE.map(packageTypeConfig);
        hexType.setHexPublicKey(hexConfiguration.getHexPublicKey());
        hexType.setUrl(hexConfiguration.getHexRepositoryUrl());
        model = hexType;
        return model;
    }

    @Nonnull
    private static TypeSpecificConfigModel createConanRemoteTypeSpecific(RemoteRepoConfig remoteRepoConfig,
            Map<String, String> packageTypeConfig) {
        TypeSpecificConfigModel model;
        ConanTypeSpecificConfigModel conanType = new ConanTypeSpecificConfigModel();
        ConanConfiguration conanConfiguration =
                ConanPackageTypeConfigMapper.INSTANCE.map(packageTypeConfig);
        conanType.setListRemoteFolderItems(remoteRepoConfig.getRepoTypeConfig().isListRemoteFolderItems());
        conanType.setForceConanAuthentication(conanConfiguration.isForceConanAuthentication());
        model = conanType;
        return model;
    }

    @Nonnull
    private static TypeSpecificConfigModel createCondaRemoteTypeSpecific(RemoteRepoConfig remoteRepoConfig) {
        CondaTypeSpecificConfigModel condaType = new CondaTypeSpecificConfigModel();
        condaType.setListRemoteFolderItems(remoteRepoConfig.getRepoTypeConfig().isListRemoteFolderItems());
        return condaType;
    }

    @Nonnull
    private TypeSpecificConfigModel createGoRemoteTypeSpecific(RemoteRepoConfig remoteRepoConfig) {
        TypeSpecificConfigModel model;
        GoTypeSpecificConfigModel goType = new GoTypeSpecificConfigModel();
        populateVcsValues(goType, remoteRepoConfig);
        goType.setMissedRetrievalCachePeriodSecs(
                remoteRepoConfig.getRepoTypeConfig().getMissedRetrievalCachePeriodSecs());
        GoConfiguration goConfiguration =
                GoPackageTypeConfigMapper.INSTANCE.map(remoteRepoConfig.getPackageTypeConfig());
        if (goConfiguration != null) {
            goType.setGitLabResolveSubgroups(goConfiguration.isGitLabResolveSubgroups());
        }
        model = goType;
        return model;
    }

    @Nonnull
    private TypeSpecificConfigModel createBowerRemoteTypeSpecific(RemoteRepoConfig remoteRepoConfig,
            Map<String, String> packageTypeConfig) {
        TypeSpecificConfigModel model;
        BowerTypeSpecificConfigModel bowerType = new BowerTypeSpecificConfigModel();
        populateVcsValues(bowerType, remoteRepoConfig);
        BowerConfiguration bowerConfiguration =
                BowerPackageTypeConfigMapper.INSTANCE.map(packageTypeConfig);
        if (bowerConfiguration != null) {
            bowerType.setRegistryUrl(bowerConfiguration.getBowerRegistryUrl());
        }
        model = bowerType;
        return model;
    }

    @Nonnull
    private TypeSpecificConfigModel createVcsRemoteTypeSpecific(RemoteRepoConfig remoteRepoConfig) {
        TypeSpecificConfigModel model;
        VcsTypeSpecificConfigModel vcsType = new VcsTypeSpecificConfigModel();
        populateVcsValues(vcsType, remoteRepoConfig);
        model = vcsType;
        return model;
    }

    @Nonnull
    private static TypeSpecificConfigModel createChefRemoteTypeSpecific(RemoteRepoConfig remoteRepoConfig) {
        TypeSpecificConfigModel model;
        ChefTypeSpecificConfigModel chefType = new ChefTypeSpecificConfigModel();
        chefType.setListRemoteFolderItems(remoteRepoConfig.getRepoTypeConfig().isListRemoteFolderItems());
        model = chefType;
        return model;
    }

    @Nonnull
    private static TypeSpecificConfigModel createPuppetRemoteTypeSpecific(RemoteRepoConfig remoteRepoConfig) {
        TypeSpecificConfigModel model;
        PuppetTypeSpecificConfigModel puppetType = new PuppetTypeSpecificConfigModel();
        puppetType.setListRemoteFolderItems(remoteRepoConfig.getRepoTypeConfig().isListRemoteFolderItems());
        model = puppetType;
        return model;
    }

    @Nonnull
    private static TypeSpecificConfigModel createPypiRemoteTypeSpecific(RemoteRepoConfig remoteRepoConfig,
            Map<String, String> packageTypeConfig) {
        TypeSpecificConfigModel model;
        PypiTypeSpecificConfigModel pypiType = new PypiTypeSpecificConfigModel();
        pypiType.setListRemoteFolderItems(remoteRepoConfig.getRepoTypeConfig().isListRemoteFolderItems());
        PypiConfiguration pyPIConfiguration =
                PypiPackageTypeConfigMapper.INSTANCE.map(packageTypeConfig);
        if (pyPIConfiguration != null) {
            pypiType.setRegistryUrl(pyPIConfiguration.getPyPIRegistryUrl());
            pypiType.setRepositorySuffix(pyPIConfiguration.getRepositorySuffix());
        }
        model = pypiType;
        return model;
    }

    @Nonnull
    private static TypeSpecificConfigModel createNpmRemoteTypeSpecific(RemoteRepoConfig remoteRepoConfig) {
        TypeSpecificConfigModel model;
        NpmTypeSpecificConfigModel npmType = new NpmTypeSpecificConfigModel();
        npmType.setListRemoteFolderItems(remoteRepoConfig.getRepoTypeConfig().isListRemoteFolderItems());
        model = npmType;
        return model;
    }

    @Nonnull
    private TypeSpecificConfigModel createNugetRemoteTypeSpecific(RemoteRepoConfig remoteRepoConfig,
            Map<String, String> packageTypeConfig) {
        TypeSpecificConfigModel model;
        NugetTypeSpecificConfigModel nugetType = new NugetTypeSpecificConfigModel();
        populateSharedNuGetValues(nugetType, remoteRepoConfig);
        NuGetConfiguration nuGetConfiguration =
                NuGetPackageTypeConfigMapper.INSTANCE.map(packageTypeConfig);
        if (nuGetConfiguration != null) {
            nugetType.setDownloadContextPath(nuGetConfiguration.getDownloadContextPath());
            nugetType.setFeedContextPath(nuGetConfiguration.getFeedContextPath());
            nugetType.setV3FeedUrl(nuGetConfiguration.getV3FeedUrl());
            nugetType.setSymbolServerUrl(nuGetConfiguration.getSymbolServerUrl());
            nugetType.setEnableNormalizedVersion(nuGetConfiguration.isEnableNormalizedVersion());
        }

        nugetType.setListRemoteFolderItems(remoteRepoConfig.getRepoTypeConfig().isListRemoteFolderItems());
        model = nugetType;
        return model;
    }

    @Nonnull
    private TypeSpecificConfigModel createOciOrDockerRemoteTypeSpecific(RepoType type,
            RemoteRepoConfig remoteRepoConfig, Map<String, String> packageTypeConfig) {
        TypeSpecificConfigModel model;
        DockerTypeSpecificConfigModel dockerType = createDockerTypeSpecificConfigModel(type);
        dockerType.setEnableTokenAuthentication(
                remoteRepoConfig.getRepoTypeConfig().isEnableTokenAuthentication());
        dockerType.setListRemoteFolderItems(remoteRepoConfig.getRepoTypeConfig().isListRemoteFolderItems());
        dockerType.setRetrievalCachePeriodSecs(
                remoteRepoConfig.getRepoTypeConfig().getRetrievalCachePeriodSecs());
        DockerConfiguration dockerConfiguration =
                DockerPackageTypeConfigMapper.INSTANCE.map(packageTypeConfig);
        ExternalDependenciesConfig dockerExternalDependencies = dockerConfiguration.getExternalDependencies();
        if (dockerExternalDependencies != null) {
            dockerType.setEnableForeignLayersCaching(dockerExternalDependencies.isEnabled());
            dockerType.setExternalPatterns(dockerExternalDependencies.getPatterns());
        }
        dockerType.setBlockPushingSchema1(dockerConfiguration.isBlockPushingSchema1());
        dockerType.setDockerProjectId(dockerConfiguration.getDockerProjectId());
        model = dockerType;
        return model;
    }

    @Nonnull
    private static TypeSpecificConfigModel createOpkgRemoteTypeSpecific(RemoteRepoConfig remoteRepoConfig) {
        TypeSpecificConfigModel model;
        OpkgTypeSpecificConfigModel opkgType = new OpkgTypeSpecificConfigModel();
        opkgType.setListRemoteFolderItems(remoteRepoConfig.getRepoTypeConfig().isListRemoteFolderItems());
        model = opkgType;
        return model;
    }

    @Nonnull
    private static TypeSpecificConfigModel createDebianRemoteTypeSpecific(RemoteRepoConfig remoteRepoConfig,
            Map<String, String> packageTypeConfig) {
        TypeSpecificConfigModel model;
        DebTypeSpecificConfigModel debType = new DebTypeSpecificConfigModel();
        DebianConfiguration debianConfiguration =
                DebianPackageTypeConfigMapper.INSTANCE.map(packageTypeConfig);
        debType.setListRemoteFolderItems(remoteRepoConfig.getRepoTypeConfig().isListRemoteFolderItems());
        debType.setDdebSupported(debianConfiguration.isDdebSupported());
        model = debType;
        return model;
    }

    private TypeSpecificConfigModel createVirtualTypeSpecific(RepoType type, VirtualRepoConfig virtualRepoConfig) {
        TypeSpecificConfigModel typeSpecific = null;
        MavenTypeSpecificConfigModel mavenModel = null;
        switch (type) {
            case Maven -> mavenModel = createMavenVirtualTypeSpecific(virtualRepoConfig);
            case Chef -> typeSpecific = createChefVirtualTypeSpecific(virtualRepoConfig);
            case Gradle -> mavenModel = new GradleTypeSpecificConfigModel();
            case Go -> typeSpecific = createGoVirtualTypeSpecific(virtualRepoConfig);
            case CRAN -> typeSpecific = createCranVirtualTypeSpecific(virtualRepoConfig);
            case Conan -> typeSpecific = createConanVirtualTypeSpecific(virtualRepoConfig);
            case Conda -> typeSpecific = createCondaVirtualTypeSpecific(virtualRepoConfig);
            case Debian -> typeSpecific = createDebianVirtualTypeSpecific(virtualRepoConfig);
            case Helm -> typeSpecific = createHelmVirtualTypeSpecific(virtualRepoConfig);
            case Alpine -> typeSpecific = createAlpineVirtualTypeSpecific(virtualRepoConfig);
            case Ivy -> mavenModel = new IvyTypeSpecificConfigModel();
            case SBT -> mavenModel = new SbtTypeSpecificConfigModel();
            case P2 -> mavenModel = createP2VirtualTypeSpecific(virtualRepoConfig);
            case Gems -> typeSpecific = new GemsTypeSpecificConfigModel();
            case GitLfs -> typeSpecific = new GitLfsTypeSpecificConfigModel();
            case Npm -> typeSpecific = createNpmVirtualTypeSpecific(virtualRepoConfig);
            case Bower -> typeSpecific = createBowerVirtualTypeSpecific(virtualRepoConfig);
            case NuGet -> typeSpecific = createNugetVirtualTypeSpecific(virtualRepoConfig);
            case Pypi -> typeSpecific = new PypiTypeSpecificConfigModel();
            case Pub -> typeSpecific = new PubTypeSpecificConfigModel();
            case Swift -> typeSpecific = new SwiftTypeSpecificConfigModel();
            case Puppet -> typeSpecific = new PuppetTypeSpecificConfigModel();
            case Docker, OCI, HelmOCI -> typeSpecific = createOciOrDockerVirtualTypeSpecific(type, virtualRepoConfig);
            case YUM -> typeSpecific = createYumVirtualTypeSpecific(virtualRepoConfig);
            case Generic -> typeSpecific = new GenericTypeSpecificConfigModel();
            case Composer -> typeSpecific = createCompsoerVirtualTypeSpecific(virtualRepoConfig);
            case Terraform -> typeSpecific = new TerraformTypeSpecificConfigModel();
            case Cargo -> typeSpecific = createCargoVirtualTypeSpecific(virtualRepoConfig);
            case Ansible -> typeSpecific = new AnsibleTypeSpecificConfigModel();
            case CocoaPods -> typeSpecific = createCocoaPodsVirtualTypeSpecific(virtualRepoConfig);
            case Hex -> typeSpecific = new HexTypeSpecificConfigModel();
            case HuggingFaceML -> typeSpecific = new HuggingFaceMLTypeSpecificConfigModel();
            case LuaRocks -> typeSpecific = new LuaRocksTypeSpecificConfigModel();
            case Nix -> typeSpecific = createNixVirtualTypeSpecific(virtualRepoConfig);
            default -> log.trace("No virtual type specific set for {}", type);
        }
        if (typeSpecific != null) {
            return typeSpecific;
        }
        if (mavenModel != null) {
            populateMavenVirtualValues(mavenModel, virtualRepoConfig);
        }
        return mavenModel;
    }

    @Nonnull
    private static TypeSpecificConfigModel createNixVirtualTypeSpecific(VirtualRepoConfig virtualRepoConfig) {
        TypeSpecificConfigModel typeSpecific;
        NixTypeSpecificConfigModel nix = new NixTypeSpecificConfigModel();
        if (virtualRepoConfig.getRepoTypeConfig().getVirtualCacheConfig() != null) {
            nix.setVirtualRetrievalCachePeriodSecs(
                    virtualRepoConfig.getRepoTypeConfig().getVirtualCacheConfig()
                            .getVirtualRetrievalCachePeriodSecs());
        }
        typeSpecific = nix;
        return typeSpecific;
    }

    @Nonnull
    private static TypeSpecificConfigModel createCargoVirtualTypeSpecific(VirtualRepoConfig virtualRepoConfig) {
        TypeSpecificConfigModel typeSpecific;
        CargoTypeSpecificConfigModel cargo = new CargoTypeSpecificConfigModel();
        CargoConfiguration cargoConfiguration =
                CargoPackageTypeConfigMapper.INSTANCE.map(virtualRepoConfig.getPackageTypeConfig());
        cargo.setAnonymousAccess(cargoConfiguration.isCargoAnonymousAccess());
        cargo.setInternalIndex(true); //virtual cargo repo supported only with internal index
        typeSpecific = cargo;
        return typeSpecific;
    }

    @Nonnull
    private static TypeSpecificConfigModel createCompsoerVirtualTypeSpecific(VirtualRepoConfig virtualRepoConfig) {
        TypeSpecificConfigModel typeSpecific;
        ComposerTypeSpecificConfigModel composer = new ComposerTypeSpecificConfigModel();
        if (virtualRepoConfig.getRepoTypeConfig().getVirtualCacheConfig() != null) {
            composer.setVirtualRetrievalCachePeriodSecs(
                    virtualRepoConfig.getRepoTypeConfig().getVirtualCacheConfig()
                            .getVirtualRetrievalCachePeriodSecs());
        }
        typeSpecific = composer;
        return typeSpecific;
    }

    @Nonnull
    private static TypeSpecificConfigModel createYumVirtualTypeSpecific(VirtualRepoConfig virtualRepoConfig) {
        TypeSpecificConfigModel typeSpecific;
        YumTypeSpecificConfigModel yum = new YumTypeSpecificConfigModel();
        if (virtualRepoConfig.getRepoTypeConfig().getVirtualCacheConfig() != null) {
            yum.setVirtualRetrievalCachePeriodSecs(virtualRepoConfig.getRepoTypeConfig().getVirtualCacheConfig()
                    .getVirtualRetrievalCachePeriodSecs());
        }
        typeSpecific = yum;
        return typeSpecific;
    }

    @Nonnull
    private TypeSpecificConfigModel createOciOrDockerVirtualTypeSpecific(RepoType type,
            VirtualRepoConfig virtualRepoConfig) {
        TypeSpecificConfigModel typeSpecific;
        DockerTypeSpecificConfigModel dockerType = createDockerTypeSpecificConfigModel(type);
        DockerConfiguration dockerConfiguration =
                DockerPackageTypeConfigMapper.INSTANCE.map(virtualRepoConfig.getPackageTypeConfig());
        dockerType.setResolveDockerTagsByTimestamp(dockerConfiguration.isResolveDockerTagsByTimestamp());
        dockerType.setCachingLocalForeignLayersEnabled(
                dockerConfiguration.isCachingLocalForeignLayersEnabled());
        typeSpecific = dockerType;
        return typeSpecific;
    }

    @Nonnull
    private TypeSpecificConfigModel createNugetVirtualTypeSpecific(VirtualRepoConfig virtualRepoConfig) {
        NugetTypeSpecificConfigModel typeSpecific = new NugetTypeSpecificConfigModel();
        populateSharedNuGetValues(typeSpecific, virtualRepoConfig);
        return typeSpecific;
    }

    @Nonnull
    private static TypeSpecificConfigModel createBowerVirtualTypeSpecific(VirtualRepoConfig virtualRepoConfig) {
        TypeSpecificConfigModel typeSpecific;
        BowerTypeSpecificConfigModel bowerType = new BowerTypeSpecificConfigModel();
        BowerConfiguration bowerConfiguration =
                BowerPackageTypeConfigMapper.INSTANCE.map(virtualRepoConfig.getPackageTypeConfig());
        ExternalDependenciesConfig bowerExternalDependencies = bowerConfiguration.getExternalDependencies();
        if (bowerExternalDependencies != null) {
            bowerType.setEnableExternalDependencies(bowerExternalDependencies.isEnabled());
            if (bowerExternalDependencies.getRemoteRepoRef() != null) {
                bowerType.setExternalRemoteRepo(bowerExternalDependencies.getRemoteRepoRef());
            }
            bowerType.setExternalPatterns(bowerExternalDependencies.getPatterns());
        }
        typeSpecific = bowerType;
        return typeSpecific;
    }

    @Nonnull
    private static TypeSpecificConfigModel createNpmVirtualTypeSpecific(VirtualRepoConfig virtualRepoConfig) {
        TypeSpecificConfigModel typeSpecific;
        NpmTypeSpecificConfigModel npmType = new NpmTypeSpecificConfigModel();
        NpmConfiguration npmConfiguration =
                NpmPackageTypeConfigMapper.INSTANCE.map(virtualRepoConfig.getPackageTypeConfig());
        ExternalDependenciesConfig npmExternalDependencies = npmConfiguration.getExternalDependencies();
        if (npmExternalDependencies != null) {
            npmType.setEnableExternalDependencies(npmExternalDependencies.isEnabled());
            if (npmExternalDependencies.getRemoteRepoRef() != null) {
                npmType.setExternalRemoteRepo(npmExternalDependencies.getRemoteRepoRef());
            }
            npmType.setExternalPatterns(npmExternalDependencies.getPatterns());
        }
        if (virtualRepoConfig.getRepoTypeConfig().getVirtualCacheConfig() != null) {
            npmType.setVirtualRetrievalCachePeriodSecs(
                    virtualRepoConfig.getRepoTypeConfig().getVirtualCacheConfig()
                            .getVirtualRetrievalCachePeriodSecs());
        }
        typeSpecific = npmType;
        return typeSpecific;
    }

    @Nonnull
    private MavenTypeSpecificConfigModel createP2VirtualTypeSpecific(VirtualRepoConfig virtualRepoConfig) {
        MavenTypeSpecificConfigModel mavenModel;
        P2TypeSpecificConfigModel p2 = new P2TypeSpecificConfigModel();
        populateVirtualP2Values(p2, virtualRepoConfig);
        mavenModel = p2;
        return mavenModel;
    }

    @Nonnull
    private static TypeSpecificConfigModel createAlpineVirtualTypeSpecific(VirtualRepoConfig virtualRepoConfig) {
        TypeSpecificConfigModel typeSpecific;
        AlpineTypeSpecificConfigModel alpine = new AlpineTypeSpecificConfigModel();
        if (virtualRepoConfig.getRepoTypeConfig().getVirtualCacheConfig() != null) {
            alpine.setVirtualRetrievalCachePeriodSecs(
                    virtualRepoConfig.getRepoTypeConfig().getVirtualCacheConfig()
                            .getVirtualRetrievalCachePeriodSecs());
        }
        typeSpecific = alpine;
        return typeSpecific;
    }

    @Nonnull
    private static TypeSpecificConfigModel createHelmVirtualTypeSpecific(VirtualRepoConfig virtualRepoConfig) {
        TypeSpecificConfigModel typeSpecific;
        HelmTypeSpecificConfigModel helm = new HelmTypeSpecificConfigModel();
        HelmConfiguration helmConfiguration =
                HelmPackageTypeConfigMapper.INSTANCE.map(virtualRepoConfig.getPackageTypeConfig());
        if (virtualRepoConfig.getRepoTypeConfig().getVirtualCacheConfig() != null) {
            helm.setVirtualRetrievalCachePeriodSecs(
                    virtualRepoConfig.getRepoTypeConfig().getVirtualCacheConfig()
                            .getVirtualRetrievalCachePeriodSecs());
        }
        helm.setUseNamespaces(helmConfiguration.isUseNamespaces());
        typeSpecific = helm;
        return typeSpecific;
    }

    @Nonnull
    private static TypeSpecificConfigModel createDebianVirtualTypeSpecific(VirtualRepoConfig virtualRepoConfig) {
        TypeSpecificConfigModel typeSpecific;
        DebTypeSpecificConfigModel debianTypeSpecificConfigModel = new DebTypeSpecificConfigModel();
        DebianConfiguration debianConfiguration =
                DebianPackageTypeConfigMapper.INSTANCE.map(virtualRepoConfig.getPackageTypeConfig());
        if (virtualRepoConfig.getRepoTypeConfig().getVirtualCacheConfig() != null) {
            debianTypeSpecificConfigModel.setVirtualRetrievalCachePeriodSecs(
                    virtualRepoConfig.getRepoTypeConfig().getVirtualCacheConfig()
                            .getVirtualRetrievalCachePeriodSecs());

            debianTypeSpecificConfigModel.setVirtualArchitectures(
                    Arrays.asList(debianConfiguration.getDebianDefaultArchitectures().split(",")));
            debianTypeSpecificConfigModel
                    .setOptionalIndexCompressionFormats(
                            debianConfiguration.getDebianOptionalIndexCompressionFormats());
        }
        debianTypeSpecificConfigModel.setDdebSupported(debianConfiguration.isDdebSupported());
        typeSpecific = debianTypeSpecificConfigModel;
        return typeSpecific;
    }

    @Nonnull
    private static TypeSpecificConfigModel createCondaVirtualTypeSpecific(VirtualRepoConfig virtualRepoConfig) {
        TypeSpecificConfigModel typeSpecific;
        CondaTypeSpecificConfigModel condaTypeSpecificConfigModel = new CondaTypeSpecificConfigModel();
        if (virtualRepoConfig.getRepoTypeConfig().getVirtualCacheConfig() != null) {
            condaTypeSpecificConfigModel.setVirtualRetrievalCachePeriodSecs(
                    virtualRepoConfig.getRepoTypeConfig().getVirtualCacheConfig()
                            .getVirtualRetrievalCachePeriodSecs());
        }
        typeSpecific = condaTypeSpecificConfigModel;
        return typeSpecific;
    }

    @Nonnull
    private static TypeSpecificConfigModel createConanVirtualTypeSpecific(VirtualRepoConfig virtualRepoConfig) {
        TypeSpecificConfigModel typeSpecific;
        ConanTypeSpecificConfigModel conanTypeSpecificConfigModel = new ConanTypeSpecificConfigModel();
        if (virtualRepoConfig.getRepoTypeConfig().getVirtualCacheConfig() != null) {
            conanTypeSpecificConfigModel.setVirtualRetrievalCachePeriodSecs(
                    virtualRepoConfig.getRepoTypeConfig().getVirtualCacheConfig()
                            .getVirtualRetrievalCachePeriodSecs());
        }


        if (virtualRepoConfig.getPackageTypeConfig() != null) {
            conanTypeSpecificConfigModel.setForceConanAuthentication(Boolean.valueOf(
                    virtualRepoConfig.getPackageTypeConfig().get(FORCE_CONAN_AUTHENTICATION.getKey())));
        }
        typeSpecific = conanTypeSpecificConfigModel;
        return typeSpecific;
    }

    @Nonnull
    private static TypeSpecificConfigModel createCranVirtualTypeSpecific(VirtualRepoConfig virtualRepoConfig) {
        TypeSpecificConfigModel typeSpecific;
        CranTypeSpecificConfigModel cranTypeSpecificConfigModel = new CranTypeSpecificConfigModel();
        if (virtualRepoConfig.getRepoTypeConfig().getVirtualCacheConfig() != null) {
            cranTypeSpecificConfigModel.setVirtualRetrievalCachePeriodSecs(
                    virtualRepoConfig.getRepoTypeConfig().getVirtualCacheConfig()
                            .getVirtualRetrievalCachePeriodSecs());
        }
        typeSpecific = cranTypeSpecificConfigModel;
        return typeSpecific;
    }

    @Nonnull
    private static TypeSpecificConfigModel createGoVirtualTypeSpecific(VirtualRepoConfig virtualRepoConfig) {
        TypeSpecificConfigModel typeSpecific;
        GoTypeSpecificConfigModel goModel = new GoTypeSpecificConfigModel();
        GoConfiguration goConfiguration =
                GoPackageTypeConfigMapper.INSTANCE.map(virtualRepoConfig.getPackageTypeConfig());
        ExternalDependenciesConfig goExternalDependencies = goConfiguration.getExternalDependencies();
        if (goExternalDependencies != null) {
            goModel.setEnableExternalDependencies(goExternalDependencies.isEnabled());
            goModel.setExternalPatterns(goExternalDependencies.getPatterns());
        }
        typeSpecific = goModel;
        return typeSpecific;
    }

    @Nonnull
    private static TypeSpecificConfigModel createCocoaPodsVirtualTypeSpecific(VirtualRepoConfig virtualRepoConfig) {
        TypeSpecificConfigModel typeSpecific;
        CocoaPodsTypeSpecificConfigModel cocoaPodModel = new CocoaPodsTypeSpecificConfigModel();
        CocoaPodsConfiguration podsConfiguration =
                CocoaPodsPackageTypeConfigMapper.INSTANCE.map(virtualRepoConfig.getPackageTypeConfig());
        ExternalDependenciesConfig podsExternalDependencies = podsConfiguration.getExternalDependencies();
        if (podsExternalDependencies != null) {
            cocoaPodModel.setEnableExternalDependencies(podsExternalDependencies.isEnabled());
            cocoaPodModel.setExternalPatterns(podsExternalDependencies.getPatterns());
        }
        typeSpecific = cocoaPodModel;
        return typeSpecific;
    }

    @Nonnull
    private static TypeSpecificConfigModel createChefVirtualTypeSpecific(VirtualRepoConfig virtualRepoConfig) {
        TypeSpecificConfigModel typeSpecific;
        ChefTypeSpecificConfigModel chef = new ChefTypeSpecificConfigModel();
        if (virtualRepoConfig.getRepoTypeConfig().getVirtualCacheConfig() != null) {
            chef.setVirtualRetrievalCachePeriodSecs(
                    virtualRepoConfig.getRepoTypeConfig().getVirtualCacheConfig()
                            .getVirtualRetrievalCachePeriodSecs());
        }
        typeSpecific = chef;
        return typeSpecific;
    }

    @Nonnull
    private static MavenTypeSpecificConfigModel createMavenVirtualTypeSpecific(VirtualRepoConfig virtualRepoConfig) {
        MavenTypeSpecificConfigModel mavenModel;
        mavenModel = new MavenTypeSpecificConfigModel();
        if (virtualRepoConfig.getPackageTypeConfig() != null) {
            mavenModel.setForceMavenAuthentication(Boolean.valueOf(
                    virtualRepoConfig.getPackageTypeConfig().get(FORCE_MAVEN_AUTHENTICATION.getKey())));
        }
        return mavenModel;
    }

    private void populateVcsValues(VcsTypeSpecificConfigModel vcsType, RemoteRepoConfig remoteRepoConfig) {
        vcsType.setMaxUniqueSnapshots(remoteRepoConfig.getRepoTypeConfig().getMaxUniqueSnapshots());
        VcsConfiguration vcsConf = VcsPackageTypeConfigMapper.INSTANCE.map(remoteRepoConfig.getPackageTypeConfig());
        vcsType.setListRemoteFolderItems(remoteRepoConfig.getRepoTypeConfig().isListRemoteFolderItems());
        if (vcsConf != null) {
            vcsType.setVcsType(vcsConf.getType());
            VcsGitConfiguration git = vcsConf.getGit();
            if (git != null) {
                vcsType.setGitProvider(git.getProvider());
                vcsType.setDownloadUrl(git.getDownloadUrl());
            }
        }
    }

    private void populateSharedMavenValues(MavenTypeSpecificConfigModel model, RealRepoConfig realRepoConfig) {
        model.setMaxUniqueSnapshots(realRepoConfig.getRepoTypeConfig().getMaxUniqueSnapshots());
        model.setHandleReleases(realRepoConfig.getRepoTypeConfig().isHandleReleases());
        model.setHandleSnapshots(realRepoConfig.getRepoTypeConfig().isHandleSnapshots());
        MavenConfiguration mavenConfiguration =
                MavenPackageTypeConfigMapper.INSTANCE.map(realRepoConfig.getPackageTypeConfig());
        model.setSuppressPomConsistencyChecks(mavenConfiguration.isSuppressPomConsistencyChecks());
    }

    private void populateMavenLocalValues(MavenTypeSpecificConfigModel model, LocalConfig localConfig) {
        populateSharedMavenValues(model, localConfig);
        model.setSnapshotVersionBehavior(localConfig.getRepoTypeConfig().getSnapshotVersionBehavior());
        model.setLocalChecksumPolicy(localConfig.getRepoTypeConfig().getChecksumPolicyType());
    }

    private void populateMavenRemoteValues(MavenTypeSpecificConfigModel model, RemoteRepoConfig remoteRepoConfig) {
        populateSharedMavenValues(model, remoteRepoConfig);
        MavenConfiguration mavenConfiguration =
                MavenPackageTypeConfigMapper.INSTANCE.map(remoteRepoConfig.getPackageTypeConfig());
        model.setEagerlyFetchJars(mavenConfiguration.isFetchJarsEagerly());
        model.setEagerlyFetchSources(mavenConfiguration.isFetchSourcesEagerly());
        model.setRemoteChecksumPolicy(remoteRepoConfig.getRepoTypeConfig().getChecksumPolicyType());
        model.setListRemoteFolderItems(remoteRepoConfig.getRepoTypeConfig().isListRemoteFolderItems());
        model.setRejectInvalidJars(mavenConfiguration.isRejectInvalidJars());
    }

    private void populateMavenVirtualValues(MavenTypeSpecificConfigModel model, VirtualRepoConfig virtualRepoConfig) {
        MavenConfiguration mavenConfiguration =
                MavenPackageTypeConfigMapper.INSTANCE.map(virtualRepoConfig.getPackageTypeConfig());
        model.setPomCleanupPolicy(mavenConfiguration.getPomRepositoryReferencesCleanupPolicy());
        model.setKeyPair(virtualRepoConfig.getSecurityConfig().getKeyPair());
    }

    private void populateVirtualP2Values(P2TypeSpecificConfigModel model, VirtualRepoConfig virtualRepoConfig) {
        P2Configuration p2Configuration =
                P2PackageTypeConfigMapper.INSTANCE.map(virtualRepoConfig.getPackageTypeConfig());
        if (p2Configuration.getUrls() == null) {
            return;
        }
        Map<String, String> urlToRepoKeyMap =
                getUrlToRepoKeyMapping(virtualRepoConfig.getRepoTypeConfig().getRepositoryRefs());
        List<P2Repo> p2Repos = Lists.newArrayList();
        p2Configuration.getUrls().forEach(url -> {
            if (StringUtils.startsWith(url, "local://")) {
                Optional.ofNullable(resolveLocalP2RepoFromUrl(url)).ifPresent(p2Repos::add);
            } else {
                urlToRepoKeyMap.keySet().stream()
                        .map(RepoConfigDescriptorBuilder::getUrlWithoutSubpath)
                        .filter(p2Url -> RepoConfigDescriptorBuilder.getUrlWithoutSubpath(url).equals(p2Url))
                        .findAny()
                        .ifPresent(containingUrl ->
                                p2Repos.add(new P2Repo(null, null, urlToRepoKeyMap.get(containingUrl), url)));
            }
        });
        model.setP2Repos(p2Repos);
        model.setForceP2Authentication(p2Configuration.isForceP2Authentication());
    }

    P2Repo resolveLocalP2RepoFromUrl(String url) {
        String rpp = StringUtils.removeStart(url, "local://");
        RepoPath repoPath = RepoPathFactory.create(rpp);
        LocalConfig localConfig =
                repositoryConfigService.getLocalOrFederatedRepoConfigByKey(repoPath.getRepoKey());
        if (localConfig != null) {
            return new P2Repo(null, null, repoPath.getRepoKey(), url);
        }
        return null;
    }

    private List<PropertySetNameModel> collectPropertySets(List<PropertySet> propertySetsList) {
        return propertySetsList.stream()
                .map(PropertySetNameModel::new)
                .toList();
    }

    /**
     * Creates a mapping of url -> remote repo key to help build the P2 model (maps 'maven group' repos only)
     */
    private Map<String, String> getUrlToRepoKeyMapping(List<@NotUnicode String> repositoryRefs) {
        ConcurrentMap<String, String> mapping = Maps.newConcurrentMap();
        Set<RepoConfig> repoConfigs =
                repositoryConfigService.getRepoConfigsByKeys(Sets.newHashSet(repositoryRefs));
        repoConfigs.stream()
                .filter(RemoteRepoConfig.class::isInstance)
                .filter(repoDescriptor -> repoDescriptor.getPackageType().isMavenGroup())
                .forEach(remoteRepoConfig ->
                        mapping.put(((RemoteRepoConfig) remoteRepoConfig).getRepoTypeConfig().getUrl(),
                                remoteRepoConfig.getKey()));
        return mapping;
    }

    private void populateSharedNuGetValues(NugetTypeSpecificConfigModel nuget, RepoConfig localConfig) {
        NuGetConfiguration nuGetConfiguration =
                NuGetPackageTypeConfigMapper.INSTANCE.map(localConfig.getPackageTypeConfig());
        nuget.setForceNugetAuthentication(nuGetConfiguration.isForceNugetAuthentication());
        nuget.setEnableNormalizedVersion(nuGetConfiguration.isEnableNormalizedVersion());
    }

    private void populateSharedConanValues(ConanTypeSpecificConfigModel conan, RepoConfig localConfig) {
        ConanConfiguration conanConfiguration =
                ConanPackageTypeConfigMapper.INSTANCE.map(localConfig.getPackageTypeConfig());
        conan.setForceConanAuthentication(conanConfiguration.isForceConanAuthentication());
    }

    private XrayRepoConfigModel getXrayConfig(XrayRepoConfig xrayRepoConfig) {
        XrayRepoConfigModel model = null;
        if (xrayRepoConfig != null) {
            model = new XrayRepoConfigModel();
            model.setEnabled(xrayRepoConfig.isEnabled());
            model.setDataTtl(xrayRepoConfig.getDataTtl());
        }
        return model;
    }

    private DownloadRedirectRepoConfigModel getDownloadRedirectConfig(
            DownloadRedirectRepoConfig downloadRedirectRepoConfig) {
        DownloadRedirectRepoConfigModel model = null;
        if (downloadRedirectRepoConfig != null) {
            model = new DownloadRedirectRepoConfigModel();
            model.setEnabled(downloadRedirectRepoConfig.isEnabled());
        }
        return model;
    }

    private void addReverseProxyConfig(AdvancedRepositoryConfigModel advanced, String repoKey) {
        CentralConfigDescriptor configDescriptor = centralConfig.getDescriptor();
        ReverseProxyDescriptor reverseProxyDescriptor = configDescriptor.getCurrentReverseProxy();
        if (reverseProxyDescriptor != null) {
            ReverseProxyRepoConfig reverseProxyRepoConfig = reverseProxyDescriptor.getReverseProxyRepoConfig(repoKey);
            advanced.setReverseProxy(reverseProxyToModel(reverseProxyRepoConfig, reverseProxyDescriptor));
        }
    }

    private ReverseProxyRepoModel reverseProxyToModel(ReverseProxyRepoConfig reverseProxy,
            ReverseProxyDescriptor reverseProxyDescriptor) {
        if (reverseProxy != null) {
            ReverseProxyRepoModel model = new ReverseProxyRepoModel();
            model.setConfigurationKey(reverseProxyDescriptor.getKey());
            model.setServerName(reverseProxy.getServerName());
            model.setServerPort(reverseProxy.getPort());
            return model;
        }
        return null;
    }

    private DockerTypeSpecificConfigModel createDockerTypeSpecificConfigModel(RepoType type) {
        DockerTypeSpecificConfigModel dockerType;
        if (Docker.equals(type)) {
            dockerType = new DockerTypeSpecificConfigModel();
        } else if (OCI.equals(type)) {
            dockerType = new OciTypeSpecificConfigModel();
        } else {
            dockerType = new HelmOciTypeSpecificConfigModel();
        }
        return dockerType;
    }

    private HelmTypeSpecificConfigModel createLocalHelmTypeSpecific(LocalConfig localConfig) {
        HelmTypeSpecificConfigModel helmTypeSpecificConfigModel = new HelmTypeSpecificConfigModel();
        HelmConfiguration helmConfiguration =
                HelmPackageTypeConfigMapper.INSTANCE.map(localConfig.getPackageTypeConfig());
        helmTypeSpecificConfigModel.setForceNonDuplicateChart(helmConfiguration.isForceNonDuplicateChart());
        helmTypeSpecificConfigModel.setForceMetadataNameVersion(helmConfiguration.isForceMetadataNameVersion());
        return helmTypeSpecificConfigModel;
    }

    private HuggingFaceMLTypeSpecificConfigModel createLocalHuggingFaceTypeSpecific(LocalConfig localConfig) {
        HuggingFaceMLTypeSpecificConfigModel hfTypeSpecificConfigModel = new HuggingFaceMLTypeSpecificConfigModel();
        HuggingFaceMLConfiguration helmConfiguration =
                HuggingFaceMLPackageTypeConfigMapper.INSTANCE.map(localConfig.getPackageTypeConfig());
        hfTypeSpecificConfigModel.setMlRepoLayout(helmConfiguration.isMlRepoLayout());
        return hfTypeSpecificConfigModel;
    }
}
