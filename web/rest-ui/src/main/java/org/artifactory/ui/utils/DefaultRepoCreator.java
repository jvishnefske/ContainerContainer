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

package org.artifactory.ui.utils;

import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import jakarta.annotation.Nonnull;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.config.RepositoryConfigService;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.common.ConstantValues;
import org.artifactory.descriptor.config.MutableCentralConfigDescriptor;
import org.artifactory.descriptor.repo.ProxyDescriptor;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.model.RemoteRepoConfig;
import org.artifactory.model.RepoConfig;
import org.artifactory.repo.onboarding.DefaultRepoModel;
import org.artifactory.repo.onboarding.RemoteDefaultRepoModel;
import org.artifactory.repo.onboarding.VirtualDefaultRepoModel;
import org.artifactory.repo.vcs.VcsGitProvider;
import org.artifactory.rest.common.exception.RepoConfigException;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.ui.rest.model.admin.configuration.repository.GeneralRepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.RepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.local.LocalAdvancedRepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.local.LocalBasicRepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.local.LocalRepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.remote.RemoteAdvancedRepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.remote.RemoteBasicRepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.remote.RemoteNetworkRepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.remote.RemoteRepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.AIEditorExtensionsTypeSpecificConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.AgentPluginsTypeSpecificConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.AgentPackagesTypeSpecificConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.AlpineTypeSpecificConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.AnsibleTypeSpecificConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.BazelModulesTypeSpecificConfigModel;
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
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.PubTypeSpecificConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.PuppetTypeSpecificConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.PypiTypeSpecificConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.SbtTypeSpecificConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.SkillsTypeSpecificConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.SwiftTypeSpecificConfigModel;
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
import org.artifactory.ui.rest.model.onboarding.QuickRepositoryModel;
import org.artifactory.ui.rest.service.admin.configuration.repositories.CreateRepositoryConfigService;
import org.artifactory.ui.rest.service.onboarding.CreateDefaultReposResponseModel;
import org.artifactory.ui.rest.service.onboarding.CreateDefaultReposResponseModel.CreatedReposByType;
import org.artifactory.util.RepoLayoutUtils;
import org.artifactory.util.RepositoriesUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.artifactory.repo.RepoDetailsType.LOCAL;
import static org.artifactory.repo.RepoDetailsType.REMOTE;
import static org.jfrog.access.common.ProjectConstants.DEFAULT_PROJECT;

/**
 * @author nadavy
 */
public class DefaultRepoCreator {

    private static final Logger log = LoggerFactory.getLogger(DefaultRepoCreator.class);

    private Map<String, DefaultRepoModel> model;
    private final MutableCentralConfigDescriptor configDescriptor;
    private final RepositoryConfigService repositoryConfigService;

    private final RepositoryService repositoryService;

    /**
     * Initialize default repos map
     */
    public DefaultRepoCreator() {
        CentralConfigService configService = ContextHelper.get().getCentralConfig();
        repositoryService = ContextHelper.get().beanForType(RepositoryService.class);
        configDescriptor = configService.getMutableDescriptor();
        repositoryConfigService = ContextHelper.get().beanForType(RepositoryConfigService.class);
        model = RepositoriesUtils.loadReposTypesAndModelsMap();
    }

