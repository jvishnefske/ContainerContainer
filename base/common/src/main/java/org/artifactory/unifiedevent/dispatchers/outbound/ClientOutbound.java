package org.artifactory.unifiedevent.dispatchers.outbound;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.unifiedevent.dispatchers.outbound.consumerclient.ClientOutboundDispatcher;
import org.artifactory.unifiedevent.model.EventEnvelope;
import org.artifactory.unifiedevent.registry.Consumer;
import org.artifactory.unifiedevent.model.DispatcherResponse;
import org.springframework.stereotype.Component;

import java.util.Map;

@AllArgsConstructor
@Slf4j
@Component
public class ClientOutbound implements OutboundDispatcher {

    protected Map<String, ClientOutboundDispatcher> clientOutboundDispatchers;

    @Override
    public <T> DispatcherResponse dispatchEvent(Consumer consumer, EventEnvelope<T> unifiedEvent) {
        ClientOutboundDispatcher clientOutboundDispatcher = clientOutboundDispatchers.get(consumer.name().getName());
        if (clientOutboundDispatcher == null) {
            log.warn("No ClientOutboundDispatcher found for consumer: {}", consumer.name());
            return null;
        }
        log.debug("Dispatching event to ClientOutboundDispatcher: {}", clientOutboundDispatcher);
        return clientOutboundDispatcher.sendEvent(unifiedEvent);
    }
}
