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

package org.artifactory.repository.onboarding;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.ArtifactoryRunningMode;
import org.artifactory.addon.OssAddonsManager;
import org.artifactory.addon.PartnerLicenseVerifier;
import org.artifactory.addon.license.LicenseOperationStatus;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.jackson.JacksonReader;
import org.artifactory.descriptor.config.MutableCentralConfigDescriptor;
import org.artifactory.descriptor.eula.EulaDescriptor;
import org.artifactory.descriptor.repo.ProxyDescriptor;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.descriptor.repo.VcsConfiguration;
import org.artifactory.descriptor.repo.vcs.VcsGitProvider;
import org.artifactory.entitlement.EntitlementType;
import org.artifactory.model.LocalRepoConfig;
import org.artifactory.model.RemoteRepoConfig;
import org.artifactory.model.RepoConfig;
import org.artifactory.model.VirtualRepoConfig;
import org.artifactory.repo.onboarding.DefaultRepoModel;
import org.artifactory.repo.onboarding.RemoteDefaultRepoModel;
import org.artifactory.repo.onboarding.VirtualDefaultRepoModel;
import org.artifactory.repo.onboarding.YamlConfigCreator;
import org.artifactory.util.AolUtils;
import org.artifactory.util.PackageTypeConfigFields;
import org.joda.time.format.ISODateTimeFormat;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.extensions.compactnotation.CompactConstructor;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.artifactory.repo.config.RepoConfigDefaultValues.DefaultUrl;
import static org.artifactory.repo.system.FirstStartupHelper.onlyDefaultReposExist;
import static org.artifactory.util.RepoLayoutUtils.MAVEN_2_DEFAULT_NAME;
import static org.artifactory.util.VcsPackageTypeConfigMapper.VCS_GIT_DOWNLOAD_URL;
import static org.artifactory.util.VcsPackageTypeConfigMapper.VCS_GIT_PROVIDER;
import static org.artifactory.util.VcsPackageTypeConfigMapper.VCS_TYPE;

/**
 * Service to create default repositories from a list in artifactory.config.import.yaml (if exists in etc)
 * YAML file will also include license, proxy and default url setup.
 * service will not run when artifactory is not in "vanilla" state
 *
 * @author nadavy
 */
@Slf4j
public class OnboardingYamlBootstrapper {

    private final MutableCentralConfigDescriptor centralConfigDescriptor;
    private Map<String, DefaultRepoModel> defaultRepositoriesByType;
    private final OnboardingYamlBootstrapperValidator onboardingYamlBootstrapperValidator;
    private final File yamlFile;
    private final String importYamlFilename;
    private static final String DEFAULT_REPO_RESOURCE = "/templates/defaultRepository.json";
    private final AddonsManager addonsManager;
    private ConfigurationYamlModel configurationYamlModel;
    private static final String JSON_EMPTY_LIST = "[]";

    public OnboardingYamlBootstrapper(File yamlImportFile, MutableCentralConfigDescriptor currentConfig) {
        this.yamlFile = yamlImportFile;
        centralConfigDescriptor = currentConfig;
        importYamlFilename = yamlImportFile.getName();
        addonsManager = ContextHelper.get().beanForType(AddonsManager.class);
        onboardingYamlBootstrapperValidator = new OnboardingYamlBootstrapperValidator(centralConfigDescriptor,
                importYamlFilename);
    }

