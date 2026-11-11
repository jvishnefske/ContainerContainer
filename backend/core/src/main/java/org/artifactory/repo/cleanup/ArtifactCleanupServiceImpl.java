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

package org.artifactory.repo.cleanup;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import lombok.AccessLevel;
import lombok.Setter;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.search.ArtifactSearchAddon;
import org.artifactory.api.common.BasicStatusHolder;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.common.ConstantValues;
import org.artifactory.config.CentralConfigKey;
import org.artifactory.descriptor.cleanup.CleanupConfigDescriptor;
import org.artifactory.descriptor.config.CentralConfigDescriptor;
import org.artifactory.descriptor.config.MutableCentralConfigDescriptor;
import org.artifactory.descriptor.replication.RemoteReplicationDescriptor;
import org.artifactory.fs.ItemInfo;
import org.artifactory.model.LocalCacheRepoConfig;
import org.artifactory.model.RepoConfig;
import org.artifactory.repo.LocalRepo;
import org.artifactory.repo.cache.expirable.CacheExpiry;
import org.artifactory.repo.service.InternalRepositoryService;
import org.artifactory.schedule.BaseTaskServiceDescriptorHandler;
import org.artifactory.schedule.Task;
import org.artifactory.schedule.TaskService;
import org.artifactory.schedule.TaskUtils;
import org.artifactory.spring.InternalContextHelper;
import org.artifactory.spring.Reloadable;
import org.artifactory.storage.service.StatsServiceImpl;
import org.artifactory.version.CompoundVersionDetails;
import org.jfrog.common.config.diff.DataDiff;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongSupplier;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.artifactory.repo.ArtifactCleanupJobSelector.getArtifactCleanupJobClass;

/**
 * The main implementation of the clean-up service
 *
 * @author Noam Tenne
 */
@Service
@Reloadable(beanClass = InternalArtifactCleanupService.class,
        initAfter = {TaskService.class, InternalRepositoryService.class},
        listenOn = CentralConfigKey.cleanupConfig)
public class ArtifactCleanupServiceImpl implements InternalArtifactCleanupService {

    private static final Logger log = LoggerFactory.getLogger(ArtifactCleanupServiceImpl.class);

    @Autowired
    private CentralConfigService centralConfigService;

    @Autowired
    private StatsServiceImpl statsService;

    @Autowired
    private InternalRepositoryService repositoryService;

    @Autowired
    private AddonsManager addonsManager;

    @Autowired
    private CacheExpiry cacheExpiry;

    private ArtifactSearchAddon searchAddon;

    @Setter(AccessLevel.PACKAGE)
    private LongSupplier timeSupplier = System::currentTimeMillis;

    @Value("${artifactory.artifacts.cleanup.job.bulk.size}")
    private int cleanupBulkSize;

    @Value("${artifactory.artifacts.cleanup.job.max.items}")
    private int cleanupMaxItems;

    @Value("${artifactory.artifacts.cleanup.job.duration.millis}")
    private long jobDuration;

    @Value("${artifactory.artifacts.cleanup.job.log.all.repo.items.to.be.cleaned}")
    private boolean logAllRepoItemsToBeCleaned;

    @Value("${artifactory.artifacts.cleanup.job.skip.Repos.With.Remote.Replication}")
    private boolean skipReposWithRemoteReplication;

    private ArtifactSearchAddon getSearchAddon() {
        return Optional.ofNullable(searchAddon).orElseGet(
                () -> {
                    searchAddon = addonsManager.addonByType(ArtifactSearchAddon.class);
                    return searchAddon;
                }
        );
    }

    @Override
    public void init() {
        reload(null, ImmutableList.of());
    }

    @Override
    public void reload(@Nullable CentralConfigDescriptor oldDescriptor, List<DataDiff<?>> configDiff) {
        CentralConfigDescriptor descriptor = centralConfigService.getDescriptor();
        CleanupConfigDescriptor oldCleanupConfig = null;
        if (oldDescriptor != null) {
            oldCleanupConfig = oldDescriptor.getCleanupConfig();
        }
        new CleanupConfigDescriptorHandler(descriptor.getCleanupConfig(), oldCleanupConfig).reschedule();
    }

