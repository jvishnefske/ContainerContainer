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

package org.artifactory.config;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.Maps;
import com.google.common.util.concurrent.MoreExecutors;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.utils.Lists;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SerializationUtils;
import org.apache.commons.lang3.builder.DiffResult;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.HaAddon;
import org.artifactory.api.config.ArtifactorySystemConfig;
import org.artifactory.api.config.BaseUrlModel;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.config.RepoConfigBackwardsCompatibleImportProcessor;
import org.artifactory.api.config.RepoExistenceFunction;
import org.artifactory.api.config.VersionInfo;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.security.SecurityService;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.status.MutableStatusHolder;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.descriptor.config.CentralConfigDescriptor;
import org.artifactory.descriptor.config.CentralConfigDescriptorImpl;
import org.artifactory.descriptor.config.MutableCentralConfigDescriptor;
import org.artifactory.descriptor.property.SystemPropertyDescriptor;
import org.artifactory.descriptor.reader.CentralConfigReader;
import org.artifactory.descriptor.replication.LocalReplicationDescriptor;
import org.artifactory.descriptor.replication.RemoteReplicationDescriptor;
import org.artifactory.descriptor.repo.ProxyDescriptor;
import org.artifactory.descriptor.security.PasswordExpirationPolicy;
import org.artifactory.descriptor.security.SecurityDescriptor;
import org.artifactory.descriptor.security.signingkeys.SigningKeysSettings;
import org.artifactory.jaxb.JaxbHelper;
import org.artifactory.model.RemoteRepoConfig;
import org.artifactory.model.RepoConfig;
import org.artifactory.platform.PlatformConfigService;
import org.artifactory.platform.PlatformGeneralConfig;
import org.artifactory.repo.ReloadableRepositoryConfigService;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.repo.confighandler.ConfigChangeEventHandler;
import org.artifactory.repo.confighandler.ConfigChangeWorkItem;
import org.artifactory.repo.confighandler.manager.ConfigChangeEventHandlerManager;
import org.artifactory.repo.confighandler.manager.ConfigChangeEventHandlerManagerImpl;
import org.artifactory.repo.confighandler.manager.ConfigDescriptorChangeWorkItem;
import org.artifactory.repo.converter.RepositoriesConfigSubConverter;
import org.artifactory.repo.migration.ConfigDescriptorRepoUtils;
import org.artifactory.repo.migration.RepoDescriptorsBackwardCompatibleImportProcessor;
import org.artifactory.repo.migration.RepoDescriptorsUpgradeProcessor;
import org.artifactory.repo.service.RepoConfigBootstrapStorageHelper;
import org.artifactory.repo.service.RepoConfigBootstrapStorageHelperFactory;
import org.artifactory.repo.service.RepositoryConfigBootstrapService;
import org.artifactory.repository.onboarding.OnboardingYamlBootstrapper;
import org.artifactory.exception.http.BadRequestException;
import org.artifactory.exception.http.ForbiddenException;
import org.artifactory.sapi.common.ExportSettings;
import org.artifactory.sapi.common.ImportKind;
import org.artifactory.sapi.common.ImportSettings;
import org.artifactory.schedule.CachedThreadPoolTaskExecutor;
import org.artifactory.security.AccessLogger;
import org.artifactory.security.SystemAuthenticationToken;
import org.artifactory.security.access.AccessService;
import org.artifactory.service.ConfigValidationService;
import org.artifactory.spring.ContextCreationListener;
import org.artifactory.spring.InternalContextHelper;
import org.artifactory.spring.Reloadable;
import org.artifactory.state.ArtifactoryServerState;
import org.artifactory.state.model.ArtifactoryStateManager;
import org.artifactory.storage.db.DbService;
import org.artifactory.storage.db.security.service.BasicCacheModel;
import org.artifactory.storage.db.security.service.VersioningCacheImpl;
import org.artifactory.storage.db.servers.service.ArtifactoryServersCommonService;
import org.artifactory.storage.fs.service.ConfigsService;
import org.artifactory.system.config.change.event.SystemConfigChangeEvent;
import org.artifactory.util.AolUtils;
import org.artifactory.util.CollectionUtils;
import org.artifactory.util.ExceptionUtils;
import org.artifactory.util.Files;
import org.artifactory.util.TomcatListeningDetector;
import org.artifactory.util.RepoConfigBootstrapUtils;
import org.artifactory.version.ArtifactoryConfigVersion;
import org.artifactory.version.ArtifactoryServiceVersionProvider;
import org.artifactory.version.CompoundVersionDetails;
import org.artifactory.version.InstallationType;
import org.artifactory.version.VersionProvider;
import org.jfrog.common.CachedValue;
import org.jfrog.common.ExecutionUtils;
import org.jfrog.common.RetryException;
import org.jfrog.common.StreamSupportUtils;
import org.jfrog.common.config.diff.DataDiff;
import org.jfrog.common.config.diff.DiffMerger;
import org.jfrog.common.platform.StringBasedEnum;
import org.jfrog.common.platform.subscription.SubscriptionType;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Repository;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import static org.artifactory.config.CentralConfigDescriptorSanitizer.merge;
import static org.artifactory.repo.confighandler.manager.UpdateOrReloadStrategyFactory.UpdateOrReloadStrategyMode.SYSTEM_STARTUP;
import static org.artifactory.system.config.change.event.SystemConfigChangeEvent.SystemConfigChangeEventType.ARTIFACTORY_CONFIG_DESCRIPTOR;
import static org.artifactory.util.ReloadablePropertiesUtil.getEnumValue;

/**
 * This class wraps the JAXB config descriptor.
 *
 * @author Fred Simon
 */
@Slf4j
@Repository("centralConfig")
@Reloadable(beanClass = InternalCentralConfigService.class,
        initAfter = {DbService.class, ConfigurationChangesInterceptors.class, ArtifactoryStateManager.class,
                AccessService.class, ReloadableRepositoryConfigService.class, RepositoryConfigBootstrapService.class,
                ConfigChangeEventHandlerManager.class},
        listenOn = CentralConfigKey.none)
public class CentralConfigServiceImpl implements InternalCentralConfigService, ContextCreationListener {

    @Autowired
    private SecurityService securityService;

    @Autowired
    private ConfigsService configsService;

    @Autowired
    private ConfigurationChangesInterceptors interceptors;

    @Autowired
    private AddonsManager addonsManager;

    @Autowired
    private CachedThreadPoolTaskExecutor cachedThreadPoolTaskExecutor;

    @Autowired
    private CentralConfigService centralConfigService;

    @Autowired
    private ConfigValidationService configValidationService;

    @Autowired
    private CentralConfigDiffValidation centralConfigDiffValidation;

    @Autowired
    private AccessService accessService;

    @Autowired
    private Set<RepositoriesConfigSubConverter> subConverters;

    @Autowired
    private ConfigChangeEventHandlerManager configChangeEventHandlerManager;

    @Autowired
    private PlatformConfigService platformConfigService;

    @Setter(onMethod_ =  @Autowired)
    private RepoExistenceFunction repoExistence;

    @Setter(onMethod_ =  @Autowired)
    private RepoConfigBackwardsCompatibleImportProcessor repoConfigBCProcessor;

    private VersioningCacheImpl<CentralConfigDescriptorCache> descriptorCache;

    private ArtifactorySystemConfig systemConfig;

    private Cache<Long, CentralConfigDescriptor> latestRevisions;
    private CachedValue<Integer> listeningPort;

    private OnboardingYamlBootstrapper onboardingYamlBootstrapper;
    private boolean loadLicFromYaml;
    private boolean isContextCreated;