    /**
     * Initialize default repos map
     */
    public Pair<MutableCentralConfigDescriptor, ArrayList<RepoConfig>> loadBootstrapSettingsFromYaml() {
        if (AolUtils.isAol()) {
            log.error("Can't bootstrap yaml file from Artifactory online instance!");
            return null;
        }
        if (!onboardingYamlBootstrapperValidator.hasOnlyEmptyDefaultRepos()) {
            log.error("can't import file {} - Artifactory repositories have already been created", importYamlFilename);
            return null;
        }
        // load json to model
        try (InputStream json = getClass().getResourceAsStream(DEFAULT_REPO_RESOURCE)) {
            defaultRepositoriesByType = JacksonReader
                    .streamAsValueTypeReference(json, new TypeReference<List<DefaultRepoModel>>() {
                    })
                    .stream()
                    .collect(Collectors.toMap(DefaultRepoModel::getRepoType, defaultModel -> defaultModel));
            log.debug("Default repositories configuration has been successfully loaded from {}", DEFAULT_REPO_RESOURCE);
            return importBootstrapYaml(yamlFile);
        } catch (Exception e) {
            String message = StringUtils.isNotBlank(e.getMessage()) ? e.getMessage() : "";
            log.error("Unable to parse YAML file {} - Artifactory can not initialize default bootstrap settings. {}",
                    importYamlFilename, message, e);
            return null;
        }
    }

    public void loadLicenseFromYAML() {
        PartnerLicenseVerifier.assertPartnerLicense();
        log.info("initiating YAML bootstrap license late install");
        if (onboardingYamlBootstrapperValidator.isYamlLicenseActionValid(configurationYamlModel)) {
            try {
                setupLicense(configurationYamlModel);
                log.debug("License(s) installed from {}", importYamlFilename);
                addonsManager.activateLicense(Collections.emptySet(), new LicenseOperationStatus(), true, true);
            } catch (Exception e) {
                log.error("Unable to parse YAML file {} - Artifactory can not initialize default bootstrap settings",
                        importYamlFilename, e);
            }
        }
    }

    /**
     * Import yaml bootstrap file as default artifactory settings.
     */
    private Pair<MutableCentralConfigDescriptor, ArrayList<RepoConfig>> importBootstrapYaml(File yamlFile) {
        SafeConstructor safeConstructor = new CompactConstructor();
        Yaml yaml = new Yaml(safeConstructor);
        try (InputStream inputStream = new FileInputStream(yamlFile)) {
            configurationYamlModel = yaml.loadAs(inputStream, ConfigurationYamlModel.class);
            if (onboardingYamlBootstrapperValidator.areConfigurationActionsValid(configurationYamlModel)) {
                log.info("Bootstrapping artifactory from {}", importYamlFilename);
                ArrayList<RepoConfig> repoConfigs =
                        setupDefaultRepos(configurationYamlModel.OnboardingConfiguration.repoTypes);
                log.debug("Repositories created from {}", importYamlFilename);
                if (configurationYamlModel.GeneralConfiguration != null) {
                    setupBaseUrl(configurationYamlModel.GeneralConfiguration.baseUrl);
                    log.debug("Base url has been setup from {}", importYamlFilename);
                    setupProxy(configurationYamlModel.GeneralConfiguration.proxies);
                    log.debug("Proxy(ies) has been setup from {}", importYamlFilename);
                    setupJcrEula(configurationYamlModel.GeneralConfiguration.eula);
                    log.debug("Accepted EULA settings has been setup from {}", importYamlFilename);
                }
                exportAndDeleteImportYaml(yamlFile, configurationYamlModel);
                log.info("Configuration reloaded from {}", importYamlFilename);
                return new ImmutablePair<>(centralConfigDescriptor, repoConfigs);
            }
            return null;
        } catch (IOException e) {
            log.error("Artifactory can't import bootstrap settings from {}", importYamlFilename, e);
            return null;
        }
    }

    private void exportAndDeleteImportYaml(File yamlFile, ConfigurationYamlModel configurationYamlModel) {
        if (configurationYamlModel.OnboardingConfiguration != null &&
                configurationYamlModel.OnboardingConfiguration.repoTypes != null &&
                onlyDefaultReposExist()) {
            exportYamlConfigurationToFile(configurationYamlModel.OnboardingConfiguration.repoTypes);
        }
        try {
            Files.delete(yamlFile.toPath());
        } catch (Exception e) {
            log.error("Artifactory can't delete file {}", importYamlFilename, e);
        }
    }


