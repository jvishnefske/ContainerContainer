package org.artifactory.unifiedevent.dispatchers.outbound.consumerclient;

import org.artifactory.unifiedevent.model.DispatcherResponse;
import org.artifactory.unifiedevent.model.EventEnvelope;

public interface ClientOutboundDispatcher {

    /**
     * Sends the specified unified event data to the external consumer client.
     *
     * @param unifiedEvent the {@link EventEnvelope} to be sent; must not be {@code null}
     * @return a {@link DispatcherResponse} indicating the result of the send operation
     */
    <T> DispatcherResponse sendEvent(EventEnvelope<T> unifiedEvent);

}