    public CentralConfigServiceImpl() {
        this.systemConfig = new ArtifactorySystemConfigImpl(ArtifactoryHome.get().getSysConfig());
        listeningPort = CachedValue.loadUsing(TomcatListeningDetector::detectMainListeningPort)
                .expireAfterRefresh(ConstantValues.listeningPortCacheRefreshIntervalSecs.getLong(), TimeUnit.SECONDS)
                .defaultValue(-1)
                .name("ArtifactoryPort")
                .build();
    }

    @Override
    public void init() {
        long expireAfterAccess = ConstantValues.centralConfigLatestRevisionsExpireAfterAccessSeconds.getLong();
        long size = ConstantValues.centralConfigLatestRevisionsDictionarySize.getLong();
        configChangeEventHandlerManager = ContextHelper.get().beanForType(ConfigChangeEventHandlerManager.class);
        latestRevisions = CacheBuilder.newBuilder()
                .expireAfterAccess(expireAfterAccess, TimeUnit.SECONDS)
                .maximumSize(size)
                .build();
        initCacheAndGetCurrent(true);
    }

    @VisibleForTesting
    boolean updateLicenseIfNeeded(MutableCentralConfigDescriptor descriptor) {
        if (!AolUtils.isAol()) {
            log.debug("Skip license property update in non-aol mode");
            return false;
        }
        if (descriptor == null || CollectionUtils.isNullOrEmpty(descriptor.getSystemProperties())) {
            log.debug("Skip license property update since there are no system properties in the config descriptor");
            return false;
        }
        Optional<SystemPropertyDescriptor> property = StreamSupportUtils.stream(descriptor.getSystemProperties())
                .filter(prop -> prop.getKey().equalsIgnoreCase(ConstantValues.aolSubscriptionType.getPropertyName()))
                .findFirst();
        if (property.isEmpty()) {
            log.debug("Skip license property update since there are no licence property in the config descriptor");
            return false;
        }

        SubscriptionType overriddenValue =
                getEnumValue(SubscriptionType.class, ConstantValues.aolSubscriptionType.getString());
        if (overriddenValue == null) {
            log.debug(
                    "Skip license property update since artifactory.aol.subscription.type property value is not " +
                            "defined");
            return false;
        }
        SystemPropertyDescriptor systemPropertyDescriptor = property.get();
        log.info("License property is going to be updated in the config descriptor. Old value: {}, new value: {}",
                systemPropertyDescriptor.getValue(), overriddenValue);
        systemPropertyDescriptor.setValue(overriddenValue.getString());
        descriptor.removeSystemProperty(systemPropertyDescriptor.getKey());
        descriptor.addSystemProperty(systemPropertyDescriptor);
        return true;
    }

    /**
     * Init the cache if empty with the current config descriptor. The config descriptor is being
     * retrieved from the existing config or the .import config.
     *
     * @param shouldForceSave - If comes from reload, forcing the descriptor save,
     *                        If comes from convert, don't force the save.
     *                        First run the converters.
     * @return The central config descriptor change
     */
    private CentralConfigDescriptorChange initCacheAndGetCurrent(boolean shouldForceSave) {
        CentralConfigDescriptorChange configDescriptorChange = getCurrentConfig();
        if (descriptorCache == null) {
            long timeout = ConstantValues.centralConfigDirtyReadsTimeoutMillis.getLong();
            descriptorCache = new VersioningCacheImpl<>(timeout, new Callable<>() {
                private boolean firstTime = true;

                @Override
                public CentralConfigDescriptorCache call() {
                    CentralConfigDescriptorCache res = getCentralConfigDescriptorCache(firstTime);
                    firstTime = false;
                    return res;
                }
            }, "centralConfigDescriptorCache");
            boolean isUpgraded = false;
            boolean isLicenseUpdated = false;
            // shouldForce splits 2 flows - convert and init. In scope of convert Arti has to do additional permutations
            // like removing duplicates from replication list in the central config descriptor. Only after it is allowed
            // to call CentralConfig.onBeforeSave interceptors
            if (shouldForceSave) {
                isLicenseUpdated = updateLicenseIfNeeded(configDescriptorChange.getNewCentralConfigDescriptor());
                if (configDescriptorChange.isBootstrapInitOrUpgradeFlowFlag()) {
                    isUpgraded = doRepoDescriptorsUpgrade(configDescriptorChange.getNewCentralConfigDescriptor(),
                            configDescriptorChange.getRepoConfigsToUpdate());
                }
            }
            boolean updateDescriptor = configDescriptorChange.isSaveDescriptor() || isLicenseUpdated;
            if (!isUpgraded && shouldForceSave && updateDescriptor) {
                MutableCentralConfigDescriptor currentConfigDescriptor =
                        configDescriptorChange.getNewCentralConfigDescriptor();
                MutableCentralConfigDescriptor oldConfigDescriptor =
                        configDescriptorChange.getOldCentralConfigDescriptor();
                forceSaveDescriptorInternal(currentConfigDescriptor, configDescriptorChange.getRepoConfigsToUpdate());
                markCacheAsDirty(); // in case of get calls to the cache before save, cache will be null
                notifyReposOnConfigChangeDuringBootstrap(currentConfigDescriptor, oldConfigDescriptor);
            }
        }
        return configDescriptorChange;
    }

    private Pair<MutableCentralConfigDescriptor, ArrayList<RepoConfig>> bootstrapConfigDescriptorFromYaml(
            MutableCentralConfigDescriptor currentConfig) {
        File yamlImportFile = ContextHelper.get().getArtifactoryHome().getArtifactoryBootstrapYamlImportFile();
        if (yamlImportFile.exists()) {
            onboardingYamlBootstrapper = new OnboardingYamlBootstrapper(yamlImportFile, currentConfig);
            return onboardingYamlBootstrapper.loadBootstrapSettingsFromYaml();
        }
        return null;
    }

    @Override
    public void reload(CentralConfigDescriptor oldDescriptor, List<DataDiff<?>> configDiff) {
        // Nothing to do
    }

    @Override
    public void destroy() {
        // Nothing to do
    }

    /**
     * Convert and save the artifactory config descriptor
     */
    @Override
    public void convert(CompoundVersionDetails source, CompoundVersionDetails target) {
        // getCurrentConfig() will always return the latest version (ie will do the conversion)
        CentralConfigDescriptorChange centralConfigDescriptorChange = initCacheAndGetCurrent(false);
        boolean shouldUpdate = centralConfigDescriptorChange.isSaveDescriptor();
        MutableCentralConfigDescriptor newCentralConfigDescriptor =
                centralConfigDescriptorChange.getNewCentralConfigDescriptor();
        boolean configConvertedForKeyStore = moveKeyStorePasswordToConfig(source, target, newCentralConfigDescriptor);
        boolean configConvertedForDuplicatedReplication =
                removeDuplicatedReplications(source, target, newCentralConfigDescriptor);
        boolean isLicenseUpdated = updateLicenseIfNeeded(newCentralConfigDescriptor);
        boolean isUpgraded = doRepoDescriptorsUpgrade(newCentralConfigDescriptor, Set.of());
        if (isUpgraded) {
            reloadConfigAfterConvert();
        } else if (configConvertedForKeyStore || configConvertedForDuplicatedReplication || isLicenseUpdated ||
                shouldUpdate) {
            // Save Config descriptor to DB and file
            forceSaveDescriptorInternal(newCentralConfigDescriptor, Set.of());
            notifyReposOnConfigChangeDuringBootstrap(newCentralConfigDescriptor,
                    centralConfigDescriptorChange.getOldCentralConfigDescriptor());
            reloadConfigAfterConvert();
        }
        // Descriptor is reloaded in order to get the ks password encrypted if needed
        saveNewBootstrapConfigFile(getDescriptor());
    }

