package org.artifactory.repo.cleanup;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.aql.AqlService;
import org.artifactory.aql.api.domain.sensitive.AqlApiItem;
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
import static org.artifactory.descriptor.repo.RepoType.HuggingFaceML;

/**
 * @author Volodymyr Shulga
 */
@Service
@Slf4j
public class HuggingFaceUploadsFolderCleanupServiceImpl implements ContextCreationListener {
    private static final String LAST_UPDATED_ATTRIBUTE = "hf_last_updated";
    private static final String TEMP_UPLOADS_DIR = "_uploads";
    private final TaskService taskService;
    private final AqlService aqlService;
    private final InternalRepositoryService repositoryService;

    @Autowired
    public HuggingFaceUploadsFolderCleanupServiceImpl(AqlService aqlService,
            InternalRepositoryService repositoryService,
            TaskService taskService) {
        this.taskService = taskService;
        this.aqlService = aqlService;
        this.repositoryService = repositoryService;
    }

    @Override
    public void onContextCreated() {
        Task huggingFaceUploadsTmpFolderCleanupTask =
                TaskUtils.createRepeatingTask(HuggingFaceUploadsFolderCleanupJob.class,
                TimeUnit.SECONDS.toMillis(ConstantValues.huggingFaceCleanupUploadsTmpFolderJobSecs.getLong()), 0L);
        taskService.startTask(huggingFaceUploadsTmpFolderCleanupTask, false);
    }

    public void clean() {
        long startTime = System.currentTimeMillis();
        log.info("Starting HuggingFace local repos _uploads folder cleanup");
        doCleanup();
        log.info("HuggingFace _uploads dirs cleanup finished, time took: {} millis",
                (System.currentTimeMillis() - startTime));
    }

    void doCleanup() {
        Set<String> hfRepoKeys = repositoryService
                .getLocalAndFederatedRepoConfigsExcludingInfoRepos().stream()
                .filter(config -> config.getPackageType() == HuggingFaceML)
                .map(RepoConfig::getKey).collect(Collectors.toSet());

        final long cleanupPeriodMillis = ConstantValues.huggingFaceCleanupUploadsTmpFolderJobSecs.getLong() * 1000L;
        try (AqlLazyResult<AqlItem> lazyResult = aqlService.executeQueryLazy(expiredFiles(cleanupPeriodMillis))) {
            AqlLazyObjectResultStreamer<FileInfoItemRow> streamer =
                    new AqlLazyObjectResultStreamer<>(lazyResult, FileInfoItemRow.class);
            FileInfoItemRow row;
            while ((row = streamer.getRow()) != null) {
                String repoKey = row.getRepo();
                String name = row.getName();
                String path = row.getPath();
                if (hfRepoKeys.contains(repoKey) && path.endsWith("/" + TEMP_UPLOADS_DIR)) {
                    log.info("Deleting HF temp file {}/{} from repo {}", path, name, repoKey);
                    repositoryService.undeploy(RepoPathFactory.create(repoKey, path + "/" + name), false, true);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to execute HF cleanup : {}", e.getMessage());
            log.debug("Failed to execute HF cleanup", e);
        }
    }

    /**
     * @return all the files that have `LAST_UPDATED_ATTRIBUTE` and were updated more than `cleanupPeriodMillis` ago.
     */
    private AqlApiItem expiredFiles(long cleanupPeriodMillis) {
        long currentTimeMillis = System.currentTimeMillis();
        // All the files that older of this date are expired and should be removed.
        final long oldestDateToKeep = currentTimeMillis - cleanupPeriodMillis;
        return createWithEmptyResults().filter(
                and(
                        type().equal(AqlItemTypeEnum.file.signature),
                        property().key().equal(LAST_UPDATED_ATTRIBUTE),
                        path().matches("*/" + TEMP_UPLOADS_DIR),
                        property().value().less(oldestDateToKeep)
                )
        ).include(repo(), path(), name()).distinct(true);
    }
}