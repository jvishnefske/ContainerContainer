package org.artifactory.unifiedevent.registry;

import com.fasterxml.jackson.core.type.TypeReference;
import lombok.AllArgsConstructor;
import org.artifactory.config.CentralConfigKey;
import org.artifactory.security.access.AccessService;
import org.artifactory.spring.Reloadable;
import org.artifactory.spring.ReloadableBean;
import org.artifactory.unifiedevent.constants.SupportedConsumers;
import org.artifactory.unifiedevent.constants.SupportedOutbound;
import org.artifactory.unifiedevent.constants.SupportedUnifiedEvents;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@AllArgsConstructor
@Component
@Reloadable(beanClass = ConsumerConfig.class, initAfter = {AccessService.class}, listenOn = CentralConfigKey.none)
public class ConsumerConfig implements ReloadableBean {

    protected AccessService accessService;
    // Decide a production prefix later
    private static final String CONSUMER_NAME_PATTERN_PREFIX = "unified.events.draft.";

    public String getAndValidateActionKey(SupportedUnifiedEvents action) {
        // Should validate that the action is valid with the available action constants
        return CONSUMER_NAME_PATTERN_PREFIX + action.getName();
    }

    public void setConsumerForAction(SupportedUnifiedEvents action, Consumer consumer, boolean isSecret) {
        List<Consumer> subscribedConsumers = new ArrayList<>(getConsumersForAction(action));
        if (subscribedConsumers.contains(consumer)) {
            return;
        }
        subscribedConsumers.add(consumer);
        String actionKey = getAndValidateActionKey(action);
        accessService.getPlatformConfigClient().setConfig(actionKey, subscribedConsumers, isSecret);
    }

    public List<Consumer> getConsumersForAction(SupportedUnifiedEvents action) {
        String actionKey = getAndValidateActionKey(action);
        List<Consumer> consumers =
                accessService.getPlatformConfigClient().getConfig(actionKey, new TypeReference<List<Consumer>>() {
                });
        return consumers != null ? consumers : new ArrayList<>();
    }

    public void removeConsumersForAction(SupportedUnifiedEvents event, Consumer consumer) {
        List<Consumer> subscribedConsumers = new ArrayList<>(getConsumersForAction(event));
        subscribedConsumers.remove(consumer);
        String actionKey = getAndValidateActionKey(event);
        accessService.getPlatformConfigClient().setConfig(actionKey, subscribedConsumers, false);
    }

    // Default registration of the worker client for all action
    @Override
    public void init() {
        // NOTE: ConsumerName and ConsumerOutbound should be same as the name of the respected Client and Outbound class
        Consumer consumer = Consumer.builder()
                .name(SupportedConsumers.WORKER_CLIENT)
                .outbound(SupportedOutbound.CLIENT)
                .build();
        for (SupportedUnifiedEvents action : SupportedUnifiedEvents.values()) {
            setConsumerForAction(action, consumer, false);
        }
    }

}