    /**
     * Removes duplication of replications per url. Each repository can contain one replication for that url
     * Local repos can contain multiple replications, however, each replication with unique url.
     *
     * @param source            - Source version
     * @param target            - Target version
     * @param artifactoryConfig - The config descriptor.
     */
    boolean removeDuplicatedReplications(CompoundVersionDetails source, CompoundVersionDetails target,
            MutableCentralConfigDescriptor artifactoryConfig) {
        log.debug("Starting remove duplicated replications process");
        File forceDuplicateReplicationConvertFile = ArtifactoryHome.get().getForceDuplicateReplicationConvertFile();
        boolean markerExists = forceDuplicateReplicationConvertFile.exists();
        if ((source.getVersion().afterOrEqual(ArtifactoryServiceVersionProvider.v7210m033.get()) ||
                target.getVersion().before(
                        ArtifactoryServiceVersionProvider.v7210m033.get())) && !markerExists) {
            log.debug("No conversion is required for {} and marker file doesn't exists",
                    source.getVersion().getVersion());
            return false;
        }
        try {
            log.debug("Starting removal of duplicated replication if needed");
            Map<String, Map<String, LocalReplicationDescriptor>> localReplicationsMap =
                    artifactoryConfig.getLocalReplicationsMap();
            if (localReplicationsMap.isEmpty() &&
                    artifactoryConfig.getRemoteReplications().isEmpty()) {
                log.debug("No replications were configured. No conversion is required");
                return false;
            }
            List<LocalReplicationDescriptor> localReplicationDescriptors = Lists.newArrayList();
            localReplicationsMap.forEach((repoKey, replicationPerUrl) -> {
                Collection<LocalReplicationDescriptor> values = replicationPerUrl.values();
                if (repoExistence.existsByRepoKey(repoKey) ||
                        artifactoryConfig.getLocalRepositoriesMap().containsKey(repoKey)) {
                    localReplicationDescriptors.addAll(new ArrayList<>(values));
                } else {
                    log.warn("Found replication for missing repo: {}. Removing replication.", repoKey);
                }
            });

            List<RemoteReplicationDescriptor> remoteReplications = artifactoryConfig.getRemoteReplications();
            Map<String, RemoteReplicationDescriptor> cleanRemoteReplications = Maps.newHashMap();
            for (RemoteReplicationDescriptor remoteReplication : remoteReplications) {
                if (repoExistence.existsByRepoKey(remoteReplication.getRepoKey()) ||
                        artifactoryConfig.getRemoteRepositoriesMap()
                                .containsKey(remoteReplication.getRepoKey())) {
                    cleanRemoteReplications.put(remoteReplication.getRepoKey(), remoteReplication);
                } else {
                    log.warn("Found replication for missing repo: {}. Removing replication.",
                            remoteReplication.getRepoKey());
                }
            }

            artifactoryConfig.setLocalReplications(localReplicationDescriptors);
            artifactoryConfig.setRemoteReplications(new ArrayList<>(cleanRemoteReplications.values()));
        } finally {
            if (markerExists) {
                if (forceDuplicateReplicationConvertFile.delete()) {
                    log.info("Removed marker file: {}", forceDuplicateReplicationConvertFile.getName());
                }
            }
        }
        log.debug("Remove duplicated replications process has finished successfully");
        return true;
    }

    private void reloadConfigAfterConvert() {
        markCacheAsDirty();
        addonsManager.addonByType(HaAddon.class).propagateConfigReload();
        storeLatestConfigToFile(getConfigXml());
    }

    @Override
    public boolean isInterested(CompoundVersionDetails source, CompoundVersionDetails target) {
        File forceDuplicateReplicationConvertFile = ArtifactoryHome.get().getForceDuplicateReplicationConvertFile();
        boolean markerExists = forceDuplicateReplicationConvertFile.exists();
        if (markerExists) {
            log.info("Found marker file for triggering conversion: {}",
                    forceDuplicateReplicationConvertFile.getAbsolutePath());
            return true;
        } else {
            log.debug("Marker file not found for {}", forceDuplicateReplicationConvertFile.getAbsolutePath());
        }
        return InternalCentralConfigService.super.isInterested(source, target);
    }

    protected boolean moveKeyStorePasswordToConfig(CompoundVersionDetails source, CompoundVersionDetails target,
            MutableCentralConfigDescriptor artifactoryConfig) {
        if (source.getVersion().afterOrEqual(ArtifactoryServiceVersionProvider.v522m001.get()) ||
                target.getVersion().before(
                        ArtifactoryServiceVersionProvider.v522m001.get())) {
            return false; // Conversion is not needed
        }
        String password = configsService.getConfig("keystore:password");
        if (StringUtils.isBlank(password)) {
            return false;
        }

        // add password to Config descriptor Object
        SecurityDescriptor securityDescriptor = artifactoryConfig.getSecurity();
        SigningKeysSettings signingKeysSettings = securityDescriptor.getSigningKeysSettings();
        if (signingKeysSettings == null) {
            signingKeysSettings = new SigningKeysSettings();
        }
        signingKeysSettings.setKeyStorePassword(password);
        securityDescriptor.setSigningKeysSettings(signingKeysSettings);

        try {
            configsService.deleteConfig("keystore:password");
        } catch (Exception e) {
            log.error("Deletion of Key Store Password from the database during conversion has failed");
        }
        log.info("Key store password location conversion was done");
        return true;
    }

    private void saveNewBootstrapConfigFile(CentralConfigDescriptor artifactoryConfig) {
        String artifactoryConfigXml = JaxbHelper.toXml(artifactoryConfig);

        File bootstrapConfigFile = ArtifactoryHome.get().getArtifactoryConfigBootstrapFile();
        File parentFile = bootstrapConfigFile.getParentFile();
        if (parentFile.canWrite()) {
            try {
                log.info("Automatically converting the config file, original will be saved in {}",
                        parentFile.getAbsolutePath());
                File newConfigFile = bootstrapConfigFile.exists() ?
                        ArtifactoryHome.get().getArtifactoryConfigNewBootstrapFile() : bootstrapConfigFile;

                FileOutputStream fos = new FileOutputStream(newConfigFile);
                IOUtils.write(artifactoryConfigXml, fos);
                fos.close();
                if (newConfigFile != bootstrapConfigFile) {
                    Files.switchFiles(newConfigFile, bootstrapConfigFile);
                }
            } catch (Exception e) {
                log.warn("The converted config xml is:\n" + artifactoryConfigXml +
                        "\nThe new configuration is saved in DB but it failed to be saved automatically to '" +
                        parentFile.getAbsolutePath() + "' due to :" + e.getMessage() + ".\n", e);
            }
        } else {
            log.warn("The converted config xml is:\n {}" +
                    "\nThe new configuration is saved in DB but it failed to be saved automatically to '{}'"
                    + " since the folder is not writable.\n", artifactoryConfigXml, parentFile.getAbsolutePath());
        }
    }

