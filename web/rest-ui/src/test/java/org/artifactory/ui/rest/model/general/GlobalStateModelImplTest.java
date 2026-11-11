package org.artifactory.ui.rest.model.general;

import org.artifactory.ui.rest.service.state.FooterMessage;
import org.artifactory.ui.rest.service.state.State;
import org.jfrog.common.platform.subscription.SubscriptionType;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Alexei Vainshtein
 */
public class GlobalStateModelImplTest {

    @Test
    public void testGetMessages() {
        List<FooterMessage> messages = new ArrayList<>();
        FooterMessage warning_message =
                FooterMessage.createWarning("warning message", null, FooterMessage.FooterMessageVisibility.user);
        messages.add(warning_message);
        GlobalStateModelImpl model = new GlobalStateModelImpl();
        model.setMessages(messages);
        Assert.assertEquals(model.getMessages(), messages);
    }

    @Test
    public void testGetStates() {
        GlobalStateModelImpl model = new GlobalStateModelImpl();
        model.setState(new State(null));
        State states = model.getState();
        Assert.assertNull(states.getSubscriptionType());

        assertResponse(SubscriptionType.ENTERPRISE_TEAM);
        assertResponse(SubscriptionType.FREE);
    }

    private void assertResponse(SubscriptionType subscriptionType) {
        GlobalStateModelImpl model = new GlobalStateModelImpl();
        model.setState(new State(subscriptionType));
        State states = model.getState();
        Assert.assertEquals(states.getSubscriptionType(), subscriptionType.getString());
    }
}