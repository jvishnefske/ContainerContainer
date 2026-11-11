package org.artifactory.unifiedevent.dispatchers;

import jakarta.validation.constraints.NotNull;
import org.artifactory.unifiedevent.model.DispatcherResponse;
import org.artifactory.unifiedevent.model.EventEnvelope;

/**
 * Defines the contract for dispatching unified events to various event dispatchers.
 * Implementations of this interface are responsible for handling the logic of event dispatching,
 * including enabling/disabling dispatchers and supporting synchronous or asynchronous operations.
 * <p>
 * Typical usage involves calling {@link #dispatchEvent(EventEnvelope)} to process an event,
 * and checking {@link #isEnabled()} and {@link #isSync()} to determine dispatcher state and mode.
 * </p>
 *
 * @author Harendrar
 */
public interface EventDispatcher {

    /**
     * Dispatches the specified unified event data.
     *
     * @param eventEnvelope the unified event data to be dispatched; must not be {@code null}
     * @return a {@link DispatcherResponse} indicating the result of the dispatch operation
     */
    <T> DispatcherResponse dispatchEvent(@NotNull EventEnvelope<T> eventEnvelope);

    /**
     * Indicates whether this dispatcher is currently enabled.
     * If disabled, events will not be dispatched to this dispatcher.
     *
     * @return {@code true} if the dispatcher is enabled; {@code false} otherwise
     */
    boolean isEnabled();

    /**
     * Indicates whether this dispatcher operates in synchronous mode.
     * In synchronous mode, the dispatcher waits for a response from the consumer before proceeding.
     *
     * @return {@code true} if the dispatcher is synchronous; {@code false} if asynchronous
     */
    boolean isSync();
}