    private CentralConfigDescriptorChange getCurrentConfig() {
        RepoConfigBootstrapUtils.verifyConflictingRepoBootstrap();
        CentralConfigDescriptorChange configDescriptorChange = new CentralConfigDescriptorChange();
        ArtifactoryHome artifactoryHome = ContextHelper.get().getArtifactoryHome();
        VersionProvider versionProvider = ContextHelper.get().getVersionProvider();
        boolean isRollback = versionProvider.getInstallationType() == InstallationType.ROLLBACK;

        //First try to see if there is an import config file to load
        String currentConfigXml = artifactoryHome.getImportConfigXml();
        String oldConfigXml = loadConfigFromStorage();
        boolean isBootstrapInitOrUpgradeFlow = false;
        boolean updateDescriptor = true;
        //If no import config file exists, or is empty, load from storage
        if (StringUtils.isBlank(currentConfigXml)) {
            currentConfigXml = oldConfigXml;
            if (!StringUtils.isBlank(currentConfigXml)) {
                updateDescriptor = false;
                isBootstrapInitOrUpgradeFlow = true;
                if (!ArtifactoryHome.get().getArtifactoryConfigLatestFile().exists()) {
                    storeLatestConfigToFile(currentConfigXml);
                }
            }
        } else {
            isBootstrapInitOrUpgradeFlow = true;
        }

        //Otherwise, load bootstrap config
        if (StringUtils.isBlank(currentConfigXml)) {
            log.info("Loading bootstrap configuration (artifactory home dir is {}).", artifactoryHome.getHomeDir());
            currentConfigXml = artifactoryHome.getBootstrapConfigXml();
        }

        MutableCentralConfigDescriptor oldDescriptor = oldConfigXml == null ? null :
                new CentralConfigReader().readAndConvert(oldConfigXml, false, false, isRollback).getLeft();

        Triple<MutableCentralConfigDescriptor, Boolean, ArtifactoryConfigVersion> newDescriptorPair =
                new CentralConfigReader().readAndConvert(currentConfigXml, updateDescriptor, false, isRollback);

        if (!ConfigDescriptorRepoUtils.isRepoUpgradeShouldBeUpdated(newDescriptorPair.getRight())) {
            isBootstrapInitOrUpgradeFlow = false;
        }

        MutableCentralConfigDescriptor configDescriptor = newDescriptorPair.getLeft();
        // Set true for rollback to save new config in file and in database
        updateDescriptor = updateDescriptor || newDescriptorPair.getMiddle()
                || isRollback;
        // try to import from yaml file
        Pair<MutableCentralConfigDescriptor, ArrayList<RepoConfig>> descriptorAndRepos =
                bootstrapConfigDescriptorFromYaml(configDescriptor);
        List<RepoConfig> repoConfigsToUpdate = new ArrayList<>();
        if (descriptorAndRepos != null) {
            configDescriptor = descriptorAndRepos.getLeft();
            updateDescriptor = true;
            loadLicFromYaml = true;
            repoConfigsToUpdate = descriptorAndRepos.getRight();
        }
        artifactoryHome.renameInitialConfigFileIfExists();
        log.trace("Current config xml is:\n{}", currentConfigXml);
        configDescriptorChange.setBootstrapInitOrUpgradeFlowFlag(isBootstrapInitOrUpgradeFlow);
        configDescriptorChange.setRepoConfigsToUpdate(new HashSet<>(repoConfigsToUpdate));
        configDescriptorChange.setNewCentralConfigDescriptor(configDescriptor);
        configDescriptorChange.setOldCentralConfigDescriptor(oldDescriptor);
        configDescriptorChange.setSaveDescriptor(updateDescriptor);
        platformConfigService.updatePlatformConfigIfNeeded();
        return configDescriptorChange;
    }

    @Nullable
    private String loadConfigFromStorage() {
        VersionProvider versionProvider = ContextHelper.get().getVersionProvider();
        InstallationType installationType = versionProvider.getInstallationType();

        if (installationType == InstallationType.ROLLBACK && isConverting()) {
            String to = versionProvider.getRunningServiceVersion().getVersion().getVersion();
            log.debug("Getting config from history for version {}", to);
            // Get config from config history by to version
            return configsService.getConfigHistory(to);
        }
        //Check in DB
        String dbConfigName = ArtifactoryHome.ARTIFACTORY_CONFIG_FILE;
        if (configsService.hasConfig(dbConfigName)) {
            log.debug("Loading existing configuration from storage.");
            return configsService.getConfig(dbConfigName);
        }
        return null;
    }

    void saveDescriptor(CentralConfigDescriptor oldDescriptor,
            CentralConfigDescriptor descriptor) throws RetryException {
        saveDescriptorInternal(oldDescriptor, descriptor);
        //Pass the old descriptor to the reload, so it could compare it to the new descriptor without the concern on
        //Concurrent thread will dirty the cache in between and the old will be lost.
        reloadConfiguration(oldDescriptor, descriptor);
        interceptors.onAfterSave(descriptor, oldDescriptor);
    }

    private MutableCentralConfigDescriptor getFilteredDescriptor(CentralConfigDescriptor descriptor) {
        MutableCentralConfigDescriptor filteredDescriptor =
                (MutableCentralConfigDescriptor) SerializationUtils.clone(descriptor);
        ConfigDescriptorRepoUtils.cleanupConfigDescriptorRepositories(filteredDescriptor);
        return filteredDescriptor;
    }

    private String preSaveDescriptor(CentralConfigDescriptor descriptor) {
        log.trace("Setting central config descriptor for config #{}.", System.identityHashCode(this));
        // call the interceptors before saving the new descriptor
        interceptors.onBeforeSave(descriptor);
        // Check that the proxies in the proxy list are unique
        checkUniqueProxies(descriptor);
        // Now save the Descriptor
        log.debug("Saving new configuration in storage...");
        return JaxbHelper.toXml(getFilteredDescriptor(descriptor));
    }

    private boolean saveConfigIfNotExists(CentralConfigDescriptor descriptor, String configString) {
        if (!configsService.hasConfig(ArtifactoryHome.ARTIFACTORY_CONFIG_FILE)) {
            configsService.addConfig(ArtifactoryHome.ARTIFACTORY_CONFIG_FILE,
                    configString, descriptor.getRevision());
            return true;
        }
        return false;
    }

    private void saveDescriptorInternal(CentralConfigDescriptor oldDescriptor, CentralConfigDescriptor descriptor)
            throws RetryException {
        if (doBackwardCompatibleImportIfNeeded((MutableCentralConfigDescriptor) descriptor, oldDescriptor)) {
            return;
        }
        String configString = preSaveDescriptor(descriptor);
        prepareToSendDataToAccess(descriptor, oldDescriptor);
        accessService.updateConfigurationToAccess();
        platformConfigService.updatePlatformConfigIfNeeded();
        if (saveConfigIfNotExists(descriptor, configString)) {
            return;
        }

        long revision = descriptor.getRevision();
        long oldDescriptorRevision = oldDescriptor.getRevision();

        if (oldDescriptorRevision <= 0) {
            log.info("Force updating new descriptor (old descriptor doesn't support diff). Old descriptor revision {}",
                    oldDescriptorRevision);
            configsService.updateConfig(ArtifactoryHome.ARTIFACTORY_CONFIG_FILE, configString, revision);
        } else if (configsService.updateConfigIfLastModificationMatch(ArtifactoryHome.ARTIFACTORY_CONFIG_FILE,
                configString, oldDescriptorRevision, revision)) {
            if (log.isDebugEnabled()) {
                log.debug("Success update {} from:{} to: {}",
                        ArtifactoryHome.ARTIFACTORY_CONFIG_FILE,
                        oldDescriptorRevision,
                        revision);
            }
        } else {
            if (log.isTraceEnabled()) {
                log.trace("Failed update update {} from:{} to: {}",
                        ArtifactoryHome.ARTIFACTORY_CONFIG_FILE
                        ,
                        oldDescriptorRevision,
                        revision);
            }
            // mark dirty so we will pull from db.
            markCacheAsDirty();
            throw new RetryException("Should update revision " + oldDescriptorRevision);
        }
        log.info("New configuration with revision {} saved.", revision);
    }

