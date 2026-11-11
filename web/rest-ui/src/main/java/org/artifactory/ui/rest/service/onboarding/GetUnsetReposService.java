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

package org.artifactory.ui.rest.service.onboarding;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import jakarta.annotation.Nonnull;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.OssAddonsManager;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.config.RepositoryConfigService;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.common.ConstantValues;
import org.artifactory.descriptor.config.MutableCentralConfigDescriptor;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.entitlement.EntitlementType;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.rest.common.service.RestService;
import org.artifactory.ui.rest.model.onboarding.OnboardingRepoState;
import org.artifactory.ui.rest.model.onboarding.OnboardingReposStateModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.artifactory.common.ConstantValues.aieditorExtensionsRepositoryEnabled;
import static org.artifactory.common.ConstantValues.jetbrainsPluginsRepositoryEnabled;
import static org.artifactory.repo.RepoDetailsType.FEDERATED;
import static org.artifactory.repo.RepoDetailsType.REMOTE;
import static org.artifactory.repo.config.RepoConfigDefaultValues.EXAMPLE_REPO_KEY;

/**
 * Return a map of repo types to one of 3 possible states - unset, already set or unavailable (in oss)
 *
 * @author nadavy
 */
@Component
public class GetUnsetReposService implements RestService {

    private RepositoryService repositoryService;
    private RepositoryConfigService repositoryConfigService;
    private CentralConfigService configService;

    private AddonsManager addonsManager;

    @Autowired
    public GetUnsetReposService(RepositoryService repositoryService, AddonsManager addonsManager,
            CentralConfigService configService, RepositoryConfigService repositoryConfigService) {
        this.repositoryService = repositoryService;
        this.addonsManager = addonsManager;
        this.configService = configService;
        this.repositoryConfigService = repositoryConfigService;
    }


    private static final List<RepoType> unsupportedRepoTypes = Lists.newArrayList(
            RepoType.P2,
            RepoType.VCS,
            RepoType.Distribution,
            RepoType.ReleaseBundles,
            RepoType.TerraformBackend
    );

    private static final List<RepoType> ossSupportedTypes = Lists.newArrayList(
            RepoType.Maven,
            RepoType.Gradle,
            RepoType.Ivy,
            RepoType.SBT,
            RepoType.Generic
    );

    private static final List<RepoType> conanCESupportedTypes = Lists.newArrayList(
            RepoType.Generic,
            RepoType.Conan
    );

    private static final List<RepoType> jcrSupportedTypes = Lists.newArrayList(
            RepoType.Generic,
            RepoType.Docker,
            RepoType.OCI,
            RepoType.HelmOCI,
            RepoType.Helm
    );

    @Override
    public void execute(ArtifactoryRestRequest request, RestResponse response) {
        Map<RepoType, OnboardingRepoState> repoTypesMap = getSupportedRepoTypesMap();
        filterRepoTypesMap(repoTypesMap);
        response.iModel(new OnboardingReposStateModel(repoTypesMap));
    }

    /**
     * Filter repositories already set up
     */
    void filterRepoTypesMap(Map<RepoType, OnboardingRepoState> repoTypesMap) {
        repositoryConfigService.getLocalRepoConfigsExcludingInfoRepos().stream()
                .filter(repoConfig -> !repoConfig.getKey().equals(EXAMPLE_REPO_KEY))
                .forEach(repoConfig -> repoTypesMap.replace(repoConfig.getPackageType(),
                        OnboardingRepoState.ALREADY_SET));
        repositoryConfigService.getRepoConfigsByType(FEDERATED)
                .forEach(repoConfig -> repoTypesMap.replace(repoConfig.getPackageType(),
                        OnboardingRepoState.ALREADY_SET));
        repositoryConfigService.getRepoConfigsByType(REMOTE).stream()
                .filter(repoConfig -> !"jcenter".equals(repoConfig.getKey()))
                .forEach(repoConfig -> repoTypesMap.replace(repoConfig.getPackageType(),
                        OnboardingRepoState.ALREADY_SET));
        repositoryService.getVirtualRepoDescriptors()
                .forEach(repoDescriptor -> repoTypesMap.replace(repoDescriptor.getType(),
                        OnboardingRepoState.ALREADY_SET));
        // build-info repo is not part of the on boarding wizard
        repoTypesMap.replace(RepoType.BuildInfo, OnboardingRepoState.ALREADY_SET);
        // jfrogsupport-bundle repo is not part of the on boarding wizard
        repoTypesMap.replace(RepoType.Support, OnboardingRepoState.ALREADY_SET);
    }

