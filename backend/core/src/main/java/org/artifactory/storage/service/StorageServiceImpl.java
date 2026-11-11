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

package org.artifactory.storage.service;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.MoreExecutors;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;
import lombok.NonNull;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.HaAddon;
import org.artifactory.addon.ha.HaCommonAddon;
import org.artifactory.api.common.BasicStatusHolder;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.api.repo.storage.RepoStorageSummaryInfo;
import org.artifactory.api.security.SecurityService;
import org.artifactory.api.storage.StorageQuotaInfo;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.status.MutableStatusHolder;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.config.CentralConfigKey;
import org.artifactory.cron.CronUtils;
import org.artifactory.descriptor.config.CentralConfigDescriptor;
import org.artifactory.descriptor.gc.GcConfigDescriptor;
import org.artifactory.descriptor.quota.QuotaConfigDescriptor;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.fs.ItemInfo;
import org.artifactory.mbean.MBeanRegistrationServiceImpl;
import org.artifactory.md.Properties;
import org.artifactory.projects.ProjectsService;
import org.artifactory.repo.interceptor.storage.StorageInterceptorAdapter;
import org.artifactory.repo.trash.TrashService;
import org.artifactory.sapi.fs.VfsItem;
import org.artifactory.sapi.interceptor.context.DeleteContext;
import org.artifactory.sapi.interceptor.context.InterceptorCreateContext;
import org.artifactory.sapi.interceptor.context.InterceptorMoveCopyContext;
import org.artifactory.schedule.BaseTaskServiceDescriptorHandler;
import org.artifactory.schedule.QuartzCommand;
import org.artifactory.schedule.Task;
import org.artifactory.schedule.TaskService;
import org.artifactory.schedule.TaskUtils;
import org.artifactory.sh.acl.ArtifactoryBinaryService;
import org.artifactory.sh.cleanup.job.BinaryStoreGarbageCollectorJob;
import org.artifactory.sh.cleanup.job.BinaryStoreTrashCleanupGCJob;
import org.artifactory.spring.ContextCreationListener;
import org.artifactory.spring.EntitlementsEventListener;
import org.artifactory.spring.InternalContextHelper;
import org.artifactory.spring.Reloadable;
import org.artifactory.storage.CacheUnAvailableException;
import org.artifactory.storage.FileStoreStorageSummary;
import org.artifactory.storage.StorageException;
import org.artifactory.storage.StorageService;
import org.artifactory.storage.StorageSummaryInfo;
import org.artifactory.storage.StorageSummaryService;
import org.artifactory.storage.StorageUsage;
import org.artifactory.storage.StorageUsageUpdateStatus;
import org.artifactory.storage.db.DbService;
import org.artifactory.storage.db.fs.model.DbMutableFile;
import org.artifactory.storage.fs.service.ConfigsService;
import org.artifactory.storage.fs.service.ConfigsServiceKey;
import org.artifactory.storage.jobs.CalculateProjectBinaryStorageJob;
import org.artifactory.storage.jobs.CalculateReposStorageSummaryJob;
import org.artifactory.storage.jobs.ShardingBalancerJob;
import org.artifactory.storage.jobs.SyncStorageUsageJob;
import org.artifactory.storage.jobs.UpdateStorageMetricJob;
import org.artifactory.storage.mbean.ManagedStorage;
import org.artifactory.util.AolUtils;
import org.artifactory.util.RepoPathUtils;
import org.artifactory.version.CompoundVersionDetails;
import org.jfrog.common.ExecutionUtils;
import org.jfrog.common.JsonParsingException;
import org.jfrog.common.JsonUtils;
import org.jfrog.common.RetryException;
import org.jfrog.common.concurrency.ConflictGuard;
import org.jfrog.common.concurrency.ConflictsGuard;
import org.jfrog.common.config.diff.DataDiff;
import org.jfrog.storage.DbType;
import org.jfrog.storage.binstore.ifc.model.BinariesDirInfo;
import org.jfrog.storage.binstore.ifc.model.BinaryProvidersInfo;
import org.jfrog.storage.binstore.ifc.model.BinaryTreeElement;
import org.jfrog.storage.binstore.ifc.model.StorageInfo;
import org.jfrog.storage.binstore.utils.BinaryProvidersWhitelistUtils;
import org.jfrog.storage.common.StorageUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.artifactory.api.repo.storage.RepoStorageSummaryInfo.RepositoryType;
import static org.artifactory.repo.system.SystemRepos.TRASH_REPO_KEY;

/**
 * @author yoavl
 */
@Service
@Reloadable(beanClass = InternalStorageService.class, initAfter = TaskService.class, listenOn =
        CentralConfigKey.gcConfig)