    /**
     * remove this code when access will return back this data
     */
    private void prepareToSendDataToAccess(CentralConfigDescriptor descriptor, CentralConfigDescriptor oldDescriptor) {
        // Update anonymous-access-enabled
        SecurityDescriptor security = descriptor.getSecurity();
        SecurityDescriptor oldSecurity = oldDescriptor != null ? oldDescriptor.getSecurity() : null;
        boolean isPasswordExpiryDays = false;
        PasswordExpirationPolicy expirationPolicy =
                security.getPasswordSettings().getExpirationPolicy();
        PasswordExpirationPolicy oldExpirationPolicy = oldSecurity != null ?
                oldSecurity.getPasswordSettings().getExpirationPolicy() : null;
        if (expirationPolicy != null) {
            if (oldExpirationPolicy != null) {
                if (!expirationPolicy.equals(oldExpirationPolicy)) {
                    isPasswordExpiryDays = true;
                }
            } else {
                isPasswordExpiryDays = true;
            }
        } else if (oldExpirationPolicy != null) {
            isPasswordExpiryDays = true;
            //that mean the user removed PasswordExpirationPolicy. and we will set it to 0
            expirationPolicy = new PasswordExpirationPolicy();

        }
        if (isPasswordExpiryDays) {
            AccessConfigHolder.getInstance()
                    .addPasswordExpiryDays(Boolean.TRUE.equals(expirationPolicy.getEnabled()) ?
                            expirationPolicy.getPasswordMaxAge() : 0);
        }
        AccessConfigHolder.getInstance().setProxySettings(descriptor.getDefaultProxy());
        if (!AolUtils.isAol()) {
            AccessConfigHolder.getInstance().setAllProxies(descriptor.getProxies());
        }
    }

    @Override
    public void markCacheAsDirty() {
        descriptorCache.promoteVersion();
    }

    boolean doRepoDescriptorsUpgrade(CentralConfigDescriptor newDescriptor,
            Set<RepoConfig> repoConfigs) {

        var repoConfigUpgradeStorageHelper = RepoConfigBootstrapStorageHelperFactory.create();
        RepoDescriptorsUpgradeProcessor upgradeFlow =
                new RepoDescriptorsUpgradeProcessor(this::forceSaveMigratedDescriptor,
                        configsService, subConverters, repoConfigUpgradeStorageHelper, ContextHelper.get());

        if (upgradeFlow.doUpgradeIfNeeded((MutableCentralConfigDescriptor) newDescriptor)) {
            prepareToSendDataToAccess(newDescriptor, null);
            accessService.updateConfigurationToAccess();

            doRepoConfigsSaveIfNeeded((MutableCentralConfigDescriptor) newDescriptor, repoConfigs,
                    repoConfigUpgradeStorageHelper);
            return true;
        }
        return false;
    }

    private boolean doBackwardCompatibleImportIfNeeded(MutableCentralConfigDescriptor descriptor,
            CentralConfigDescriptor oldDescriptor) {
        RepoDescriptorsBackwardCompatibleImportProcessor importer =
                new RepoDescriptorsBackwardCompatibleImportProcessor(this::forceSaveMigratedDescriptor,
                        subConverters, repoConfigBCProcessor, ContextHelper.get());
        if (importer.doImportIfNeeded(descriptor)) {
            prepareToSendDataToAccess(descriptor, oldDescriptor);
            accessService.updateConfigurationToAccess();
            platformConfigService.updatePlatformConfigIfNeeded();
            return true;
        }
        return false;
    }

    void forceSaveDescriptorInternal(CentralConfigDescriptor newDescriptor, Set<RepoConfig> repoConfigs) {
        String configString = preSaveDescriptor(newDescriptor);
        prepareToSendDataToAccess(newDescriptor, null);
        accessService.updateConfigurationToAccess();

        var repoConfigUpgradeStorageHelper = RepoConfigBootstrapStorageHelperFactory.create();
        if (saveConfigIfNotExists(newDescriptor, configString)) {
            doRepoConfigsSaveIfNeeded((MutableCentralConfigDescriptor) newDescriptor, repoConfigs,
                    repoConfigUpgradeStorageHelper);
            return;
        }

        configsService.updateConfig(ArtifactoryHome.ARTIFACTORY_CONFIG_FILE, configString, newDescriptor.getRevision());
        doRepoConfigsSaveIfNeeded((MutableCentralConfigDescriptor) newDescriptor,
                repoConfigs, repoConfigUpgradeStorageHelper);
        log.info("New configuration forced saved.");
    }

    private void doRepoConfigsSaveIfNeeded(MutableCentralConfigDescriptor newDescriptor, Set<RepoConfig> repoConfigs,
            RepoConfigBootstrapStorageHelper repoConfigBootstrapStorageHelper) {
        if (!repoConfigs.isEmpty()) {
            repoConfigs.stream()
                    .filter(repoConfig -> RepoDetailsType.REMOTE.equals(repoConfig.getRepoType()))
                    .map(RemoteRepoConfig.class::cast)
                    .map(RemoteRepoConfig::getKey)
                    .forEach(newDescriptor::conditionallyAddToBackups);
            Authentication originalAuthentication = SecurityContextHolder.getContext().getAuthentication();
            try {
                SecurityContextHolder.getContext().setAuthentication(new SystemAuthenticationToken());
                repoConfigBootstrapStorageHelper.bulkInsertRepositoryConfigs(repoConfigs, false);
            } finally {
                SecurityContextHolder.getContext().setAuthentication(originalAuthentication);
            }
            repoConfigBootstrapStorageHelper.reloadRepoConfigurations();
        }
    }

    private void forceSaveMigratedDescriptor(CentralConfigDescriptor descriptor) {
        String configString = preSaveDescriptor(descriptor);
        if (saveConfigIfNotExists(descriptor, configString)) {
            markCacheAsDirty();
            return;
        }
        configsService.updateConfig(ArtifactoryHome.ARTIFACTORY_CONFIG_FILE, configString, descriptor.getRevision());
        log.info("Migrated configuration has saved.");
        markCacheAsDirty();
    }

    @Override
    public CentralConfigDescriptor getDescriptor() {
        return descriptorCache == null ? null : descriptorCache.get().descriptor;
    }

    @Override
    public DateTimeFormatter getDateFormatter() {
        PlatformGeneralConfig platformGeneralConfig = platformConfigService.getPlatformGeneralConfig();
        return DateTimeFormat.forPattern(platformGeneralConfig.getDateFormat());
    }

    @Override
    public String getServerName() {
        return descriptorCache.get().serverName;
    }

    @Override
    public String format(long date) {
        return getDateFormatter().print(date);
    }

    @Override
    public VersionInfo getServiceVersionInfo() {
        return new VersionInfo(ConstantValues.artifactoryServiceVersion.getString(),
                ConstantValues.artifactoryServiceRevision.getString(),
                ConstantValues.artifactoryServicePackageHandlerVersion.getString());
    }

    @Override
    public VersionInfo getProductVersionInfo() {
        return new VersionInfo(ConstantValues.artifactoryProductVersion.getString(),
                ConstantValues.artifactoryProductRevision.getString(),
                ConstantValues.artifactoryServicePackageHandlerVersion.getString());
    }

    @Override
    public String getConfigXml() {
        return JaxbHelper.toXml(descriptorCache.get().descriptor);
    }

    @Override
    public void setConfigXml(String xmlConfig) {
        setConfigXml(xmlConfig, false);
    }

    @Override
    public void setConfigXml(String xmlConfig, boolean isApiRequest) {
        Pair<MutableCentralConfigDescriptor, ArtifactoryConfigVersion> result =
                new CentralConfigReader().read(xmlConfig, ConstantValues.configXmlValidationEnabled.getBoolean(),
                        isApiRequest);
        validateRepoDescriptorsImport(result.getLeft(), result.getRight());
        saveAndReloadContextWithRetry(result.getLeft(), true);
        platformConfigService.updatePlatformConfigIfNeeded();
    }

