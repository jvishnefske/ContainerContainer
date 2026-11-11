package org.artifactory.jfbus;

import org.artifactory.async.Async;
import org.springframework.stereotype.Component;

/**
 * Spring-managed asynchronous seam in front of {@link UemV2Publisher#publish}.
 * <p>
 * Mirrors the V1 path, where {@code JfbusEventingService.sendEvent} is {@code @Async}: the
 * {@link org.artifactory.unifiedevent.dispatchers.UemV2EventDispatcher} runs on the shared unified-event dispatch
 * pool (small, {@code CallerRunsPolicy}), so the broker/outbox round-trip is offloaded here to the async executor.
 * That keeps a slow broker from saturating the dispatch pool and leaking latency onto request/download threads.
 * <p>
 * {@code @Async} only takes effect through the Spring proxy, hence this dedicated bean rather than annotating
 * {@link UemV2PublisherImpl} (which is constructed directly in {@code UemV2PublisherConfiguration}) or the
 * dispatcher (self-invocation would not be advised). When UEM v2 is disabled the injected {@link UemV2Publisher}
 * is the no-op fallback, so this delegates to nothing.
 */
@Component
public class UemV2AsyncPublisher {

    private final UemV2Publisher uemV2Publisher;

    public UemV2AsyncPublisher(UemV2Publisher uemV2Publisher) {
        this.uemV2Publisher = uemV2Publisher;
    }

    /**
     * Publishes the request off the calling thread. Fire-and-forget: {@link UemV2Publisher#publish} swallows broker
     * failures internally, so nothing is thrown back to the async executor.
     */
    @Async
    public void publish(UemV2PublishRequest request) {
        uemV2Publisher.publish(request);
    }
}
