package org.artifactory.ui;

import org.artifactory.ui.rest.service.state.State;
import org.jfrog.common.platform.subscription.SubscriptionType;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * @author Alexei Vainshtein
 */
public class StateTest {

    @Test
    public void testState() {
        assertSubscription(null);
        assertSubscription(SubscriptionType.ENTERPRISE_TEAM);
        assertSubscription(SubscriptionType.FREE);
    }

    private void assertSubscription(SubscriptionType subscriptionType) {
        State provider = new State(subscriptionType);
        if (subscriptionType != null) {
            Assert.assertEquals(provider.getSubscriptionType(), subscriptionType.getString());
        } else {
            Assert.assertNull(provider.getSubscriptionType());
        }
    }
}