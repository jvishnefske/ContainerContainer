package org.artifactory.storage.jobs;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.schedule.JobCommand;
import org.artifactory.schedule.JobName;
import org.artifactory.schedule.QuartzCommand;
import org.artifactory.schedule.TaskUser;
import org.artifactory.sh.acl.ArtifactoryBinaryService;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

@Slf4j
@JobCommand(jobName = JobName.SHARDING_BALANCING,
        singleton = true,
        schedulerUser = TaskUser.SYSTEM,
        manualUser = TaskUser.SYSTEM,
        description = "Balancing Job"
)
public class ShardingBalancerJob extends QuartzCommand {
    @Override
    protected void onExecute(JobExecutionContext callbackContext) throws JobExecutionException {
        try {
            ArtifactoryBinaryService binaryStore = ContextHelper.get().beanForType(ArtifactoryBinaryService.class);
            log.debug("Starting sharding balancing job");
            binaryStore.tryToOptimize();
        } catch (Exception e) {
            throw new JobExecutionException(e);
        }
    }
}