    /**
     * Create default repositories for the given repo types list
     *
     * @param repoTypes list of repo types
     */
    private ArrayList<RepoConfig> setupDefaultRepos(List<String> repoTypes) {
        if (repoTypes == null) {
            return new ArrayList<>();
        }
        List<String> repoTypesToCreate = repoTypes;
        if (addonsManager instanceof OssAddonsManager) {
            repoTypesToCreate = filterNotOssRepos(repoTypes);
        }
        boolean edgeLicensed = addonsManager.isEntitled(EntitlementType.EDGE);
        List<List<RepoConfig>> allRepoConfigs = repoTypesToCreate
                .stream()
                .map(defaultRepositoriesByType::get)
                .filter(Objects::nonNull)
                .map(repoDefaultModel -> createRepositories(repoDefaultModel,
                        RepoType.fromType(repoDefaultModel.getRepoType()), edgeLicensed))
                .toList();
        return new ArrayList<>(allRepoConfigs.stream()
                .flatMap(List::stream)
                .distinct().toList());
    }

    /**
     * Filter repositories not available in Artifactory OSS
     */
    private List<String> filterNotOssRepos(List<String> repoTypes) {
        List<String> availableRepos = new ArrayList<>();
        if (isConanCE()) {
            availableRepos.add(RepoType.Conan.getType());
        } else if (isJCR()) {
            availableRepos.add(RepoType.Docker.getType());
            availableRepos.add(RepoType.OCI.getType());
            availableRepos.add(RepoType.HelmOCI.getType());
            availableRepos.add(RepoType.Helm.getType());
        } else {
            availableRepos.addAll(Lists
                    .newArrayList(RepoType.Maven.getType(), RepoType.Gradle.getType(), RepoType.Ivy.getType(),
                            RepoType.SBT.getType()));
        }
        return repoTypes.stream()
                .filter(availableRepos::contains)
                .toList();
    }

    /**
     * Create local, remote and virtual (whatever applies) of a given repo type, as stated in the json model
     */
    private List<RepoConfig> createRepositories(DefaultRepoModel defaultModel, RepoType repoType,
            boolean edgeLicensed) {
        log.info("Creating default repositories for {}", repoType.getType());
        Map<String, LocalRepoConfig> localRepoConfigMap = Maps.newHashMap();
        Map<String, RemoteRepoConfig> remoteRepoConfigMap = Maps.newHashMap();
        List<RepoConfig> retVal = Lists.newArrayList();
        // create local
        if (defaultModel.getLocalRepoKeys() != null) {
            defaultModel.getLocalRepoKeys()
                    .forEach(localRepo ->
                            addLocalRepoConfig(repoType, defaultModel.getLayout(), localRepoConfigMap,
                                    localRepo));
        }
        // create remote
        if (defaultModel.getRemoteRepoKeys() != null && !edgeLicensed) {
            defaultModel.getRemoteRepoKeys()
                    .forEach(remoteRepo -> addRemoteRepoConfig(repoType, defaultModel.getLayout(),
                            remoteRepoConfigMap,
                            remoteRepo));
        }
        // create virtual
        if (defaultModel.getVirtualRepoKeys() != null) {
            defaultModel.getVirtualRepoKeys()
                    .forEach(virtualRepo -> retVal.add(createVirtualRepoConfig(repoType, defaultModel.getLayout(),
                            localRepoConfigMap,
                            remoteRepoConfigMap, virtualRepo)));
        }
        retVal.addAll(localRepoConfigMap.values());
        retVal.addAll(remoteRepoConfigMap.values());
        return retVal;
    }

