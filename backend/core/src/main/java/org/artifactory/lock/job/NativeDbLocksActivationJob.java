package org.artifactory.lock.job;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.descriptor.config.MutableCentralConfigDescriptor;
import org.artifactory.lock.ClusterNativeDbLocksMechanismStatus;
import org.artifactory.lock.NativeDbLocksMechanismStatus;
import org.artifactory.lock.service.NativeDbLocksActivationService;
import org.artifactory.schedule.JobCommand;
import org.artifactory.schedule.JobName;
import org.artifactory.schedule.QuartzCommand;
import org.artifactory.schedule.TaskUser;
import org.artifactory.util.AolUtils;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

import java.util.stream.Collectors;

import static org.artifactory.throtlling.qrl.enums.QueryRateLimiterType.SYSTEM;
import static org.artifactory.throttling.qrl.service.QueryRateLimiterServiceImpl.withQueryRateLimiter;

/**
 * Cluster-singleton job that enables native DB locks in AUTO mode once the whole cluster is ready.
 * <p>
 * Scheduled on SaaS only by {@link NativeDbLocksActivationJobScheduler}. Self-hosted customers must enable
 * native locks manually when their DB setup supports it.
 * <p>
 * Sets {@code forceNativeDbLocksMechanismInAutoMode=true} via {@link CentralConfigService} so the change is
 * propagated across the cluster, then unschedules itself.
 */
@Slf4j
@JobCommand(jobName = JobName.NATIVE_DB_LOCKS_ACTIVATION,
        singleton = true,
        allowedAffinity = false,
        description = "Activate native DB locks in AUTO mode when cluster is ready",
        schedulerUser = TaskUser.SYSTEM,
        manualUser = TaskUser.SYSTEM,
        monitored = true)
public class NativeDbLocksActivationJob extends QuartzCommand {

    @Override
    protected void onExecute(JobExecutionContext callbackContext) throws JobExecutionException {
        if (!AolUtils.isAol()) {
            log.debug("Skipping native DB locks auto-activation scheduling on self-hosted");
            return;
        }
        withQueryRateLimiter(SYSTEM, this::executeInternal);
    }

    private void executeInternal() {
        ArtifactoryContext context = ContextHelper.get();
        if (context == null) {
            log.warn("Context is not bound, skipping native DB locks activation job run");
            return;
        }
        log.info("Initializing job to activate native locks");
        try {
            CentralConfigService centralConfigService = context.beanForType(CentralConfigService.class);
            NativeDbLocksActivationService statusService = context.beanForType(NativeDbLocksActivationService.class);
            if (statusService.resolveLocalStatus() != NativeDbLocksMechanismStatus.READY_NOT_IN_USE) {
                log.info("Native DB locks auto-activation is no longer needed, unscheduling job");
                unscheduleActivationJob();
                return;
            }
            if (!statusService.isClusterInfrastructureReadyForActivation(context)) {
                log.info("Cluster infrastructure is not ready for native DB locks activation, will retry");
                return;
            }
            ClusterNativeDbLocksMechanismStatus clusterStatus = statusService.resolveClusterStatus(context);

            if (!clusterStatus.allReadyNotInUse()) {
                logClusterActivationDeferred(clusterStatus);
                return;
            }

            activateNativeLocks(centralConfigService);
            unscheduleActivationJob();
        } catch (Exception e) {
            log.error("Failed to activate native DB locks in AUTO mode, will retry on next run", e);
        }
    }

    private void logClusterActivationDeferred(ClusterNativeDbLocksMechanismStatus clusterStatus) {
        String nodeStatuses = clusterStatus.nodes().stream()
                .map(node -> node.nodeId() + '=' + node.status())
                .collect(Collectors.joining(", "));
        if (clusterStatus.unreachableNodeIds().isEmpty()) {
            log.info("Deferring native DB locks activation: node statuses=[{}]", nodeStatuses);
            return;
        }
        log.info("Deferring native DB locks activation: node statuses=[{}], unreachable peers=[{}]",
                nodeStatuses, String.join(", ", clusterStatus.unreachableNodeIds()));
    }

    private void activateNativeLocks(CentralConfigService centralConfigService) {
        MutableCentralConfigDescriptor descriptor = centralConfigService.getMutableDescriptor();
        if (descriptor.isForceNativeDbLocksMechanismInAutoMode()) {
            log.info("Native DB locks in AUTO mode are already activated");
            return;
        }
        descriptor.setForceNativeDbLocksMechanismInAutoMode(true);
        centralConfigService.saveEditedDescriptorAndReload(descriptor);
        log.info("Activated native DB locks in AUTO mode");
    }

    private void unscheduleActivationJob() {
        cancelMyself();
    }
}
