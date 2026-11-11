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
import org.jfrog.common.ExceptionLogUtils;
import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.artifactory.aql.api.domain.sensitive.AqlApiItem.createWithEmptyResults;
import static org.artifactory.aql.api.domain.sensitive.AqlApiItem.created;
import static org.artifactory.aql.api.domain.sensitive.AqlApiItem.name;
import static org.artifactory.aql.api.domain.sensitive.AqlApiItem.path;
import static org.artifactory.aql.api.domain.sensitive.AqlApiItem.repo;
import static org.artifactory.aql.api.domain.sensitive.AqlApiItem.type;
import static org.artifactory.aql.api.internal.AqlBase.and;
import static org.artifactory.descriptor.repo.RepoType.Nix;

/**
 * Service that cleans up old files from _uploads folders in Nix local repositories.
 * @author Natalie Berni
 */
@Service
@Slf4j
public class NixUploadsFolderCleanupServiceImpl implements ContextCreationListener {

    private static final String NAR_XZ_TEMP_UPLOADS_FOLDER = "_uploads";
    private final TaskService taskService;
    private final AqlService aqlService;
    private final InternalRepositoryService repositoryService;

    @Autowired
    public NixUploadsFolderCleanupServiceImpl(AqlService aqlService,
            InternalRepositoryService repositoryService,
            TaskService taskService) {
        this.taskService = taskService;
        this.aqlService = aqlService;
        this.repositoryService = repositoryService;
    }
    @Override
    public void onContextCreated() {
        Task nixUploadsTmpFolderCleanupTask =
                TaskUtils.createRepeatingTask(NixUploadsFolderCleanupJob.class,
                TimeUnit.SECONDS.toMillis(ConstantValues.nixCleanupUploadsTmpFolderJobSecs.getLong()), 0L);
        taskService.startTask(nixUploadsTmpFolderCleanupTask, false);
    }

    public void clean() {
        long startTime = System.currentTimeMillis();
        log.info("Starting Nix local repos _uploads folder cleanup");
        doCleanup();
        log.info("Nix _uploads dirs cleanup finished, time took: {} millis",
                (System.currentTimeMillis() - startTime));
    }

    /**
     * Cleans up all the files in _uploads folders that were created more than
     * {@link ConstantValues#nixCleanupUploadsTmpFolderJobSecs} seconds ago.
     * Lazy AQL is used to avoid loading all the results into memory at once.
     */
    void doCleanup() {
        final long cleanupPeriodMillis = ConstantValues.nixCleanupUploadsTmpFolderJobSecs.getLong() * 1000L;

        Set<String> nixRepoKeys = repositoryService
                .getLocalAndFederatedRepoConfigsExcludingInfoRepos().stream()
                .filter(config -> config.getPackageType() == Nix)
                .map(RepoConfig::getKey).collect(Collectors.toSet());

        for (String repoKey : nixRepoKeys) {
            try (AqlLazyResult<AqlItem> lazyResult = aqlService.executeQueryLazy(
                    expiredFiles(repoKey, cleanupPeriodMillis))) {
                AqlLazyObjectResultStreamer<FileInfoItemRow> streamer =
                        new AqlLazyObjectResultStreamer<>(lazyResult, FileInfoItemRow.class);
                FileInfoItemRow row;
                while ((row = streamer.getRow()) != null) {
                    String name = row.getName();
                    String path = row.getPath();
                    log.info("Deleting Nix temp file {}/{} from repo {}", path, name, repoKey);
                    repositoryService.undeploy(RepoPathFactory.create(repoKey, path + "/" + name), false, true);
                }
            } catch (Exception e) {
                ExceptionLogUtils.debugOrWarn(log,
                        "Failed to clean Nix _uploads folder in repo " + repoKey, e);
            }
        }
    }

    /**
     * @return all the files in _uploads folders that were created more than {@code cleanupPeriodMillis} ago.
     */
    private AqlApiItem expiredFiles(String repoKey, long cleanupPeriodMillis) {
        long currentTimeMillis = System.currentTimeMillis();
        // All the files that older of this date are expired and should be removed.
        final long oldestDateToKeep = currentTimeMillis - cleanupPeriodMillis;
        return createWithEmptyResults().filter(
                and(
                        repo().equal(repoKey),
                        type().equal(AqlItemTypeEnum.file.signature),
                        path().matches("*/" + NAR_XZ_TEMP_UPLOADS_FOLDER),
                        created().less(new DateTime(oldestDateToKeep))
                )
        ).include(repo(), path(), name()).distinct(true);
    }
}
