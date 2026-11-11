package org.artifactory.storage.jobs;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.common.ConstantValues;
import org.artifactory.repo.service.ImportJob;
import org.artifactory.schedule.JobCommand;
import org.artifactory.schedule.QuartzCommand;
import org.artifactory.schedule.StopCommand;
import org.artifactory.schedule.StopStrategy;
import org.artifactory.schedule.TaskUser;
import org.artifactory.spring.InternalArtifactoryContext;
import org.artifactory.spring.InternalContextHelper;
import org.artifactory.storage.service.ProjectBinarySizeService;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.artifactory.schedule.JobName.PROJECT_BINARY_STORAGE_CALCULATION;

/**
 * Cron job that calculates and updates project binary storage cache.
 *
 * @author Evgeny Kurtser
 */
@Slf4j
@JobCommand(jobName = PROJECT_BINARY_STORAGE_CALCULATION, singleton = true, schedulerUser = TaskUser.SYSTEM,
        manualUser = TaskUser.SYSTEM,
        description = "Calculate Project Binary Storage Job",
        commandsToStop = {@StopCommand(command = ImportJob.class, strategy = StopStrategy.IMPOSSIBLE)})
public class CalculateProjectBinaryStorageJob extends QuartzCommand {

    @Override
    protected void onExecute(JobExecutionContext callbackContext) throws JobExecutionException {
        InternalArtifactoryContext context = InternalContextHelper.get();
        if (!context.isReady()) {
            log.debug("Skipping execution of '{}', server is not ready yet",
                    CalculateProjectBinaryStorageJob.class.getName());
            return;
        }

        ProjectBinarySizeService calculationService =
                context.beanForType(ProjectBinarySizeService.class);

        // Cleanup old cache entries
        int daysThreshold = ConstantValues.projectBinaryStorageCalculationEvictionDays.getInt();
        long since = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(daysThreshold);
        int cleanedEntries = calculationService.cleanup(since);
        log.info("Cleaned up {} old project binary storage cache entries older than {} days",
                cleanedEntries, daysThreshold);

        // Parse timeout (in minutes)
        long timeoutMillis =
                TimeUnit.MINUTES.toMillis(ConstantValues.projectBinaryStorageCalculationTimeoutMinutes.getLong());
        // Execute calculation with timeout
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<Integer> future = executor.submit(calculationService::calculateAllProjectsSizes);
        try (executor) {
            Integer processedCount = future.get(timeoutMillis, TimeUnit.MILLISECONDS);
            log.info("Project binary storage calculation completed successfully. Processed {} projects",
                    processedCount);
        } catch (TimeoutException e) {
            future.cancel(true);
            log.error("Project binary storage calculation timed out after {} minutes. Old values remain in cache",
                    TimeUnit.MILLISECONDS.toMinutes(timeoutMillis));
        } catch (Exception e) {
            log.error("Project binary storage calculation failed due to {}: {}. Old values remain in cache.",
                    e.getClass().getSimpleName(), e.getMessage(), e);
        } finally {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(1, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }


    /**
     * Build Quartz scheduling expression from configuration.
     * To reduce load, generates a random execution time once per day if no predefined cron is provided.
     *
     * @return Quartz scheduling expression
     */
    @NonNull
    public static String buildQuartzExp(String serviceId) {
        String predefinedExp = ConstantValues.projectBinaryStorageCalculationJobCron.getString();
        return getRandomHourMinuteCron(predefinedExp, serviceId, "Project Binary Storage");
    }
}