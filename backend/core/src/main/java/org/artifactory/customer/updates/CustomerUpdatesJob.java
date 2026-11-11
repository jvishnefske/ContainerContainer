package org.artifactory.customer.updates;

import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.subscriptions.SubscriptionsService;
import org.artifactory.schedule.JobCommand;
import org.artifactory.schedule.JobName;
import org.artifactory.schedule.QuartzCommand;
import org.artifactory.schedule.TaskUser;
import org.jfrog.common.platform.subscription.SubscriptionType;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Omri Ziv
 */
@JobCommand(jobName = JobName.CUSTOMER_UPDATES, description = "Usage Updates",
        singleton = true, clusterSingleton = false, allowedAffinity = false, schedulerUser = TaskUser.SYSTEM,
        manualUser = TaskUser.SYSTEM)
public class CustomerUpdatesJob extends QuartzCommand {

    private static final Logger log = LoggerFactory.getLogger(CustomerUpdatesJob.class);

    @Override
    protected void onExecute(JobExecutionContext callbackContext) throws JobExecutionException {
        SubscriptionsService subscriptionsService = ContextHelper.get().beanForType(SubscriptionsService.class);
        if (subscriptionsService.isFreeSubscription()) {
            log.debug("Usage Updates job started");
            ContextHelper.get().beanForType(CustomerUpdatesService.class).fetchCustomerUpdatesAndStore();
            log.debug("Usage Updates job finished");
        } else if (log.isDebugEnabled()) {
            SubscriptionType subscriptionType = subscriptionsService.getSubscriptionType();
            if (subscriptionType != null) {
                log.debug("Found non free subscription {}", subscriptionType.getString());
            }
        }
    }
}