    /**
     * Add a single local repo to the config descriptor
     */
    private void addLocalRepoConfig(RepoType repoType, String layout,
            Map<String, LocalRepoConfig> localRepoDescriptorMap, String localRepo) {
        LocalRepoConfig localRepoConfig = LocalRepoConfig.create(localRepo, repoType);
        if (repoType == RepoType.Maven) {
            handleLocalMavenRepo(localRepo, localRepoConfig);
        }
        if (repoType == RepoType.Debian) {
            Map<String, String> packageTypeConfig = localRepoConfig.getPackageTypeConfig();
            packageTypeConfig.put(PackageTypeConfigFields.OPTIONAL_INDEX_COMPRESSION_FORMATS.getKey(), JSON_EMPTY_LIST);
        }
        if (repoType.isDockerGroup()) {
            Map<String, String> packageTypeConfig = localRepoConfig.getPackageTypeConfig();
            packageTypeConfig.put(PackageTypeConfigFields.DOCKER_API_VERSION.getKey(),
                    PackageTypeConfigFields.DOCKER_API_VERSION.getDefaultValue());
        }
        if (centralConfigDescriptor.isRepoLayoutExists(layout)) {
            localRepoConfig.getBaseConfig().setRepoLayoutRef(layout);
        } else {
            log.debug("Repo layout '{}' does not exist in the config descriptor", layout);
        }
        localRepoDescriptorMap.put(localRepo, localRepoConfig);
    }

    /**
     * set snapshot and release repositories in Maven local repos
     */
    private void handleLocalMavenRepo(String localRepo, LocalRepoConfig localRepoConfig) {
        if (localRepo.contains("snapshot")) {
            localRepoConfig.getRepoTypeConfig().setHandleSnapshots(true);
            localRepoConfig.getRepoTypeConfig().setHandleReleases(false);
        } else {
            localRepoConfig.getRepoTypeConfig().setHandleSnapshots(false);
            localRepoConfig.getRepoTypeConfig().setHandleReleases(true);
        }
    }

    /**
     * Add a single remote repo to the config descriptor
     */
    private void addRemoteRepoConfig(RepoType repoType, String layout,
            Map<String, RemoteRepoConfig> remoteRepoConfigMap, RemoteDefaultRepoModel remoteRepo) {
        RemoteRepoConfig remoteRepoConfig;
        if (RepoType.Gradle == repoType || RepoType.Ivy == repoType || RepoType.SBT == repoType) {
            remoteRepoConfig = RemoteRepoConfig.create(remoteRepo.getRepoKey(), RepoType.Maven);
            if (centralConfigDescriptor.isRepoLayoutExists(MAVEN_2_DEFAULT_NAME)) {
                remoteRepoConfig.getBaseConfig().setRepoLayoutRef(MAVEN_2_DEFAULT_NAME);
            } else {
                log.debug("Repo layout '{}' does not exist in the config descriptor", layout);
            }
        } else {
            remoteRepoConfig = RemoteRepoConfig.create(remoteRepo.getRepoKey(), repoType);
            if (centralConfigDescriptor.isRepoLayoutExists(layout)) {
                remoteRepoConfig.getBaseConfig().setRepoLayoutRef(layout);
            } else {
                log.debug("Repo layout '{}' does not exist in the config descriptor", layout);
            }
        }
        if (Strings.isNullOrEmpty(remoteRepo.getUrl())) {
            remoteRepoConfig.getRepoTypeConfig().setUrl(DefaultUrl.urlByType(repoType));
        } else {
            remoteRepoConfig.getRepoTypeConfig().setUrl(remoteRepo.getUrl());
        }
        VcsGitProvider provider = isNotBlank(remoteRepo.getGitProvider()) ?
                VcsGitProvider.valueOf(remoteRepo.getGitProvider()) : gitProviderByType(repoType);
        if (provider != null) {
            VcsConfiguration vcsConfiguration = new VcsConfiguration();
            vcsConfiguration.getGit().setProvider(provider);
            Map<String, String> packageTypeConfig = remoteRepoConfig.getPackageTypeConfig();
            packageTypeConfig.put(VCS_TYPE, vcsConfiguration.getType().name());
            packageTypeConfig.put(VCS_GIT_PROVIDER, vcsConfiguration.getGit().getProvider().name());
            if (vcsConfiguration.getGit().getDownloadUrl() != null) {
                packageTypeConfig.put(VCS_GIT_DOWNLOAD_URL, vcsConfiguration.getGit().getDownloadUrl());
            }
        }
        remoteRepoConfigMap.put(remoteRepo.getRepoKey(), remoteRepoConfig);
    }

