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

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.api.repo.exception.ItemNotFoundRuntimeException;
import org.artifactory.common.ConstantValues;
import org.artifactory.config.CentralConfigKey;
import org.artifactory.descriptor.config.CentralConfigDescriptor;
import org.artifactory.fs.ItemInfo;
import org.artifactory.mime.MavenNaming;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.service.InternalRepositoryService;
import org.artifactory.sapi.interceptor.context.DeleteContext;
import org.artifactory.schedule.Task;
import org.artifactory.schedule.TaskService;
import org.artifactory.schedule.TaskUtils;
import org.artifactory.spring.Reloadable;
import org.artifactory.storage.fs.service.FileService;
import org.jfrog.common.config.diff.DataDiff;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.artifactory.common.ConstantValues.folderChildrenPruningEnabled;
import static org.artifactory.common.ConstantValues.trashcanFolderPruningEnabled;
import static org.artifactory.repo.system.SystemRepos.IN_TRANSIT_REPO_KEY;
import static org.artifactory.repo.system.SystemRepos.RELEASE_BUNDLE_REPO_KEY;
import static org.artifactory.repo.system.SystemRepos.TRASH_REPO_KEY;
import static org.artifactory.util.RepoPathUtils.isTrash;

/**
 * Asynchronous implementation of folder pruning.
 * This implementation allows only one thread to perform actual pruning.
 *
 * @author Shay Yaakov
 */
@Slf4j
@Service
@Reloadable(beanClass = InternalFolderPruningService.class,
        initAfter = {TaskService.class, InternalRepositoryService.class},
        listenOn = CentralConfigKey.none)
public class FolderPruningServiceImpl implements InternalFolderPruningService {
    private static final long CHILDREN_LOAD_LIMIT = 2;
    private List<String> repoWhitelist;

    private final RepositoryService repoService;

    private final TaskService taskService;

    private final FileService fileService;

    private final MeterRegistry meterRegistry;

    private final ConcurrentLinkedQueue<CandidatePruningFolder> foldersToPrune = new ConcurrentLinkedQueue<>();
    private final Set<CandidatePruningFolder> foldersToPruneSet = ConcurrentHashMap.newKeySet();
    private final Semaphore pruneSemaphore = new Semaphore(1);
    private final AtomicInteger queueSize = new AtomicInteger(0);

    public FolderPruningServiceImpl(@Autowired RepositoryService repoService, @Autowired TaskService taskService,
            @Autowired FileService fileService, @Autowired(required = false) MeterRegistry meterRegistry) {
        this.repoService = repoService;
        this.taskService = taskService;
        this.fileService = fileService;
        this.meterRegistry = meterRegistry;
    }

    @Getter
    @EqualsAndHashCode
    static class CandidatePruningFolder {
        private final RepoPath folderPath;
        @EqualsAndHashCode.Exclude
        private final Long insertTime;

        CandidatePruningFolder(RepoPath folderPath, Long insertTime) {
            this.folderPath = folderPath;
            this.insertTime = insertTime;
        }
    }

    @Override
    public void init() {
        Task folderPruningTask = TaskUtils.createRepeatingTask(FolderPruningJob.class,
                TimeUnit.SECONDS.toMillis(ConstantValues.folderPruningIntervalSecs.getLong()),
                TimeUnit.SECONDS.toMillis(ConstantValues.folderPruningIntervalSecs.getLong()));
        taskService.startTask(folderPruningTask, false);
        repoWhitelist = List.of(TRASH_REPO_KEY, IN_TRANSIT_REPO_KEY, RELEASE_BUNDLE_REPO_KEY);
        registerMetrics();
    }

    private void registerMetrics() {
        if (meterRegistry != null) {
            Gauge.builder("folder.pruning.queue.size", this::getQueueSize)
                    .description("The number of folders queued for pruning")
                    .register(meterRegistry);
        }
    }

    private void registerPruneCounter() {
        if (meterRegistry != null) {
            Counter.builder("folder.pruning.count")
                    .description("The number of folders pruned")
                    .register(meterRegistry)
                    .increment();
        }
    }

    @Override
    public void reload(CentralConfigDescriptor oldDescriptor, List<DataDiff<?>> configDiff) {
        //noop
    }

    @Override
    public void doPrune() {
        if (!pruneSemaphore.tryAcquire()) {
            log.debug("Received prune empty folders request, but a pruning process is already running.");
            return;
        }

        try {
            CandidatePruningFolder queuedFolderRepoPath;
            while ((queuedFolderRepoPath = foldersToPrune.peek()) != null) {
                long timeSinceRequest = System.currentTimeMillis() - queuedFolderRepoPath.insertTime;
                long quietPeriod = TimeUnit.SECONDS.toMillis(ConstantValues.folderPruningQuietPeriodSecs.getLong());
                long delta = quietPeriod - timeSinceRequest;
                if (delta > 0) {
                    // In the quiet period let's wait for better time
                    if (delta < quietPeriod / 2L) {
                        // Let's just wait here
                        Thread.sleep(delta);
                        continue;
                    }
                    // Wait for next execution
                    break;
                }
                queuedFolderRepoPath = foldersToPrune.poll();
                if (queuedFolderRepoPath == null) {
                    break;
                }
                queueSize.decrementAndGet();
                doPruneSingleRepoPath(queuedFolderRepoPath.folderPath);
                registerPruneCounter();
                foldersToPruneSet.remove(queuedFolderRepoPath);
            }
        } catch (InterruptedException e) {
            log.error("Got interrupted waiting for quiet period", e);
        } finally {
            pruneSemaphore.release();
        }
    }

