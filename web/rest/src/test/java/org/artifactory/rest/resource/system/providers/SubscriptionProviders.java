package org.artifactory.rest.resource.system.providers;

import org.jfrog.common.platform.subscription.SubscriptionType;
import org.testng.annotations.DataProvider;

/**
 * @author Alexei Vainshtein
 */
public class SubscriptionProviders {

    @DataProvider
    public Object[][] subscriptionProvider() {
        return new Object[][] {
                {SubscriptionType.FREE_XRAY},
                {SubscriptionType.FREE}
        };
    }
}
