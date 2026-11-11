package org.artifactory.api.subscriptions;

import org.jfrog.common.platform.subscription.SubscriptionType;
import org.springframework.stereotype.Service;

@Service
public class DefaultSubscriptionsServiceImpl implements SubscriptionsService {

    @Override
    public String getPurchaseUrl() {
        return null;
    }

    @Override
    public SubscriptionType getSubscriptionType() {
        return null;
    }

    @Override
    public boolean isSubscriptionSet() {
        return false;
    }

    @Override
    public boolean isFreeSubscription() {
        return false;
    }
}
