package org.artifactory.jfbus;

public interface UemV2Publisher {

    /**
     * Publishes a UEM v2 JFBus message. No-op when the feature flag
     * ({@link org.artifactory.common.ConstantValues#uemV2PublisherEnabled}) is disabled or JFBus
     * is not configured. Broker exceptions are logged and swallowed; callers do not need to
     * handle failures for fire-and-forget events.
     */
    void publish(UemV2PublishRequest request);

    /**
     * Registers the broker topic for {@code entityType} at startup.
     * Delegates to {@link JfbusEventingService#createTopic} with {@code skipIfExists=true}.
     * No-op when JFBus is not configured.
     */
    void createTopic(String entityType, int partitions);
}