    /**
     * Create default repo configs per package type.
     *
     * @return an aggregated set of created default repo configs per package type.
     */
    public Set<RepoConfig> createDefaultRepos(CreateRepositoryConfigService createRepoService, RestResponse response,
            RepoType repoType, CreateDefaultReposResponseModel createDefaultReposResponseModel,
            boolean isSupportRemoteRepositories) {
        List<String> excludedLocalReposToInclude = Lists.newArrayList();
        List<String> excludedRemoteReposToInclude = Lists.newArrayList();
        CreatedReposByType createdReposByType = createDefaultReposResponseModel.addRepoType(repoType);

        Function<RepoConfig, String> repoConfigToKeyFunc = repoConfig -> repoConfig.getKey();

        Set<RepoConfig> defaultLocalRepoConfigsByPackageType =
                createDefaultLocalRepoConfigsByPackageType(createRepoService, response, repoType,
                        excludedLocalReposToInclude);
        List<String> defaultLocalRepoKeys =
                defaultLocalRepoConfigsByPackageType.stream().map(repoConfigToKeyFunc).toList();
        createdReposByType.setLocalRepos(defaultLocalRepoKeys);

        Set<RepoConfig> defaultRemoteRepoConfigsByPackageType =
                createDefaultRemoteRepoConfigsByPackageType(createRepoService, response, repoType,
                        excludedRemoteReposToInclude);
        List<String> defaultRemoteRepoKeys =
                defaultRemoteRepoConfigsByPackageType.stream().map(repoConfigToKeyFunc).toList();
        if (isSupportRemoteRepositories) {
            createdReposByType.setRemoteRepos(defaultRemoteRepoKeys);
        }

        Set<RepoConfig> defaultVirtualRepoConfigsByPackageType =
                createDefaultVirtualRepoConfigsByPackageType(createRepoService, response, repoType,
                        excludedLocalReposToInclude, excludedRemoteReposToInclude);
        List<String> defaultVirtualRepoKeys =
                defaultVirtualRepoConfigsByPackageType.stream().map(repoConfigToKeyFunc).toList();
        createdReposByType.setVirtualRepos(defaultVirtualRepoKeys);

        Set<RepoConfig> allRepoConfigs = Sets.newHashSet(defaultLocalRepoConfigsByPackageType);
        allRepoConfigs.addAll(defaultRemoteRepoConfigsByPackageType);
        allRepoConfigs.addAll(defaultVirtualRepoConfigsByPackageType);
        return allRepoConfigs;
    }

    /**
     * Create default repo of given repo type.
     */
    public RepoConfig createQuickRepo(CreateRepositoryConfigService createRepoService, RestResponse<?> response,
            QuickRepositoryModel repoModel, CreateDefaultReposResponseModel createDefaultReposResponseModel,
            boolean isSupportRemoteRepositories) {
        RepositoryConfigModel<?, ?, ?> repoConfigModel = null;
        String project = repoModel.getProject();
        switch (repoModel.getRepoType()) {
            case LOCAL:
                repoConfigModel = createLocalRepoConfigModel(repoModel.getPackageType(), repoModel.getRepoName(),
                        project, repoModel.getEnvs(), repoModel.isXrayEnabled());
                break;
            case REMOTE:
                if (isSupportRemoteRepositories) {
                    repoConfigModel = createQuickRemoteConfigModel(repoModel);
                } else {
                    log.warn("Quick remote repository '{}' creation skipped as remote repositories are not supported " +
                            "with Artifactory Edge license", repoModel.getRepoName());
                }
                break;
            case VIRTUAL:
                if (!isDefaultDeploymentRepoValid(repoModel)) {
                    createDefaultReposResponseModel.setValid(false);
                } else {
                    repoConfigModel = createQuickVirtualConfigModel(repoModel);
                }
                break;
            default:
                log.warn("Unsupported repository creation request for {} repository '{}'", repoModel.getRepoType(),
                        repoModel.getRepoName());

        }
        if (repoConfigModel == null) {
            return null;
        }
        RepoConfig repoConfig = null;
        try {
            repoConfig = createRepoService.createRepoConfig(response, repoConfigModel, configDescriptor,
                    repositoryService, false);
            CreatedReposByType createdReposByType =
                    createDefaultReposResponseModel.addRepoType(repoModel.getPackageType());
            if (StringUtils.isNotBlank(project) && !DEFAULT_PROJECT.equals(project)) {
                createDefaultReposResponseModel.addRepoToProject(repoModel.getRepoName(), project,
                        repoModel.getEnvs());
            }
            createdReposByType.addRepoToList(repoModel.getRepoType(), repoModel.getRepoName());
        } catch (RepoConfigException e) {
            log.error("Cannot create quick repositories for {}, cause: {}", repoModel.getRepoName(), e.getMessage());
            log.debug("Quick repository creation failed", e);
        }

        boolean isValid = repoConfig != null && configDescriptor != null && createDefaultReposResponseModel.isValid();
        createDefaultReposResponseModel.setValid(isValid);
        if (isValid) {
            createRepoService.addMetricRepoCreated(repoConfigModel.getTypeSpecific().getRepoType());
            repositoryConfigService.upsertRepoConfig(repoConfig);
        }
        return repoConfig;
    }

