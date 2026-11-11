package org.artifactory.repo.cleanup;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.docker.DockerAddon;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.common.ConstantValues;
import org.artifactory.schedule.Task;
import org.artifactory.schedule.TaskService;
import org.artifactory.schedule.TaskUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * @author dudim
 */
@Slf4j
@Service
public class DockerUploadsFolderCleanupServiceImpl implements DockerUploadsFolderCleanupService {


    private final TaskService taskService;
    private final RepositoryService repositoryService;
    private final AddonsManager addonsManager;

    @Autowired
    public DockerUploadsFolderCleanupServiceImpl(RepositoryService repositoryService,
            AddonsManager addonsManager,
            TaskService taskService) {
        this.repositoryService = repositoryService;
        this.taskService = taskService;
        this.addonsManager = addonsManager;
    }

    @Override
    public void onContextCreated() {
        Task dockerUploadsTmpFolderCleanupTask = TaskUtils.createRepeatingTask(DockerUploadsFolderCleanupJob.class,
                TimeUnit.SECONDS.toMillis(ConstantValues.dockerCleanupUploadsTmpFolderJobSecs.getLong()), 0L);
        taskService.startTask(dockerUploadsTmpFolderCleanupTask, false);
    }

    @Override
    public void clean() {
        long startTime = System.currentTimeMillis();
        log.info("Starting temp folder cleanup");
        doCleanup();
        log.info("Temp folder cleanup finished, time took: {} millis", (System.currentTimeMillis() - startTime));

    }

    void doCleanup() {
        repositoryService.getLocalAndFederatedRepoConfigsExcludingInfoRepos().stream()
                .filter(localRepo -> localRepo.getPackageType().isDockerGroup())
                .forEach(repoConfig -> addonsManager.addonByType(DockerAddon.class)
                        .searchAndCleanupTempFolders(repoConfig.getKey()));
    }
}