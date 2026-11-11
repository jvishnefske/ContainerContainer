package org.artifactory.ui.rest.service.state;

import lombok.Getter;
import lombok.NoArgsConstructor;
import org.jfrog.common.platform.subscription.SubscriptionType;

/**
 * @author Alexei Vainshtein
 */
@Getter
@NoArgsConstructor
public class State {

    private String subscriptionType;

    public State(SubscriptionType subscriptionTypeEnum) {
        if (subscriptionTypeEnum != null) {
            this.subscriptionType = subscriptionTypeEnum.getString();
        }
    }
}