    @Override
    public void destroy() {
        new CleanupConfigDescriptorHandler(null, null).unschedule();
    }

    @Override
    public void convert(CompoundVersionDetails source, CompoundVersionDetails target) {
        //nop
    }

    @Override
    public String callManualArtifactCleanup(BasicStatusHolder statusHolder) {
        TaskService taskService = InternalContextHelper.get().getTaskService();
        Class<? extends ArtifactCleanupJob> cleanupClass = getArtifactCleanupJobClass();
        Task task = TaskUtils.createManualTask(cleanupClass, 0L);
        taskService.checkCanStartManualTask(task, cleanupClass, statusHolder);
        if (!statusHolder.isError()) {
            try {
                return taskService.startTask(task, true, true);
            } catch (Exception e) {
                statusHolder.error("Error scheduling manual artifact cleanup", e, log);
            }
        }
        return null;
    }

    static class CleanupConfigDescriptorHandler extends BaseTaskServiceDescriptorHandler<CleanupConfigDescriptor> {

        final List<CleanupConfigDescriptor> oldDescriptorHolder = Lists.newArrayList();
        final List<CleanupConfigDescriptor> newDescriptorHolder = Lists.newArrayList();

        CleanupConfigDescriptorHandler(CleanupConfigDescriptor newDesc, CleanupConfigDescriptor oldDesc) {
            if (newDesc != null) {
                newDescriptorHolder.add(newDesc);
            }
            if (oldDesc != null) {
                oldDescriptorHolder.add(oldDesc);
            }
        }

        @Override
        public String jobName() {
            return "Artifact Cleanup";
        }

        @Override
        public List<CleanupConfigDescriptor> getNewDescriptors() {
            return newDescriptorHolder;
        }

        @Override
        public List<CleanupConfigDescriptor> getOldDescriptors() {
            return oldDescriptorHolder;
        }

        @Override
        public Predicate<Task> getAllPredicate() {
            return input -> input == null || getArtifactCleanupJobClass().isAssignableFrom(input.getType());
        }

        @Override
        public Predicate<Task> getPredicate(@Nonnull CleanupConfigDescriptor descriptor) {
            return getAllPredicate();
        }

        @Override
        public void activate(@Nonnull CleanupConfigDescriptor descriptor, boolean manual,
                BasicStatusHolder basicStatusHolder) {
            Task cleanupTask = TaskUtils.createCronTask(getArtifactCleanupJobClass(), descriptor.getCronExp());
            InternalContextHelper.get().getTaskService().startTask(cleanupTask, manual, manual);
        }

        @Override
        public CleanupConfigDescriptor findOldFromNew(@Nonnull CleanupConfigDescriptor newDescriptor) {
            return oldDescriptorHolder.isEmpty() ? null : oldDescriptorHolder.get(0);
        }
    }

    @Override
    public void clean() {
        log.info("Starting execution of Auto-clean on all repositories");
        long maxTimeForCleanup = timeSupplier.getAsLong() + jobDuration;

        // flush the download statistics before starting the cleanup
        statsService.flushStats();

        List<LocalCacheRepoConfig> cachedRepoConfigs = repositoryService.getCachedRepoConfigs();
        AtomicInteger count = new AtomicInteger(0);
        cachedRepoConfigs.stream()
                .takeWhile(cacheRepoConfig ->
                        moreItemsToClean(count.get()) && stillInJobDuration(maxTimeForCleanup)
                )
                .forEach(cacheRepoConfig ->
                        performCleanOnRepo(cacheRepoConfig, count, maxTimeForCleanup));
        log.info("Current execution of Auto-clean on all repositories has ended. " +
                        "{} artifact(s) were cleaned",
                count.get());
    }