    @Override
    public void prune(RepoPath folderRepoPath) {
        log.trace("Prune called for {}", folderRepoPath);
        if (folderRepoPath.isRoot()) {
            return;
        }
        //we want to avoid duplications in the queue
        CandidatePruningFolder candidatePruningFolder =
                new CandidatePruningFolder(folderRepoPath, System.currentTimeMillis());
        if (foldersToPruneSet.add(candidatePruningFolder) &&
                foldersToPrune.offer(candidatePruningFolder)) {
            queueSize.incrementAndGet();
            log.debug("Added folder repo path '{}' to pruning queue.", folderRepoPath);
        }
    }

    private void doPruneSingleRepoPath(RepoPath repoPathToPrune) {
        try {
            List<ItemInfo> loadedChildren = fileService.loadChildren(repoPathToPrune, CHILDREN_LOAD_LIMIT);
            if (shouldPrune(repoPathToPrune, loadedChildren)) {
                log.debug("Pruning empty folder '{}'", repoPathToPrune);
                DeleteContext deleteContext = new DeleteContext(repoPathToPrune)
                        .triggerMetadataCalculation(false)
                        .isExpectEmpty(!hasOnlyMavenMetadataChild(loadedChildren))
                        .triggeredByFolderPruning(true);
                repoService.undeploy(repoPathToPrune, true, deleteContext);
                return;
            }
            if (shouldPruneFolderChildren(repoPathToPrune, loadedChildren)) {
                repoService.getChildrenStream(repoPathToPrune)
                        .forEach(child -> pruneIfFolder(repoPathToPrune, child));
            }
        } catch (ItemNotFoundRuntimeException e) {
            log.debug("Failed to load children for pruning candidate '{}'", repoPathToPrune, e);
        }
    }

    /**
     * Checks if the given path should be pruned
     * <p>
     * Pruning applies only if the path is a folder, and it is either empty or contains only a Maven metadata file.
     *
     */
    private boolean shouldPrune(RepoPath repoPathToPrune, List<ItemInfo> loadedChildren) {
        if (loadedChildren.size() > 1) {
            return false;
        }
        if (hasOnlyMavenMetadataChild(loadedChildren)) {
            return true;
        }
        return loadedChildren.isEmpty() && isFolder(repoPathToPrune);
    }

    private boolean shouldPruneFolderChildren(RepoPath repoPathToPrune, List<ItemInfo> loadedChildren) {
        return !loadedChildren.isEmpty() && (folderChildrenPruningEnabled.getBoolean() || (isTrash(repoPathToPrune) &&
                trashcanFolderPruningEnabled.getBoolean()));
    }

    private void pruneIfFolder(RepoPath repoPathToPrune, ItemInfo child) {
        RepoPath path = child.getRepoPath();
        if (child.isFolder()) {
            log.debug("Folder '{}' contains child '{}' which is a folder, adding to pruning",
                    repoPathToPrune, child);
            prune(path);
        }
    }

    private boolean isFolder(RepoPath repoPathToPrune) {
        ItemInfo unlockedItemInfo;
        try {
            unlockedItemInfo = repoService.getItemInfo(repoPathToPrune);
        } catch (IllegalArgumentException | ItemNotFoundRuntimeException e) {
            log.debug("Failed to get item info for folder validation '{}'", repoPathToPrune, e);
            return false;
        }

        try {
            if (!unlockedItemInfo.isFolder()) {
                log.trace("Cannot prune the file '{}', can only prune folders.", repoPathToPrune);
                return false;
            }
        } catch (ItemNotFoundRuntimeException e) {
            log.debug("Failed to check folder type for '{}'", repoPathToPrune, e);
            return false;
        }

        return true;
    }

    private boolean hasOnlyMavenMetadataChild(List<ItemInfo> childrenNames) {
        return childrenNames.size() == 1 && MavenNaming.MAVEN_METADATA_NAME.equals(childrenNames.getFirst().getName());
    }

    @Override
    public boolean isRepoToBePruned(String repoKey) {
        return repoWhitelist.contains(repoKey);
    }

    /**
     * Returns the current number of folders queued for pruning.
     *
     * @return the current queue size
     */
    @Override
    public int getQueueSize() {
        return queueSize.get();
    }
}