    boolean isDefaultDeploymentRepoValid(QuickRepositoryModel repoModel) {
        String defaultDeployment = repoModel.getDefaultDeploymentRepo();
        return StringUtils.isEmpty(defaultDeployment) ||
                repositoryConfigService.getLocalOrFederatedRepoConfigByKey(defaultDeployment) != null;
    }

    @Nonnull
    private VirtualRepositoryConfigModel createQuickVirtualConfigModel(QuickRepositoryModel repoModel) {
        VirtualDefaultRepoModel virtualRepoModel = new VirtualDefaultRepoModel(repoModel.getRepoName(),
                repoModel.getDefaultDeploymentRepo(), repoModel.getIncludedLocalRepositories(),
                repoModel.getIncludedRemoteRepositories());
        return createVirtualRepoConfigModel(repoModel.getPackageType(), virtualRepoModel, List.of(), List.of(),
                repoModel.getProject(), repoModel.getEnvs());
    }

    private RemoteRepositoryConfigModel createQuickRemoteConfigModel(QuickRepositoryModel repoModel) {
        RemoteDefaultRepoModel remoteRepo =
                model.get(repoModel.getPackageType().getType()).getRemoteRepoKeys().stream().findFirst()
                        .map(r -> new RemoteDefaultRepoModel(repoModel.getRepoName(), r.getUrl(),
                                r.getGitProvider(), false))
                        .orElse(null);
        if (remoteRepo != null) {
            return createRemoteRepoConfigModel(repoModel.getPackageType(), remoteRepo, repoModel.getProject(),
                    repoModel.getEnvs(), repoModel.isXrayEnabled());
        }
        return null;
    }

    public MutableCentralConfigDescriptor getConfigDescriptor() {
        return configDescriptor;
    }

    /**
     * Creates default local repo configs per package type.
     *
     * @return a set of default local repo configs per package type.
     */
    private Set<RepoConfig> createDefaultLocalRepoConfigsByPackageType(CreateRepositoryConfigService createRepoService,
            RestResponse response, RepoType repoType, List<String> excludedReposToInclude) {
        List<String> repoKeys = model.get(repoType.getType()).getLocalRepoKeys();
        Set<RepoConfig> defaultRepoConfigsPerType = Sets.newHashSet();
        if (repoKeys != null) {

            for (String localRepoKey : repoKeys) {
                try {
                    RepoConfig localRepoConfigByPackage =
                            createRepoService.createRepoConfig(response, createLocalRepoConfigModel(repoType,
                                            localRepoKey, DEFAULT_PROJECT, null, false), configDescriptor,
                                    repositoryService,
                                    false);
                    if (localRepoConfigByPackage != null) {
                        defaultRepoConfigsPerType.add(localRepoConfigByPackage);
                        createRepoService.addMetricRepoCreated(repoType);
                        repositoryConfigService.upsertRepoConfig(localRepoConfigByPackage);
                    } else {
                        if (isSameRepoSameTypeAlreadyExists(localRepoKey, repoType)) {
                            defaultRepoConfigsPerType.add(localRepoConfigByPackage);
                            log.debug("{} already exists, but not excluding from virtuals", localRepoKey);
                        } else {
                            log.debug("Repository {} already exists", localRepoKey);
                            excludedReposToInclude.add(localRepoKey);
                        }
                    }
                } catch (RepoConfigException e) {
                    String message =
                            "Cannot create default local repositories for " + repoType.getType() + ", cause: " +
                                    e.getMessage();
                    log.error(message);
                    log.debug(message, e);
                }
            }
        }
        return defaultRepoConfigsPerType;
    }