    private void performCleanOnRepo(LocalCacheRepoConfig localCacheRepoConfig,
            AtomicInteger count,
            long maxTimeForCleanup) {
        if (shouldSkipRepoConfiguredWithRemoteReplication(localCacheRepoConfig)) {
            log.debug("Skipping auto-clean on repository '{}' because it is configured with remote replication.",
                    localCacheRepoConfig.getKey());
            return;
        }
        String repoKey = localCacheRepoConfig.getKey();
        long periodMillis = getHoursInMillis(
                localCacheRepoConfig.getRemoteRepoConfig().getRepoTypeConfig().getUnusedArtifactsCleanupPeriodHours());
        if (periodMillis <= 0) {
            log.debug("Skipping auto-clean on repository '{}': period value is {}.", repoKey, periodMillis);
            return;
        }

        LocalRepo<?, ?> storingRepo = repositoryService.localFederatedOrCachedRepositoryByKey(repoKey);
        //Perform sanity checks
        if (storingRepo == null) {
            log.warn("Could not find the storing repository '{}' - auto-clean was not performed.", repoKey);
            return;
        }
        if (!storingRepo.isCache()) {
            log.warn("Cannot cleanup non-cache repository '{}'.", repoKey);
            return;
        }

        doClean(repoKey, periodMillis, count, maxTimeForCleanup);
    }

    private boolean shouldSkipRepoConfiguredWithRemoteReplication(LocalCacheRepoConfig localCacheRepoConfig) {
        if (skipReposWithRemoteReplication) {
            MutableCentralConfigDescriptor descriptor = centralConfigService.getMutableDescriptor();
            RemoteReplicationDescriptor remoteReplication = descriptor.getRemoteReplication(
                    localCacheRepoConfig.getRemoteRepoConfig().getKey());
            return remoteReplication != null && remoteReplication.isEnabled();
        }
        return false;
    }

    @VisibleForTesting
    boolean isLocalGenerated(@Nonnull String repoKey, @Nonnull String repoPath) {
        RepoConfig repoConfig = repositoryService.localFederatedOrCachedRepoConfigByKey(repoKey);
        return repoConfig != null && cacheExpiry.isLocalGenerated(repoConfig.getPackageType(), repoKey, repoPath);
    }

    private void doClean(String repoKey, long periodMillis, AtomicInteger count, long maxTimeForCleanup) {
        log.info("Auto-clean has begun on the repository '{}' with period of {} millis.", repoKey, periodMillis);

        //Calculate unused artifact expiry
        long expiryMillis = (timeSupplier.getAsLong() - periodMillis);

        //Perform a metadata search on the given repo. Look for artifacts
        // that have lastDownloaded stats matching since/created before
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(expiryMillis);
        Set<String> localFederatedOrCachedRepoKeys =
                repositoryService.getLocalFederatedAndCachedRepositories().stream().map(rep -> repoKey)
                        .collect(Collectors.toSet());

        logArtifactsToBeCleaned(repoKey, calendar, localFederatedOrCachedRepoKeys, expiryMillis);

        while (moreItemsToClean(count.get()) && stillInJobDuration(maxTimeForCleanup)) {
            int currentBulkSize = Math.min(cleanupBulkSize, (cleanupMaxItems - count.get()));
            int cleanedArtifacts = cleanArtifacts(repoKey, calendar, currentBulkSize,
                    localFederatedOrCachedRepoKeys,
                    expiryMillis);
            if (cleanedArtifacts == 0) {
                break;
            }
            count.getAndAdd(cleanedArtifacts);
        }
    }