    /**
     * Return a map of supported repo types for onboarding setup
     */
    Map<RepoType, OnboardingRepoState> getSupportedRepoTypesMap() {
        Map<RepoType, OnboardingRepoState> repoTypesMap = Maps.newHashMap();
        boolean isOss = addonsManager instanceof OssAddonsManager;
        OnboardingRepoState defaultState = isOss ? OnboardingRepoState.UNAVAILABLE :
                OnboardingRepoState.UNSET;
        MutableCentralConfigDescriptor configDescriptor = configService.getMutableDescriptor();

        // set all repo types to the default state
        Stream<RepoType> valuesStream = Arrays.stream(RepoType.values());
        valuesStream = valuesStream.filter(this::shouldIncludeRepoTypeInQuickSetup);

        if (StringUtils.isBlank(configDescriptor.getUrlBase())) {
            // don't return cargo for quick setup until custom base url is set.
            valuesStream = valuesStream
                    .filter(repoType -> repoType != RepoType.Cargo);
        }
        valuesStream.forEach(repoType -> repoTypesMap.put(repoType, defaultState));
        // in oss make only ossSupportedTypes available
        if (isOss) {
            if (addonsManager.getArtifactoryRunningMode().isConan()) {
                conanCESupportedTypes.forEach(repoType -> repoTypesMap.put(repoType, OnboardingRepoState.UNSET));
            } else if (addonsManager.getArtifactoryRunningMode().isJCR()) {
                jcrSupportedTypes.forEach(repoType -> repoTypesMap.put(repoType, OnboardingRepoState.UNSET));
            } else {
                ossSupportedTypes.forEach(repoType -> repoTypesMap.put(repoType, OnboardingRepoState.UNSET));
            }
        }

        // remove unsupported repo types
        unsupportedRepoTypes.forEach(repoTypesMap::remove);
        return repoTypesMap;
    }

    boolean isPackagesIdeEntitled() {
        return ConstantValues.forcePluginsRepositoryEnabled.getBoolean() ||
                addonsManager.isEntitled(EntitlementType.PACKAGES_IDE_EXTENSION);
    }

    /**
     * @return False if the repoType should get filtered out
     */
    private boolean shouldIncludeRepoTypeInQuickSetup(@Nonnull RepoType repoType) {
        return switch (repoType) {
            case BazelModules -> ConstantValues.bazelModulesRepositoryEnabled.getBoolean();
            case Nix -> ConstantValues.nixRepositoryEnabled.getBoolean();
            case AgentPlugins -> ConstantValues.agentPluginsRepositoryEnabled.getBoolean();
            case MachineLearning -> ConstantValues.machineLearningRepositoryEnabled.getBoolean();
            case Ansible -> ConstantValues.ansibleRepositoryEnabled.getBoolean();
            case Hex -> false; // Disabled until Hex support is added with RTDEV-56078
            case NimModel -> ConstantValues.nimModelRepositoryEnabled.getBoolean();
            case Jem -> ConstantValues.jemRepositoryEnabled.getBoolean();
            case AIEditorExtensions -> aieditorExtensionsRepositoryEnabled.getBoolean() && isPackagesIdeEntitled();
            case JetBrainsPlugins -> jetbrainsPluginsRepositoryEnabled.getBoolean() && isPackagesIdeEntitled();
            case FrogML -> false;
            default -> true;
        };
    }
}