    /**
     * Create a local default repo config model by package type.
     *
     * @return a local repo config model by package type.
     */
    private LocalRepositoryConfigModel createLocalRepoConfigModel(RepoType repoType, String repoKey, String project,
            Set<String> environments, boolean xrayEnabled) {
        LocalRepositoryConfigModel localRepositoryConfigModel = new LocalRepositoryConfigModel();
        GeneralRepositoryConfigModel general = new GeneralRepositoryConfigModel();
        general.setRepoKey(repoKey);
        general.setProjectKey(project);
        general.setEnvironments(environments);
        LocalBasicRepositoryConfigModel basic = new LocalBasicRepositoryConfigModel();
        TypeSpecificConfigModel typeSpecific;
        basic.setLayout(model.get(repoType.getType()).getLayout());
        if (xrayEnabled) {
            XrayRepoConfigModel xrayRepoConfigModel = new XrayRepoConfigModel();
            xrayRepoConfigModel.setEnabled(true);
            xrayRepoConfigModel.setDataTtl(ConstantValues.xrayRepoDataTtlDays.getInt());
            basic.setXrayConfig(xrayRepoConfigModel);
        }
        typeSpecific = getTypeSpecific(repoType);
        if (RepoType.Maven == repoType) {
            if (repoKey.contains("snapshot")) {
                ((MavenTypeSpecificConfigModel) typeSpecific).setHandleSnapshots(true);
                ((MavenTypeSpecificConfigModel) typeSpecific).setHandleReleases(false);
            } else {
                ((MavenTypeSpecificConfigModel) typeSpecific).setHandleSnapshots(false);
                ((MavenTypeSpecificConfigModel) typeSpecific).setHandleReleases(true);
            }
        }
        LocalAdvancedRepositoryConfigModel advanced = new LocalAdvancedRepositoryConfigModel();
        localRepositoryConfigModel.setTypeSpecific(typeSpecific);
        localRepositoryConfigModel.setBasic(basic);
        localRepositoryConfigModel.setAdvanced(advanced);
        localRepositoryConfigModel.setReplications(null);
        localRepositoryConfigModel.setGeneral(general);
        return localRepositoryConfigModel;
    }

    /**
     * Creates default remote repo configs per package type
     *
     * @return a set of default remote repo configs per package type
     */
    private Set<RepoConfig> createDefaultRemoteRepoConfigsByPackageType(CreateRepositoryConfigService createRepoService,
            RestResponse response, RepoType repoType, List<String> excludedReposToInclude) {
        List<RemoteDefaultRepoModel> repoKeys = model.get(repoType.getType()).getRemoteRepoKeys();
        Set<RepoConfig> defaultRemoteRepoConfigsByType = Sets.newHashSet();
        if (repoKeys != null) {
            for (RemoteDefaultRepoModel remoteRepo : repoKeys) {
                RepoConfig defaultRemoteRepoConfigByType =
                        createDefaultRemoteRepoConfigByPackageType(createRepoService, response, repoType,
                                excludedReposToInclude, remoteRepo);
                if (defaultRemoteRepoConfigByType != null) {
                    defaultRemoteRepoConfigsByType.add(defaultRemoteRepoConfigByType);
                    repositoryConfigService.upsertRepoConfig(defaultRemoteRepoConfigByType);
                }
            }
        }
        return defaultRemoteRepoConfigsByType;
    }

    /**
     * Create default remote repo config by package type.
     *
     * @return default remote repo config by package type if created successfully, null otherwise.
     */
    private RepoConfig createDefaultRemoteRepoConfigByPackageType(CreateRepositoryConfigService createRepoService,
            RestResponse response, RepoType repoType, List<String> excludedReposToInclude,
            RemoteDefaultRepoModel remoteRepo) {
        try {
            RepoConfig repoConfig = createRepoService.createRepoConfig(response,
                    createRemoteRepoConfigModel(repoType, remoteRepo, DEFAULT_PROJECT, null, false), configDescriptor,
                    repositoryService, false);
            if (repoConfig != null) {
                createRepoService.addMetricRepoCreated(repoType);
            } else {
                // add jcenter to the response in case already created
                if (isJCenterAlreadyExists(remoteRepo.getRepoKey())) {
                    log.debug("{} already exists, but not excluding from virtuals", remoteRepo);
                } else {
                    if (!isSameRepoSameTypeAlreadyExists(remoteRepo.getRepoKey(), repoType)) {
                        excludedReposToInclude.add(remoteRepo.getRepoKey());
                        log.debug("Repository {} already exists", remoteRepo);
                    }
                }
            }
            return repoConfig;
        } catch (RepoConfigException e) {
            String message =
                    "Cannot create default remote repositories for " + repoType.getType() + ", cause: " +
                            e.getMessage();
            log.error(message);
            log.debug(message, e);
            return null;
        }
    }