    private int cleanArtifacts(String repoKey, Calendar calendar, Integer bulkSize,
            Set<String> localFederatedOrCachedRepoKeys,
            long expiryMillis) {
        AtomicInteger cleanedArtifactsCount = new AtomicInteger(0);
        try (Stream<ItemInfo> metadataSearchResults = getStreamOfArtifactsForCleanup(repoKey, calendar, bulkSize,
                localFederatedOrCachedRepoKeys, expiryMillis)) {
            Set<String> artifactsNotCleaned = new HashSet<>();
            metadataSearchResults
                    .forEach(it -> {
                        try {
                            log.trace("Undeploying artifact '{}'", it.getRepoPath());
                            BasicStatusHolder undeployStatus =
                                    repositoryService.undeploy(it.getRepoPath(), false, true);
                            if (!undeployStatus.isError()) {
                                cleanedArtifactsCount.incrementAndGet();
                            } else {
                                artifactsNotCleaned.add(it.getRepoPath().getId());
                            }
                        } catch (Exception e) {
                            log.error(String.format("Could not auto-clean artifact '%s'.", it.getRepoPath().getId()),
                                    e);
                        }
                    });
            if (!artifactsNotCleaned.isEmpty()) {
                log.warn("Auto-clean iteration on the repository '{}' has ended. {} artifact(s) were NOT cleaned: {}",
                        repoKey, artifactsNotCleaned.size(), artifactsNotCleaned);
            }

            // closing the stream.
        } finally {
            log.info("Auto-clean iteration on the repository '{}' has ended. {} artifact(s) were cleaned",
                    repoKey, cleanedArtifactsCount.get());
        }
        return cleanedArtifactsCount.get();
    }

    private void logArtifactsToBeCleaned(String repoKey, Calendar calendar, Set<String> localFederatedOrCachedRepoKeys,
            long expiryMillis) {
        if (logAllRepoItemsToBeCleaned) {
            StringBuilder artifactRepoPaths = new StringBuilder();
            final int[] artifactsToCleanCount = {0};
            getStreamOfArtifactsForCleanup(repoKey, calendar, null, localFederatedOrCachedRepoKeys, expiryMillis)
                    .forEach(it -> {
                        artifactsToCleanCount[0]++;
                        artifactRepoPaths.append("\n").append(it.getRepoPath());
                    });
            log.trace("Found the following list of artifacts (size: {}) to clean from repository '{}': {}",
                    artifactsToCleanCount[0], repoKey, artifactRepoPaths);
        } else {
            log.trace("Property '{}' set to false, not logging artifacts to be cleaned from repository '{}'.",
                    ConstantValues.ArtifactCleanupJobLogAllRepoItemsToBeCleaned.getPropertyName(), repoKey);
        }

    }

    private Stream<ItemInfo> getStreamOfArtifactsForCleanup(String repoKey, Calendar calendar, Integer bulkSize,
            Set<String> localFederatedOrCachedRepoKeys, long expiryMillis) {
        
        boolean sortByUpdated = ConstantValues.artifactsCleanupJobSortByUpdated.getBoolean();
        log.debug("Fetching artifacts for cleanup from repo '{}' with sorting by updated: {}", repoKey, sortByUpdated);

        return getSearchAddon().streamArtifactsItemInfoNotDownloadedSince(calendar, calendar, bulkSize,
                        sortByUpdated, repoKey)
                // paranoid checks
                .filter(it -> localFederatedOrCachedRepoKeys.contains(it.getRepoKey()))
                .filter(it -> expiryMillis > it.getCreated())
                .filter(it -> !isLocalGenerated(it.getRepoKey(), it.getRelPath()));
    }

    private boolean moreItemsToClean(int count) {
        boolean moreLeft = count < cleanupMaxItems;
        if (!moreLeft) {
            log.warn("ArtifactCleanupJob has reached the maximum number of items allowed to be cleaned ({}). " +
                            "Stopping job execution.",
                    cleanupMaxItems);
        }
        return moreLeft;
    }

    private boolean stillInJobDuration(long maxTimeForCleanup) {
        boolean stillInJobDuration = timeSupplier.getAsLong() < maxTimeForCleanup;
        if (!stillInJobDuration) {
            log.warn("ArtifactCleanupJob has reached the maximum job duration ({} ms). Stopping job execution.",
                    jobDuration);
        }
        return stillInJobDuration;
    }

    /**
     * Returns the given number of hours, in milliseconds
     *
     * @param hours Number of hours to convert
     * @return Number of hours - In milliseconds
     */
    private long getHoursInMillis(int hours) {
        return TimeUnit.HOURS.toMillis(hours);
    }
}