public class StorageServiceImpl extends StorageInterceptorAdapter
        implements InternalStorageService, ContextCreationListener, EntitlementsEventListener {
    public static final String STORAGE_SUMMARY_KEY = "reposStorageSummary";
    public static final String STORAGE_SUMMARY_MAP = "storageSummaryMap";
    private static final Logger log = LoggerFactory.getLogger(StorageServiceImpl.class);
    private static final List<String> SENSITIVE_PROVIDER_KEYS = Lists.newArrayList("identity", "credential",
            "proxyIdentity", "proxyCredential", "accountName", "accountKey", "kmsCryptoMode");
    private final Random random = new Random();
    private CentralConfigService centralConfigService;
    private DbService dbService;
    private ArtifactoryBinaryService binaryStore;
    private TaskService taskService;
    private RepositoryService repositoryService;
    private AddonsManager addonsManager;
    private ProjectsService projectsService;
    private ConfigsService configsService;
    private boolean derbyUsed;
    private Map<String, AtomicLong> reposStorageDiff;
    private Map<String, AtomicLong> projectsStorageDiff;
    private double storageQuotaErrorTolerance;
    private int storageUsageSyncMaxRetriesUntilFlush;
    private boolean storageSummaryCacheEnable;
    private AtomicInteger storageUsageSyncCallsSinceLastFlush;
    private AtomicBoolean isStorageUsageSyncJobActivated;
    private StorageSummaryService storageSummaryService;

    @Override
    public void compress(BasicStatusHolder statusHolder) {
        if (!derbyUsed) {
            statusHolder.error("Compress command is not supported on current database type.", log);
            return;
        }
        logStorageSizes();
        dbService.compressDerbyDb(statusHolder);
        logStorageSizes();
    }

    @Override
    public void logStorageSizes() {
        ArtifactoryHome artifactoryHome = ContextHelper.get().getArtifactoryHome();
        File derbyDirectory = new File(artifactoryHome.getDataDir(), "derby");
        long sizeOfDirectory = FileUtils.sizeOfDirectory(derbyDirectory);
        if (log.isInfoEnabled()) {
            log.info("Derby database storage size: {} ({})", StorageUnit.toReadableString(sizeOfDirectory),
                    derbyDirectory);
        }
    }

    @Override
    public void ping(boolean localOnly) {
        binaryStore.ping(localOnly);
    }

    @Override
    public BinaryProvidersInfo<Map<String, String>> getBinaryProviderInfo() {
        return this.getGeneralBinaryProviderInfo(binaryStore.getBinaryProvidersInfo());
    }

    @Override
    public BinaryProvidersInfo<Map<String, String>> getFullBinaryProviderInfo() {
        return this.getGeneralBinaryProviderInfo(binaryStore.getFullBinaryProvidersInfo());
    }

    private BinaryProvidersInfo<Map<String, String>> getGeneralBinaryProviderInfo(
            BinaryProvidersInfo<Map<String, String>> binaryProvidersInfo) {
        // Add the quota limits to the result
        QuotaConfigDescriptor quotaConfig = centralConfigService.getDescriptor().getQuotaConfig();
        if (quotaConfig != null && quotaConfig.isEnabled()) {
            int quotaErrorLimit = quotaConfig.getDiskSpaceLimitPercentage();
            int quotaWarningLimit = quotaConfig.getDiskSpaceWarningPercentage();

            Optional<BinaryTreeElement<Map<String, String>>> rootElement =
                    Optional.of(binaryProvidersInfo.rootTreeElement);

            while (rootElement.isPresent() &&
                    !BinaryProvidersWhitelistUtils.isSupportQuota(rootElement.get().getData().get("type"))) {
                rootElement = Optional.ofNullable(rootElement.get().getNextBinaryTreeElement());
            }

            if (rootElement.isPresent()) {
                rootElement.get().getData().put("quotaErrorLimit", String.valueOf(quotaErrorLimit));
                rootElement.get().getData().put("quotaWarningLimit", String.valueOf(quotaWarningLimit));
            }
        }
        removeSensitiveValues(binaryProvidersInfo);
        return binaryProvidersInfo;
    }

    @Override
    public FileStoreStorageSummary getFileStoreStorageSummary() {
        List<BinariesDirInfo> binariesDirs = Lists.newArrayList();
        BinariesDirInfo binariesDir = binaryStore.getBinariesDirInfo();
        if (binariesDir != null) {
            binariesDirs.add(binariesDir);
        }
        BinaryProvidersInfo<Map<String, String>> binaryProvidersInfo = binaryStore.getBinaryProvidersInfo();

        return new FileStoreStorageSummary(binariesDirs, binaryProvidersInfo);
    }

    @Override
    public void calculateStorageSummaryAsync() {
        calculateStorageSummaryWithLock(false);
    }

    @Override
    public void calculateStorageSummaryAsyncOnStartup() {
        calculateStorageSummaryWithLock(true);
    }

    @Override
    public void calculateStorageSummary() {
        calculateStorageSummaryWithLock(false);
    }

    private void removeSensitiveValues(BinaryProvidersInfo<Map<String, String>> binaryProviderInfo) {
        BinaryTreeElement<Map<String, String>> rootTreeElement = binaryProviderInfo.rootTreeElement;
        while (rootTreeElement != null) {
            Map<String, String> finalData = rootTreeElement.getData();
            SENSITIVE_PROVIDER_KEYS.forEach(finalData::remove);
            rootTreeElement = rootTreeElement.getNextBinaryTreeElement();
        }
    }

    private void calculateStorageSummaryWithLock(boolean onStartup) {
        log.debug("Start to calculate storage summary");
        ConflictGuard lock = getStorageSummaryLock();
        boolean lockAcquired = false;
        try {
            lockAcquired = lock.tryToLock(0, TimeUnit.SECONDS);
            if (lockAcquired) {
                log.debug("Lock acquired on key {}", STORAGE_SUMMARY_KEY);
                if (!storageSummaryCacheEnable) {
                    calculateStorageSummaryOld(onStartup);
                } else if (onStartup) {
                    calculateStorageSummaryOnStartup();
                } else {
                    calculateStorageSummaryForce();
                }
            } else {
                log.debug("Could not acquire lock on key {}, cache was not updated", STORAGE_SUMMARY_KEY);
            }
        } catch (InterruptedException e) {
            log.error("Failed to acquire lock on key {}. {}'", STORAGE_SUMMARY_KEY, e.getMessage(), e);
        } finally {
            if (lockAcquired) {
                lock.unlock();
                log.debug("Lock on key '{}' released in finally block", STORAGE_SUMMARY_KEY);
            }
            log.debug("Finished to calculate storage summary");
        }
    }

    private void calculateStorageSummaryOld(boolean onStartup) {
        StorageSummaryInfo info = storageSummaryService.updateAndGet();
        clearStorageDiffs();
        saveAccurateStorageUsageStateWithRetries(info);
        propagateStorageSummaryIfNeeded(info, onStartup);
    }

    private void calculateStorageSummaryForce() {
        StorageSummaryInfo info = storageSummaryService.updateAndGet();
        clearStorageDiffs();
        saveAccurateStorageUsageStateWithRetries(info);
        propagateStorageSummaryIfNeeded(info, false);
    }

    private void calculateStorageSummaryOnStartup() {
        StorageSummaryInfo info = storageSummaryService.get();
        clearStorageDiffs();
        if (ConstantValues.projectsStorageQuotaEnabled.getBoolean()) {
            StorageUsageUpdateStatus status = syncStorageUsageWithStorageSummary();
            if (status == StorageUsageUpdateStatus.EMPTY) {
                saveAccurateStorageUsageStateWithRetries(info);
            }
        }
    }

    private ConflictGuard getStorageSummaryLock() {
        ConflictsGuard<Object> conflictsGuard = addonsManager.addonByType(HaCommonAddon.class)
                .getConflictsGuard(STORAGE_SUMMARY_MAP);
        return conflictsGuard.getLock(STORAGE_SUMMARY_KEY);
    }

    @Override
    public void clearStorageDiffs() {
        projectsStorageDiff.clear();
        reposStorageDiff.clear();
        storageUsageSyncCallsSinceLastFlush.set(0);
    }

    private void propagateStorageSummaryIfNeeded(StorageSummaryInfo cache, boolean onStartup) {
        HaAddon haAddon = addonsManager.addonByType(HaAddon.class);
        if (haAddon.isHaEnabled()) {
            if (onStartup) {
                ContextHelper.get().beanForType(SecurityService.class)
                        .doAsSystem(() -> haAddon.propagateStorageSummaryCache(cache));
            } else {
                haAddon.propagateStorageSummaryCache(cache);
            }
        }
    }

    private void propagateStorageUsageSyncIfNeeded() {
        HaAddon haAddon = addonsManager.addonByType(HaAddon.class);
        if (haAddon.isHaEnabled()) {
            haAddon.propagateStorageUsageChanges();
        }
    }

    @Override
    public StorageQuotaInfo getStorageQuotaInfo(long fileContentLength) {
        CentralConfigDescriptor descriptor = centralConfigService.getDescriptor();
        QuotaConfigDescriptor quotaConfig = descriptor.getQuotaConfig();
        if (quotaConfig == null) {
            return null;
        }
        if (!quotaConfig.isEnabled()) {
            return null;
        }
        StorageInfo storageInfo = binaryStore.getStorageInfoSummary();
        long totalSpace = storageInfo.getTotalSpace();
        long usageSpace = storageInfo.getUsedSpace();
        return new StorageQuotaInfo(totalSpace, usageSpace, quotaConfig.getDiskSpaceLimitPercentage(),
                quotaConfig.getDiskSpaceWarningPercentage());
    }

    @Override
    public void callManualGarbageCollect(BasicStatusHolder statusHolder) {
        callManualTask(BinaryStoreGarbageCollectorJob.class, true, statusHolder);
    }

    @Override
    public void callManualShardingBalancer(BasicStatusHolder statusHolder) {
        log.debug("Check if sharding balancer is needed from GC");
        if (binaryStore.isNeedOptimization()) {
            log.debug("Sharding balancer is needed in GC, starting balancing job");
            callManualTask(ShardingBalancerJob.class, false, statusHolder);
        } else {
            log.info("Sharding balancer is not required");
        }
    }

    @Override
    public void runShardingBalancer(BasicStatusHolder statusHolder) {
        callManualTask(ShardingBalancerJob.class, true, statusHolder);
    }

    private <T extends QuartzCommand> void callManualTask(Class<T> jobClass, boolean waitForRunning,
            BasicStatusHolder statusHolder) {
        Task task = TaskUtils.createManualTask(jobClass, 0L);
        taskService.checkCanStartManualTask(task, jobClass, statusHolder);
        if (!statusHolder.isError()) {
            try {
                String token = taskService.startTask(task, waitForRunning, true);
                if (!waitForRunning) {
                    return;
                }
                taskService.waitForTaskCompletion(token);
            } catch (Exception e) {
                statusHolder.error("Error activating task: " + e.getMessage(), e, log);
            }
        }
    }

    @Override
    public boolean isDerbyUsed() {
        return derbyUsed;
    }

    @Override
    public void init() {
        derbyUsed = dbService.getDatabaseType() == DbType.DERBY;

        ContextHelper.get().beanForType(MBeanRegistrationServiceImpl.class).
                register(new ManagedStorage(this), "Storage", "Binary Storage");

        CentralConfigDescriptor descriptor = centralConfigService.getDescriptor();
        rescheduleGC(descriptor, null);
        scheduleTrashBinariesCleanupGCJob();

        isStorageUsageSyncJobActivated = new AtomicBoolean(false);
        reposStorageDiff = new ConcurrentHashMap<>();
        projectsStorageDiff = new ConcurrentHashMap<>();
        storageQuotaErrorTolerance = (ConstantValues.projectsStorageQuotaErrorTolerancePercentage.getInt() / 100.0);
        storageUsageSyncMaxRetriesUntilFlush = ConstantValues.storageUsageSyncMaxRetriesUntilFlush.getInt();
        storageUsageSyncCallsSinceLastFlush = new AtomicInteger(0);
        storageSummaryCacheEnable = ConstantValues.storageSummaryHqcEnable.getBoolean();
    }

    void rescheduleGC(CentralConfigDescriptor descriptor, GcConfigDescriptor o) {
        new GcSchedulerHandler(descriptor.getGcConfig(), o,
                configsService.getConfig(ConfigsServiceKey.ARTIFACTORY_SERVICE_ID)).reschedule();
    }

    @Override
    public void reload(CentralConfigDescriptor oldDescriptor, List<DataDiff<?>> configDiff) {
        CentralConfigDescriptor descriptor = centralConfigService.getDescriptor();
        rescheduleGC(descriptor, oldDescriptor.getGcConfig());
    }

    @Override
    public void destroy() {
        new GcSchedulerHandler(null, null).unschedule();
    }

    @Override
    public void convert(CompoundVersionDetails source, CompoundVersionDetails target) {
        //nop
    }

    @Override
    public void onContextCreated() {
        activateStorageSummaryJob();
        activateLogStorageStatusJob();
        activateStorageUsageSyncJobIfNeeded();
        activateCalculateProjectBinaryStorageJobIfNeeded();
    }

    @Override
    public long getCachedStorageSize() {
        try {
            return storageSummaryService.getLocalCache().getBinariesInfo().getBinariesSize();
        } catch (CacheUnAvailableException e) {
            log.debug("Storage summary cache miss.");
        }
        return binaryStore.getBinariesInfo().getBinariesSize();
    }

    @Override
    public boolean isProjectsStorageQuotaEnabled() {
        return isStorageUsageSyncJobActivated.get();
    }

    @Override
    public @NonNull Map<String, Long> getAllProjectsNotifiedOnStorageQuota() {
        final StorageUsage storageUsage = Optional.ofNullable(readStorageUsageFromDB())
                .orElseThrow(() -> new IllegalStateException("StorageUsage must already be existing at this point"));
        return storageUsage.getProjectsLastNotifiedOnStorageQuota();
    }

    @Override
    public void updateProjectStorageQuotaNotificationTimestamp(@NonNull Set<String> projects) {
        Map<String, Long> allNotifiedProjects = getAllProjectsNotifiedOnStorageQuota();
        long currentTimestamp = System.currentTimeMillis();
        projects.forEach(projectName -> allNotifiedProjects.put(projectName, currentTimestamp));

        saveProjectsStorageQuotaNotificationInfoWithRetries(allNotifiedProjects);
    }

    private void activateStorageSummaryJob() {
        String serviceId = configsService.getConfig(ConfigsServiceKey.ARTIFACTORY_SERVICE_ID);
        String quartzExpression = CalculateReposStorageSummaryJob.buildRandomQuartzExp(serviceId);
        Task task = TaskUtils.createCronTask(CalculateReposStorageSummaryJob.class,
                quartzExpression, "Update Repo Storage Summary Job");
        taskService.startTask(task, false);
        log.info("Scheduling CalculateReposStorageSummaryJob to run at '{}'", quartzExpression);

        // Initialize the cache for the first time
        advisedMe().calculateStorageSummaryAsyncOnStartup();
    }

    private void activateStorageUsageSyncJobIfNeeded() {
        if (isStorageUsageSyncJobActivated.get()) {
            return;
        }
        if (!shouldEnableStorageQuota()) {
            return;
        }
        String quartzExpression = SyncStorageUsageJob.buildRandomQuartzExp();
        Task task = TaskUtils.createCronTask(SyncStorageUsageJob.class,
                quartzExpression, "Sync Storage Usage Job");
        taskService.startTask(task, false);
        isStorageUsageSyncJobActivated.set(true);
        log.info("Scheduling SyncStorageUsageJob to run at '{}'", quartzExpression);
    }

    private void activateCalculateProjectBinaryStorageJobIfNeeded() {
        if (ConstantValues.projectBinaryStorageCalculationEnabled.getBoolean()) {
            String serviceId = configsService.getConfig(ConfigsServiceKey.ARTIFACTORY_SERVICE_ID);
            String quartzExpression = CalculateProjectBinaryStorageJob.buildQuartzExp(serviceId);
            Task task = TaskUtils.createCronTask(CalculateProjectBinaryStorageJob.class,
                    quartzExpression, "Calculate Project Binary Storage Job");
            taskService.startTask(task, false);
            log.info("Scheduling CalculateProjectBinaryStorageJob to run at '{}'", quartzExpression);
        }
    }

    boolean shouldEnableStorageQuota() {
        return ConstantValues.projectsStorageQuotaEnabled.getBoolean();
    }

    private void activateLogStorageStatusJob() {
        boolean canBeActivated = ConstantValues.metricsEnabled.getBoolean()
                && ConstantValues.updateStorageStatusJobEnabled.getBoolean();
        if (!canBeActivated) {
            log.info("LogStorageStatusJob disabled and not scheduled to run");
            return;
        }
        String quartzExpression = UpdateStorageMetricJob.buildRandomQuartzExp();
        if (!StringUtils.isBlank(quartzExpression) && CronUtils.isValid(quartzExpression)) {
            Task task = TaskUtils.createCronTask(UpdateStorageMetricJob.class,
                    quartzExpression, "Update Repo Storage Summary Job");
            taskService.startTask(task, false);
            log.info("Scheduling LogStorageStatusJob to run at '{}'", quartzExpression);
        } else {
            log.info("Cron expression {} is not valid. LogStorageStatusJob not scheduled to run", quartzExpression);
        }
    }

    private void scheduleTrashBinariesCleanupGCJob() {
        if (ConstantValues.gcTrashBinariesCleanupEnabled.getBoolean()) {
            long interval = ConstantValues.gcTrashBinariesCleanupIntervalSecs.getLong();
            long initialDelay = ConstantValues.gcTrashBinariesCleanupDelaySecs.getLong();
            if (initialDelay < 0) {
                // Need to pick a random value to decrease DB load when run in HA
                initialDelay = generateRandomDelay(interval);
            }
            Task task = TaskUtils
                    .createRepeatingTask(BinaryStoreTrashCleanupGCJob.class, TimeUnit.SECONDS.toMillis(interval),
                            TimeUnit.SECONDS.toMillis(initialDelay));
            InternalContextHelper.get().getTaskService().startTask(task, false);
        }
    }

    private long generateRandomDelay(long interval) {
        return random.longs(interval / 2, interval + interval / 2)
                .findFirst().orElse(interval);
    }

    private StorageService advisedMe() {
        return ContextHelper.get().beanForType(StorageService.class);
    }

    @Override
    public long getProjectStorageUsage(@Nullable String projectKey) {
        return storageSummaryService.getLocalCache().getProjectsTotalSizes().getOrDefault(projectKey, 0L) +
                projectsStorageDiff.getOrDefault(projectKey, new AtomicLong(0)).get();
    }

    @Override
    public void onLicenseLoaded() {
        activateStorageUsageSyncJobIfNeeded();
    }

    @Override
    public void afterCreate(VfsItem<? extends ItemInfo> fsItem, MutableStatusHolder statusHolder,
            InterceptorCreateContext ctx) {
        try {
            addOrRemoveItemFromLocalCache(fsItem, false, false);
        } catch (Exception e) {
            log.error("", e);
        }
    }

    @Override
    public void afterDelete(VfsItem<? extends ItemInfo> fsItem, MutableStatusHolder statusHolder, DeleteContext ctx) {
        try {
            boolean toTrashCan = ContextHelper.get().beanForType(TrashService.class)
                    .isTrashcanEnabled() && !ctx.isTriggeredByMove();
            if (toTrashCan) {
                log.debug("Trash can is enabled, adding an event to the default project");
                addOrRemoveItemFromLocalCache(fsItem, true, true);
            } else {
                addOrRemoveItemFromLocalCache(fsItem, true, false);
            }
        } catch (Exception e) {
            log.error("", e);
        }
    }

    @Override
    public void afterMove(VfsItem<? extends ItemInfo> sourceItem,
            VfsItem<? extends ItemInfo> targetItem, MutableStatusHolder statusHolder, Properties properties,
            InterceptorMoveCopyContext ctx) {
        try {
            addOrRemoveItemFromLocalCache(targetItem, false, false);
        } catch (Exception e) {
            log.error("", e);
        }
    }

    @Override
    public void afterCopy(VfsItem<? extends ItemInfo> sourceItem,
            VfsItem<? extends ItemInfo> targetItem, MutableStatusHolder statusHolder, Properties properties,
            InterceptorMoveCopyContext ctx) {
        try {
            addOrRemoveItemFromLocalCache(targetItem, false, false);
        } catch (Exception e) {
            log.error("", e);
        }
    }

    private void addOrRemoveItemFromLocalCache(@NotNull VfsItem<? extends ItemInfo> fsItem, boolean delete,
            boolean toTrashCan) {
        if (!isProjectsStorageQuotaEnabled()) {
            return;
        }
        if (!fsItem.isFile()) {
            return;
        }
        long length = ((DbMutableFile) fsItem).length();

        if (length == 0) {
            return;
        }
        // order here matters - this should be before the next conditional delete (*-1)
        if (delete && toTrashCan) {
            reposStorageDiff.computeIfAbsent(TRASH_REPO_KEY, k -> new AtomicLong()).addAndGet(length);
            projectsStorageDiff.computeIfAbsent(ProjectsService.DEFAULT_PROJECT, k -> new AtomicLong())
                    .addAndGet(length);
        }
        if (delete) {
            length *= -1;
        }
        reposStorageDiff.computeIfAbsent(fsItem.getRepoKey(), k -> new AtomicLong()).addAndGet(length);
        String project = projectsService.getProjectKeyOfRepository(fsItem.getRepoKey());
        projectsStorageDiff.computeIfAbsent(project, k -> new AtomicLong()).addAndGet(length);
        log.debug("Added to local StorageDiffs a new item at size {} from repo {} and project {}.", length,
                fsItem.getRepoKey(), project);
    }

    @Override
    public void syncStorageUsageDiffsIfNeeded() {
        if (storageUsageSyncCallsSinceLastFlush.incrementAndGet() < storageUsageSyncMaxRetriesUntilFlush &&
                projectsStorageDiff.keySet().stream().noneMatch(this::isProjectDiffReachedThreshold)) {
            log.debug("Skipping on storage usage update");
            return;
        }
        log.debug("Running storage usage diff sync with DB");
        saveAndReloadStorageUsageDiffsWithRetries();
    }

    private boolean isProjectDiffReachedThreshold(@Nullable String project) {
        long projectLimit = projectsService.getProjectStorageQuotaBytes(project);
        return projectLimit != ProjectsService.UNLIMITED_STORAGE &&
                Math.abs(projectsStorageDiff.getOrDefault(project, new AtomicLong(0L)).get()) >
                        (projectLimit * storageQuotaErrorTolerance);
    }

    /**
     * 1. Read the latest StorageUsage.json from the DB.
     * 2. Sync the DB StorageUsage with the local storage usages diffs.
     * 3. Save the updated StorageUsage into the DB.
     * 4. Load the updated StorageUsage data into the StorageUsageCache.
     */
    void saveAndReloadStorageUsageDiffsWithRetries() {
        try {
            ExecutorService currentThreadExecutorService = MoreExecutors.newDirectExecutorService();
            ExecutionUtils.retry(
                    () -> {
                        saveAndReloadStorageUsageDiffs();
                        storageUsageSyncCallsSinceLastFlush.set(0);
                        return null;
                    }, ExecutionUtils.RetryOptions.builder()
                            .numberOfRetries(ConstantValues.storageUsageSaveNumberOfRetries.getInt())
                            .backoffMaxDelay(ConstantValues.storageUsageSaveBackoffMaxDelay.getInt())
                            .exponentialBackoffMultiplier(ConstantValues.storageUsageSaveBackoffMultiplier.getInt())
                            .build(),
                    currentThreadExecutorService).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new IllegalStateException("Could not merge and save new StorageUsage [" + e.getMessage() + "]", e);
        }
    }

    void saveAndReloadStorageUsageDiffs() throws RetryException {
        // Make a copy of the current local diffs in case of failure
        Map<String, AtomicLong> repoDiffsBU = new HashMap<>(reposStorageDiff);
        Map<String, AtomicLong> projectDiffsBU = new HashMap<>(projectsStorageDiff);
        try {
            StorageUsage oldStorageUsage = readStorageUsageFromDB();
            if (oldStorageUsage == null) {
                log.debug("Storage Summary calculation must run first in order to create the {}",
                        ArtifactoryHome.STORAGE_USAGE_FILE);
                return;
            }


            StorageUsage mergedStorageUsage = mergeStorageDiffWithStorageUsage(oldStorageUsage);
            saveStorageUsage(oldStorageUsage, mergedStorageUsage);
            propagateStorageUsageSyncIfNeeded();
        } catch (RetryException e) {
            restoreUsageDiffsDataOnFailure(repoDiffsBU, projectDiffsBU);
            throw e;
        } catch (Exception e) {
            restoreUsageDiffsDataOnFailure(repoDiffsBU, projectDiffsBU);
            log.error("Failed to update storage usage with an error: {}", e.getMessage(), e);
        }
    }

    @Override
    public StorageUsageUpdateStatus syncStorageUsageWithStorageSummary() {
        try {
            log.debug("Syncing storage summary cache with DB StorageUsage");
            StorageUsage storageUsage = readStorageUsageFromDB();
            if (storageUsage == null) {
                log.warn("Failed reading StorageUsage json from DB");
                return StorageUsageUpdateStatus.EMPTY;
            }
            log.debug("Reading StorageUsage revision {}", storageUsage.getRevision());
            updateStorageSummaryWithStorageUsage(storageUsage);
            return StorageUsageUpdateStatus.UPDATED;
        } catch (Exception e) {
            log.error("Failed to update storage usage with an error: {}", e.getMessage(), e);
            return StorageUsageUpdateStatus.ERROR;
        }
    }

    /**
     * @return Current StorageUsage state from the DB
     */
    @Nullable
    StorageUsage readStorageUsageFromDB() throws JsonParsingException, StorageException {
        String oldStorageUsageString = configsService.getConfig(ArtifactoryHome.STORAGE_USAGE_FILE);
        if (oldStorageUsageString == null) {
            return null;
        }
        return JsonUtils.getInstance().readValue(oldStorageUsageString, StorageUsage.class);
    }

    /**
     * @param oldStorageUsage Latest StorageUsage state from the DB
     * @return Merged StorageUsage of both the DB state and the local storage diffs
     */
    StorageUsage mergeStorageDiffWithStorageUsage(@NotNull StorageUsage oldStorageUsage) {
        // Swap local diff-maps
        log.debug("Merging StorageUsage revision {} with local storage usage diffs", oldStorageUsage.getRevision());
        Map<String, AtomicLong> reposMapDiff = reposStorageDiff;
        reposStorageDiff = new ConcurrentHashMap<>();
        projectsStorageDiff = new ConcurrentHashMap<>();
        StorageUsage mergedStorageUsage = new StorageUsage(oldStorageUsage);
        // For performance reasons - we're getting a batch of repositories <> projects mapping
        Map<String, String> repoKeyToProjectMap = getDbAndDiffRepoKeyToProjectMap(oldStorageUsage, reposMapDiff);
        // Below method will copy and calculate the old storage diff for proj <> size
        reassignAndCalcOldReposToCurrentProjects(oldStorageUsage, mergedStorageUsage, repoKeyToProjectMap);
        // Add the local repos\projects diffs into the mergedStorageUsage and backup the diffs for failure
        addDiffToMergedStorage(mergedStorageUsage, reposMapDiff, repoKeyToProjectMap);
        // We do the clean after the merge to avoid having the repoDiff re-adding removed repos
        cleanRemovedRepositories(mergedStorageUsage.getRepoStorageSum());
        addNewProjects(mergedStorageUsage);
        mergedStorageUsage.bumpRevision();
        log.debug("Done merging StorageUsage and bumped revision to {}", mergedStorageUsage.getRevision());
        return mergedStorageUsage;
    }

    private Map<String, String> getDbAndDiffRepoKeyToProjectMap(StorageUsage oldStorageUsage,
            Map<String, AtomicLong> reposMapDiff) {
        Set<String> dbAndDiffRepoSet =
                Stream.concat(oldStorageUsage.getRepoStorageSum().keySet().stream(), reposMapDiff.keySet().stream())
                        .collect(Collectors.toSet());
        return projectsService.getRepoKeyToProjectMap(dbAndDiffRepoSet, false);
    }

    /**
     * Builds the a merged (new) StorageUsage from the oldStorageUsage. Instantiates a new map for the projects sizes <>
     * repo sizes by checking what is the project owning the repo as it could have changed since the last storage sync
     * job run.
     *
     * @param oldStorageUsage    StorageUsage as it was read from the database
     * @param mergedStorageUsage Merged StorageUsage with up-to-date projects <> repos <> sizes
     */
    private void reassignAndCalcOldReposToCurrentProjects(@NotNull StorageUsage oldStorageUsage,
            @NotNull StorageUsage mergedStorageUsage, Map<String, String> repoKeyToProjectMap) {
        // reset the projects map (start from 0) - also cleans any removed projects
        mergedStorageUsage.setProjectsStorageSum(new ConcurrentHashMap<>());
        // add each repo size to its current project
        oldStorageUsage.getRepoStorageSum().forEach((key, value) -> {
            String projectKey = repoKeyToProjectMap
                    .getOrDefault(RepoPathUtils.removeDashCache(key), ProjectsService.DEFAULT_PROJECT);
            log.trace("Repo '{}' is owned by the '{}' project", key, projectKey);
            long projSize = mergedStorageUsage.getProjectsStorageSum().getOrDefault(projectKey, 0L);
            mergedStorageUsage.getProjectsStorageSum().put(projectKey, projSize + value);
            log.trace("Existing '{}' project size is '{}', added '{}' bytes", projectKey, projSize, value);
        });
        if (log.isTraceEnabled()) {
            mergedStorageUsage.getRepoStorageSum()
                    .forEach((key, value) -> log.trace("Repo '{}' has '{}' size", key, value));
        }
    }

    /**
     * Calculates the up-to-date projects sizes map as well as the repositories sizes map by adding or deducting each
     * one of the maps respective diff to be saved as the latest status in the DB. The project <> repo size relation is
     * determined by checking the current owning project of a repo by using
     * {@link ProjectsService#getProjectKeyOfRepository(String)}}.
     *
     * @param mergedStorageUsage updated repo <> current owned projects sizes
     * @param reposMapDiff       in-memory saved diff to be added/deducted to the mergedStorageUsage from the DB
     */
    private void addDiffToMergedStorage(StorageUsage mergedStorageUsage, Map<String, AtomicLong> reposMapDiff,
            Map<String, String> repoKeyToProjectMap) {
        for (Map.Entry<String, AtomicLong> repoDiff : reposMapDiff.entrySet()) {
            final String repoKey = repoDiff.getKey();
            // add the diff to the repo map
            long repoSize = mergedStorageUsage.getRepoStorageSum().getOrDefault(repoKey, 0L);
            long repoDiffSize = repoDiff.getValue().get();
            log.trace("Existing repo '{}' has size '{}'. Diff adds: '{}' bytes", repoKey, repoSize, repoDiffSize);
            mergedStorageUsage.getRepoStorageSum().put(repoKey, repoSize + repoDiffSize);
            // add the diff to the project map
            String projectKey = repoKeyToProjectMap
                    .getOrDefault(RepoPathUtils.removeDashCache(repoKey), ProjectsService.DEFAULT_PROJECT);
            long projectSize = mergedStorageUsage.getProjectsStorageSum().getOrDefault(projectKey, 0L);
            // if trash can is enabled - default will get the bytes of the deleted repo
            log.trace("Project '{}' has size '{}'. Diff adds: '{}' bytes", projectKey, projectSize, repoDiffSize);
            mergedStorageUsage.getProjectsStorageSum().put(projectKey, projectSize + repoDiffSize);
        }
    }

    private void addNewProjects(StorageUsage mergedStorageUsage) {
        projectsService.getAllProjects().forEach(project ->
                mergedStorageUsage.getProjectsStorageSum().putIfAbsent(project, 0L));
    }

    /**
     * Filters Trash can, meaning it will stay in the list
     */
    private void cleanRemovedRepositories(Map<String, ?> mapToHandle) {
        Set<String> allRepoKeys = repositoryService.getAllRepoKeys();
        if (allRepoKeys != null) {
            mapToHandle.keySet().removeIf(repoKey -> !allRepoKeys.contains(repoKey) && !TRASH_REPO_KEY.equals(repoKey));
        }
    }


    /**
     * The method saves the storageUsage as JSON into the configs table.
     * If there is no @ArtifactoryHome.STORAGE_USAGE_FILE in the Configs table we will insert a new one.
     * Otherwise, we will update the STORAGE_USAGE_FILE using versioning mechanism.
     *
     * @throws RetryException
     */
    private void saveStorageUsage(@NotNull StorageUsage oldStorageUsage, @NotNull StorageUsage storageUsage)
            throws RetryException {
        String storageUsageString;
        try {
            storageUsageString = JsonUtils.getInstance().writeValueAsString(storageUsage);
        } catch (JsonParsingException e) {
            log.error("Failed to map StorageUsage into a JSON.", e);
            throw e;
        }
        if (saveStorageUsageIfNotExist(storageUsageString, storageUsage.getRevision())) {
            log.debug("Successfully created {} revision: {}", ArtifactoryHome.STORAGE_USAGE_FILE,
                    storageUsage.getRevision());
            return;
        }

        long oldRevision = oldStorageUsage.getRevision();
        long revision = storageUsage.getRevision();
        if (configsService.updateConfigIfLastModificationMatch(ArtifactoryHome.STORAGE_USAGE_FILE,
                storageUsageString, oldRevision, revision)) {
            log.debug("Successfully updated {} from: {} to: {}",
                    ArtifactoryHome.STORAGE_USAGE_FILE, oldRevision, revision);
            updateStorageSummaryWithStorageUsage(storageUsage);
        } else {
            log.trace("Failed updating {} from: {} to: {}",
                    ArtifactoryHome.STORAGE_USAGE_FILE, oldRevision, revision);
            throw new RetryException("Should update revision " + oldRevision);
        }
    }

    /**
     * Restore the temporary backup storage diffs into the
     */
    private void restoreUsageDiffsDataOnFailure(@NotNull Map<String, AtomicLong> reposBuMap,
            @NotNull Map<String, AtomicLong> projectsBuMap) {
        mergeDiffChanges(reposBuMap, reposStorageDiff);
        mergeDiffChanges(projectsBuMap, projectsStorageDiff);
    }

    void mergeDiffChanges(@NotNull Map<String, AtomicLong> sourceDiffMap,
            @NotNull Map<String, AtomicLong> targetDiffMap) {
        Iterator<Map.Entry<String, AtomicLong>> diffIterator = sourceDiffMap.entrySet().iterator();
        while (diffIterator.hasNext()) {
            Map.Entry<String, AtomicLong> diffEntry = diffIterator.next();
            targetDiffMap.computeIfAbsent(diffEntry.getKey(), k -> new AtomicLong(0L))
                    .getAndAdd(diffEntry.getValue().get());
        }
    }

    private boolean saveStorageUsageIfNotExist(@NotNull String storageString, long revision) {
        if (!configsService.hasConfig(ArtifactoryHome.STORAGE_USAGE_FILE)) {
            configsService.addConfig(ArtifactoryHome.STORAGE_USAGE_FILE, storageString,
                    revision);
            return true;
        }
        return false;
    }

    /**
     * Update the local storageSummaryCache with given StorageUsage
     */
    void updateStorageSummaryWithStorageUsage(@NotNull StorageUsage storageUsage) {
        final StorageSummaryInfo cache;
        try {
            cache = storageSummaryService.getLocalCache();
        } catch (CacheUnAvailableException e) {
            log.error("Failed to reload Storage usage state: Storage summary cache miss.", e.getCause());
            return;
        }
        log.debug("Updating in-memory storage summary for projects");
        if (log.isTraceEnabled()) {
            printOldAndNewProjectsAndSizes(storageUsage.getProjectsStorageSum(), cache.getProjectsTotalSizes());
        }
        cache.setProjectsTotalSizes(storageUsage.getProjectsStorageSum());

        Map<String, String> repoKeyToProjectMap =
                projectsService.getRepoKeyToProjectMap(storageUsage.getRepoStorageSum().keySet(), false);
        cache.recalculateRepoStorageSummary(shallowCopyOfRepoStorageSummaries -> {
            storageUsage.getRepoStorageSum().keySet()
                    .forEach(repoKey -> shallowCopyOfRepoStorageSummaries.computeIfAbsent(repoKey,
                                    k -> new RepoStorageSummaryInfo(repoKey, RepositoryType.NA, 0, 0,
                                            storageUsage.getRepoStorageSum().get(repoKey),
                                            RepoType.Generic.getDisplayName(),
                                            repoKeyToProjectMap.getOrDefault(RepoPathUtils.removeDashCache(repoKey),
                                                    ProjectsService.DEFAULT_PROJECT)))
                            .setUsedSpace(latestUsedSpace(repoKey, shallowCopyOfRepoStorageSummaries, storageUsage)));
            cleanRemovedRepositories(shallowCopyOfRepoStorageSummaries);
        });
        log.debug("Successfully updated storage summary cache with StorageUsage revision {}",
                storageUsage.getRevision());
    }

    private long latestUsedSpace(final String repoKey, final Map<String, RepoStorageSummaryInfo> freshMap,
            final StorageUsage storageUsage) {
        return Optional.ofNullable(freshMap.get(repoKey))
                .map(RepoStorageSummaryInfo::getUsedSpace)
                .orElseGet(
                        () -> storageUsage.getRepoStorageSum().get(repoKey)
                );
    }

    private void printOldAndNewProjectsAndSizes(Map<String, Long> oldProjectSizes, Map<String, Long> newProjectsSizes) {
        oldProjectSizes.forEach((project, size) -> log.trace("Old map projectKey '{}' has {} size", project, size));
        newProjectsSizes.forEach((project, size) -> log.trace("New map projectKey '{}' has {} size", project, size));
    }

    private void saveProjectsStorageQuotaNotificationInfoWithRetries(
            @NotNull Map<String, Long> projectsLastNotifiedOnStorageQuota) {
        try {
            ExecutorService currentThreadExecutorService = MoreExecutors.newDirectExecutorService();
            ExecutionUtils.retry(() -> saveProjectsStorageQuotaNotificationInfo(projectsLastNotifiedOnStorageQuota),
                    ExecutionUtils.RetryOptions.builder()
                            .numberOfRetries(ConstantValues.storageUsageSaveNumberOfRetries.getInt())
                            .backoffMaxDelay(ConstantValues.storageUsageSaveBackoffMaxDelay.getInt())
                            .exponentialBackoffMultiplier(ConstantValues.storageUsageSaveBackoffMultiplier.getInt())
                            .build(),
                    currentThreadExecutorService).get();
        } catch (InterruptedException | ExecutionException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Could not merge and save new StorageUsage [" + e.getMessage() + "]", e);
        }
    }

    @Nullable
    private Object saveProjectsStorageQuotaNotificationInfo(
            @NonNull Map<String, Long> projectsLastNotifiedOnStorageQuota) throws RetryException {

        StorageUsage storageUsage = readStorageUsageFromDB();
        long oldRevision = Objects.requireNonNull(storageUsage).getRevision();
        storageUsage.setProjectsLastNotifiedOnStorageQuota(projectsLastNotifiedOnStorageQuota);
        storageUsage.bumpRevision();

        String storageUsageString;
        try {
            storageUsageString = JsonUtils.getInstance().writeValueAsString(storageUsage);
        } catch (JsonParsingException e) {
            log.error("Failed to map StorageUsage into a JSON.", e);
            throw e;
        }

        long revision = storageUsage.getRevision();
        if (configsService.updateConfigIfLastModificationMatch(ArtifactoryHome.STORAGE_USAGE_FILE,
                storageUsageString, oldRevision, revision)) {
            log.debug("Successfully updated {} from: {} to: {}", ArtifactoryHome.STORAGE_USAGE_FILE,
                    oldRevision, revision);
        } else {
            throw new RetryException(
                    String.format("Failed updating %s from: %s to: %s", ArtifactoryHome.STORAGE_USAGE_FILE,
                            oldRevision,
                            revision));
        }

        return null;
    }

    /**
     * Create or update the DB StorageUsage.json with latest Storage summary calculation results
     *
     * @param info - StorageSummaryInfo state
     */
    private void saveAccurateStorageUsageStateWithRetries(@NotNull StorageSummaryInfo info) {
        if (!isProjectsStorageQuotaEnabled()) {
            return;
        }
        try {
            ExecutorService currentThreadExecutorService = MoreExecutors.newDirectExecutorService();
            ExecutionUtils.retry(
                    () -> {
                        saveAccurateStorageUsageState(info);
                        storageUsageSyncCallsSinceLastFlush.set(0);
                        return null;
                    }, ExecutionUtils.RetryOptions.builder()
                            .numberOfRetries(ConstantValues.storageUsageSaveNumberOfRetries.getInt())
                            .backoffMaxDelay(ConstantValues.storageUsageSaveBackoffMaxDelay.getInt())
                            .exponentialBackoffMultiplier(ConstantValues.storageUsageSaveBackoffMultiplier.getInt())
                            .build(),
                    currentThreadExecutorService).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new IllegalStateException("Could not merge and save new StorageUsage [" + e.getMessage() + "]", e);
        }
    }

    private void saveAccurateStorageUsageState(@NotNull StorageSummaryInfo info) throws RetryException {
        // Create a storageUsage out of the StorageSummaryInfo
        StorageUsage newStorageUsage = new StorageUsage(info);
        // Read the current storageUsage from the DB - if there is
        StorageUsage oldStorageUsage = null;
        try {
            oldStorageUsage = readStorageUsageFromDB();
        } catch (Exception e) {
            log.error("Failed reading {} from DB with an error {}", ArtifactoryHome.STORAGE_USAGE_FILE, e.getMessage());
        }
        if (oldStorageUsage != null) {
            newStorageUsage.setRevision(oldStorageUsage.getRevision());
            newStorageUsage.bumpRevision();
            // Preserve the quota notification timestamps across full recalculations. Without this, the recalc
            // overwrites the DB state with an empty map and ProjectStorageQuotaNotificationJob re-notifies every
            // project over quota, ignoring projects.storage.quota.notification.period (RTDEV-88337).
            newStorageUsage.setProjectsLastNotifiedOnStorageQuota(
                    new HashMap<>(oldStorageUsage.getProjectsLastNotifiedOnStorageQuota()));
        } else {
            log.debug("Failed to load old storageUsage from DB. Will initiate new StorageUsage from revision 1");
            oldStorageUsage = new StorageUsage();
        }
        saveStorageUsage(oldStorageUsage, newStorageUsage);
        log.debug("Successfully saved Accurate StorageUsage State from storage summary update at {}",
                info.getLastUpdated());
    }

    @Autowired
    public void setCentralConfigService(CentralConfigService centralConfigService) {
        this.centralConfigService = centralConfigService;
    }

    @Autowired
    public void setDbService(DbService dbService) {
        this.dbService = dbService;
    }

    @Autowired
    public void setBinaryStore(ArtifactoryBinaryService binaryStore) {
        this.binaryStore = binaryStore;
    }

    @Autowired
    public void setStorageSummaryService(StorageSummaryService storageSummaryService) {
        this.storageSummaryService = storageSummaryService;
    }

    @Autowired
    public void setTaskService(TaskService taskService) {
        this.taskService = taskService;
    }

    @Autowired
    public void setRepositoryService(RepositoryService repositoryService) {
        this.repositoryService = repositoryService;
    }

    @Autowired
    public void setAddonsManager(AddonsManager addonsManager) {
        this.addonsManager = addonsManager;
    }

    @Autowired
    public void setProjectsService(ProjectsService projectsService) {
        this.projectsService = projectsService;
    }

    @Autowired
    public void setConfigsService(ConfigsService configsService) {
        this.configsService = configsService;
    }

    @VisibleForTesting
    void setInternalBinaryService(ArtifactoryBinaryService internalBinaryService) {
        binaryStore = internalBinaryService;
    }

    static class GcSchedulerHandler extends BaseTaskServiceDescriptorHandler<GcConfigDescriptor> {

        final List<GcConfigDescriptor> oldDescriptorHolder = Lists.newArrayList();
        final List<GcConfigDescriptor> newDescriptorHolder = Lists.newArrayList();
        private final String cron;

        GcSchedulerHandler(GcConfigDescriptor newDesc, GcConfigDescriptor oldDesc) {
            this(newDesc, oldDesc, null);
        }

        GcSchedulerHandler(GcConfigDescriptor newDesc, GcConfigDescriptor oldDesc, @Nullable String serviceId) {
            if (newDesc != null) {
                newDescriptorHolder.add(newDesc);
            }
            if (oldDesc != null) {
                oldDescriptorHolder.add(oldDesc);
            }
            cron = AolUtils.isAol() ?
                    generateCron(serviceId) :
                    null;
        }

        /**
         * Generates Quartz cron expression based on configuration parameter {@linkplain ConstantValues#gcIntervalSecs}
         * and minutes and hours derived from Service ID of this RT instance.
         * <p>
         * {@linkplain ConstantValues#gcIntervalSecs} configuration defines how often (in seconds)
         * the GC task should be triggered.
         * The method uses this parameter along with randomly (based on Service ID) autogenerated starting
         * hour and minute in order to build Quartz cron expression.
         * <p>
         * Note: the GC task can be configured to run within 1 day only.
         * Meaning that any value out of 1-24 hours range will be interpreted as 24 hours.
         * <pre>Example 1: if artifactory.gcIntervalSecs = 21600 (6 hours) and autogenerated hour:minute are 17:42
         * then the method returns "0 42 5/6 ? * *" (triggers every 6 hours starting at 05:42:00)</pre>
         * <pre>Example 2: if artifactory.gcIntervalSecs = 86400 (24 hours) and autogenerated hour:minute are 11:07
         * then the method returns "0 07 11 ? * *" (triggers every day starting at 11:07:00)</pre>
         * <pre>Example 3: if artifactory.gcIntervalSecs = 129600 (36 hours) and autogenerated hour:minute are 09:23
         * then the method returns "0 23 9 ? * *" (triggers every day starting at 09:23:00)</pre>
         *
         * @param serviceId The identifier of the service. Can be <em>null</em>.
         * @return A cron expression generated based on the provided service ID and configuration cron template.
         * If <code>serviceId</code> is <em>null</em> then trigger time will be based on 22:00.
         * @see ConstantValues#gcIntervalSecs
         */
        @NonNull
        private String generateCron(@Nullable String serviceId) {
            int hash;
            int intervalInHours = -1;
            int startingAtMinute = 0;
            int startingAtHour = 22;
            // If the is no service ID (should have never happened in prod) or interval configuration is invalid
            // (including 24) then set default trigger once a day at 22:00.
            if (!StringUtils.isBlank(serviceId)) {
                long intervalInSecs = ConstantValues.gcIntervalSecs.getLong();
                intervalInHours = (int) Math.min(TimeUnit.SECONDS.toHours(intervalInSecs), 24);
                intervalInHours = intervalInHours == 0 ? 24 : intervalInHours;
                hash = serviceId.hashCode();
                startingAtMinute = Math.abs(hash % 60);
                startingAtHour = Math.abs(hash % intervalInHours);
                // If interval is one day exactly, I still want to spread trigger time rather than to use default one.
                boolean exactOneDay = intervalInSecs == TimeUnit.DAYS.toSeconds(1);
                if (exactOneDay || intervalInHours < 1 || intervalInHours == 24) {
                    intervalInHours = -1;
                }
            }
            String res = generateCronExpression(intervalInHours, startingAtMinute, startingAtHour);
            log.debug("Autogenerated cron expression: {}", res);
            return res;
        }

        /**
         * Generates a Quartz cron expression based on the specified parameters.
         * If the interval for triggering the job <code>intervalInHours</code> is invalid (non-positive),
         * then the method returns a cron expression that schedules the job
         * to run at the specified minute and hour every day.
         * If a valid interval is provided, the method returns a cron expression that schedules the job to run
         * <code>intervalInHours</code>, starting at the specified minute and hour.
         *
         * @param intervalInHours  The interval in hours for triggering the job. Must be between 1 and 23, inclusive.
         * @param startingAtMinute The minute when the job should start running.
         * @param startingAtHour   The hour when the job should start running.
         * @return A Quartz cron expression based on the specified parameters.
         */
        private static String generateCronExpression(int intervalInHours, int startingAtMinute, int startingAtHour) {
            return intervalInHours <= 0 ?
                    "0 %d %d ? * *".formatted(startingAtMinute, startingAtHour) :
                    "0 %d %d/%d ? * *".formatted(startingAtMinute, startingAtHour, intervalInHours);
        }

        @Override
        public String jobName() {
            return "Garbage Collector";
        }

        @Override
        public List<GcConfigDescriptor> getNewDescriptors() {
            return newDescriptorHolder;
        }

        @Override
        public List<GcConfigDescriptor> getOldDescriptors() {
            return oldDescriptorHolder;
        }

        @Override
        public Predicate<Task> getAllPredicate() {
            return input -> (input != null) && BinaryStoreGarbageCollectorJob.class.isAssignableFrom(input.getType());
        }

        @Override
        public Predicate<Task> getPredicate(@Nonnull GcConfigDescriptor descriptor) {
            return getAllPredicate();
        }

        @Override
        public void activate(@Nonnull GcConfigDescriptor descriptor, boolean manual, BasicStatusHolder statusHolder) {
            Task garbageCollectorTask;
            if (AolUtils.isAol()) {
                garbageCollectorTask = TaskUtils.createCronTask(BinaryStoreGarbageCollectorJob.class,
                        cron, "Binaries Garbage Collector");
            } else {
                garbageCollectorTask = TaskUtils.createCronTask(BinaryStoreGarbageCollectorJob.class,
                        descriptor.getCronExp(), "Binaries Garbage Collector");
            }
            InternalContextHelper.get().getTaskService().startTask(garbageCollectorTask, manual);
        }

        @Override
        public GcConfigDescriptor findOldFromNew(@Nonnull GcConfigDescriptor newDescriptor) {
            return oldDescriptorHolder.isEmpty() ? null : oldDescriptorHolder.get(0);
        }
    }
}