    private void validateRepoDescriptorsImport(MutableCentralConfigDescriptor descriptor,
            ArtifactoryConfigVersion version) {
        int repoDescriptorsCount = ConfigDescriptorRepoUtils.getRepoDescriptorTotalCount(descriptor);
        boolean isNewConfig = !ConfigDescriptorRepoUtils.isRepoUpgradeShouldBeUpdated(version);
        if (isNewConfig && repoDescriptorsCount != 0) {
            throw new IllegalArgumentException(
                    "Cannot import configuration: config descriptor contains old repositories " +
                            "configuration, please use the new repositories export format instead.");
        }
    }

    private MutableCentralConfigDescriptor mergeDescriptors(
            CentralConfigDescriptor currentDescriptor,
            MutableCentralConfigDescriptor newDescriptor, boolean forceReplace) {
        log.debug("Checking if need to merge current version {} and new {}",
                currentDescriptor != null ? currentDescriptor.getRevision() : -1, newDescriptor.getRevision());

        if (forceReplace || currentDescriptor == null || newDescriptor.getRevision() <= 0 ||
                currentDescriptor.getRevision() == newDescriptor.getRevision()) {
            return this.getValidatedNewDescriptor(currentDescriptor, newDescriptor);
        }

        CentralConfigDescriptor baseDescriptor = latestRevisions.getIfPresent(newDescriptor.getRevision());
        if (baseDescriptor == null) {
            log.warn("Configuration update wasn't against the latest and the original version couldn't be found");
            return this.getValidatedNewDescriptor(currentDescriptor, newDescriptor);
        }

        log.info("Merging new changes of revision {} to current revision {}", baseDescriptor.getRevision(),
                currentDescriptor.getRevision());
        List<DataDiff<?>> diff = this.getValidatedDataDiffs(baseDescriptor, newDescriptor);
        return applyNewData(currentDescriptor, diff);
    }

    /**
     * This function is used for a single endpoint for getting the new descriptor
     * It fixes an issue where one config update could be in process while another is taking place, and thus
     * the first update may be lost
     *
     * @param currentDescriptor - Existing descriptor
     * @param newDescriptor     - The new descriptor to be written
     * @return newDescriptor
     */
    private MutableCentralConfigDescriptor getValidatedNewDescriptor(CentralConfigDescriptor currentDescriptor,
            MutableCentralConfigDescriptor newDescriptor) {
        if (currentDescriptor != null) {
            this.getValidatedDataDiffs(currentDescriptor, newDescriptor);
        }
        return newDescriptor;
    }

    private List<DataDiff<?>> getValidatedDataDiffs(CentralConfigDescriptor currentDescriptor,
            MutableCentralConfigDescriptor newDescriptor) {
        List<DataDiff<?>> diffs = DiffMerger.diffToDataDiff(findDiff(currentDescriptor, newDescriptor).getDiffs());
        this.centralConfigDiffValidation.validate(diffs);
        return diffs;
    }

    private MutableCentralConfigDescriptor applyNewData(CentralConfigDescriptor currentDescriptor,
            Collection<DataDiff<?>> diff) {
        CentralConfigDescriptorImpl newConfig =
                (CentralConfigDescriptorImpl) SerializationUtils.clone(currentDescriptor);
        DiffMerger.mergeDiffs(newConfig, diff);
        return newConfig;
    }

    @Override
    public void mergeAndSaveNewData(Collection<DataDiff<?>> diffs, boolean shouldValidate) {
        MutableCentralConfigDescriptor newDescriptor = applyNewData(getMutableDescriptor(), diffs);
        if (shouldValidate) {
            configValidationService.validate(newDescriptor);
        }
        saveEditedDescriptorAndReload(newDescriptor);
    }

    @Override
    public BaseUrlModel getPlatformBaseUrl() {
        return new BaseUrlModel(centralConfigService.getMutableDescriptor().getUrlBase());
    }

    @Override
    public ArtifactorySystemConfig getSystemConfiguration() {
        return systemConfig;
    }

    @Override
    public DiffResult findDiff(@Nonnull CentralConfigDescriptor oldDescriptor,
            @Nonnull CentralConfigDescriptor newDescriptor) {
        return ArtifactoryConfigDiffUtil.findDiff(oldDescriptor, newDescriptor);
    }

    @Override
    public void setLogo(File logo) throws IOException {
        ArtifactoryHome artifactoryHome = ContextHelper.get().getArtifactoryHome();
        File targetFile = new File(artifactoryHome.getLogoDir(), "logo");
        if (logo == null) {
            FileUtils.deleteQuietly(targetFile);
        } else {
            FileUtils.copyFile(logo, targetFile);
        }
    }

    @Override
    public MutableCentralConfigDescriptor getMutableDescriptor() {
        return (MutableCentralConfigDescriptor) SerializationUtils.clone(descriptorCache.get().descriptor);
    }

    @Override
    public void saveEditedDescriptorAndReload(MutableCentralConfigDescriptor descriptor, Object... changed) {
        for (Object changedCurr : changed) {
            this.configValidationService.validate(changedCurr);
        }

        if (descriptor == null) {
            throw new IllegalStateException("Currently edited descriptor is null.");
        }

        if (!securityService.isAnyProjectAdmin() && !securityService.hasMcScope()) {
            throw new ForbiddenException("Only an admin user can save the artifactory configuration.");
        }
        validateDescriptor(descriptor);

        saveAndReloadContextWithRetry(descriptor, false);
    }

    @Override
    public void reloadConfiguration() {
        reloadConfiguration(descriptorCache.get().descriptor, null);
    }

    private void reloadConfiguration(CentralConfigDescriptor oldDescriptor, CentralConfigDescriptor newDescriptor) {

        //When 2 threads are trying to save the descriptor at the same time, the second is rejected and retry.
        //Once rejected, it marks the descriptor cache as dirty.
        //The first thread, expects to get the old one from the cache, and mark the cache dirty, so downstream the flow
        //In the RtAppContext.reload(...) it expects the old and the new to have diff, so it will reload other beans
        //according to the diff.
        //But since some one else already dirty the cache,, it will get the new one, so it will finally compare
        //new to new, and reload beans will not triggered.
        //In order to resolve that, we call this method with the old descriptor,
        //So we will not need to reload it from the cache
        //We suspect that in some use cases, the old descriptor might be null, so in in this case we still load it from
        //the cache.

        if (oldDescriptor == null) {
            oldDescriptor = descriptorCache.get().descriptor;
        }
        markCacheAsDirty();
        if (oldDescriptor != null) {
            callReload(oldDescriptor);
        }
    }

    @Override
    public void reloadConfigurationLazy() {
        CentralConfigDescriptor oldDescriptor = descriptorCache.get().descriptor;
        markCacheAsDirty();
        // Async
        if (oldDescriptor != null) {
            ContextHelper.get().beanForType(InternalCentralConfigService.class).callReload(oldDescriptor);
        }
        // Async
        configChangeEventHandlerManager.handleCreateOrUpdateChanges(getMutableDescriptor(), oldDescriptor);
    }

    @Override
    public void callReload(CentralConfigDescriptor oldDescriptor) {
        InternalContextHelper.get().reload(oldDescriptor, List.of());
    }