    private boolean isJCenterAlreadyExists(String remoteRepoKey) {
        return "jcenter".equals(remoteRepoKey) &&
                isSameRepoSameTypeAlreadyExists(remoteRepoKey, RepoType.Maven);
    }

    private boolean isSameRepoSameTypeAlreadyExists(String repoKey, RepoType repoType) {
        RemoteRepoConfig remoteRepoConfig = repositoryConfigService.getRemoteRepoConfigByKey(repoKey);
        return remoteRepoConfig != null && remoteRepoConfig.getPackageType().equals(repoType);
    }

    /**
     * Create a remote default repo of given repo type
     */
    private RemoteRepositoryConfigModel createRemoteRepoConfigModel(RepoType repoType,
            RemoteDefaultRepoModel remoteRepo, String project, Set<String> environments, boolean xrayEnabled) {
        DefaultRepoModel defaultRepoModel = model.get(repoType.getType());
        RemoteRepositoryConfigModel remoteRepositoryConfigModel = new RemoteRepositoryConfigModel();
        GeneralRepositoryConfigModel general = new GeneralRepositoryConfigModel();
        general.setRepoKey(remoteRepo.getRepoKey());
        general.setProjectKey(project);
        general.setEnvironments(environments);
        RemoteBasicRepositoryConfigModel basic = new RemoteBasicRepositoryConfigModel();
        if (xrayEnabled) {
            XrayRepoConfigModel xrayRepoConfigModel = new XrayRepoConfigModel();
            xrayRepoConfigModel.setEnabled(true);
            xrayRepoConfigModel.setDataTtl(ConstantValues.xrayRepoDataTtlDays.getInt());
            basic.setXrayConfig(xrayRepoConfigModel);
        }
        RemoteAdvancedRepositoryConfigModel advanced = new RemoteAdvancedRepositoryConfigModel();
        ProxyDescriptor defaultProxy = configDescriptor.getDefaultProxy();
        if (defaultProxy != null) {
            RemoteNetworkRepositoryConfigModel network = new RemoteNetworkRepositoryConfigModel();
            network.setProxy(defaultProxy.getKey());
            advanced.setNetwork(network);
        }
        TypeSpecificConfigModel typeSpecific;
        typeSpecific = getTypeSpecific(repoType);
        if (repoType == RepoType.Gradle || repoType == RepoType.Ivy || repoType == RepoType.SBT) {
            basic.setLayout(RepoLayoutUtils.MAVEN_2_DEFAULT_NAME);
        } else {
            basic.setLayout(defaultRepoModel.getLayout());
        }
        if (Strings.isNullOrEmpty(remoteRepo.getUrl())) {
            basic.setUrl(typeSpecific.getUrl());
        } else {
            basic.setUrl(remoteRepo.getUrl());
        }
        if (isNotBlank(remoteRepo.getGitProvider()) &&
                typeSpecific instanceof VcsTypeSpecificConfigModel vcsTypeSpecificConfigModel) {
            vcsTypeSpecificConfigModel.setGitProvider(VcsGitProvider.valueOf(remoteRepo.getGitProvider()));
        }
        configureAdvancedByRepoType(repoType, advanced);
        remoteRepositoryConfigModel.setTypeSpecific(typeSpecific);
        remoteRepositoryConfigModel.setBasic(basic);
        remoteRepositoryConfigModel.setAdvanced(advanced);
        remoteRepositoryConfigModel.setReplications(null);
        remoteRepositoryConfigModel.setGeneral(general);
        return remoteRepositoryConfigModel;
    }

