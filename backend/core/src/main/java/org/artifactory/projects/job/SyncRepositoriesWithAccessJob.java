package org.artifactory.projects.job;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.projects.ProjectsService;
import org.artifactory.schedule.JobCommand;
import org.artifactory.schedule.JobName;
import org.artifactory.schedule.QuartzCommand;
import org.artifactory.schedule.TaskUser;
import org.artifactory.util.RepoPathUtils;
import org.jfrog.access.common.ResourceType;
import org.quartz.JobExecutionContext;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author Mor Merhav
 * Created on 22/02/2021
 */
@Slf4j
@JobCommand(jobName = JobName.SYNC_REPOSITORIES_WITH_ACCESS,
        singleton = true,
        allowedAffinity = false,
        description = "Sync repositories with Access",
        schedulerUser = TaskUser.SYSTEM,
        manualUser = TaskUser.SYSTEM)
public class SyncRepositoriesWithAccessJob extends QuartzCommand {

    private final ProjectsService projectsService;
    private final RepositoryService repositoryService;

    public SyncRepositoriesWithAccessJob() {
        this.projectsService = ContextHelper.get().beanForType(ProjectsService.class);
        this.repositoryService = ContextHelper.get().beanForType(RepositoryService.class);
    }

    @Override
    protected void onExecute(JobExecutionContext callbackContext) {
        log.debug("Starting synchronization of repositories with Access");
        Set<String> allRepoKeys = repositoryService.getAllRepoKeys();
        allRepoKeys = allRepoKeys.stream().map(RepoPathUtils::removeDashCache).collect(Collectors.toSet());
        projectsService.syncProjectResources(ResourceType.REPO, allRepoKeys);
        log.debug("Finished synchronization of repositories with Access");
    }
}
