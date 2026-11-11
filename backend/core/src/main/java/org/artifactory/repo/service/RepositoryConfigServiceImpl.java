package org.artifactory.repo.service;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.DiffResult;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.HaAddon;
import org.artifactory.addon.curation.CurationAddon;
import org.artifactory.addon.ha.HaCommonAddon;
import org.artifactory.addon.plugin.pkgtype.PluginPackageTypes;
import org.artifactory.concurrent.ConflictsGuardUtils;
import org.jfrog.common.concurrency.ConflictsGuard;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.config.InternalRepositoryConfigService;
import org.artifactory.api.config.RepositoryConfigService;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.status.MutableStatusHolder;
import org.artifactory.federation.FederatedRepoService;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.config.ArtifactoryConfigDiffUtil;
import org.artifactory.descriptor.config.CentralConfigDescriptor;
import org.artifactory.descriptor.config.MutableCentralConfigDescriptor;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.metrics.MetricsInfo;
import org.artifactory.metrics.MetricsThreadLocal;
import org.artifactory.model.AggregatedReposConfig;
import org.artifactory.model.BasicRepoConfig;
import org.artifactory.model.FederatedRepoConfig;
import org.artifactory.model.FederatedRepositoryRepoTypeConfig;
import org.artifactory.model.LocalCacheRepoConfig;
import org.artifactory.model.LocalConfig;
import org.artifactory.model.LocalRepoConfig;
import org.artifactory.model.RealRepoConfig;
import org.artifactory.model.ReleaseBundleRepoConfig;
import org.artifactory.model.RemoteRepoConfig;
import org.artifactory.model.RepoConfig;
import org.artifactory.model.RepoConfigPropagationModel;
import org.artifactory.model.VirtualRepoConfig;
import org.artifactory.projects.ProjectsService;
import org.artifactory.projects.ProjectsUtils;
import org.artifactory.repo.ExtendedRepositoryConfig;
import org.artifactory.repo.ReloadableRepositoryConfigService;
import org.artifactory.repo.RepoConfigChange;
import org.artifactory.repo.metadata.RepoConfigChangeFactory;
import org.artifactory.repo.RepoConfigChangesReload;
import org.artifactory.repo.RepoConfigChangesReloadFactory;
import org.artifactory.repo.RepoConfigReload;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.repo.cache.RepoConfigCache;
import org.artifactory.repo.cache.RepoConfigCacheDiffCalculator;
import org.artifactory.repo.converter.RepositoriesConfigSubConverter;
import org.artifactory.repo.converter.RepositoriesConverterManager;
import org.artifactory.repo.interceptor.RepoConfigChangeInterceptor;
import org.artifactory.repo.interceptor.RepoConfigChangeValidator;
import org.artifactory.repo.interceptor.RepoConfigChanges;
import org.artifactory.repo.packagetype.ExternalDependenciesConfig;
import org.artifactory.repo.packagetype.PomCleanupPolicy;
import org.artifactory.repo.packagetype.configuration.MavenConfiguration;
import org.artifactory.repo.propagation.RepoConfigPropagationModelFilter;
import org.artifactory.repo.system.SystemRepos;
import org.artifactory.repo.system.suppliers.BuildInfoRepo;
import org.artifactory.repo.virtual.VirtualRepositoryConfigResolverImpl;
import org.artifactory.exception.http.BadRequestException;
import org.artifactory.exception.http.NotFoundException;
import org.artifactory.sapi.common.ExportSettings;
import org.artifactory.sapi.common.ImportSettings;
import org.artifactory.security.SystemAuthenticationToken;
import org.artifactory.service.ConfigValidationService;
import org.artifactory.spring.Reloadable;
import org.artifactory.spring.RepoConfigChangeListener;
import org.artifactory.storage.db.repo.entity.DbRepositoryConfig;
import org.artifactory.storage.db.repo.service.RepositoryConfigStorageService;
import org.artifactory.storage.db.repo.service.mapper.RepositoryConfigAttributesMapper;
import org.artifactory.storage.db.repo.service.mapper.RepositoryConfigAttributesMapperImpl;
import org.artifactory.storage.db.repo.service.mapper.RepositoryConfigMapper;
import org.artifactory.storage.db.repo.service.mapper.RepositoryConfigMapperImpl;
import org.artifactory.threadlocal.cache.SupplierWithError;
import org.artifactory.util.CollectionUtils;
import org.artifactory.util.ExternalDependenciesPackageTypeConfigMapper;
import org.artifactory.util.FederatedMemberUtils;
import org.artifactory.util.bundles.BundleConfigurationUtil;
import org.artifactory.version.CompoundVersionDetails;
import org.jfrog.common.JsonUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;

import static org.artifactory.common.ConstantValues.removeRepositoryFlowV2Enabled;
import static org.artifactory.common.ConstantValues.repoConfigCacheGetAllOptimizationRatio;
import static org.artifactory.common.ConstantValues.repoConfigCacheSyncUpMillis;
import static org.artifactory.common.ConstantValues.repoConfigChangeVerifyOnGetAllRepoKeysSinceSize;
import static org.artifactory.common.ConstantValues.repoConfigDbBatchSize;
import static org.artifactory.common.ConstantValues.repoConfigExpirationMillis;
import static org.artifactory.common.ConstantValues.skipExampleRepoCreation;
import static org.artifactory.concurrent.NonStandardLockCategory.REPO_CREATION;
import static org.artifactory.repo.RepoConfigChange.ConfigAction.REPO_CREATE;
import static org.artifactory.repo.RepoConfigChange.ConfigAction.REPO_DELETE;
import static org.artifactory.repo.RepoConfigChange.ConfigAction.REPO_UPDATE;
import static org.artifactory.repo.RepoDetailsType.FEDERATED;
import static org.artifactory.repo.RepoDetailsType.LOCAL;
import static org.artifactory.repo.RepoDetailsType.RELEASE_BUNDLE;
import static org.artifactory.repo.RepoDetailsType.REMOTE;
import static org.artifactory.repo.RepoDetailsType.VIRTUAL;
import static org.artifactory.repo.RepoPath.REMOTE_CACHE_SUFFIX;
import static org.artifactory.repo.service.RepositoryConfigServiceHelper.generateExampleRepoLocalConfig;
import static org.artifactory.repo.service.RepositoryConfigServiceHelper.isHiddenRepo;
import static org.artifactory.repo.service.RepositoryConfigServiceHelper.isInfoRepo;
import static org.artifactory.repo.service.validation.RepositoryNameConfigurationValidator.validateInternalRepoName;
import static org.artifactory.repo.system.SystemRepos.SystemRepoConfig.getExcludedFromImportSystemRepos;
import static org.artifactory.repo.system.SystemRepos.SystemRepoConfig.isSystemRepo;
import static org.artifactory.repo.system.SystemRepos.isSupportBundle;
import static org.artifactory.throtlling.qrl.enums.QueryRateLimiterType.SYSTEM;
import static org.artifactory.throttling.qrl.service.QueryRateLimiterServiceImpl.withQueryRateLimiter;
import static org.artifactory.util.RepoPathUtils.isCache;
import static org.artifactory.util.RepoPathUtils.isTrash;

/**
 * @author Tamar Rosen
 */