    @Override
    public void importFrom(ImportSettings settings) {
        MutableStatusHolder status = settings.getStatusHolder();
        File dirToImport = settings.getBaseDir();
        //noinspection ConstantConditions
        if (dirToImport != null && dirToImport.isDirectory() && dirToImport.listFiles().length > 0) {
            status.status("Importing config...", log);
            File newConfigFile = new File(dirToImport.getPath(), ArtifactoryHome.ARTIFACTORY_CONFIG_FILE);
            if (newConfigFile.exists()) {
                status.status("Reloading configuration from " + newConfigFile, log);
                String xmlConfig = Files.readFileToString(newConfigFile);
                MutableCentralConfigDescriptor mutableCentralConfigDescriptor = setConfigXml(settings, xmlConfig);
                if (ImportKind.HYBRID_MIGRATION.equals(settings.getImportKind())) {
                    mutableCentralConfigDescriptor =
                            sanitizeAndSetConfigXml((CentralConfigDescriptorImpl) getDescriptor(),
                                    mutableCentralConfigDescriptor);
                }
                saveAndReloadContextWithRetry(mutableCentralConfigDescriptor, true);
                status.status("Configuration reloaded from " + newConfigFile, log);
            }
        } else if (settings.isFailIfEmpty()) {
            String error = "The given base directory is either empty, or non-existent";
            throw new IllegalArgumentException(error);
        }
    }

    @VisibleForTesting
    MutableCentralConfigDescriptor sanitizeAndSetConfigXml(CentralConfigDescriptorImpl targetDescriptor,
            MutableCentralConfigDescriptor mutableCentralConfigDescriptor) {
        return merge(mutableCentralConfigDescriptor, targetDescriptor);
    }

    private MutableCentralConfigDescriptor setConfigXml(ImportSettings settings, String xmlConfig) {
        Pair<MutableCentralConfigDescriptor, ArtifactoryConfigVersion> result =
                new CentralConfigReader().read(xmlConfig, ConstantValues.configXmlValidationEnabled.getBoolean(),
                        false);
        validateRepoDescriptorsImport(result.getLeft(), result.getRight());
        validateRepositoriesImport(result.getLeft(), settings);
        return result.getLeft();
    }

    private void validateRepositoriesImport(MutableCentralConfigDescriptor newDescriptor, ImportSettings settings) {
        Path reposConfigFilePath =
                Paths.get(settings.getBaseDir().getAbsolutePath(), ArtifactoryHome.ARTIFACTORY_REPO_CONFIG_FILE);
        if (java.nio.file.Files.exists(reposConfigFilePath)) {
            int repoDescriptorsCount = ConfigDescriptorRepoUtils.getRepoDescriptorTotalCount(newDescriptor);
            if (repoDescriptorsCount > 0) {
                throw new IllegalArgumentException(
                        "Cannot import configuration: 'artifactory.config.xml' contains old repositories " +
                                "configuration, please use the new repositories export format instead.");
            }
        }
    }

    @Override
    public void exportTo(ExportSettings settings) {
        MutableStatusHolder status = settings.getStatusHolder();
        status.status("Exporting config...", log);
        File destFile = new File(settings.getBaseDir(), ArtifactoryHome.ARTIFACTORY_CONFIG_FILE);
        JaxbHelper.writeConfig(descriptorCache.get().descriptor, destFile);
    }

    void saveAndReloadContextWithRetry(MutableCentralConfigDescriptor newDescriptor, boolean forceReplace) {
        try {
            ExecutorService currentThreadExecutorService = MoreExecutors.newDirectExecutorService();
            ExecutionUtils.retry(
                    () -> {
                        saveAndReloadContext(SerializationUtils.clone(newDescriptor), forceReplace);
                        return null;
                    }, ExecutionUtils.RetryOptions.builder()
                            .numberOfRetries(ConstantValues.centralConfigSaveNumberOfRetries.getInt())
                            .backoffMaxDelay(ConstantValues.centralConfigSaveBackoffMaxDelay.getInt())
                            .exponentialBackoffMultiplier(ConstantValues.centralConfigSaveBackoffMultiplier.getInt())
                            .failedLog("Last retry failed: ${ERROR}. Not trying again")
                            .build(),
                    currentThreadExecutorService).get();

            cachedThreadPoolTaskExecutor.submit(() -> storeLatestConfigToFile(getConfigXml()));
            if (!ContextHelper.get().isCentralConfigImportInProgress()) {
                addonsManager.addonByType(HaAddon.class).propagateConfigReload();
            }
        } catch (InterruptedException | ExecutionException e) {
            // If the error is ConfigurationException then we shouldn't throw 500
            // but 400 bad request.
            Throwable cause = e.getCause();
            String message = "Could not merge and save new descriptor [" + e.getMessage() + "]";
            if (cause != null && cause.getCause() instanceof ConfigurationException) {
                throw new BadRequestException(message, e);
            }
            throw new IllegalStateException(message, e);
        }
    }

    @SuppressWarnings("UnnecessaryLocalVariable")
    private void saveAndReloadContext(MutableCentralConfigDescriptor newDescriptor, boolean forceReplace)
            throws RetryException {
        // get data once ! We use single state per update taken by the current descriptor.
        // Since we resolve collsion in the save (update where revision= ?).
        CentralConfigDescriptor currentDescriptor = getDescriptor();
        if (log.isTraceEnabled()) {
            log.trace("TEST: current descriptor version is {}", currentDescriptor.getRevision());
        }
        MutableCentralConfigDescriptor merged = mergeDescriptors(currentDescriptor, newDescriptor, forceReplace);
        CentralConfigDescriptor oldDescriptor = currentDescriptor;

        long oldRevision = bumpRevision(oldDescriptor, merged);
        if (log.isTraceEnabled()) {
            log.trace("TEST: version after bump is {}", oldRevision);
        }
        //Reload only if all single artifactory or unique schema version in Artifactory HA cluster
        log.info("Reloading configuration... old revision {}, new revision {}", oldRevision, merged.getRevision());

        try {
            if (oldDescriptor == null) {
                throw new IllegalStateException("The system was not loaded, and a reload was called");
            }
            if (log.isDebugEnabled()) {
                log.debug("SaveDescriptor( oldDescriptor rev is:{}, merged rev is:{}) bumped from  rev: {}  by rev: {}",
                        oldDescriptor.getRevision(),
                        merged.getRevision(),
                        oldRevision,
                        newDescriptor.getRevision()
                );
            }
            ConfigDescriptorChangeWorkItem createOrUpdateChanges =
                    notifyReposOnDeleteConfigChange(merged, oldDescriptor);
            saveDescriptor(oldDescriptor, merged);
            configChangeEventHandlerManager.handle(createOrUpdateChanges);

            // TODO: [by FSI] If reload fails, we have the new descriptor in memory but not used
            // Need to find ways to revert or be very robust on reload.
            log.info("Configuration reloaded.");
            AccessLogger.configurationChanged();

            if (log.isDebugEnabled()) {
                log.debug("Old configuration:\n{}", JaxbHelper.toXml(oldDescriptor));
                log.debug("New configuration:\n{}", JaxbHelper.toXml(merged));
            }
        } catch (ConfigurationException e) {
            String msg = "Failed to reload configuration:" + e.getMessage();
            log.warn(msg);
            log.debug(msg, e);
            throw e;
        } catch (Exception e) {
            if (e instanceof RetryException) {
                log.debug("Couldn't save configuration with revision {} to database. Retrying...",
                        merged.getRevision());
                throw e;
            }
            String msg = "Failed to reload configuration: " + e.getMessage();
            if (ExceptionUtils.getCauseOfType(e, XrayOperationException.class) == null) {
                log.error(msg);
                log.debug(msg, e);
            }
            throw new RuntimeException(msg, e);
        }
    }

