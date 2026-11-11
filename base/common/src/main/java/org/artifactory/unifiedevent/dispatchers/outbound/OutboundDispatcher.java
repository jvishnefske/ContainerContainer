package org.artifactory.unifiedevent.dispatchers.outbound;

import org.artifactory.unifiedevent.model.EventEnvelope;
import org.artifactory.unifiedevent.registry.Consumer;
import org.artifactory.unifiedevent.model.DispatcherResponse;

public interface OutboundDispatcher {

    /**
     * Dispatches the specified unified event data to the given consumer.
     *
     * @param consumer the {@link Consumer} to which the event should be dispatched; must not be {@code null}
     * @param payload the {@link EventEnvelope} to be dispatched; must not be {@code null}
     * @return a {@link DispatcherResponse} indicating the result of the dispatch operation
     */
    <T> DispatcherResponse dispatchEvent(Consumer consumer, EventEnvelope<T> payload);

}