@Slf4j
@Service
@Primary
@Reloadable(beanClass = ReloadableRepositoryConfigService.class, initAfter = {RepositoryConfigBootstrapService.class})
public class RepositoryConfigServiceImpl
        implements InternalRepositoryConfigService, ReloadableRepositoryConfigService, RepoConfigChangesMapFunction {
    private static final int REPO_LOCK_TIMEOUT_SECS = 0;

    private final RepositoryConfigStorageService storageService;
    private final Set<RepositoriesConfigSubConverter> subConverters;
    private final RepositoryConfigMapper repositoryConfigMapper;
    private final RepositoryConfigAttributesMapper repoConfigAttributesMapper;
    private final AddonsManager addonsManager;
    private Set<RepoConfigChangeListener> listeners = new HashSet<>();
    private Set<RepoConfigChangeValidator> validators = new HashSet<>();
    private Set<RepoConfigChangeInterceptor> interceptors = new HashSet<>();
    private ConfigValidationService configValidationService;
    private FederatedRepoService federatedRepoService;
    @Nullable
    private RepoConfigChangeFactory repoConfigChangeFactory;
    private volatile RepoConfigCache repoConfigCache;
    private final ReentrantLock repoCacheCreateLock = new ReentrantLock();

    @Autowired
    public RepositoryConfigServiceImpl(RepositoryConfigStorageService storageService,
            Set<RepositoriesConfigSubConverter> subConverters, AddonsManager addonsManager) {
        this.storageService = storageService;
        this.subConverters = subConverters;
        this.addonsManager = addonsManager;
        this.repositoryConfigMapper = new RepositoryConfigMapperImpl();
        this.repoConfigAttributesMapper = new RepositoryConfigAttributesMapperImpl();
        createRepoConfigCache();
    }

    @Override
    public void onBeforeContextCreated() {
        if (countAll() == 0) {
            createDefaultRepoConfigsIfDoesNotExist();
        }
    }

    @Override
    public void init() {
        listeners =
                new HashSet<>(ContextHelper.get().beansForType(RepoConfigChangeListener.class).values());
        validators = new HashSet<>(ContextHelper.get().beansForType(RepoConfigChangeValidator.class).values());
        interceptors = new HashSet<>(ContextHelper.get().beansForType(RepoConfigChangeInterceptor.class).values());
    }

    private RepoConfigCache createRepoConfigCache() {
        RepoConfigCache oldCache = this.repoConfigCache;
        try {
            log.debug("Creating repo cache. Acquire lock...");
            repoCacheCreateLock.lock();
            log.debug("Lock has been acquired. Creating repo cache instance...");
            this.repoConfigCache = new RepoConfigCache(
                    repoConfigCacheSyncUpMillis.getLong(),
                    repoConfigExpirationMillis.getLong(),
                    repoConfigDbBatchSize.getInt(),
                    Double.parseDouble(repoConfigCacheGetAllOptimizationRatio.getString()),
                    this::getAllRepoConfigsWithoutCache,
                    this::getRepoByKeyWithoutCache,
                    this::getRepoConfigsByKeysWithoutCache,
                    this::callOnRepoConfigReloadAsync,
                    this::loadAllBasicRepoConfigsFromDb);
            if (oldCache != null) {
                oldCache.destroy();
            }
        } finally {
            log.debug("Repo cache has been created. Releasing lock...");
            repoCacheCreateLock.unlock();
            log.debug("Lock has been released");
        }
        return oldCache;
    }

    @Override
    public void destroy() {
        repoConfigCache.destroy();
    }

    @VisibleForTesting
    void setRepoConfigCache(RepoConfigCache repoConfigCache) {
        this.repoConfigCache = repoConfigCache;
    }

    @Autowired
    public void setConfigValidationService(ConfigValidationService configValidationService) {
        this.configValidationService = configValidationService;
    }

    @Autowired
    public void setFederatedRepoService(FederatedRepoService federatedRepoService) {
        this.federatedRepoService = federatedRepoService;
    }

    /**
     * Wired in production by Spring. Left optional so unit-test harnesses that construct the service directly do not
     * have to pass a factory; in that case the {@link RepoConfigChange} objects we publish carry no
     * {@link org.artifactory.repo.RepoMetadata} and downstream consumers fall back to
     * {@link org.artifactory.repo.RepoMetadata#empty()}.
     */
    @Autowired(required = false)
    public void setRepoConfigChangeFactory(RepoConfigChangeFactory repoConfigChangeFactory) {
        this.repoConfigChangeFactory = repoConfigChangeFactory;
    }

    @Override
    public List<RepoConfig> getRepoConfigsByType(RepoDetailsType repoType) {
        Set<String> filteredRepoKeys = getFilteredRepoKeysByTypes(Set.of(repoType)).collect(toSet());
        return new ArrayList<>(repoConfigCache.getRepositoryConfigsByKeys(filteredRepoKeys));
    }

    @Override
    public List<RepoConfig> getRepoConfigsByTypes(Set<RepoDetailsType> reposTypes) {
        Set<String> filteredRepoKeys = getFilteredRepoKeysByTypes(reposTypes).collect(toSet());
        return new ArrayList<>(repoConfigCache.getRepositoryConfigsByKeys(filteredRepoKeys));
    }

    @Override
    public List<RepoConfig> getRepoConfigsByTypesAndProject(Set<RepoDetailsType> reposTypes, String projectKey) {
        Stream<BasicRepoConfig> repoConfigStream = repoConfigCache.getAllBasicConfigs().stream()
                .filter(repoConfig -> reposTypes.contains(repoConfig.getRepoType()));
        repoConfigStream = filterBasicConfigByProject(repoConfigStream, projectKey);
        Set<String> filteredRepoKeys = repoConfigStream.map(BasicRepoConfig::getRepoKey).collect(toSet());
        return new ArrayList<>(repoConfigCache.getRepositoryConfigsByKeys(filteredRepoKeys));
    }

    @Override
    public List<RepoConfig> getRepoConfigsByProjectAndType(Function<String, Boolean> projectFilter,
            Set<RepoDetailsType> types) {
        Set<String> filteredRepoKeys = getFilteredRepoKeysByProjectAndTypes(projectFilter, types).collect(toSet());
        return new ArrayList<>(repoConfigCache.getRepositoryConfigsByKeys(filteredRepoKeys));
    }

    @Override
    public List<String> getRepoConfigKeysByProjectAndType(Function<String, Boolean> projectFilter,
            Set<RepoDetailsType> types) {
        return getFilteredRepoKeysByProjectAndTypes(projectFilter, types).toList();
    }

    @VisibleForTesting
    Stream<String> getFilteredRepoKeysByTypes(Set<RepoDetailsType> repoTypes) {
        return repoConfigCache.getAllBasicConfigs().stream()
                .filter(repoConfig -> repoTypes.contains(repoConfig.getRepoType()))
                .map(BasicRepoConfig::getRepoKey);
    }

    @VisibleForTesting
    Stream<String> getFilteredRepoKeysByProjectAndTypes(Function<String, Boolean> projectFilter,
            Set<RepoDetailsType> repoTypes) {
        if (projectFilter == null) {
            return getFilteredRepoKeysByTypes(repoTypes);
        }
        return getFilteredRepoKeysByTypes(repoTypes).filter(projectFilter::apply);
    }

    @Override
    public List<RepoConfig> getOrderedRepoConfigsByTypes(LinkedHashSet<RepoDetailsType> reposTypes) {
        Map<RepoDetailsType, List<RepoConfig>> repoDetailsTypeListMap = getRepoConfigsByTypes(reposTypes).stream()
                .collect(Collectors.groupingBy(RepoConfig::getRepoType));
        List<RepoConfig> orderedRepoConfigs = new ArrayList<>();
        for (RepoDetailsType type : reposTypes) {
            List<RepoConfig> configs = repoDetailsTypeListMap.get(type);
            if (configs != null) {
                orderedRepoConfigs.addAll(configs);
            }
        }
        return orderedRepoConfigs;
    }

    @Override
    public List<RepoConfig> getAllRepoConfigsBySearchPattern(String search) {
        return getRepoConfigsBySearchPatternInternal(null, null, null, search);
    }

    @Override
    public List<RepoConfig> getRepoConfigsBySearchPattern(Set<RepoDetailsType> repoDetailsTypes, String projectKey,
            String search) {
        return getRepoConfigsBySearchPatternInternal(repoDetailsTypes, null, projectKey, search);
    }

    @Override
    public List<RepoConfig> getRepoConfigsBySearchPattern(Set<RepoDetailsType> type, Set<RepoType> packageTypes,
            String projectKey, String search) {
        return getRepoConfigsBySearchPatternInternal(type, packageTypes, projectKey, search);
    }

    private List<RepoConfig> getRepoConfigsBySearchPatternInternal(Set<RepoDetailsType> repoDetailsTypes,
            Set<RepoType> repoTypes, String projectKey, String search) {
        Stream<BasicRepoConfig> repoConfigs = repoConfigCache.getAllBasicConfigs().stream();
        if (repoDetailsTypes != null) {
            repoConfigs = repoConfigs.filter(repoConfig -> repoDetailsTypes.contains(repoConfig.getRepoType()));
        }
        if (repoTypes != null) {
            repoConfigs = repoConfigs.filter(repoConfig -> repoTypes.contains(repoConfig.getPackageType()));
        }
        if (projectKey != null) {
            repoConfigs = filterBasicConfigByProject(repoConfigs, projectKey);
        }
        Stream<String> repoKeyStream = repoConfigs.map(BasicRepoConfig::getRepoKey);
        Set<String> filteredRepoKeys = filterByPattern(repoKeyStream, search).collect(toSet());
        if (filteredRepoKeys.isEmpty()) {
            return Collections.emptyList();
        }
        return new ArrayList<>(repoConfigCache.getRepositoryConfigsByKeys(filteredRepoKeys));
    }

    private Stream<String> filterByPattern(Stream<String> repoConfigs, String search) {
        if (StringUtils.isBlank(search)) {
            return repoConfigs;
        }
        return repoConfigs.filter(repoKey -> StringUtils.containsIgnoreCase(repoKey, search));
    }

    @Override
    public List<RepoConfig> getRepoConfigsByPackageType(RepoType packageType) {
        Collection<BasicRepoConfig> allBasicRepoConfigs = repoConfigCache.getAllBasicConfigs();
        Set<String> filteredRepoKeys = allBasicRepoConfigs.stream()
                .filter(repoConfig -> packageType.equals(repoConfig.getPackageType()))
                .map(BasicRepoConfig::getRepoKey)
                .collect(toSet());
        return new ArrayList<>(repoConfigCache.getRepositoryConfigsByKeys(filteredRepoKeys));
    }

    @Override
    public List<RepoConfig> getRepoConfigsByTypeAndPackageType(Set<RepoDetailsType> type, RepoType packageType) {
        return new ArrayList<>(
                repoConfigCache.getRepositoryConfigsByKeys(getRepoConfigKeysByTypeAndPackageType(type, packageType)));
    }

    @Override
    public Set<String> getRepoConfigKeysByTypeAndPackageType(Set<RepoDetailsType> type, RepoType packageType) {
        Collection<BasicRepoConfig> allBasicRepoConfigs = repoConfigCache.getAllBasicConfigs();
        return allBasicRepoConfigs.stream()
                .filter(repoConfig -> packageType.equals(repoConfig.getPackageType()) &&
                        type.contains(repoConfig.getRepoType()))
                .map(BasicRepoConfig::getRepoKey)
                .collect(toSet());
    }

    @Override
    public Set<String> getRepoConfigKeysByType(Set<RepoDetailsType> type) {
        Collection<BasicRepoConfig> allBasicRepoConfigs = repoConfigCache.getAllBasicConfigs();
        return allBasicRepoConfigs.stream()
                .filter(repoConfig -> type.contains(repoConfig.getRepoType()))
                .map(BasicRepoConfig::getRepoKey)
                .collect(toSet());
    }

    @Override
    public void insertRepoConfig(RepoConfig repositoryConfig) {
        if (repositoryConfig == null) {
            log.warn("Cannot insert repository: repository config cannot be null");
            if (log.isTraceEnabled()) {
                log.trace("Cannot insert repository: repository config cannot be null", new NullPointerException());
            }
            return;
        }
        badRequestValidation(repositoryConfig);
        if (ContextHelper.get().isReposImportInProgress()) {
            log.info("Cannot insert repository config '{}', import is still in progress", repositoryConfig.getKey());
            return;
        }
        globalValidation(Set.of(repositoryConfig));
        withRepoLock(repositoryConfig.getKey(), () -> {
            if (repositoryConfig instanceof RemoteRepoConfig remoteRepoConfig) {
                insertRemoteRepoConfig(remoteRepoConfig);
            } else {
                insertRepositoryInternal(repositoryConfig);
            }
        });
    }

    @Override
    public void upsertRepoConfig(RepoConfig repositoryConfig) {
        if (repositoryConfig == null) {
            log.warn("Cannot upsert repository: repository config cannot be null");
            if (log.isTraceEnabled()) {
                log.trace("Cannot upsert repository: repository config cannot be null", new NullPointerException());
            }
            return;
        }
        if (ContextHelper.get().isReposImportInProgress()) {
            log.info("Cannot upsert repository config '{}', import is still in progress",
                    repositoryConfig.getKey());
            return;
        }
        globalValidation(Set.of(repositoryConfig));

        withRepoLock(repositoryConfig.getKey(), () -> upsertRepoConfigInternal(repositoryConfig));
    }

    private void upsertRepoConfigInternal(RepoConfig repositoryConfig) {
        RepoConfig oldRepoConfig = getRepoConfigByKeyNoValidationChecks(repositoryConfig.getKey(), false);
        boolean isInsert = oldRepoConfig == null;
        if (isInsert && isCache(repositoryConfig.getKey())) {
            throw new BadRequestException(
                    "Unable to create a " + repositoryConfig.getRepoType().typeNameLowercase() + " repository '" +
                            repositoryConfig.getKey() + "' with '-cache' suffix");
        }
        if (isBlankTrashOrSupportBundle(repositoryConfig.getKey())) {
            throw new BadRequestException(String.format(
                    "'%s' repository, the repo name is forbidden", repositoryConfig.getKey()));
        }
        if (repositoryConfig instanceof RemoteRepoConfig remoteRepoConfig) {
            assertCurationIsValid(remoteRepoConfig);
            remoteRepoConfig.processUrlCredentials();
        }
        if (isInsert && !repositoryConfig.getRepoType().equals(VIRTUAL)) {
            setBackups(repositoryConfig.getKey());
        }
        addXrayNotAvailableModeIfNeeded(repositoryConfig, oldRepoConfig);
        RepoConfigChange repoConfigChange = isInsert ? getRepoConfigCreateChange(repositoryConfig) :
                getRepoConfigUpdateChange(repositoryConfig, oldRepoConfig);
        flushRepoConfigChange(repositoryConfig, repoConfigChange);
    }

    private void addXrayNotAvailableModeIfNeeded(RepoConfig repositoryConfig, RepoConfig oldRepoConfig) {
        if (oldRepoConfig != null && oldRepoConfig.getXrayNotAvailableMode() != null) {
            repositoryConfig.getSecurityConfig().setXrayNotAvailableMode(oldRepoConfig.getXrayNotAvailableMode());
        }
    }

    private void assertCurationIsValid(RemoteRepoConfig config) {
        if (!isCurationConfigValid(config)) {
            throw new IllegalArgumentException(String.format(
                    "Cannot upsert '%s' repo due to invalid curation settings", config.getKey()));
        }
    }

    private boolean isCurationConfigValid(RemoteRepoConfig config) {
        return ContextHelper.get().beanForType(AddonsManager.class).addonByType(CurationAddon.class)
                .isCurationConfigValid(config);
    }

    @Override
    public void updateRepoConfig(RepoConfig repositoryConfig) {
        updateRepoConfig(repositoryConfig, false);
    }

    private void updateRepoConfig(RepoConfig repositoryConfig, boolean skipRepoNameValidation,
            boolean skipBeforeInterceptors, boolean skipAfterInterceptors) {
        if (repositoryConfig == null) {
            log.warn("Cannot update repository: repository config cannot be null");
            if (log.isTraceEnabled()) {
                log.trace("Cannot update repository: repository config cannot be null", new NullPointerException());
            }
            return;
        }
        if (ContextHelper.get().isReposImportInProgress()) {
            log.info("Cannot update repository config '{}', import is still in progress", repositoryConfig.getKey());
            return;
        }
        globalValidation(Set.of(repositoryConfig), skipRepoNameValidation);
        if (repositoryConfig instanceof RemoteRepoConfig remoteRepoConfig) {
            remoteRepoConfig.processUrlCredentials();
        }
        withRepoLock(repositoryConfig.getKey(),
                () -> updateRepositoryInternal(repositoryConfig, skipBeforeInterceptors, skipAfterInterceptors));
    }

    @Override
    public void removeDanglingChildRefFromVirtual(String virtualRepoKey, String childRepoKey,
            @Nullable RepoConfig removedRepo) {
        withRepoLock(virtualRepoKey, () -> {
            RepoConfig config = getRepoConfigByKey(virtualRepoKey);
            if (!(config instanceof VirtualRepoConfig fresh)) {
                return;
            }
            if (!fresh.getRepoTypeConfig().getRepositoryRefs().contains(childRepoKey)) {
                return;
            }
            fresh.removeRepository(childRepoKey);
            if (removedRepo != null) {
                clearVirtualRepoRefsForRemovedChild(fresh, removedRepo);
            } else {
                clearVirtualRepoRefsForRemovedChild(fresh, LocalRepoConfig.create(childRepoKey, RepoType.Generic));
                clearVirtualRepoRefsForRemovedChild(fresh, RemoteRepoConfig.create(childRepoKey, RepoType.Generic));
            }
            updateRepositoryInternal(fresh, false, false);
            log.info("Removed dangling reference to '{}' from federated virtual repo '{}'",
                    childRepoKey, virtualRepoKey);
        });
    }

    @Override
    public void updateRepoConfig(RepoConfig repositoryConfig, boolean skipRepoNameValidation) {
        updateRepoConfig(repositoryConfig, skipRepoNameValidation, false, false);
    }

    @Override
    public void updateRepoConfigWithoutChecks(RepoConfig repositoryConfig) {
        updateRepoConfig(repositoryConfig, true, true, false);
    }

    @Override
    public void updateRepoConfigSilently(RepoConfig repositoryConfig) {
        updateRepoConfig(repositoryConfig, true, true, true);
    }

    @Override
    public RepoConfig deleteRepoConfig(String repoKey) {
        if (StringUtils.isBlank(repoKey)) {
            log.warn("Cannot delete repository: repository key cannot be null");
            if (log.isTraceEnabled()) {
                log.trace("Cannot delete repository: repository key cannot be null", new NullPointerException());
            }
            return null;
        }
        RepoConfig oldRepoConfig = getRepoConfigByKeyNoValidationChecks(repoKey, false);
        if (oldRepoConfig == null) {
            log.debug("Cannot delete repository '{}': repository config does not exist.", repoKey);
            return null;
        }
        bulkDeleteRepoConfigs(List.of(repoKey), true, true);
        return oldRepoConfig;
    }

    @Override
    public void bulkInsertRepoConfigs(List<? extends RepoConfig> repositoryConfigs,
            boolean reload, boolean propagateChanges, boolean doBackups) {
        if (CollectionUtils.isNullOrEmpty(repositoryConfigs)) {
            return;
        }
        repositoryConfigs.forEach(RepositoryConfigServiceImpl::badRequestValidation);
        if (ContextHelper.get().isReposImportInProgress()) {
            log.info("Cannot insert repository configs, import is still in progress");
            return;
        }
        globalValidation(repositoryConfigs);
        Set<String> repoKeys = repositoryConfigs.stream().map(RepoConfig::getKey).collect(toSet());
        withRepoLocks(repoKeys, () -> bulkInsertRepoConfigsInternal(repositoryConfigs, reload, propagateChanges,
                doBackups));
    }

    private void bulkInsertRepoConfigsInternal(List<? extends RepoConfig> repositoryConfigs,
            boolean reload, boolean propagateChanges, boolean doBackups) {
        Collection<RepoConfig> existingRepos = getExistingRepoConfigs(repositoryConfigs, false);
        Map<RepoDetailsType, List<RepoConfigChange>> typeToChanges =
                convertRepoConfigsToCreateUpdateChanges(repositoryConfigs, existingRepos);
        enrichAllWithMetadata(typeToChanges);
        triggerOnBeforeSaveOperations(typeToChanges, false);
        if (doBackups) {
            setBackups(repositoryConfigs.stream()
                    .filter(repoConfig -> RepoDetailsType.REMOTE.equals(repoConfig.getRepoType()))
                    .map(RemoteRepoConfig.class::cast)
                    .map(RemoteRepoConfig::getKey)
                    .toList());
        }
        Set<DbRepositoryConfig> reposToCreate = typeToChanges.values().stream()
                .flatMap(Collection::stream)
                .filter(change -> REPO_CREATE.equals(change.getConfigAction()))
                .map(change -> {
                    long timestamp = System.currentTimeMillis();
                    change.getNewRepoConfig().setCreation(timestamp);
                    change.getNewRepoConfig().setModification(timestamp);
                    return repositoryConfigMapper.repositoryConfigToDbRepositoryConfig(change.getNewRepoConfig());
                })
                .collect(Collectors.toSet());
        Set<String> rootFoldersToCreate = ConstantValues.repoRootAtomicCreation.getBoolean()
                ? RepoConfigRootFolderKeyExtractor.extractRootFolderKeysForInsert(new RepoConfigChanges(typeToChanges))
                : Set.of();
        storageService.bulkInsertRepositoryConfigs(reposToCreate, rootFoldersToCreate);
        if (!reposToCreate.isEmpty()) {
            repoConfigCache.addRepoConfigKeys(dbInfoBlobListToRepoKeyAndModificationMap(reposToCreate));
            if (propagateChanges) {
                propagateRepoConfigChanges(typeToChanges);
            }
        }
        if (reload) {
            callOnRepoConfigsReload(RepoConfigChangesReload.fromChanges(typeToChanges), false);
            interceptors.forEach(interceptor -> interceptor.onAfterSave(typeToChanges));
        }
    }

    @Override
    public void bulkUpsertRepoConfigs(Collection<? extends RepoConfig> repositoryConfigs, boolean reload) {
        bulkUpsertRepositoryConfigs(repositoryConfigs, reload, false);
    }

    private void bulkUpsertRepositoryConfigs(Collection<? extends RepoConfig> repositoryConfigs, boolean reload,
            boolean isImport) {
        if (repositoryConfigs.isEmpty()) {
            return;
        }
        if (!isImport && ContextHelper.get().isReposImportInProgress()) {
            log.info("Cannot upsert repository configs, import is still in progress");
            return;
        }
        globalValidation(repositoryConfigs);
        repositoryConfigs.stream()
                .filter(RemoteRepoConfig.class::isInstance)
                .map(RemoteRepoConfig.class::cast)
                .forEach(RemoteRepoConfig::processUrlCredentials);

        Set<String> repoKeys = repositoryConfigs.stream().map(RepoConfig::getKey).collect(toSet());
        withRepoLocks(repoKeys, () -> bulkUpsertRepositoryConfigsInternal(repositoryConfigs, reload, isImport));
    }

    private void bulkUpsertRepositoryConfigsInternal(Collection<? extends RepoConfig> repositoryConfigs, boolean reload,
            boolean isImport) {
        Collection<RepoConfig> existingRepos = getExistingRepoConfigs(repositoryConfigs, isImport);
        Map<RepoDetailsType, List<RepoConfigChange>> mutableRepoConfigChangesByType =
                convertRepoConfigsToCreateUpdateChanges(repositoryConfigs, existingRepos, isImport);
        enrichAllWithMetadata(mutableRepoConfigChangesByType);
        triggerOnBeforeSaveOperations(mutableRepoConfigChangesByType, isImport);
        flushRepoConfigChanges(mutableRepoConfigChangesByType, true);
        if (reload) {
            callOnRepoConfigsReload(RepoConfigChangesReload.fromChanges(mutableRepoConfigChangesByType), false);
            interceptors.forEach(interceptor -> interceptor.onAfterSave(mutableRepoConfigChangesByType));
        }
    }

    @Override
    public void bulkDeleteRepoConfigs(List<String> repositoryConfigKeys, boolean reload,
            boolean propagateChanges) {
        if (repositoryConfigKeys.isEmpty()) {
            return;
        }
        bulkDeleteRepositoryConfigs(repositoryConfigKeys, reload, propagateChanges, false);
    }

    @VisibleForTesting
    void bulkDeleteRepositoryConfigs(List<String> repositoryConfigKeys, boolean reload,
            boolean propagateChanges, boolean isImport) {
        // System repos with importAllowed=false (e.g. _intransit, jfrog-full-sync-info, observability repos)
        // never appear in an exported config and must not be diff-deleted during full system import. Otherwise
        // the synchronous content cleanup runs as the importing admin user (not _system) and is rejected by
        // FullSyncStorageInterceptor / ObservabilityInterceptor for repos marked deleteOnlyBySuperUser=true,
        // failing the import with HTTP 500. See RTDEV-86629.
        List<String> excludedFromImportSystemRepos = getExcludedFromImportSystemRepos();
        Set<String> keysToLock = repositoryConfigKeys.stream()
                .filter(key -> !isBlankTrashOrSupportBundle(key)
                        && !(isImport && excludedFromImportSystemRepos.contains(key)))
                .collect(toSet());
        withRepoLocks(keysToLock, () -> {
            List<RepoConfig> repoConfigs = new ArrayList<>();
            for (String repoKey : keysToLock) {
                RepoConfig oldRepoConfig = getRepoConfigByKeyNoValidationChecks(repoKey, isImport);
                if (oldRepoConfig != null) {
                    repoConfigs.add(oldRepoConfig);
                }
            }
            bulkDeleteRepositoryConfigsInternal(repoConfigs, reload, propagateChanges, isImport);
        });
    }

    @VisibleForTesting
    void bulkDeleteRepositoryConfigsInternal(Collection<RepoConfig> repoConfigs, boolean reload,
            boolean propagateChanges, boolean isImport) {
        if (!isImport && ContextHelper.get().isReposImportInProgress()) {
            log.info("Cannot delete repository configs, import is still in progress");
            return;
        }
        if (repoConfigs.isEmpty()) {
            return;
        }
        List<RepoConfigChange> repoConfigChanges = new ArrayList<>();
        for (RepoConfig oldRepoConfig : repoConfigs) {
            if (oldRepoConfig != null) {
                RepoConfigChange repoConfigChange = enrichWithMetadata(RepoConfigChange.deleteOf(oldRepoConfig));
                repoConfigChanges.add(repoConfigChange);
            }
        }

        List<Runnable> deferredFederatedCleanups = new ArrayList<>();
        List<VirtualRepoConfig> virtualRepoConfigs =
                deleteReferenceFromVirtualRepos(repoConfigs.stream().toList(), isImport, deferredFederatedCleanups);
        if (!virtualRepoConfigs.isEmpty()) {
            Collection<RepoConfig> existingRepos = getExistingRepoConfigs(virtualRepoConfigs, isImport);
            Map<RepoDetailsType, List<RepoConfigChange>> changedVirtualRepoConfigs =
                    convertRepoConfigsToUpdateChanges(virtualRepoConfigs, existingRepos);
            enrichAllWithMetadata(changedVirtualRepoConfigs);
            if (changedVirtualRepoConfigs.get(VIRTUAL) != null) {
                repoConfigChanges.addAll(changedVirtualRepoConfigs.get(VIRTUAL));
            }
        }

        Map<RepoDetailsType, List<RepoConfigChange>> mutableRepoConfigChangesByType = repoConfigChanges.stream()
                .filter(change -> change.getOldRepoConfig() != null)
                .collect(groupingBy(change -> change.getOldRepoConfig().getRepoType()));
        if (isImport || isRepoDeleteFlowV1()) {
            triggerOnBeforeSaveOperations(mutableRepoConfigChangesByType, isImport);
        }
        if (reload) {
            // Force access to the repositories cache to avoid a collapsing effect of any previous changes with new
            // changes during the reload flow
            String repoKey = "DUMMY_REPO";
            listeners.stream()
                    .filter(InternalRepositoryService.class::isInstance)
                    .findFirst()
                    .map(InternalRepositoryService.class::cast)
                    .ifPresent(repoService -> repoService.repositoryByKey(repoKey));
        }
        dereferenceRepositoryFromConfigDescriptor(repoConfigs.stream().toList());
        flushRepoConfigChanges(mutableRepoConfigChangesByType, propagateChanges);
        if (reload) {
            callOnRepoConfigsReload(RepoConfigChangesReload.fromChanges(mutableRepoConfigChangesByType), false);
            interceptors.forEach(interceptor -> interceptor.onAfterSave(mutableRepoConfigChangesByType));
        }
        deferredFederatedCleanups.forEach(Runnable::run);
    }

    private boolean isRepoDeleteFlowV1() {
        return !removeRepositoryFlowV2Enabled.getBoolean();
    }

    private void triggerOnBeforeSaveOperations(
            Map<RepoDetailsType, List<RepoConfigChange>> mutableRepoConfigChangesByType, boolean isImport) {
        validators.forEach(
                validator -> validator.validate(new RepoConfigChanges(mutableRepoConfigChangesByType, isImport)));
        interceptors.forEach(
                listener -> listener.onBeforeSave(new RepoConfigChanges(mutableRepoConfigChangesByType, isImport)));
    }

    @Override
    public void insertPackageConfigByKey(String repoKey, Map<String, String> packageConfig) {
        if (ContextHelper.get().isReposImportInProgress()) {
            log.info("Cannot update repository config '{}', import is still in progress", repoKey);
            return;
        }
        RepoConfig repositoryConfig = repoConfigCache.getByKey(repoKey);
        RepoConfig oldRepoConfig = getRepoConfigByKey(repositoryConfig.getKey());
        if (oldRepoConfig != null) {
            globalValidation(Set.of(oldRepoConfig));
        }
        packageConfig.forEach((k, v) -> repositoryConfig.getPackageTypeConfig().put(k, v));
        RepoConfigChange repoConfigChange = getRepoConfigUpdateChange(repositoryConfig, oldRepoConfig);
        flushRepoConfigChange(repositoryConfig, repoConfigChange);
    }

    @Override
    public RepoConfig getRepoConfigByKey(String repoKey) {
        return getRepoConfigByKey(repoKey, false);
    }

    @Override
    public RepoConfig getRepoConfigByKey(String repoKey, boolean skipCacheRepoValidation) {
        if (StringUtils.isBlank(repoKey)) {
            return null;
        }
        if (!isManaged(repoKey, skipCacheRepoValidation)) {
            throw new IllegalArgumentException(String.format(
                    "Cannot retrieve '%s' repo config from the RepositoryConfigService. Please use the " +
                            "RepositoryService instead.", repoKey));
        }
        return getRepoConfigByKeyNoValidationChecks(repoKey, false);
    }

    @Override
    public boolean isManaged(String repoKey) {
        return isManaged(repoKey, false);
    }

    private boolean isManaged(String repoKey, boolean skipCacheRepoValidation) {
        return !(isTrash(repoKey) || isSupportBundle(repoKey) || !skipCacheRepoValidation && isCache(repoKey));
    }

    @Override
    public RepoConfig getRepoConfigByKeyNoValidationChecks(String repoKey) {
        return getRepoConfigByKeyNoValidationChecks(repoKey, false);
    }


    @VisibleForTesting
    RepoConfig getRepoConfigByKeyNoValidationChecks(String repoKey, boolean isImport) {
        if (isImport) {
            return getRepoConfigFromDb(repoKey);
        }
        return repoConfigCache.getByKey(repoKey);
    }

    private RepoConfig getRepoConfigFromDb(String repoKey) {
        return dbInfoBlobToRepositoryConfig(storageService.findRepositoryByKey(repoKey));
    }

    private Set<RepoConfig> getRepoConfigsFromDb(Set<String> repoKeys) {
        return storageService.findRepositoryConfigsByKeys(repoKeys).stream()
                .map(this::dbInfoBlobToRepositoryConfig)
                .filter(Objects::nonNull)
                .collect(toSet());
    }

    @Override
    public RealRepoConfig getRealRepoConfigByKey(String repoKey) {
        if (isInvalid(repoKey)) {
            return null;
        }
        RepoConfig repoConfig = getRepoConfigByKey(repoKey);
        if (repoConfig == null) {
            return null;
        }
        if (repoConfig.isReal()) {
            return (RealRepoConfig) repoConfig;
        }
        return null;
    }

    @Override
    @Nullable
    public VirtualRepoConfig getVirtualRepoConfigByKey(String repoKey) {
        if (isInvalid(repoKey)) {
            return null;
        }
        RepoConfig repoConfig = getRepoConfigByKey(repoKey);
        if (repoConfig != null && RepoDetailsType.VIRTUAL.equals(repoConfig.getRepoType())) {
            return (VirtualRepoConfig) repoConfig;
        }
        return null;
    }

    @Override
    public RemoteRepoConfig getRemoteRepoConfigByKey(String repoKey) {
        if (isInvalid(repoKey)) {
            return null;
        }
        RepoConfig repoConfig = getRepoConfigByKey(repoKey);
        if (repoConfig != null && REMOTE.equals(repoConfig.getRepoType())) {
            return (RemoteRepoConfig) repoConfig;
        }
        return null;
    }

    @Override
    public boolean existsByRepoKey(String repoKey) {
        return !StringUtils.isBlank(repoKey) && repoConfigCache.existByKey(repoKey);
    }

    @Override
    public boolean existsByKeyIgnoreCase(String repoKey) {
        return !StringUtils.isBlank(repoKey) && repoConfigCache.getAllRepoKeys().stream()
                .anyMatch(key -> key.compareToIgnoreCase(repoKey) == 0);
    }

    @Override
    public Collection<RepoConfig> getAllRepoConfigs() {
        return repoConfigCache.getAll();
    }

    @Override
    public Collection<BasicRepoConfig> getAllBasicRepoConfigsView() {
        return repoConfigCache.getAllBasicConfigsView();
    }

    @Override
    public Collection<RepoConfig> getAllRepoConfigsForRebuild() {
        return repoConfigCache.getAllWithoutCopy();
    }

    @Override
    public Set<String> getAllRepoConfigKeys() {
        return repoConfigCache.getAllRepoKeys();
    }

    @Override
    public Set<RepoConfig> getRepoConfigsByKeys(Collection<String> repoKeys) {
        return getRepoConfigsByKeys(repoKeys, repoConfigCache.getAllRepoKeys());
    }

    @Override
    public LinkedHashSet<RepoConfig> getRepoConfigsByKeysInSpecificOrder(VirtualRepoConfig virtualRepoConfig) {
        return getRepoConfigsByKeysInSpecificOrder(virtualRepoConfig, getAllRepoConfigKeys());
    }

    @Override
    public LinkedHashSet<RepoConfig> getRepoConfigsByKeysInSpecificOrder(VirtualRepoConfig virtualRepoConfig,
            Set<String> allRepoKeys) {
        LinkedHashSet<String> repositoryRefs =
                new LinkedHashSet<>(virtualRepoConfig.getRepoTypeConfig().getRepositoryRefs());
        if (repositoryRefs.isEmpty()) {
            log.debug(
                    "Attempt to search aggregated repositories, but virtual repo '{}' does not contain any. Returning" +
                            " empty list...", virtualRepoConfig.getKey());
            return Sets.newLinkedHashSet();
        }
        Set<RepoConfig> repositoryConfigsByKeys = getRepoConfigsByKeys(repositoryRefs, allRepoKeys);
        Map<String, RepoConfig> configByKey = repositoryConfigsByKeys.stream()
                .collect(Collectors.toMap(RepoConfig::getKey, Function.identity(), (a, b) -> a));
        LinkedHashSet<RepoConfig> ordered = Sets.newLinkedHashSetWithExpectedSize(repositoryConfigsByKeys.size());
        for (String ref : repositoryRefs) {
            RepoConfig config = configByKey.get(ref);
            if (config != null) {
                ordered.add(config);
            }
        }
        return ordered;
    }

    @Override
    public <T> ExtendedRepositoryConfig<T> getExtendedRepoConfigByKey(String repoKey, Class<T> type) {
        RepoConfig repo = getRepoConfigByKey(repoKey);
        return new ExtendedRepositoryConfig<>(repo, getDeserializedType(type, repo));
    }

    @Override
    public Map<String, String> getPackageConfigByRepoKey(String repoKey) {
        RepoConfig repoConfig = repoConfigCache.getByKey(repoKey);
        if (repoConfig == null) {
            return Map.of();
        }
        return repoConfig.getPackageTypeConfig();
    }

    @Override
    public LocalConfig getLocalOrFederatedRepoConfigByKey(String repoKey) {
        if (isInvalid(repoKey)) {
            return null;
        }
        var repoConfig = getRepoConfigByKey(repoKey);
        if (repoConfig == null) {
            return null;
        }
        if (repoConfig instanceof LocalConfig localConfig) {
            return localConfig;
        }
        log.debug("No matching local repository. Found repository is: {}", repoConfig.getClass().getSimpleName());
        return null;
    }

    @Override
    public LocalRepoConfig getLocalRepoConfigByKey(String repoKey) {
        if (isInvalid(repoKey)) {
            return null;
        }
        var repoConfig = getRepoConfigByKey(repoKey);
        if (repoConfig == null) {
            return null;
        }
        if (repoConfig instanceof LocalRepoConfig localRepoConfig) {
            return localRepoConfig;
        }
        log.debug("No matching local repository. Found repository is: {}", repoConfig.getClass().getSimpleName());
        return null;
    }

    @Override
    public LocalRepoConfig getLocalRepoConfigByKeyForDelete(String repoKey) {
        if (isBlankTrashOrSupportBundle(repoKey)) {
            return null;
        }
        var repoConfig = getRepoConfigByKeyNoValidationChecks(repoKey, false);
        if (repoConfig == null) {
            return null;
        }
        if (repoConfig instanceof LocalRepoConfig localRepoConfig) {
            return localRepoConfig;
        }
        log.debug("No matching local repository. Found repository is: {}", repoConfig.getClass().getSimpleName());
        return null;
    }

    @Override
    public FederatedRepoConfig getFederatedRepoConfigByKey(String repoKey) {
        if (isInvalid(repoKey)) {
            return null;
        }
        var repoConfig = getRepoConfigByKeyNoValidationChecks(repoKey, false);
        if (repoConfig == null) {
            return null;
        }
        if (repoConfig instanceof FederatedRepoConfig federatedRepoConfig) {
            return federatedRepoConfig;
        }
        log.debug("No matching federated repository. Found repository is: {}", repoConfig.getClass().getSimpleName());
        return null;
    }

    @Override
    public <T> T getPackageConfigByRepoKey(String repoKey, Class<T> packageTypeModel) {
        RepoConfig repoConfig = repoConfigCache.getByKey(repoKey);
        if (repoConfig == null) {
            return null;
        }
        return getDeserializedType(packageTypeModel, repoConfig);
    }

    @Nonnull
    public static <T> T getDeserializedType(Class<T> packageTypeModel, RepoConfig repositoryConfig) {
        JsonUtils jsonMapper = JsonUtils.getInstance();
        byte[] packageTypeConfigByKey =
                jsonMapper.valueToByteArray(repositoryConfig.getPackageTypeConfig());
        return jsonMapper.readValue(packageTypeConfigByKey, packageTypeModel);
    }

    @Override
    public void convert(CompoundVersionDetails source, CompoundVersionDetails target) {
        List<DbRepositoryConfig> dbRepoConfigs = storageService.getAllRepositories();

        RepositoriesConverterManager repositoriesConverterManager = new RepositoriesConverterManager(subConverters);

        Set<DbRepositoryConfig> convertedDbRepos = dbRepoConfigs.stream()
                .map(dbRepo -> convertBlobOrSkip(repositoriesConverterManager, dbRepo))
                .filter(Objects::nonNull)
                .map(repoConfig -> {
                    // don't need to set creation date time since method is called for existing repo configs that are
                    // persisted
                    repoConfig.setModification(System.currentTimeMillis());
                    return repositoryConfigMapper.repositoryConfigToDbRepositoryConfig(repoConfig);
                })
                .collect(Collectors.toSet());

        // During conversion, don't create new root folders - they should already exist
        storageService.bulkUpdateRepositoryConfigs(convertedDbRepos, Set.of());
        createRepoConfigCache();
    }

    /**
     * Converts a single repository config blob, or returns {@code null} to skip it when the DB column holds a package
     * type unknown to this Artifactory version (e.g. after a rollback). Skipping leaves the blob untouched, so the
     * original type is restored on a later re-upgrade rather than being coerced to Generic and persisted. A corrupt
     * blob of a known type still propagates from {@code converterManager.convert} (unchanged fail-fast behavior).
     *
     * <p>This skip is independent of {@link ConstantValues#repoConfigSkipUnknownPackageType}, which governs only the
     * runtime read path, because here the result is re-serialized and persisted.</p>
     */
    @Nullable
    private RepoConfig convertBlobOrSkip(RepositoriesConverterManager converterManager, DbRepositoryConfig dbRepo) {
        // Use fromString so package-type aliases (e.g. "rpm" -> YUM) are recognized as known types, matching how the
        // column is read elsewhere via RepoType.fromType; only a genuinely unknown type is skipped.
        String packageType = dbRepo.getPackageType();
        if (packageType == null || RepoType.fromString(packageType) == null) {
            log.error("Unknown package type '{}' for repository '{}' (config conversion/migration), likely after a "
                    + "rollback to a previous Artifactory version that predates the type; skipping conversion and "
                    + "leaving the stored blob unchanged, so the original type is restored on re-upgrade.",
                    dbRepo.getPackageType(), dbRepo.getKey());
            return null;
        }
        return converterManager.convert(dbRepo.getRepositoryInfoBlob());
    }

    @Override
    public void rebuildRepoConfigCache() {
        createRepoConfigCache();
    }

    @Override
    public void callOnRepoConfigsReload(RepoConfigChangesReload repoConfigChangesReload, boolean fromPropagation) {
        log.info("Calling onRepoConfigChange...");
        if (log.isDebugEnabled()) {
            repoConfigChangesReload.getAll()
                    .forEach(repo -> log.debug("Changed repo: {}, action: {}", repo.getRepoKey(),
                            repo.getConfigAction()));
        }
        if (fromPropagation) {
            ContextHelper.get().beanForType(InternalRepositoryService.class)
                    .onPropagatedRepoConfigReload(repoConfigChangesReload);
        } else {
            ContextHelper.get().beanForType(InternalRepositoryService.class)
                    .onRepoConfigReload(repoConfigChangesReload);
        }
        listeners.stream()
                .filter(listener -> !(listener instanceof InternalRepositoryService))
                .forEach(listener -> listener.onRepoConfigReload(repoConfigChangesReload));
    }

    @Override
    public void callOnRepoConfigsReloadAsync(RepoConfigChangesReload repoConfigChangesReload,
            boolean fromPropagation) {
        callOnRepoConfigsReload(repoConfigChangesReload, fromPropagation);
    }

    @Override
    public int countAll() {
        return repoConfigCache.countAll();
    }

    @Override
    public int deleteAll(boolean reload, boolean propagateChanges) {
        Collection<RepoConfig> repoConfigs = getAllRepoConfigs();
        bulkDeleteRepositoryConfigsInternal(repoConfigs, reload, propagateChanges, false);
        return repoConfigs.size();
    }

    @Override
    public void reloadRepoConfigs() {
        RepoConfigCache oldCache = createRepoConfigCache();
        ContextHelper.get().beanForType(RepositoryConfigService.class)
                .calculateCacheDiffAndReloadAsync(oldCache.getCachedBasicRepoConfigs(),
                        repoConfigCache.getCachedBasicRepoConfigs());
    }

    @Override
    public void calculateCacheDiffAndReloadAsync(Map<String, BasicRepoConfig> oldBasicRepoConfigs,
            Map<String, BasicRepoConfig> newBasicRepoConfigs) {
        reloadRepoConfigs(
                RepoConfigCacheDiffCalculator.calculateCacheDiff(oldBasicRepoConfigs, newBasicRepoConfigs));
    }

    @Override
    public List<String> getLocalAndFederatedRepoConfigsExcludingInfoRepoKeys() {
        return repoConfigCache.getAllBasicConfigs().stream()
                .filter(repoConfig -> Set.of(LOCAL, FEDERATED).contains(repoConfig.getRepoType()))
                .filter(repoConfig -> !isInfoRepo(repoConfig.getPackageType()) &&
                        !isHiddenRepo(repoConfig.getRepoKey()))
                .map(BasicRepoConfig::getRepoKey)
                .toList();
    }

    @Override
    public Set<String> findAllMissingRepoRootPaths() {
        return storageService.findAllMissingRepoRootPaths();
    }

    @Override
    public int createMissingRepoRootFolders() {
        return storageService.createMissingRepoRootFolders();
    }

    @Override
    public List<LocalConfig> getLocalRepoConfigsIncludingInfoRepos() {
        return getRepoConfigsByType(LOCAL).stream()
                .map(LocalConfig.class::cast)
                .collect(Collectors.toList());
    }

    @Override
    public List<LocalConfig> getLocalRepoConfigsIncludingInfoReposByProject(@Nullable String projectKey) {
        Stream<RepoConfig> repoConfigs = getRepoConfigsByType(LOCAL).stream();
        repoConfigs = filterByProject(repoConfigs, projectKey);
        return repoConfigs
                .filter(repo -> !SystemRepos.isInTransitRepo(repo.getKey()) &&
                        !BundleConfigurationUtil.isReleaseBundlesV2DistributionRepoKey(repo.getKey()))
                .map(LocalConfig.class::cast)
                .toList();
    }

    @Override
    public List<LocalConfig> getLocalRepoConfigsExcludingInfoRepos() {
        return getLocalRepoConfigsExcludingInfoRepos(false);
    }

    @Override
    public boolean isInfoOrHiddenRepo(RepoConfig repoConfig) {
        return isInfoRepo(repoConfig.getPackageType()) || isHiddenRepo(repoConfig.getKey());
    }

    @Override
    public List<LocalConfig> getLocalRepoConfigsExcludingInfoRepos(boolean includeShippingOrBillingLogsRepos) {
        Set<String> repoKeys = new HashSet<>(
                getLocalRepoConfigKeysExcludingInfoRepos(includeShippingOrBillingLogsRepos)
        );
        return new ArrayList<>(
                repoConfigCache.getRepositoryConfigsByKeys(repoKeys).stream().map(LocalConfig.class::cast).toList());
    }

    @Override
    public List<LocalConfig> getLocalRepoConfigsExcludingInfoReposByProject(Function<String, Boolean> projectFilter) {
        return getLocalRepoConfigsExcludingInfoRepos().stream()
                .filter(repo -> projectFilter.apply(repo.getKey()))
                .toList();
    }

    @Override
    public boolean hasRepositoriesByProjectAndTypes(String projectKey, Set<RepoDetailsType> types) {
        Stream<BasicRepoConfig> repoConfigs = repoConfigCache.getAllBasicConfigs().stream();
        repoConfigs = filterByTypes(repoConfigs, types);
        repoConfigs = repoConfigs
                .filter(repoConfig -> !isInfoRepo(repoConfig.getPackageType()))
                .filter(repoConfig -> !isHiddenRepo(repoConfig.getRepoKey()));
        repoConfigs = filterBasicConfigByProject(repoConfigs, projectKey);
        return repoConfigs.findFirst().isPresent();
    }

    private Stream<BasicRepoConfig> filterByTypes(Stream<BasicRepoConfig> repoConfigs, Set<RepoDetailsType> types) {
        if (types != null && RepoDetailsType.values().length != types.size()) {
            return repoConfigs.filter(repoConfig -> types.contains(repoConfig.getRepoType()));
        }
        return repoConfigs;
    }

    private Stream<BasicRepoConfig> filterBasicConfigByProject(Stream<BasicRepoConfig> repoConfigs, String projectKey) {
        if (ProjectsUtils.isDefaultProject(projectKey)) {
            return repoConfigs;
        }
        ProjectsService projectsService = ContextHelper.get().beanForType(ProjectsService.class);
        Set<String> repoKeys = projectsService.getProjectRepositoriesKeys(projectKey, true);
        return repoConfigs.filter(repoConfig -> repoKeys.contains(repoConfig.getRepoKey()));
    }

    private Stream<RepoConfig> filterByProject(Stream<RepoConfig> repoConfigs, String projectKey) {
        if (ProjectsUtils.isDefaultProject(projectKey)) {
            return repoConfigs;
        }
        ProjectsService projectsService = ContextHelper.get().beanForType(ProjectsService.class);
        Set<String> repoKeys = projectsService.getProjectRepositoriesKeys(projectKey, true);
        return repoConfigs.filter(repoConfig -> repoKeys.contains(repoConfig.getKey()));
    }

    @Override
    public List<String> getLocalRepoConfigKeysExcludingInfoRepos() {
        return getLocalRepoConfigKeysExcludingInfoRepos(false);
    }

    @Override
    public List<String> getLocalRepoConfigKeysExcludingInfoRepos(boolean includeShippingOrBillingLogsRepos) {
        return repoConfigCache.getAllBasicConfigs().stream()
                .filter(repoConfig -> Objects.equals(LOCAL, repoConfig.getRepoType()))
                .filter(repoConfig -> !isInfoRepo(repoConfig.getPackageType()) &&
                        !isHiddenRepo(repoConfig.getRepoKey(), includeShippingOrBillingLogsRepos))
                .map(BasicRepoConfig::getRepoKey)
                .toList();
    }

    @Override
    public List<RepoConfig> getLocalAndFederatedRepoConfigsExcludingInfoRepos() {
        return Stream.concat(getLocalRepoConfigsExcludingInfoRepos().stream(),
                        getRepoConfigsByType(FEDERATED).stream())
                .toList();
    }

    @Override
    public List<RepoConfig> getLocalAndFederatedRepoConfigsIncludingInfoRepos() {
        return Stream.concat(getLocalRepoConfigsIncludingInfoRepos().stream(),
                        getRepoConfigsByType(FEDERATED).stream())
                .toList();
    }

    private void validateRepoNames(Collection<? extends RepoConfig> repoConfigs) {
        repoConfigs.forEach(repoConfig -> validateInternalRepoName(repoConfig.getKey(),
                repoConfig.getRepoType().typeNameLowercase()));
    }

    @Override
    public void reloadRepoConfigs(RepoConfigPropagationModel model) {
        if (model == null || model.isEmpty()) {
            log.debug("Reload repo configuration: nothing to handle");
            return;
        }
        RepoConfigPropagationModelFilter repoConfigPropagationModelFilter =
                new RepoConfigPropagationModelFilter(repoConfigCache.getCachedBasicRepoConfigs(),
                        storageService, repoConfigChangeVerifyOnGetAllRepoKeysSinceSize.getInt());
        repoConfigPropagationModelFilter.filter(model);

        RepoConfigChangesReload repoConfigChangesReload = createConfigChangesReloadFromPropagation(model);

        reloadCache(model.getRepoConfigCreatedOrUpdatedMap(), model.getRepoConfigDeletedKeys());

        ContextHelper.get().beanForType(RepositoryConfigService.class)
                .callOnRepoConfigsReloadAsync(repoConfigChangesReload, true);
    }

    private void reloadCache(Map<String, BasicRepoConfig> createOrUpdateMap, Set<String> repoKeysToDelete) {
        repoConfigCache.addAndUpdateRepoConfigKeys(createOrUpdateMap);
        repoConfigCache.deleteRepoConfigsByKeys(repoKeysToDelete);
    }

    @Override
    public boolean existsByType(RepoDetailsType repoType, String repoKey) {
        return !isInvalid(repoKey) && repoType.equals(repoConfigCache.getRepoDetailsTypeByKey(repoKey));
    }

    @Override
    public boolean existsByTypeAndPackageType(RepoDetailsType repoDetailsType, RepoType repoType, String repoKey) {
        return !isInvalid(repoKey) && repoDetailsType.equals(repoConfigCache.getRepoDetailsTypeByKey(repoKey)) &&
                repoType.equals(repoConfigCache.getRepoPackageTypeByKey(repoKey));
    }

    @Override
    public boolean existsByPackageType(RepoType repoType, String repoKey) {
        if (repoType == null || isInvalid(repoKey) || !existsByRepoKey(repoKey)) {
            return false;
        }
        return repoType.equals(repoConfigCache.getRepoPackageTypeByKey(repoKey));
    }

    @Override
    public List<VirtualRepoConfig> getVirtualRepoConfigsContainingRepoConfig(RepoConfig repoConfig, int depth) {
        List<VirtualRepoConfig> reposToDisplay = new ArrayList<>();
        if (repoConfig == null) {
            return reposToDisplay;
        }
        RepoConfig config = repoConfig;
        if (repoConfig instanceof LocalCacheRepoConfig cacheRepoConfig) {
            //VirtualRepoResolver does not directly support local cache repos, so if the items config is a cache,
            //We extract the caches remote repo, and use it instead
            config = cacheRepoConfig.getRemoteRepoConfig();
        }
        List<VirtualRepoConfig> virtualRepos = getVirtualRepoConfigs();
        for (VirtualRepoConfig virtualRepo : virtualRepos) {
            VirtualRepositoryConfigResolverImpl resolver = new VirtualRepositoryConfigResolverImpl(this);
            LinkedHashSet<RepoDetailsType> repoDetailsTypes =
                    CollectionUtils.newLinkedHashSet(LOCAL, FEDERATED, REMOTE, VIRTUAL);
            Set<String> resolvedRepoConfigs =
                    resolver.getRepoConfigs(virtualRepo.getKey(), repoDetailsTypes, depth).stream()
                            .map(RepoConfig::getKey)
                            .collect(Collectors.toSet());
            if (resolvedRepoConfigs.contains(config.getKey())) {
                reposToDisplay.add(virtualRepo);
            }
        }
        return reposToDisplay;
    }

    @Override
    public List<VirtualRepoConfig> getVirtualRepoConfigsContainingRepoConfig(RepoConfig repoConfig) {
        return getVirtualRepoConfigsContainingRepoConfig(repoConfig, Integer.MAX_VALUE);
    }

    @Override
    public boolean hasDirectlyAssociatedVirtualRepos(RepoConfig repoConfig) {
        if (repoConfig == null) {
            return false;
        }
        String repoKey = (repoConfig instanceof LocalCacheRepoConfig cacheRepoConfig)
                ? cacheRepoConfig.getRemoteRepoConfig().getKey()
                : repoConfig.getKey();

        return getVirtualRepoConfigs().stream()
                .anyMatch(virtualRepoConfig -> virtualRepoConfig.getRepoTypeConfig().getRepositoryRefs()
                        .contains(repoKey));
    }

    @Override
    public void exportTo(ExportSettings settings) {
        MutableStatusHolder status = settings.getStatusHolder();
        status.status("Exporting repositories config...", log);
        writeRepoConfigsToFile(settings.getBaseDir(), getAllRepoConfigsSkipCache());
    }

    @Override
    public void writeRepoConfigsToFile(File baseDir, Collection<RepoConfig> allRepositoryConfigs) {
        File destFile = new File(baseDir, ArtifactoryHome.ARTIFACTORY_REPO_CONFIG_FILE);
        AggregatedReposConfig aggregatedReposConfig = new AggregatedReposConfig(allRepositoryConfigs);
        JsonUtils.getInstance().writeValue(destFile, aggregatedReposConfig, true);
    }

    @Override
    public void importFrom(ImportSettings settings) {
        MutableStatusHolder status = settings.getStatusHolder();
        status.status("Importing repositories config...", log);
        File newReposConfigFile = new File(settings.getBaseDir(), ArtifactoryHome.ARTIFACTORY_REPO_CONFIG_FILE);
        if (!newReposConfigFile.exists()) {
            log.info("Repositories configuration file {} does not exist, repositories will not be imported.",
                    newReposConfigFile);
            return;
        }
        status.status("Reloading repositories configuration from " + newReposConfigFile, log);
        AggregatedReposConfig aggregatedReposConfig =
                JsonUtils.getInstance().readValue(newReposConfigFile, AggregatedReposConfig.class);
        List<RepoConfig> repoConfigs = filterRepoConfigsAllowedForImport(aggregatedReposConfig.getAll());
        importRepoConfigs(repoConfigs, true, true);
        status.status("Repositories configuration reloaded from " + newReposConfigFile, log);
    }

    @Override
    public void importRepoConfigs(List<? extends RepoConfig> repoConfigsToImport, boolean reload,
            boolean propagateChanges) {
        RepositoryGridConfigUtils.validateNoGridTopologyIdWhenGridDisabled(repoConfigsToImport);
        List<RepoConfig> localReposToMigrate =
                RepositoryGridConfigUtils.detectLocalReposWithFederation(repoConfigsToImport);

        repoConfigsToImport = convertImportedRepoConfigs(repoConfigsToImport);
        removeFederatedMembersFromImportedRepoConfigs(repoConfigsToImport);

        List<String> oldRepoConfigsKeys =
                new ArrayList<>(loadAllBasicRepoConfigsFromDb().keySet());
        bulkUpsertRepositoryConfigs(repoConfigsToImport, reload, true);
        oldRepoConfigsKeys.removeAll(repoConfigsToImport.stream().map(RepoConfig::getKey).toList());
        bulkDeleteRepositoryConfigs(oldRepoConfigsKeys, reload, propagateChanges, true);

        // Migrate local repos with federation capability to federated repos
        migrateLocalReposWithFederation(localReposToMigrate);
    }

    /**
     * Migrates local repos with federation capability to federated repos.
     */
    @VisibleForTesting
    void migrateLocalReposWithFederation(List<RepoConfig> repoConfigs) {
        if (repoConfigs.isEmpty() || federatedRepoService == null) {
            return;
        }

        List<String> failedMigrations = new ArrayList<>();
        repoConfigs.stream()
                .filter(LocalRepoConfig.class::isInstance)
                .map(LocalRepoConfig.class::cast)
                .forEach(config -> {
                    String error = migrateLocalConfigToFederatedIfNeeded(config);
                    if (error != null) {
                        failedMigrations.add(config.getKey() + ": " + error);
                    }
                });

        if (!failedMigrations.isEmpty()) {
            log.warn("Failed to migrate {} repositories to federated: {}",
                    failedMigrations.size(), failedMigrations);
        }
    }

    /**
     * Attempts to migrate a local repo to federated if it has federation configuration.
     *
     * @return null if successful or skipped, error message if migration failed
     */
    @VisibleForTesting
    String migrateLocalConfigToFederatedIfNeeded(LocalRepoConfig originalConfig) {
        String repoKey = originalConfig.getKey();
        try {
            String gridTopologyId = originalConfig.getBaseConfig().getFederationConfig().getGridTopologyId();
            LocalRepoConfig.FederationExportData federationData = originalConfig.getFederation();

            if (!RepositoryGridConfigUtils.shouldMigrateToFederated(gridTopologyId, federationData, repoKey)) {
                return null; // Skipped - not a failure
            }

            log.info("Migrating local repo {} to federated (gridTopologyId: {}, hasFederationData: {})",
                    repoKey, gridTopologyId, federationData != null);
            federatedRepoService.migrate(repoKey);

            restoreFederationDetails(repoKey, federationData);
            return null; // Success
        } catch (BadRequestException e) {
            log.debug("Migration error details for repo {}: {}", repoKey, e.getMessage(), e);
            return e.getMessage();
        }
    }

    private void restoreFederationDetails(String repoKey, LocalRepoConfig.FederationExportData federationData) {
        if (!(getRepoConfigByKeyNoValidationChecks(repoKey, true)
                instanceof FederatedRepoConfig federatedRepoConfig)) {
            return;
        }

        if (restoreFederationData(federatedRepoConfig, federationData, repoKey)) {
            upsertRepoConfig(federatedRepoConfig);
        }
    }

    private boolean restoreFederationData(FederatedRepoConfig federatedRepoConfig,
            LocalRepoConfig.FederationExportData federationData,
            String repoKey) {
        if (federationData == null) {
            return false;
        }

        boolean updated = false;
        boolean membersRestored = false;

        if (!ConstantValues.gridEnabled.getBoolean() &&
                restoreFederatedMembers(federatedRepoConfig.getRepoTypeConfig(), federationData, repoKey)) {
            updated = true;
            membersRestored = true;
        }

        if (restoreProxySettings(federatedRepoConfig.getRepoTypeConfig(), federationData)) {
            updated = true;
        }

        if (updated) {
            log.info("Restored federation data on migrated federated repo {} (members: {}, proxy: {})",
                    repoKey, membersRestored,
                    StringUtils.isNotBlank(federationData.getProxy()));
        }

        return updated;
    }

    private boolean restoreFederatedMembers(FederatedRepositoryRepoTypeConfig federatedRepoTypeConfig,
            LocalRepoConfig.FederationExportData federationData,
            String repoKey) {
        if (federationData.getMembers() != null && !federationData.getMembers().isEmpty()) {
            federatedRepoTypeConfig.setFederatedMembers(federationData.getMembers());
            log.info("Restored {} members on migrated federated repo {}",
                    federationData.getMembers().size(), repoKey);
            return true;
        }
        return false;
    }

    private boolean restoreProxySettings(FederatedRepositoryRepoTypeConfig federatedRepoTypeConfig,
            LocalRepoConfig.FederationExportData federationData) {
        boolean updated = false;
        if (StringUtils.isNotBlank(federationData.getProxy())) {
            federatedRepoTypeConfig.setProxyRef(federationData.getProxy());
            updated = true;
        }
        if (federationData.isDisableProxy()) {
            federatedRepoTypeConfig.setDisableProxy(true);
            updated = true;
        }
        return updated;
    }

    @Override
    public RepoType getRepoType(String repoKey) {
        if (StringUtils.isBlank(repoKey)) {
            return null;
        }
        if (repoKey.endsWith(REMOTE_CACHE_SUFFIX)) {
            repoKey = StringUtils.removeEnd(repoKey, REMOTE_CACHE_SUFFIX);
        }
        RepoConfig repoConfigByKey = getRepoConfigByKey(repoKey);
        return repoConfigByKey == null ? null : repoConfigByKey.getPackageType();
    }

    @VisibleForTesting
    void globalValidation(Collection<? extends RepoConfig> repoConfigs) {
        globalValidation(repoConfigs, false);
    }

    @VisibleForTesting
    void globalValidation(Collection<? extends RepoConfig> repoConfigs, boolean skipRepoNameValidation) {
        configValidationService.validate(repoConfigs);
        if (!skipRepoNameValidation) {
            validateRepoNames(repoConfigs);
        }
    }

    /**
     * @return set of repo configs directly from the storage and not through the cache
     */
    public Set<RepoConfig> getAllRepoConfigsSkipCache() {
        return storageService.getAllRepositories().stream()
                .map(repositoryConfigMapper::dbInfoBlobToRepositoryConfig)
                .filter(Objects::nonNull)
                .collect(toSet());
    }

    private List<RepoConfig> convertImportedRepoConfigs(List<? extends RepoConfig> repoConfigs) {
        Set<byte[]> repoInfoBlobs = repoConfigs.stream()
                .map(repoConfig -> JsonUtils.getInstance().valueToByteArray(repoConfig))
                .collect(Collectors.toSet());
        RepositoriesConverterManager repositoriesConverterManager = new RepositoriesConverterManager(subConverters);
        return repoInfoBlobs.stream()
                .map(repositoriesConverterManager::convert)
                .toList();
    }

    /**
     * Remove all remote members from federated repositories because after import the validation of remote members
     * fails due to token issue.
     * After import, the user should establish trust and add the federated members again.
     *
     * @param repoConfigs all repos configs that should be imported
     */
    private void removeFederatedMembersFromImportedRepoConfigs(List<? extends RepoConfig> repoConfigs) {
        log.debug("Removing remote federated members from imported federated repositories - Started.");
        CentralConfigService centralConfigService = ContextHelper.get().getCentralConfig();
        String federatedRepoUrlBaseOrDefault = centralConfigService.getDescriptor().getFederatedRepoUrlBaseOrDefault();
        repoConfigs.stream()
                .filter(repoConfig -> repoConfig.getRepoType().equals(FEDERATED))
                .map(FederatedRepoConfig.class::cast)
                .forEach(federatedRepoConfig -> federatedRepoConfig.getRepoTypeConfig().getFederatedMembers()
                        .removeIf(federatedMember -> {
                            if (!FederatedMemberUtils.isLocal(federatedMember, federatedRepoConfig.getKey(),
                                    federatedRepoUrlBaseOrDefault)) {
                                log.warn("In federated repo: {}, remote member {} was removed.",
                                        federatedRepoConfig.getKey(), federatedMember.getUrlOrTemplate());
                                return true;
                            } else {
                                return false;
                            }
                        }));
        log.debug("Removing remote federated members from imported federated repositories - Finished.");
    }

    private static boolean isInvalid(String repoKey) {
        return isBlankTrashOrSupportBundle(repoKey) || isCache(repoKey);
    }

    static boolean isBlankTrashOrSupportBundle(String repoKey) {
        return StringUtils.isBlank(repoKey) || isTrash(repoKey) || isSupportBundle(repoKey);
    }

    private void insertRemoteRepoConfig(RemoteRepoConfig remoteRepoConfig) {
        remoteRepoConfig.processUrlCredentials();
        insertRepositoryInternal(remoteRepoConfig);
    }

    @VisibleForTesting
    public void setCache(RepoConfigCache repoConfigCache) {
        this.repoConfigCache = repoConfigCache;
    }

    @Override
    @Nonnull
    public List<VirtualRepoConfig> getVirtualRepoConfigs() {
        return getRepoConfigsByType(RepoDetailsType.VIRTUAL).stream()
                .map(VirtualRepoConfig.class::cast)
                .toList();
    }

    @Override
    @Nonnull
    public List<RemoteRepoConfig> getRemoteRepoConfigs() {
        return getRepoConfigsByType(REMOTE).stream()
                .map(RemoteRepoConfig.class::cast)
                .collect(Collectors.toList());
    }

    @Override
    @Nonnull
    public List<ReleaseBundleRepoConfig> getReleaseBundleRepoConfigs() {
        return getRepoConfigsByType(RELEASE_BUNDLE).stream()
                .map(ReleaseBundleRepoConfig.class::cast)
                .collect(Collectors.toList());
    }

    @Override
    @Nonnull
    public List<FederatedRepoConfig> getFederatedRepoConfigs() {
        return getRepoConfigsByType(FEDERATED).stream()
                .filter(federatedRepoConfig -> !isInfoRepo(federatedRepoConfig.getPackageType()))
                .map(FederatedRepoConfig.class::cast)
                .collect(Collectors.toList());
    }

    @Override
    @Nonnull
    public List<RealRepoConfig> getLocalFederatedAndRemoteRepoConfigsByProject(@Nullable String projectKey) {
        Stream<RepoConfig> repoConfigs =
                getRepoConfigsByTypes(Set.of(LOCAL, REMOTE, FEDERATED)).stream();
        repoConfigs = filterByProject(repoConfigs, projectKey);
        return repoConfigs.map(RealRepoConfig.class::cast).toList();
    }

    @Override
    @Nonnull
    public List<RealRepoConfig> getLocalFederatedAndRemoteRepoConfigs() {
        return getRepoConfigsByTypes(Set.of(LOCAL, REMOTE, FEDERATED))
                .stream()
                .map(RealRepoConfig.class::cast)
                .collect(Collectors.toList());
    }

    @Override
    @Nonnull
    public List<FederatedRepoConfig> getAllFederatedRepoConfigsWithSystemRepo() {
        return getRepoConfigsByType(FEDERATED).stream().map(FederatedRepoConfig.class::cast)
                .collect(Collectors.toList());
    }

    @Nonnull
    private RepoConfigChange getRepoConfigCreateChange(RepoConfig repositoryConfig) {
        return enrichWithMetadata(new RepoConfigChange(repositoryConfig.getKey(),
                REPO_CREATE, repositoryConfig, null));
    }

    /**
     * Builds the change for a single-repository update ({@code upsertRepoConfig} on an existing key,
     * {@code updateRepoConfig*}, {@code insertPackageConfigByKey}). Before the change is built, the plugin package
     * type entry of {@code packageTypeConfig} is settled by {@link PluginPackageTypes#packageTypeConfigForUpdate}:
     * an update that does not mention the key keeps the stored plugin package type, so a configuration rebuilt from
     * a partial model (the UI repository editor) does not drop it. Bulk upserts and imports replace the configuration
     * as given.
     */
    @Nonnull
    private RepoConfigChange getRepoConfigUpdateChange(RepoConfig repositoryConfig, RepoConfig oldRepoConfig) {
        preservePluginPackageType(repositoryConfig, oldRepoConfig);
        return enrichWithMetadata(new RepoConfigChange(repositoryConfig.getKey(),
                RepoConfigChange.ConfigAction.REPO_UPDATE, repositoryConfig, oldRepoConfig));
    }

    private static void preservePluginPackageType(RepoConfig repositoryConfig, RepoConfig oldRepoConfig) {
        if (oldRepoConfig == null) {
            return;
        }
        Map<String, String> requested = repositoryConfig.getPackageTypeConfig();
        Map<String, String> decided = PluginPackageTypes.packageTypeConfigForUpdate(repositoryConfig.getPackageType(),
                requested, oldRepoConfig.getPackageType(), oldRepoConfig.getPackageTypeConfig());
        if (decided != requested) {
            requested.clear();
            requested.putAll(decided);
        }
    }

    /**
     * Attaches a {@link org.artifactory.repo.RepoMetadata} snapshot to the change via the
     * {@link RepoConfigChangeFactory}, when wired. The factory is only injected in production; unit-test paths that
     * construct the service directly skip enrichment and emit changes with a {@code null} metadata field (downstream
     * consumers fall back to {@link org.artifactory.repo.RepoMetadata#empty()}).
     */
    @Nonnull
    private RepoConfigChange enrichWithMetadata(@Nonnull RepoConfigChange change) {
        if (repoConfigChangeFactory != null) {
            repoConfigChangeFactory.enrich(change);
        }
        return change;
    }

    /**
     * Bulk variant of {@link #enrichWithMetadata(RepoConfigChange)} for the change maps produced by the static
     * {@code convertRepoConfigsToCreateUpdateChanges} helper, which cannot call the factory itself (it has no
     * service context). Resolves the snapshot for every change in place.
     */
    private void enrichAllWithMetadata(@Nonnull Map<RepoDetailsType, List<RepoConfigChange>> changesByType) {
        if (repoConfigChangeFactory == null) {
            return;
        }
        for (List<RepoConfigChange> changes : changesByType.values()) {
            for (RepoConfigChange change : changes) {
                repoConfigChangeFactory.enrich(change);
            }
        }
    }

    @Nonnull
    private Set<DbRepositoryConfig> getDbRepoConfigsByAction(
            Map<RepoDetailsType, List<RepoConfigChange>> mutableRepoConfigChangesByType,
            RepoConfigChange.ConfigAction configAction) {
        return mutableRepoConfigChangesByType.values().stream()
                .flatMap(Collection::stream)
                .filter(change -> configAction.equals(change.getConfigAction()))
                .map(change -> repositoryConfigMapper
                        .repositoryConfigToDbRepositoryConfig(change.getNewRepoConfig()))
                .collect(Collectors.toSet());
    }

    @VisibleForTesting
    void dereferenceRepositoryFromConfigDescriptor(List<RepoConfig> oldRepoConfigs) {
        CentralConfigService centralConfigService = ContextHelper.get().getCentralConfig();
        CentralConfigDescriptor oldDescriptor = centralConfigService.getMutableDescriptor();
        MutableCentralConfigDescriptor mutableDescriptor = centralConfigService.getMutableDescriptor();
        oldRepoConfigs.forEach(mutableDescriptor::removeReferences);
        DiffResult diffs = ArtifactoryConfigDiffUtil.findDiff(oldDescriptor, mutableDescriptor);
        if (!diffs.getDiffs().isEmpty()) {
            centralConfigService.saveEditedDescriptorAndReload(mutableDescriptor);
        }
    }

    private List<VirtualRepoConfig> deleteReferenceFromVirtualRepos(List<RepoConfig> removedRepos, boolean isImport,
            List<Runnable> deferredFederatedCleanups) {
        // remove from any virtual repository
        List<VirtualRepoConfig> virtualRepoConfigs = findRepositoriesByType(VIRTUAL, isImport).stream()
                .map(VirtualRepoConfig.class::cast)
                .toList();
        if (virtualRepoConfigs.isEmpty()) {
            return List.of();
        }
        List<VirtualRepoConfig> virtualRepoConfigsToUpdate = new ArrayList<>();
        for (VirtualRepoConfig virtualRepoConfig : virtualRepoConfigs) {
            removedRepos.forEach(removedRepo -> {
                if (removeRefsFromVirtualRepoConfig(removedRepo, virtualRepoConfig, deferredFederatedCleanups)) {
                    virtualRepoConfigsToUpdate.add(virtualRepoConfig);
                }
            });

        }
        return virtualRepoConfigsToUpdate;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Deletion flow first removes the child from {@code repositoryRefs} (or schedules federated async cleanup
     * for federated virtuals). This helper only clears dependent fields that still reference the removed key
     * (default deployment repo ref for removed locals, external-dependencies remote ref for removed remotes).
     */
    @Override
    public boolean clearVirtualRepoRefsForRemovedChild(VirtualRepoConfig virtualRepoConfig, RepoConfig removedRepo) {
        boolean changed = false;
        if (removedRepo instanceof LocalConfig
                && virtualRepoConfig.getRepoTypeConfig().getDefaultDeploymentRepoRef() != null
                && virtualRepoConfig.getRepoTypeConfig().getDefaultDeploymentRepoRef()
                .equals(removedRepo.getKey())) {
            virtualRepoConfig.getRepoTypeConfig().setDefaultDeploymentRepoRef(null);
            changed = true;
        }
        ExternalDependenciesConfig externalDependencies =
                ExternalDependenciesPackageTypeConfigMapper.INSTANCE.map(virtualRepoConfig.getPackageTypeConfig());
        if (removedRepo instanceof RemoteRepoConfig && externalDependencies != null
                && removedRepo.getKey().equals(externalDependencies.getRemoteRepoRef())) {
            removeExternalDependencies(virtualRepoConfig);
            changed = true;
        }
        return changed;
    }

    /**
     * True if this virtual lists the removed repo in {@code repositoryRefs}. Default deployment and external
     * dependency refs are cleared during cleanup when they match the same key (validators keep them consistent
     * with repositoryRefs for normal saves).
     */
    private boolean isVirtualRepoReferencingRemovedRepo(VirtualRepoConfig virtualRepoConfig, String removedRepoKey) {
        List<String> refs = virtualRepoConfig.getRepoTypeConfig().getRepositoryRefs();
        return refs != null && refs.contains(removedRepoKey);
    }

    private boolean removeRefsFromVirtualRepoConfig(RepoConfig removedRepo, VirtualRepoConfig virtualRepoConfig,
            List<Runnable> deferredFederatedCleanups) {
        if (virtualRepoConfig.isFederated()) {
            if (ConstantValues.virtualRepoFederationEnabled.getBoolean()
                    && federatedRepoService != null
                    && isVirtualRepoReferencingRemovedRepo(virtualRepoConfig, removedRepo.getKey())) {
                // Defer until after callOnRepoConfigsReload so the RepositoriesCache reflects the deletion
                // before the async task calls repoExists(childRepoKey); otherwise the repo still appears live
                // and cleanup is silently skipped with no retry scheduled.
                String virtualRepoKey = virtualRepoConfig.getKey();
                deferredFederatedCleanups.add(() ->
                        federatedRepoService.cleanupDanglingRefAsync(virtualRepoKey, removedRepo));
            }
            // Keep federated virtual cleanup eventual-consistent only; never modify repository refs synchronously.
            // When async cleanup is not eligible (flag off, missing service, or no matching ref), leave as-is.
            return false;
        }
        boolean virtualConfigChanged = virtualRepoConfig.removeRepository(removedRepo.getKey());
        virtualConfigChanged |= clearVirtualRepoRefsForRemovedChild(virtualRepoConfig, removedRepo);
        return virtualConfigChanged;
    }

    private void removeExternalDependencies(RepoConfig repoConfig) {
        repoConfig.getPackageTypeConfig()
                .remove(ExternalDependenciesPackageTypeConfigMapper.EXTERNAL_DEPENDENCIES_ENABLED);
        repoConfig.getPackageTypeConfig()
                .remove(ExternalDependenciesPackageTypeConfigMapper.EXTERNAL_DEPENDENCIES_PATTERNS);
        repoConfig.getPackageTypeConfig()
                .remove(ExternalDependenciesPackageTypeConfigMapper.EXTERNAL_DEPENDENCIES_REMOTE_REPO_REF);
    }

    private void flushRepoConfigChanges(Map<RepoDetailsType, List<RepoConfigChange>> mutableRepoConfigChangesByType,
            boolean propagateChanges) {
        mutableRepoConfigChangesByType.values()
                .stream()
                .flatMap(List::stream)
                .filter(change -> change.getConfigAction().equals(REPO_UPDATE))
                .filter(change -> change.getNewRepoConfig() != null)
                .filter(change -> change.getOldRepoConfig() != null)
                .forEach(configChange -> {
                    configChange.getNewRepoConfig().setModification(System.currentTimeMillis());
                });

        mutableRepoConfigChangesByType.values()
                .stream()
                .flatMap(List::stream)
                .filter(change -> change.getConfigAction().equals(REPO_CREATE))
                .filter(change -> change.getNewRepoConfig() != null)
                .forEach(configChange -> {
                    long timestamp = System.currentTimeMillis();
                    configChange.getNewRepoConfig().setCreation(timestamp);
                    configChange.getNewRepoConfig().setModification(timestamp);
                });

        Set<DbRepositoryConfig> reposToCreate = getDbRepoConfigsByAction(mutableRepoConfigChangesByType, REPO_CREATE);
        Set<DbRepositoryConfig> reposToUpdate = getDbRepoConfigsByAction(mutableRepoConfigChangesByType, REPO_UPDATE);
        Set<String> reposToDelete = mutableRepoConfigChangesByType.values().stream()
                .flatMap(Collection::stream)
                .filter(change -> REPO_DELETE.equals(change.getConfigAction()))
                .map(RepoConfigChange::getRepoKey)
                .collect(Collectors.toSet());
        RepoConfigChanges repoConfigChanges = new RepoConfigChanges(mutableRepoConfigChangesByType);
        Set<String> rootFoldersToCreateForInsert = ConstantValues.repoRootAtomicCreation.getBoolean()
                ? RepoConfigRootFolderKeyExtractor.extractRootFolderKeysForInsert(repoConfigChanges)
                : Set.of();
        Set<String> rootFoldersToCreateForUpdate = ConstantValues.repoRootAtomicCreation.getBoolean()
                ? RepoConfigRootFolderKeyExtractor.extractRootFolderKeysForUpdate(repoConfigChanges)
                : Set.of();
        storageService.flushBulkChanges(reposToCreate, reposToUpdate, reposToDelete,
                rootFoldersToCreateForInsert, rootFoldersToCreateForUpdate);
        if (!reposToCreate.isEmpty()) {
            repoConfigCache.addRepoConfigKeys(dbInfoBlobListToRepoKeyAndModificationMap(reposToCreate));
        }
        if (!reposToUpdate.isEmpty()) {
            repoConfigCache.updateRepoConfigKeys(dbInfoBlobListToRepoKeyAndModificationMap(reposToUpdate));
        }
        if (!reposToDelete.isEmpty()) {
            repoConfigCache.deleteRepoConfigsByKeys(reposToDelete);
        }
        if (propagateChanges) {
            propagateRepoConfigChanges(mutableRepoConfigChangesByType);
        }
        writeMetricsInfoToMetricsThreadLocal(mutableRepoConfigChangesByType);
    }

    private Map<String, RepoConfig> getAllRepoConfigsWithoutCache() {
        List<DbRepositoryConfig> dbRepoConfigs =
                withQueryRateLimiter(SYSTEM, storageService::getAllRepositories);
        return dbInfoBlobListToRepoConfigsSet(dbRepoConfigs).stream()
                .collect(Collectors.toMap(RepoConfig::getKey, Function.identity()));
    }

    private RepoConfig getRepoByKeyWithoutCache(String repoKey) {
        DbRepositoryConfig dbRepositoryConfig =
                withQueryRateLimiter(SYSTEM, () -> storageService.findRepositoryByKey(repoKey));
        if (dbRepositoryConfig != null) {
            return repositoryConfigMapper.dbInfoBlobToRepositoryConfig(dbRepositoryConfig);
        }
        return null;
    }

    private Map<String, RepoConfig> getRepoConfigsByKeysWithoutCache(Set<String> repoKeys) {
        Set<DbRepositoryConfig> dbRepoConfigs =
                withQueryRateLimiter(SYSTEM, () -> storageService.findRepositoryConfigsByKeys(repoKeys));
        return dbInfoBlobListToRepoConfigsSet(dbRepoConfigs).stream()
                .collect(Collectors.toMap(RepoConfig::getKey, Function.identity()));
    }

    private Set<RepoConfig> dbInfoBlobListToRepoConfigsSet(Collection<DbRepositoryConfig> dbInfoBlobs) {
        return dbInfoBlobs.stream()
                .map(dbRepo -> repositoryConfigMapper.dbInfoBlobToRepositoryConfig(dbRepo))
                .filter(Objects::nonNull)
                .collect(toSet());
    }

    private Map<String, BasicRepoConfig> dbInfoBlobListToRepoKeyAndModificationMap(
            Collection<DbRepositoryConfig> dbInfoBlobs) {
        return dbInfoBlobListToRepoConfigsSet(dbInfoBlobs).stream()
                .collect(toMap(RepoConfig::getKey, RepoConfig::toBasic));
    }

    private void flushRepoConfigChange(RepoConfig repositoryConfig, RepoConfigChange repoConfigChange,
            boolean skipBeforeInterceptors, boolean skipAfterInterceptors) {
        RepoDetailsType type = repositoryConfig.getRepoType();
        Map<RepoDetailsType, List<RepoConfigChange>> mutableRepoConfigChangesByType =
                new EnumMap<>(RepoDetailsType.class);
        mutableRepoConfigChangesByType.put(type, Lists.newLinkedList(List.of(repoConfigChange)));
        if (!skipBeforeInterceptors) {
            triggerOnBeforeSaveOperations(mutableRepoConfigChangesByType, false);
        }
        flushRepoConfigChanges(mutableRepoConfigChangesByType, true);
        callOnRepoConfigsReload(RepoConfigChangesReload.fromChanges(mutableRepoConfigChangesByType), false);
        if (!skipAfterInterceptors) {
            interceptors.forEach(interceptor -> interceptor.onAfterSave(mutableRepoConfigChangesByType));
        }
    }

    private void flushRepoConfigChange(RepoConfig repositoryConfig, RepoConfigChange repoConfigChange) {
        flushRepoConfigChange(repositoryConfig, repoConfigChange, false, false);
    }

    private void createDefaultRepoConfigsIfDoesNotExist() {
        Authentication originalAuthentication = SecurityContextHolder.getContext().getAuthentication();
        try {
            SecurityContextHolder.getContext().setAuthentication(new SystemAuthenticationToken());
            List<LocalRepoConfig> reposToCreate = getLocalRepoConfigsToCreate();
            bulkInsertRepoConfigs(reposToCreate, true, true, false);
        } finally {
            SecurityContextHolder.getContext().setAuthentication(originalAuthentication);
        }
    }

    /**
     * Generate Local repo configs to create on first start.
     * If {@link ConstantValues#skipExampleRepoCreation} is set, example-repo-local will not get created.
     */
    @NonNull
    private List<LocalRepoConfig> getLocalRepoConfigsToCreate() {
        LocalRepoConfig buildInfoRepoConfig =
                new BuildInfoRepo().buildRepoConfig(ContextHelper.get().getCentralConfig().getDescriptor());
        LocalRepoConfig exampleLocalRepoConfig = generateExampleRepoLocalConfig();
        List<LocalRepoConfig> reposToCreate;
        if (skipExampleRepoCreation.getBoolean()) {
            reposToCreate = List.of(buildInfoRepoConfig);
        } else {
            reposToCreate = List.of(buildInfoRepoConfig, exampleLocalRepoConfig);
        }
        return reposToCreate;
    }

    private void insertRepositoryInternal(RepoConfig repositoryConfig) {
        setBackups(repositoryConfig.getKey());
        RepoConfigChange repoConfigChange = getRepoConfigCreateChange(repositoryConfig);
        flushRepoConfigChange(repositoryConfig, repoConfigChange);
    }

    private void updateRepositoryInternal(RepoConfig repositoryConfig, boolean skipBeforeInterceptors,
            boolean skipAfterInterceptors) {
        RepoConfig oldRepoConfig = getRepoConfigByKey(repositoryConfig.getKey());
        if (oldRepoConfig == null) {
            throw new NotFoundException(
                    String.format("Cannot update repository '%s': repository config does not exist.",
                            repositoryConfig.getKey()));
        }
        RepoConfigChange repoConfigChange = getRepoConfigUpdateChange(repositoryConfig, oldRepoConfig);
        flushRepoConfigChange(repositoryConfig, repoConfigChange, skipBeforeInterceptors, skipAfterInterceptors);
    }

    private void setBackups(String repoKey) {
        setBackups(List.of(repoKey));
    }

    private void setBackups(List<String> repoKeys) {
        CentralConfigService centralConfig = ContextHelper.get().getCentralConfig();
        MutableCentralConfigDescriptor mutableDescriptor = centralConfig.getMutableDescriptor();
        List<String> addToBackups =
                repoKeys.stream().filter(mutableDescriptor::conditionallyAddToBackups).toList();
        if (!addToBackups.isEmpty()) {
            centralConfig.saveEditedDescriptorAndReload(mutableDescriptor);
        }
    }

    private void propagateRepoConfigChanges(Map<RepoDetailsType, List<RepoConfigChange>> repoConfigChangeByType) {
        RepoConfigPropagationModel propagationModel = new RepoConfigPropagationModel();
        List<RepoConfigReload> repoConfigReloads = repoConfigChangeByType.values().stream()
                .flatMap(Collection::stream).map(RepoConfigReload::new).toList();
        propagationModel.setRepoConfigCreateOrUpdate(
                filterRepoConfigChangeByAction(repoConfigReloads, Set.of(REPO_CREATE, REPO_UPDATE)));
        propagationModel.setRepoConfigDelete(filterRepoConfigChangeByAction(repoConfigReloads, Set.of(REPO_DELETE)));
        if (propagationModel.isEmpty()) {
            return;
        }
        ContextHelper.get().beanForType(AddonsManager.class).addonByType(HaAddon.class)
                .propagateRepoConfigsChanges(propagationModel);
    }

    private Set<RepoConfigReload> filterRepoConfigChangeByAction(List<RepoConfigReload> repoConfigReloads,
            Set<RepoConfigChange.ConfigAction> actions) {
        return repoConfigReloads.stream()
                .filter(repo -> actions.contains(repo.getConfigAction()))
                .collect(Collectors.toSet());
    }

    private boolean isPomCleanUpPolicyChanged(RepoConfig newRepoConfig, RepoConfig oldRepoConfig) {
        // TODO[t.r] - consider reading the values differently
        MavenConfiguration newMavenConfig = getDeserializedType(MavenConfiguration.class, newRepoConfig);
        MavenConfiguration oldMavenConfig = getDeserializedType(MavenConfiguration.class, oldRepoConfig);
        PomCleanupPolicy newPolicy = newMavenConfig.getPomRepositoryReferencesCleanupPolicy();
        PomCleanupPolicy oldPolicy = oldMavenConfig.getPomRepositoryReferencesCleanupPolicy();
        return !newPolicy.equals(oldPolicy);
    }

    private boolean isRemoteRepoConfigChanged(RemoteRepoConfig newConfig, RemoteRepoConfig oldConfig) {
        return newConfig.getRepoTypeConfig().getSocketTimeoutMillis() !=
                (oldConfig.getRepoTypeConfig().getSocketTimeoutMillis()) ||
                !StringUtils.equals(newConfig.getRepoTypeConfig().getUrl(), oldConfig.getRepoTypeConfig().getUrl());
    }

    /**
     * Writes relevant data to {@link MetricsThreadLocal}. This data is later used by MetricsListenerService.
     *
     * @param typeToChanges Map of config changes to process.
     */
    void writeMetricsInfoToMetricsThreadLocal(Map<RepoDetailsType, List<RepoConfigChange>> typeToChanges) {
        List<MetricsInfo> metricsInfos = new ArrayList<>();
        typeToChanges.forEach((repoDetailsType, repoConfigChanges) -> repoConfigChanges
                .forEach(repoConfigChange -> metricsInfos.add(
                        MetricsInfo.builder().repoClass(repoDetailsType.typeNameLowercase())
                                .repoType(repoConfigChange.getConfigAction() == REPO_CREATE ?
                                        repoConfigChange.getNewRepoConfig().getPackageType().getType() :
                                        repoConfigChange.getOldRepoConfig().getPackageType().getType())
                                .action(getRepoActionName(repoConfigChange)).build())));
        MetricsThreadLocal.setMetricsInfoList(metricsInfos);
    }

    private String getRepoActionName(RepoConfigChange repoConfigChange) {
        switch (repoConfigChange.getConfigAction()) {
            case REPO_CREATE:
                return "REPO_CREATED";
            case REPO_UPDATE:
                return "REPO_UPDATED";
            case REPO_DELETE:
                return "REPO_DELETED";
            default:
                String actionName = repoConfigChange.getConfigAction().name();
                log.error("Failed to convert {} config action. Action not found", actionName);
                return null;
        }
    }

    @Override
    public Map<RepoDetailsType, List<RepoConfigChange>> apply(Set<? extends RepoConfig> repoConfigs) {
        Collection<RepoConfig> existingRepos = getExistingRepoConfigs(repoConfigs.stream().toList(), false);
        Map<RepoDetailsType, List<RepoConfigChange>> changes =
                convertRepoConfigsToCreateUpdateChanges(repoConfigs.stream().toList(), existingRepos);
        enrichAllWithMetadata(changes);
        return changes;
    }

    /**
     * Convert repo configs to {@link RepoConfigChange} and group by {@link RepoDetailsType}.
     */
    public static Map<RepoDetailsType, List<RepoConfigChange>> convertRepoConfigsToCreateUpdateChanges(
            Collection<? extends RepoConfig> repoConfigsToClassify, Collection<RepoConfig> existingRepoConfigs) {
        return convertRepoConfigsToCreateUpdateChanges(repoConfigsToClassify, existingRepoConfigs, false);
    }

    public static Map<RepoDetailsType, List<RepoConfigChange>> convertRepoConfigsToCreateUpdateChanges(
            Collection<? extends RepoConfig> repoConfigsToClassify, Collection<RepoConfig> existingRepoConfigs,
            boolean isImport) {
        Map<String, RepoConfig> existingRepoConfigsMap = existingRepoConfigs.stream()
                .collect(toMap(RepoConfig::getKey, Function.identity()));
        return repoConfigsToClassify.stream()
                .map(repo -> {
                    RepoConfig existingRepo = existingRepoConfigsMap.get(repo.getKey());
                    if (!isImport && existingRepo == null) {
                        badRequestValidation(repo);
                    }
                    RepoConfigChange.ConfigAction configAction = existingRepo == null ? REPO_CREATE : REPO_UPDATE;
                    return new RepoConfigChange(repo.getKey(), configAction, repo, existingRepo);
                })
                .collect(groupingBy(change -> change.getNewRepoConfig().getRepoType()));
    }

    private static void badRequestValidation(RepoConfig repo) {
        if (isCache(repo.getKey())) {
            throw new BadRequestException(
                    "Unable to create a " + repo.getRepoType().typeNameLowercase() + " repository '" +
                            repo.getKey() + "' with '-cache' suffix");
        }
        if (isBlankTrashOrSupportBundle(repo.getKey())) {
            throw new BadRequestException(String.format(
                    "'%s' repository, the repo name is forbidden", repo.getKey()));
        }
    }

    /**
     * Convert repo configs to {@link RepoConfigChange} and group by {@link RepoDetailsType}.
     */
    public static Map<RepoDetailsType, List<RepoConfigChange>> convertRepoConfigsToUpdateChanges(
            List<? extends RepoConfig> repoConfigsToClassify, Collection<RepoConfig> existingRepoConfigs) {
        Map<String, RepoConfig> existingRepoConfigsMap = existingRepoConfigs.stream()
                .collect(toMap(RepoConfig::getKey, Function.identity()));
        return repoConfigsToClassify.stream()
                .map(repo -> {
                    RepoConfig existingRepo = existingRepoConfigsMap.get(repo.getKey());
                    if (existingRepo != null) {
                        return new RepoConfigChange(repo.getKey(), REPO_UPDATE, repo, existingRepo);
                    }
                    return null;
                }).filter(Objects::nonNull)
                .collect(groupingBy(change -> change.getNewRepoConfig().getRepoType()));
    }


    private Collection<RepoConfig> getExistingRepoConfigs(Collection<? extends RepoConfig> repositoryConfigs,
            boolean isImport) {
        Set<String> repoKeys = repositoryConfigs.stream().map(RepoConfig::getKey).collect(Collectors.toSet());
        return isImport ? getRepoConfigsByKeysWithoutCache(repoKeys).values() : getReposByKeysFromCache(repoKeys);
    }

    private Collection<RepoConfig> getReposByKeysFromCache(Set<String> repoKeys) {
        Set<String> requestedRepoKeys = new HashSet<>(repoKeys);
        Collection<String> allRepoKeys = repoConfigCache.getAllRepoKeys();
        requestedRepoKeys.retainAll(allRepoKeys);
        return requestedRepoKeys.isEmpty() ? List.of() : repoConfigCache.getRepositoryConfigsByKeys(requestedRepoKeys);
    }

    private Collection<RepoConfig> findRepositoriesByType(RepoDetailsType repoType, boolean isImport) {
        if (isImport) {
            String typeAsString = repoConfigAttributesMapper.repoDetailTypeToLowercaseRepoTypeName(repoType);
            return dbInfoBlobListToRepoConfigsSet(storageService.findRepositoriesByDetailsType(typeAsString));
        }
        Set<String> filteredRepoKeys = findRepositoryKeysByType(repoType);
        return new ArrayList<>(repoConfigCache.getRepositoryConfigsByKeys(filteredRepoKeys));
    }

    @Nonnull
    private Set<String> findRepositoryKeysByType(@Nonnull RepoDetailsType repoType) {
        Collection<BasicRepoConfig> allBasicRepoConfigs = repoConfigCache.getAllBasicConfigs();
        return allBasicRepoConfigs.stream()
                .filter(repoConfig -> repoType.equals(repoConfig.getRepoType()))
                .map(BasicRepoConfig::getRepoKey)
                .collect(toSet());
    }

    private RepoConfig dbInfoBlobToRepositoryConfig(DbRepositoryConfig dbRepositoryConfig) {
        return Optional.of(dbRepositoryConfig)
                .map(repositoryConfigMapper::dbInfoBlobToRepositoryConfig)
                .orElse(null);
    }

    private void callOnRepoConfigReloadAsync(RepoConfigPropagationModel model) {
        if (model == null) {
            return;
        }
        RepoConfigChangesReload repoConfigChangesReload = createConfigChangesReloadFromPropagation(model);
        ContextHelper.get().beanForType(RepositoryConfigService.class).callOnRepoConfigsReloadAsync(
                repoConfigChangesReload, false);
    }

    private RepoConfigChangesReload createConfigChangesReloadFromPropagation(RepoConfigPropagationModel model) {
        Set<String> createdRepoKeys = model.getRepoConfigCreatedKeys();
        Set<String> updatedRepoKeys = model.getRepoConfigUpdatedKeys();
        Set<String> deletedRepoKeys = model.getRepoConfigDeletedKeys();
        RepoConfigChangesReloadFactory repoConfigChangesReloadFactory =
                new RepoConfigChangesReloadFactory(repoConfigCache::getRepositoryConfigsByKeys,
                        this::getRepoConfigsFromDb);
        return repoConfigChangesReloadFactory.create(createdRepoKeys, updatedRepoKeys, deletedRepoKeys);
    }

    private Map<String, BasicRepoConfig> loadAllBasicRepoConfigsFromDb() {
        Map<String, BasicRepoConfig> result = withQueryRateLimiter(SYSTEM,
                () -> storageService.getAllBasicRepoConfigs().stream()
                        .filter(entity -> !skipUnknownPackageType(entity.getPackageType()))
                        .map(entity -> new BasicRepoConfig(entity.getKey(), entity.getModified(),
                                repoConfigAttributesMapper.repoTypeNameToRepoDetailsType(entity.getType()),
                                repoConfigAttributesMapper.typeStringToPackageType(entity.getPackageType())))
                        .collect(toMap(BasicRepoConfig::getRepoKey, Function.identity()))
        );
        enrichWithMemberCounts(result);
        return result;
    }

    /**
     * Whether an unknown-type repo (detected from the {@code package_type} column via {@link RepoType#fromString},
     * alias-aware, matching {@code convertBlobOrSkip}) should be excluded from the basics cache, mirroring the blob
     * read path. Kept and loaded as Generic when the skip policy is disabled.
     */
    private static boolean skipUnknownPackageType(String packageType) {
        return ConstantValues.repoConfigSkipUnknownPackageType.getBoolean()
                && RepoType.fromString(packageType) == null;
    }

    /**
     * DB basic rows don't carry member-count, so we load full configs
     * only for virtual/federated repos (typically a small fraction) and
     * replace their lightweight entries with ones that include the count.
     */
    private void enrichWithMemberCounts(Map<String, BasicRepoConfig> basics) {
        Set<String> memberTypeKeys = basics.values().stream()
                .filter(b -> b.getRepoType() == VIRTUAL || b.getRepoType() == FEDERATED)
                .map(BasicRepoConfig::getRepoKey)
                .collect(Collectors.toSet());
        if (memberTypeKeys.isEmpty()) {
            return;
        }
        getRepoConfigsByKeysWithoutCache(memberTypeKeys)
                .forEach((key, config) -> basics.put(key, new BasicRepoConfig(config)));
    }

    private Set<RepoConfig> getRepoConfigsByKeys(Collection<String> repoKeys, Set<String> allRepoKeys) {
        if (repoKeys.isEmpty()) {
            log.debug(
                    "Attempt to search repository by keys, but no repository keys were provided. Returning empty list" +
                            "...");
            return Set.of();
        }
        Set<String> copyOfRequestedKeys = new HashSet<>(repoKeys);
        copyOfRequestedKeys.retainAll(allRepoKeys);
        if (copyOfRequestedKeys.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(repoConfigCache.getRepositoryConfigsByKeys(copyOfRequestedKeys));
    }

    @Override
    public <T, Y extends Exception> T runWithVirtualResolveConfigCache(String virtualRepoKey,
            SupplierWithError<T, Y> supplier) throws Y {
        return repoConfigCache.runWithVirtualResolveConfigCache(virtualRepoKey, supplier);
    }

    public List<RepoConfig> filterRepoConfigsAllowedForImport(List<RepoConfig> repoConfigs) {
        return repoConfigs.stream()
                .filter(repoConfig -> !isSystemRepo(repoConfig.getKey()) ||
                        !getExcludedFromImportSystemRepos().contains(repoConfig.getKey()))
                .toList();
    }

    public List<String> filterReposAllowedForImport(List<String> repoKeys) {
        return repoKeys.stream()
                .filter(repoKey -> !isSystemRepo(repoKey) || !getExcludedFromImportSystemRepos().contains(repoKey))
                .toList();
    }

    /**
     * Executes an action while holding a lock for the given repository key.
     * Uses the appropriate locking mechanism (JVM or DB) based on HA configuration.
     *
     * @param repoKey the repository key to lock
     * @param action  the action to execute while holding the lock
     */
    private void withRepoLock(String repoKey, Runnable action) {
        ConflictsGuardUtils.runWithLocks(getRepoCreationLock(), Set.of(repoKey),
                REPO_LOCK_TIMEOUT_SECS, TimeUnit.SECONDS, action);
    }

    /**
     * Executes an action while holding locks for multiple repository keys.
     * Locks are acquired in sorted order to prevent deadlocks.
     * Uses the appropriate locking mechanism (JVM or DB) based on HA configuration.
     *
     * @param repoKeys the repository keys to lock
     * @param action   the action to execute while holding the locks
     */
    private void withRepoLocks(Set<String> repoKeys, Runnable action) {
        ConflictsGuardUtils.runWithLocks(getRepoCreationLock(), repoKeys,
                REPO_LOCK_TIMEOUT_SECS, TimeUnit.SECONDS, action);
    }

    private ConflictsGuard<String> getRepoCreationLock() {
        return addonsManager.addonByType(HaCommonAddon.class).getConflictsGuard(REPO_CREATION.getName());
    }

}
