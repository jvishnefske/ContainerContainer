package org.artifactory.customer.updates;

import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.subscriptions.SubscriptionsService;
import org.jfrog.common.platform.subscription.SubscriptionType;
import org.mockito.Mockito;
import org.quartz.JobExecutionException;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * @author Alexei Vainshtein
 */
public class CustomerUpdatesJobTest {

    private CustomerUpdatesJob customerUpdatesJob;
    private CustomerUpdatesService customerUpdatesService;
    private SubscriptionsService subscriptionsService;

    @BeforeMethod
    public void setup() {
        customerUpdatesJob = new CustomerUpdatesJob();
        ArtifactoryContext context = Mockito.mock(ArtifactoryContext.class);
        subscriptionsService = Mockito.mock(SubscriptionsService.class);
        Mockito.when(context.beanForType(SubscriptionsService.class)).thenReturn(subscriptionsService);
        customerUpdatesService = Mockito.mock(CustomerUpdatesService.class);
        Mockito.when(context.beanForType(CustomerUpdatesService.class)).thenReturn(customerUpdatesService);
        ArtifactoryContextThreadBinder.bind(context);
    }

    @AfterMethod
    public void tearDown() {
        ArtifactoryContextThreadBinder.unbind();
    }

    @Test
    public void testOnExecuteFreeSubscription() throws JobExecutionException {
        Mockito.when(subscriptionsService.isFreeSubscription()).thenReturn(true);
        customerUpdatesJob.onExecute(null);
        Mockito.verify(customerUpdatesService, Mockito.times(1)).fetchCustomerUpdatesAndStore();
    }

    @Test
    public void testOnExecuteNonFreeSubscription() throws JobExecutionException {
        Mockito.when(subscriptionsService.isFreeSubscription()).thenReturn(false);
        Mockito.when(subscriptionsService.getSubscriptionType()).thenReturn(SubscriptionType.ENTERPRISE_TEAM);
        customerUpdatesJob.onExecute(null);
        Mockito.verify(customerUpdatesService, Mockito.times(0)).fetchCustomerUpdatesAndStore();
    }
}