    /**
     * Create default virtual repo configs per package type
     *
     * @return a set of default virtual repo configs per package type.
     */
    private Set<RepoConfig> createDefaultVirtualRepoConfigsByPackageType(
            CreateRepositoryConfigService createRepoService, RestResponse response, RepoType repoType,
            List<String> excludedLocalReposToInclude, List<String> excludedRemoteReposToInclude) {
        List<VirtualDefaultRepoModel> virtualRepos = model.get(repoType.getType()).getVirtualRepoKeys();
        Set<RepoConfig> defaultVirtualRepoConfigsByPackageType = Sets.newHashSet();
        if (virtualRepos != null) {
            for (VirtualDefaultRepoModel virtualRepo : virtualRepos) {
                try {
                    VirtualRepositoryConfigModel virtualRepoConfigModel =
                            createVirtualRepoConfigModel(repoType, virtualRepo, excludedLocalReposToInclude,
                                    excludedRemoteReposToInclude, DEFAULT_PROJECT, null);
                    RepoConfig repoConfig =
                            createRepoService.createRepoConfig(response, virtualRepoConfigModel, configDescriptor,
                                    repositoryService, false);
                    if (repoConfig != null) {
                        defaultVirtualRepoConfigsByPackageType.add(repoConfig);
                        repositoryConfigService.upsertRepoConfig(repoConfig);
                    }
                } catch (RepoConfigException e) {
                    String message =
                            "Cannot create default virtual repositories for " + repoType.getType() + ", cause: " +
                                    e.getMessage();
                    log.error(message);
                    log.debug(message, e);
                }
            }
        }

        if (!defaultVirtualRepoConfigsByPackageType.isEmpty()) {
            createRepoService.addMetricRepoCreated(repoType);
        }
        return defaultVirtualRepoConfigsByPackageType;
    }

    private VirtualRepositoryConfigModel createVirtualRepoConfigModel(RepoType repoType,
            VirtualDefaultRepoModel virtualRepoModel, List<String> excludedLocalReposToInclude,
            List<String> excludedRemoteReposToInclude, String project, Set<String> environments) {
        DefaultRepoModel defaultRepoModel = model.get(repoType.getType());
        VirtualRepositoryConfigModel virtualRepositoryConfigModel = new VirtualRepositoryConfigModel();
        GeneralRepositoryConfigModel general = new GeneralRepositoryConfigModel();
        general.setRepoKey(virtualRepoModel.getRepoKey());
        general.setProjectKey(project);
        general.setEnvironments(environments);
        VirtualBasicRepositoryConfigModel basic = new VirtualBasicRepositoryConfigModel();
        basic.setDefaultDeploymentRepo(virtualRepoModel.getDefaultDeployment());
        basic.setSelectedRepositories(
                getVirtualSelectedRepos(virtualRepoModel, excludedLocalReposToInclude, excludedRemoteReposToInclude));
        basic.setLayout(defaultRepoModel.getLayout());
        VirtualAdvancedRepositoryConfigModel advanced = new VirtualAdvancedRepositoryConfigModel();
        TypeSpecificConfigModel typeSpecific = getTypeSpecific(repoType);
        virtualRepositoryConfigModel.setTypeSpecific(typeSpecific);
        virtualRepositoryConfigModel.setBasic(basic);
        virtualRepositoryConfigModel.setAdvanced(advanced);
        virtualRepositoryConfigModel.setGeneral(general);
        return virtualRepositoryConfigModel;
    }

    /**
     * returns all given repotype local and remote default repos.
     * default virtual will contain all those repos by default
     */
    private List<VirtualSelectedRepository> getVirtualSelectedRepos(VirtualDefaultRepoModel virtualRepo,
            List<String> excludedLocalReposToInclude, List<String> excludedRemoteReposToInclude) {
        List<VirtualSelectedRepository> virtualSelectedRepositoryList = Lists.newArrayList();
        List<String> includedLocalRepos = virtualRepo.getIncludedLocalRepos();
        if (includedLocalRepos != null) {
            if (!excludedLocalReposToInclude.isEmpty()) {
                includedLocalRepos.removeAll(excludedLocalReposToInclude);
            }
            for (String localRepo : includedLocalRepos) {
                VirtualSelectedRepository virtualSelectedRepository = new VirtualSelectedRepository();
                virtualSelectedRepository.setRepoName(localRepo);
                virtualSelectedRepository.setType(LOCAL.typeNameLowercase());
                virtualSelectedRepositoryList.add(virtualSelectedRepository);
            }
        }
        List<String> includedRemoteRepos = virtualRepo.getIncludedRemoteRepos();
        if (includedRemoteRepos != null) {
            if (!excludedRemoteReposToInclude.isEmpty()) {
                includedRemoteRepos.removeAll(excludedRemoteReposToInclude);
            }
            for (String remoteRepo : includedRemoteRepos) {
                VirtualSelectedRepository virtualSelectedRepository = new VirtualSelectedRepository();
                virtualSelectedRepository.setRepoName(remoteRepo);
                virtualSelectedRepository.setType(REMOTE.typeNameLowercase());
                virtualSelectedRepositoryList.add(virtualSelectedRepository);
            }
        }
        return virtualSelectedRepositoryList;
    }

