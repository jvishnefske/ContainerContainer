package org.artifactory.repo.cleanup;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.aql.AqlService;
import org.artifactory.aql.api.domain.sensitive.AqlApiItem;
import org.artifactory.aql.model.AqlComparatorEnum;
import org.artifactory.aql.model.AqlItemTypeEnum;
import org.artifactory.aql.result.AqlLazyResult;
import org.artifactory.aql.result.rows.AqlItem;
import org.artifactory.aql.result.rows.AqlLazyObjectResultStreamer;
import org.artifactory.aql.result.rows.FileInfoItemRow;
import org.artifactory.common.ConstantValues;
import org.artifactory.model.RepoConfig;
import org.artifactory.repo.RepoPathFactory;
import org.artifactory.repo.service.InternalRepositoryService;
import org.artifactory.schedule.Task;
import org.artifactory.schedule.TaskService;
import org.artifactory.schedule.TaskUtils;
import org.artifactory.spring.ContextCreationListener;
import org.jfrog.common.ExceptionLogUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.artifactory.aql.api.domain.sensitive.AqlApiItem.createWithEmptyResults;
import static org.artifactory.aql.api.domain.sensitive.AqlApiItem.name;
import static org.artifactory.aql.api.domain.sensitive.AqlApiItem.path;
import static org.artifactory.aql.api.domain.sensitive.AqlApiItem.property;
import static org.artifactory.aql.api.domain.sensitive.AqlApiItem.repo;
import static org.artifactory.aql.api.domain.sensitive.AqlApiItem.type;
import static org.artifactory.aql.api.internal.AqlBase.and;
import static org.artifactory.descriptor.repo.RepoType.Conda;

/**
 * Service that cleans up old Conda package shard files marked for deletion.
 */
@Service
@Slf4j
public class CondaShardsCleanupServiceImpl implements ContextCreationListener {

    private static final String CONDA_SHARD_MARKED_FOR_DELETION = "conda.shard.markedForDeletion";
    private static final String CONDA_SHARD_MARKED_FOR_DELETION_TIMESTAMP_MILLIS =
            "conda.shard.markedForDeletionTimestampMillis";
    private static final String JFROG_SHARDS_FOLDER = ".jfrog/shards/";
    private static final String PACKAGE_SHARDS_FOLDER = "/package_shards";
    private static final String MSGPACK_ZST_SUFFIX = ".msgpack.zst";

    private final TaskService taskService;
    private final AqlService aqlService;
    private final InternalRepositoryService repositoryService;

    @Autowired
    public CondaShardsCleanupServiceImpl(AqlService aqlService, InternalRepositoryService repositoryService,
            TaskService taskService) {
        this.taskService = taskService;
        this.aqlService = aqlService;
        this.repositoryService = repositoryService;
    }

    @Override
    public void onContextCreated() {
        if (!ConstantValues.condaShardsCleanupEnabled.getBoolean()) {
            log.info("Conda package shards cleanup job is disabled");
            return;
        }
        if (taskService.hasTaskOfType(CondaShardsCleanupJob.class)) {
            log.debug("Conda package shards cleanup job is already scheduled");
            return;
        }
        long intervalMs = TimeUnit.SECONDS.toMillis(ConstantValues.condaShardsCleanupIntervalSecs.getLong());
        Task condaShardsCleanupTask = TaskUtils.createRepeatingTask(CondaShardsCleanupJob.class,
                intervalMs, intervalMs);
        taskService.startTask(condaShardsCleanupTask, false);
    }

    public void clean() {
        if (!ConstantValues.condaShardsCleanupEnabled.getBoolean()) {
            log.debug("Conda package shards cleanup is disabled; skipping");
            return;
        }
        long startTime = System.currentTimeMillis();
        log.info("Starting Conda package shards cleanup");
        doCleanup();
        log.info("Conda package shards cleanup finished, time took: {} millis",
                System.currentTimeMillis() - startTime);
    }

    void doCleanup() {
        Set<String> condaRepoKeys = repositoryService.getLocalAndFederatedRepoConfigsExcludingInfoRepos().stream()
                .filter(config -> config.getPackageType().equals(Conda))
                .map(RepoConfig::getKey)
                .collect(Collectors.toSet());

        for (String repoKey : condaRepoKeys) {
            cleanupRepo(repoKey);
        }
    }

    private void cleanupRepo(String repoKey) {
        long batchSize = ConstantValues.condaShardsCleanupBatchSize.getLong();
        try (AqlLazyResult<AqlItem> lazyResult = aqlService.executeQueryLazy(
                expiredMarkedPackageShards(repoKey, batchSize))) {
            AqlLazyObjectResultStreamer<FileInfoItemRow> streamer =
                    new AqlLazyObjectResultStreamer<>(lazyResult, FileInfoItemRow.class);
            FileInfoItemRow row;
            while ((row = streamer.getRow()) != null) {
                deleteIfPackageShard(row);
            }
        } catch (Exception e) {
            ExceptionLogUtils.debugOrWarn(log, "Failed to clean Conda package shards in repo " + repoKey, e);
        }
    }

    private void deleteIfPackageShard(FileInfoItemRow row) {
        String repoKey = row.getRepo();
        String path = row.getPath();
        String name = row.getName();
        if (!isPackageShard(path, name)) {
            log.debug("Skipping non-package-shard Conda cleanup candidate {}/{}/{}", repoKey, path, name);
            return;
        }
        log.info("Deleting Conda package shard {}/{} from repo {}", path, name, repoKey);
        repositoryService.undeploy(RepoPathFactory.create(repoKey, path + "/" + name), false, true);
    }

    private boolean isPackageShard(String path, String name) {
        return path != null && path.startsWith(JFROG_SHARDS_FOLDER) && path.endsWith(PACKAGE_SHARDS_FOLDER) &&
                name != null && name.endsWith(MSGPACK_ZST_SUFFIX);
    }

    /**
     * Returns Conda shard files that were marked for deletion before the cleanup safety window.
     * <p>
     * The safety window prevents a race between marking a superseded shard and making the updated repodata/shard index
     * visible to readers. A shard is eligible only when its deletion timestamp is older than
     * {@code now - conda.shards.cleanup.safetyWindowMillis}, so recently superseded shards remain available during
     * that propagation window.
     */
    private AqlApiItem expiredMarkedPackageShards(String repoKey, long batchSize) {
        long oldestDateToKeep = System.currentTimeMillis() -
                ConstantValues.condaShardsCleanupSafetyWindowMillis.getLong();
        return createWithEmptyResults().filter(
                and(
                        repo().equal(repoKey),
                        type().equal(AqlItemTypeEnum.file.signature),
                        path().matches(JFROG_SHARDS_FOLDER + "*"),
                        property().property(CONDA_SHARD_MARKED_FOR_DELETION, AqlComparatorEnum.equals,
                                Boolean.TRUE.toString()),
                        property().property(CONDA_SHARD_MARKED_FOR_DELETION_TIMESTAMP_MILLIS,
                                AqlComparatorEnum.less, String.valueOf(oldestDateToKeep))
                )
        ).include(repo(), path(), name()).distinct(true).limit(batchSize);
    }
}