    /**
     * Create a single virtual repo to the config descriptor or update existing one
     */
    private VirtualRepoConfig createVirtualRepoConfig(RepoType repoType, String layout,
            Map<String, LocalRepoConfig> localRepoConfigMap,
            Map<String, RemoteRepoConfig> remoteRepoConfigMap, VirtualDefaultRepoModel virtualRepo) {
        // create new virtual repo
        VirtualRepoConfig virtualRepoConfig = VirtualRepoConfig.create(virtualRepo.getRepoKey(), repoType);
        if (repoType.isDockerGroup()) {
            Map<String, String> packageTypeConfig = virtualRepoConfig.getPackageTypeConfig();
            packageTypeConfig.put(PackageTypeConfigFields.DOCKER_API_VERSION.getKey(),
                    PackageTypeConfigFields.DOCKER_API_VERSION.getDefaultValue());
        }
        if (centralConfigDescriptor.isRepoLayoutExists(layout)) {
            virtualRepoConfig.getBaseConfig().setRepoLayoutRef(layout);
        } else {
            log.debug("Repo layout '{}' does not exist in the config descriptor", layout);
        }
        virtualRepoConfig.getRepoTypeConfig().setDefaultDeploymentRepoRef(
                localRepoConfigMap.get(virtualRepo.getDefaultDeployment()).getKey());
        virtualRepoConfig.getRepoTypeConfig().setRepositoryRefs(
                getSelectedVirtualRepos(virtualRepo.getIncludedLocalRepos(), virtualRepo.getIncludedRemoteRepos(),
                        localRepoConfigMap, remoteRepoConfigMap));
        return virtualRepoConfig;
    }

    /**
     * Return the included repositories of a virtual repository, as stated in the defaultRepository.json model
     */
    private List<String> getSelectedVirtualRepos(List<String> includedLocalRepos,
            List<String> includedRemoteRepos, Map<String, LocalRepoConfig> localRepoConfigMap,
            Map<String, RemoteRepoConfig> remoteRepoConfigMap) {
        List<RepoConfig> repoConfigList = Lists.newArrayList();
        if (includedLocalRepos != null) {
            includedLocalRepos.forEach(includedRepo -> {
                if (localRepoConfigMap.get(includedRepo) != null) {
                    repoConfigList.add(localRepoConfigMap.get(includedRepo));
                }
            });
        }
        if (includedRemoteRepos != null) {
            includedRemoteRepos.forEach(includedRepo -> {
                if (remoteRepoConfigMap.get(includedRepo) != null) {
                    repoConfigList.add(remoteRepoConfigMap.get(includedRepo));
                }
            });
        }
        return repoConfigList.stream().map(RepoConfig::getKey).toList();
    }

    private VcsGitProvider gitProviderByType(RepoType repoType) {
        switch (repoType) {
            case Go:
                return VcsGitProvider.ARTIFACTORY;
            default:
                return null;
        }
    }

    private void setupLicense(ConfigurationYamlModel configurationYamlModel) {
        ArtifactoryRunningMode artifactoryRunningMode = addonsManager.getArtifactoryRunningMode();
        GeneralConfigurationYamlModel generalConfiguration = configurationYamlModel.GeneralConfiguration;
        if (generalConfiguration == null) {
            return;
        }
        if (artifactoryRunningMode.isHa()) {
            setupHaLicenses(generalConfiguration.licenseKeys, addonsManager);
        } else if (!artifactoryRunningMode.isOss() && !artifactoryRunningMode.isConan() &&
                !artifactoryRunningMode.isJCR()) {
            setupNoHaLicense(generalConfiguration.licenseKey, addonsManager);
        }
    }