    private long bumpRevision(CentralConfigDescriptor oldDescriptor, MutableCentralConfigDescriptor newDescriptor) {
        long oldRevision = newDescriptor.getRevision();
        latestRevisions.put(oldRevision, oldDescriptor);
        newDescriptor.setRevision(oldRevision + 1);

        return oldRevision;
    }

    @Override
    public <T extends Enum<T> & StringBasedEnum> T getReloadablePropertyByEnum(Class<T> enumType,
            ConstantValues constantValue) {
        CentralConfigDescriptor descriptor = getDescriptor();
        if (descriptor == null) {
            return null;
        }
        Optional<SystemPropertyDescriptor> first = StreamSupportUtils.stream(descriptor.getSystemProperties())
                .filter(systemProperty -> systemProperty.getKey().equalsIgnoreCase(constantValue.getPropertyName()))
                .findFirst();
        if (first.isPresent()) {
            String value = first.get().getValue();
            T enumValue = getEnumValue(enumType, value);
            if (enumValue != null) {
                return enumValue;
            }
        }
        return getEnumValue(enumType, constantValue.getString());
    }

    private ConfigDescriptorChangeWorkItem notifyReposOnDeleteConfigChange(MutableCentralConfigDescriptor merged,
            CentralConfigDescriptor oldDescriptor) {
        var systemConfigEvent = new SystemConfigChangeEvent(ARTIFACTORY_CONFIG_DESCRIPTOR, oldDescriptor, merged);
        var systemConfigChangeWorkItem = new ConfigChangeWorkItem(systemConfigEvent);
        return configChangeEventHandlerManager.handleOnlyConfigDescriptorDeleteChange(systemConfigChangeWorkItem);
    }

    private void notifyReposOnConfigChangeDuringBootstrap(MutableCentralConfigDescriptor newConfigDescriptor,
            MutableCentralConfigDescriptor oldConfigDescriptor) {
        var systemConfigEvent = new SystemConfigChangeEvent(ARTIFACTORY_CONFIG_DESCRIPTOR, oldConfigDescriptor,
                newConfigDescriptor);
        var systemConfigChangeWorkItem = new ConfigChangeWorkItem(systemConfigEvent);
        Collection<ConfigChangeEventHandler> configChangeEventHandlers =
                ContextHelper.get().beansForType(ConfigChangeEventHandler.class).values();
        ConfigChangeEventHandlerManager bootStrapConfigChangeEventHandlerManager =
                new ConfigChangeEventHandlerManagerImpl(configChangeEventHandlers, SYSTEM_STARTUP);
        bootStrapConfigChangeEventHandlerManager.handle(systemConfigChangeWorkItem);
    }

    private void storeLatestConfigToFile(String configXml) {
        try {
            int maxFiles = ConstantValues.fileRollerMaxFilesToRetain.getInt();
            if (maxFiles < 0) {
                log.warn("A negative integer value '{}' was provided for '{}'. Ignoring and falling back to '{}'.",
                        maxFiles, ConstantValues.fileRollerMaxFilesToRetain.getPropertyName(),
                        ConstantValues.fileRollerMaxFilesToRetain.getDefValue());
                maxFiles = Integer.parseInt(ConstantValues.fileRollerMaxFilesToRetain.getDefValue());
            }

            org.jfrog.common.FileUtils.writeContentToRollingFile(
                    configXml, ArtifactoryHome.get().getArtifactoryConfigLatestFile(), maxFiles);
        } catch (IOException e) {
            log.error("Error occurred while performing a backup of the latest configuration.", e);
        }
    }

    private void checkUniqueProxies(CentralConfigDescriptor descriptor) {
        List<ProxyDescriptor> proxies = descriptor.getProxies();
        Map<String, ProxyDescriptor> map = new HashMap<>(proxies.size());
        for (ProxyDescriptor proxy : proxies) {
            String key = proxy.getKey();
            ProxyDescriptor oldProxy = map.put(key, proxy);
            if (oldProxy != null) {
                throw new RuntimeException("Duplicate proxy key in configuration: " + key + ".");
            }
        }
    }

    private CentralConfigDescriptorCache getCentralConfigDescriptorCache(boolean withValidation) {
        log.debug("Starting to load central config");
        try {
            String currentConfigXml = loadConfigFromStorage();
            if (!StringUtils.isBlank(currentConfigXml)) {
                MutableCentralConfigDescriptor config =
                        new CentralConfigReader().read(currentConfigXml, withValidation, false).getLeft();
                String serverName = config.getServerName();
                if (serverName == null) {
                    log.debug("No custom server name in configuration. Using hostname instead.");
                    try {
                        serverName = InetAddress.getLocalHost().getHostName();
                    } catch (UnknownHostException e) {
                        log.warn("Could not use local hostname as the server instance id: {}", e.getMessage());
                        serverName = "localhost";
                    }
                }
                log.debug("Successfully finished load central config");
                return new CentralConfigDescriptorCache(config, serverName);
            }
            log.info("No Central configuration found in the database, using default configuration.");
        } catch (Exception e) {
            log.error("Could not load configuration due to: " + e.getMessage(), e);
        }
        return new CentralConfigDescriptorCache(null, null);
    }

    @Override
    public void onContextCreated() {
        if (loadLicFromYaml) {
            loadLicFromYaml = false;
            onboardingYamlBootstrapper.loadLicenseFromYAML();
            //we don't need it anymore allow gc to remove.
            onboardingYamlBootstrapper = null;
        }
        isContextCreated = true;
    }

    @Override
    public Integer getListeningPort() {
        return listeningPort.get();
    }

    @Override
    public boolean isContextCreated() {
        return isContextCreated;
    }

    @Override
    public ProxyDescriptor getServiceProxyByKey(String serviceKey) {
        return getDescriptor().getProxies().stream()
                .filter(proxy -> (proxy.isPlatformDefault() || proxy.getServicesList().contains(serviceKey)))
                .findFirst()
                .orElse(null);
    }

    public static class CentralConfigDescriptorCache implements BasicCacheModel {

        public final CentralConfigDescriptor descriptor;
        public final String serverName;
        private volatile long version;

        public CentralConfigDescriptorCache(CentralConfigDescriptor descriptor, String serverName) {
            this.descriptor = descriptor;
            this.serverName = serverName;
        }

        @Override
        public long getVersion() {
            return version;
        }

        @Override
        public void setVersion(long version) {
            this.version = version;
        }

        @Override
        public void destroy() {
            // nop
        }
    }

    /**
     * Checks if given descriptor is valid xml (including xsd validation).
     * @param configDescriptor descriptor to validate
     */
    @VisibleForTesting
    void validateDescriptor(MutableCentralConfigDescriptor configDescriptor) {
        if (!ConstantValues.configDescriptorXSDValidationEnabled.getBoolean()) {
            return;
        }
        try {
            String xmlString = JaxbHelper.toXml(configDescriptor);
            JaxbHelper.readConfig(xmlString, ConstantValues.configXmlValidationEnabled.getBoolean());
        } catch (Exception e) {
            Throwable rootCause = ExceptionUtils.getRootCause(e);
            log.warn("Error occurred while validating the descriptor", rootCause);
            throw new ConfigurationException("Found config violations: " + rootCause.getMessage());
        }
    }

    /**
     * Returns true if the current Artifactory instance is converting state, false otherwise.
     */
    private boolean isConverting() {
        ArtifactoryServersCommonService artifactoryServersCommonService =
                ContextHelper.get().beanForType(ArtifactoryServersCommonService.class);
        return artifactoryServersCommonService.getCurrentMember() != null &&
                artifactoryServersCommonService.getCurrentMember().getServerState()
                        == ArtifactoryServerState.CONVERTING;
    }
}