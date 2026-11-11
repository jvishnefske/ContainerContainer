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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.api.common.BasicStatusHolder;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.config.CentralConfigKey;
import org.artifactory.descriptor.cleanup.CleanupConfigDescriptor;
import org.artifactory.descriptor.config.CentralConfigDescriptor;
import org.artifactory.model.VirtualRepoConfig;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.repo.service.InternalRepositoryService;
import org.artifactory.schedule.BaseTaskServiceDescriptorHandler;
import org.artifactory.schedule.Task;
import org.artifactory.schedule.TaskService;
import org.artifactory.schedule.TaskUtils;
import org.artifactory.spring.InternalContextHelper;
import org.artifactory.spring.Reloadable;
import org.artifactory.version.CompoundVersionDetails;
import org.jfrog.common.config.diff.DataDiff;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * @author Yoav Luft
 */
@Slf4j
@Service
@Reloadable(beanClass = InternalVirtualCacheCleanupService.class,
        initAfter = {TaskService.class, InternalRepositoryService.class},
        listenOn = CentralConfigKey.virtualCacheCleanupConfig)
public class VirtualCacheCleanupServiceImpl implements InternalVirtualCacheCleanupService {
    
    private final CentralConfigService centralConfigService;
    private final RepositoryService repositoryService;
    private final TaskService taskService;
    private final Set<VirtualCacheCleaner> virtualCacheCleaners;

    @Autowired
    public VirtualCacheCleanupServiceImpl(CentralConfigService centralConfigService,
            RepositoryService repositoryService, TaskService taskService,
            Set<VirtualCacheCleaner> virtualCacheCleaners) {
        this.centralConfigService = centralConfigService;
        this.repositoryService = repositoryService;
        this.taskService = taskService;
        this.virtualCacheCleaners = virtualCacheCleaners;
    }

    @Override
    public void init() {
        reload(null, ImmutableList.of());
    }

    @Override
    public void reload(CentralConfigDescriptor oldDescriptor, List<DataDiff<?>> configDiff) {
        CentralConfigDescriptor descriptor = centralConfigService.getDescriptor();
        CleanupConfigDescriptor oldCleanupConfig = null;
        if (oldDescriptor != null) {
            oldCleanupConfig = oldDescriptor.getVirtualCacheCleanupConfig();
        }
        CleanupConfigDescriptor virtualCacheCleanupConfig = descriptor.getVirtualCacheCleanupConfig();
        new VirtualCacheCleanupConfigHandler(virtualCacheCleanupConfig, oldCleanupConfig).reschedule();
    }

    @Override
    public void destroy() {

    }

    @Override
    public void convert(CompoundVersionDetails source, CompoundVersionDetails target) {

    }

    @Nullable
    @Override
    public String callVirtualCacheCleanup(BasicStatusHolder statusHolder) {
        Task task = TaskUtils.createManualTask(VirtualCacheCleanupJob.class, 0L);
        taskService.checkCanStartManualTask(task, VirtualCacheCleanupJob.class, statusHolder);
        log.info("Virtual repositories cleanup was scheduled to run.");
        if (!statusHolder.isError()) {
            try {
                return taskService.startTask(task, true, true);
            } catch (Exception e) {
                statusHolder.error("Failed to run virtual cache cleanup", e, log);
            }
        }
        return null;
    }

    @Override
    public void clean() {
        long totalRemovedFiles = 0;
        log.info("Starting cleanup of virtual repositories cache");
        List<VirtualRepoConfig> virtualRepoConfigs = repositoryService.getRepoConfigsByType(RepoDetailsType.VIRTUAL)
                .stream().map(VirtualRepoConfig.class::cast).collect(Collectors.toList());
        for (VirtualRepoConfig virtualRepoConfig : virtualRepoConfigs) {
            for (VirtualCacheCleaner cacheCleaner : virtualCacheCleaners) {
                if (cacheCleaner.shouldRun(virtualRepoConfig)) {
                    totalRemovedFiles += cacheCleaner.cleanCache(virtualRepoConfig);
                }
            }
        }
        log.info("Completed virtual repositories cleanup: removed {} cached files.", totalRemovedFiles);
    }

    static class VirtualCacheCleanupConfigHandler
            extends BaseTaskServiceDescriptorHandler<CleanupConfigDescriptor> {

        final List<CleanupConfigDescriptor> oldDescriptorHolder = Lists.newArrayList();
        final List<CleanupConfigDescriptor> newDescriptorHolder = Lists.newArrayList();

        VirtualCacheCleanupConfigHandler(CleanupConfigDescriptor newDesc, CleanupConfigDescriptor oldDesc) {
            if (newDesc != null) {
                newDescriptorHolder.add(newDesc);
            }
            if (oldDesc != null) {
                oldDescriptorHolder.add(oldDesc);
            }
        }

        @Override
        public String jobName() {
            return "Virtual Cache Cleanup";
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
            return input -> input == null || VirtualCacheCleanupJob.class.isAssignableFrom(input.getType());
        }

        @Override
        public Predicate<Task> getPredicate(@Nonnull CleanupConfigDescriptor descriptor) {
            return getAllPredicate();
        }

        @Override
        public void activate(@Nonnull CleanupConfigDescriptor descriptor, boolean manual,
                BasicStatusHolder statusHolder) {
            Task cleanupTask = TaskUtils.createCronTask(VirtualCacheCleanupJob.class, descriptor.getCronExp());
            InternalContextHelper.get().getTaskService().startTask(cleanupTask, manual, manual);
        }

        @Override
        public CleanupConfigDescriptor findOldFromNew(@Nonnull CleanupConfigDescriptor newDescriptor) {
            return oldDescriptorHolder.isEmpty() ? null : oldDescriptorHolder.get(0);
        }
    }
}
