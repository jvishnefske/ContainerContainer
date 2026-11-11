package org.artifactory.mpu.cleanup;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.common.ConstantValues;
import org.artifactory.schedule.JobCommand;
import org.artifactory.schedule.JobName;
import org.artifactory.schedule.QuartzCommand;
import org.artifactory.schedule.TaskUser;
import org.artifactory.sh.acl.ArtifactoryBinaryService;
import org.artifactory.spring.InternalArtifactoryContext;
import org.artifactory.spring.InternalContextHelper;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

/**
 * Cron job that cleanup multipart uploads from storage.
 *
 * @author Idan Dadon
 */
@JobCommand(jobName = JobName.MULTIPART_UPLOAD_CLEANUP_JOB, singleton = true, schedulerUser = TaskUser.SYSTEM,
        manualUser = TaskUser.SYSTEM, description = "Cleanup multipart uploads from db and storage")
@Slf4j
public class MultipartUploadCleanupJob extends QuartzCommand {

    @Override
    protected void onExecute(JobExecutionContext callbackContext) throws JobExecutionException {
        InternalArtifactoryContext context = InternalContextHelper.get();
        if (!context.isReady()) {
            log.debug("Skipping execution of '{}', server is not ready yet",
                    MultipartUploadCleanupJob.class.getName());
            return;
        }
        ArtifactoryBinaryService binaryService = context.beanForType(ArtifactoryBinaryService.class);
        if (binaryService.isMultipartUploadSupported()) {
            binaryService.cleanupMultipartUploads();
        }
    }

    /**
     * To reduce load on AOLs we set random execution time
     *
     * @return Quartz scheduling expression
     */
    public static String buildRandomQuartzExp(String serviceId) {
        // Adding an option to override CRON expression
        String predefinedExp = ConstantValues.mpuCleanupCron.getString();
        return getRandomHourMinuteCron(predefinedExp, serviceId, "Multipart Upload Cleanup Job");
    }
}