    private void setupHaLicenses(Set<String> licenseKeys, AddonsManager addonsManager) {
        if (licenseKeys != null && !licenseKeys.isEmpty()) {
            // skip here is not really skip, it is just for skipping during the startup process. When loading the
            // license from the storage to the memory, we online validate if needed anyway
            addonsManager.addLicenses(licenseKeys, new LicenseOperationStatus(), true);
        }
    }

    private void setupNoHaLicense(String licenseKey, AddonsManager addonsManager) {
        if (licenseKey != null && !addonsManager.isLicenseInstalled()) {
            // skip here is not really skip, it is just for skipping during the startup process. When loading the
            // license from the storage to the memory, we online validate if needed anyway
            addonsManager.addLicenses(Sets.newHashSet(licenseKey), new LicenseOperationStatus(), true);
        }
    }

    private void setupBaseUrl(String baseUrl) {
        if (baseUrl == null) {
            return;
        }
        log.info("Setting up base url from {}", importYamlFilename);
        centralConfigDescriptor.setUrlBase(baseUrl);
    }

    private void setupJcrEula(EulaConfigurationYamlModel eulaConfigurationYamlModel) {
        if (isJCR()) {
            boolean eulaAccepted = eulaConfigurationYamlModel != null && eulaConfigurationYamlModel.accepted;
            EulaDescriptor eulaConfig = centralConfigDescriptor.getEulaConfig();
            if (eulaAccepted && (eulaConfig == null || !eulaConfig.isAccepted())) {
                EulaDescriptor eulaDescriptor = new EulaDescriptor();
                eulaDescriptor.setAccepted(true);
                eulaDescriptor.setAcceptDate(ISODateTimeFormat.dateTime().print(System.currentTimeMillis()));
                centralConfigDescriptor.setEulaConfig(eulaDescriptor);
            }
        }
    }

    private void setupProxy(List<ProxyConfigurationYamlModel> proxies) {
        if (proxies == null) {
            return;
        }
        log.info("Setting up proxies from {}", importYamlFilename);
        proxies.forEach(proxy -> {
            ProxyDescriptor proxyDescriptor = new ProxyDescriptor();
            proxyDescriptor.setKey(proxy.key);
            proxyDescriptor.setHost(proxy.host);
            proxyDescriptor.setPort(proxy.port);
            proxyDescriptor.setUsername(proxy.userName);
            proxyDescriptor.setPassword(proxy.password);
            proxyDescriptor.setPlatformDefault(proxy.platformDefault);
            proxyDescriptor.setServices(proxy.services);
            centralConfigDescriptor.addProxy(proxyDescriptor, true);
        });
    }

    private void exportYamlConfigurationToFile(List<String> repoTypeList) {
        DateFormat formatter = new SimpleDateFormat("yyyyMMdd.HHmmss");
        String timestamp = formatter.format(System.currentTimeMillis());
        File yamlOutput = new File(ContextHelper.get().getArtifactoryHome().getEtcDir()
                + "/artifactory.config." + timestamp + ".yml");
        try {
            YamlConfigCreator yamlConfigCreator = new YamlConfigCreator();
            yamlConfigCreator.saveBootstrapYaml(repoTypeList, centralConfigDescriptor, yamlOutput);
        } catch (IOException e) {
            log.error("artifactory can't export settings to {}", yamlOutput.getName(), e);
        }
    }


    private boolean isConanCE() {
        return ContextHelper.get().beanForType(AddonsManager.class).getArtifactoryRunningMode().isConan();
    }

    private boolean isJCR() {
        return ContextHelper.get().beanForType(AddonsManager.class).getArtifactoryRunningMode().isJCR();
    }
}