    /**
     * Configures advanced repository settings based on the repository type.
     *
     * @param repoType The type of the repository (e.g., Ansible, Maven, etc.).
     * @param advanced The advanced repository configuration model to modify.
     */
    private void configureAdvancedByRepoType(RepoType repoType, RemoteAdvancedRepositoryConfigModel advanced) {
        if (Objects.requireNonNull(repoType) == RepoType.Ansible) {
            advanced.setBypassHeadRequests(true);
        }
    }

    private TypeSpecificConfigModel getTypeSpecific(RepoType repoType) {
        return switch (repoType) {
            case Nix -> new NixTypeSpecificConfigModel();
            case BazelModules -> new BazelModulesTypeSpecificConfigModel();
            case AgentPlugins -> new AgentPluginsTypeSpecificConfigModel();
            case AgentPackages -> new AgentPackagesTypeSpecificConfigModel();
            case Maven -> new MavenTypeSpecificConfigModel();
            case Bower -> new BowerTypeSpecificConfigModel();
            case Chef -> new ChefTypeSpecificConfigModel();
            case CocoaPods -> new CocoaPodsTypeSpecificConfigModel();
            case Debian -> new DebTypeSpecificConfigModel();
            case Composer -> new ComposerTypeSpecificConfigModel();
            case Docker -> new DockerTypeSpecificConfigModel();
            case OCI -> new OciTypeSpecificConfigModel();
            case HelmOCI -> new HelmOciTypeSpecificConfigModel();
            case Gems -> new GemsTypeSpecificConfigModel();
            case GitLfs -> new GitLfsTypeSpecificConfigModel();
            case Gradle -> new GradleTypeSpecificConfigModel();
            case Go -> new GoTypeSpecificConfigModel();
            case Helm -> new HelmTypeSpecificConfigModel();
            case Alpine -> new AlpineTypeSpecificConfigModel();
            case Ivy -> new IvyTypeSpecificConfigModel();
            case Npm -> new NpmTypeSpecificConfigModel();
            case NuGet -> new NugetTypeSpecificConfigModel();
            case Pypi -> new PypiTypeSpecificConfigModel();
            case Vagrant -> new VagrantTypeSpecificConfigModel();
            case Opkg -> new OpkgTypeSpecificConfigModel();
            case YUM -> new YumTypeSpecificConfigModel();
            case SBT -> new SbtTypeSpecificConfigModel();
            case Skills -> new SkillsTypeSpecificConfigModel();
            case Conan -> new ConanTypeSpecificConfigModel();
            case Puppet -> new PuppetTypeSpecificConfigModel();
            case CRAN -> new CranTypeSpecificConfigModel();
            case Conda -> new CondaTypeSpecificConfigModel();
            case Cargo -> new CargoTypeSpecificConfigModel();
            case Pub -> new PubTypeSpecificConfigModel();
            case Swift -> new SwiftTypeSpecificConfigModel();
            case Terraform -> new TerraformTypeSpecificConfigModel();
            case HuggingFaceML -> new HuggingFaceMLTypeSpecificConfigModel();
            case MachineLearning -> new MLTypeSpecificConfigModel();
            case Ansible -> new AnsibleTypeSpecificConfigModel();
            case Hex -> new HexTypeSpecificConfigModel();
            case NimModel -> new NimModelTypeSpecificConfigModel();
            case AIEditorExtensions -> new AIEditorExtensionsTypeSpecificConfigModel();
            case JetBrainsPlugins -> new JetBrainsPluginsTypeSpecificConfigModel();
            case LuaRocks -> new LuaRocksTypeSpecificConfigModel();
            case Jem -> ConstantValues.jemRepositoryEnabled.getBoolean() ? new JemTypeSpecificConfigModel() :
                    new GenericTypeSpecificConfigModel();
            default -> new GenericTypeSpecificConfigModel();
        };
    }
}