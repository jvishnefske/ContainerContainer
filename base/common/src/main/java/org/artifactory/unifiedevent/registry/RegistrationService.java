package org.artifactory.unifiedevent.registry;

import org.artifactory.unifiedevent.constants.FilterCriteriaType;
import org.artifactory.unifiedevent.constants.SupportedConsumers;
import org.artifactory.unifiedevent.constants.SupportedUnifiedEvents;
import org.artifactory.unifiedevent.model.IncludeExclude;

import java.util.Map;

/**
 * Service interface for registering, updating, retrieving, and unregistering consumers
 * for SynEventDispatchers with specific event filter criteria.
 */
public interface RegistrationService {

    /**
     * Registers a new consumer for the specified events with filter criteria.
     *
     * @param consumer       the name of the consumer
     * @param url            the callback URL for the consumer
     * @param filterCriteria a map of event types to their filter criteria
     */
    void registerConsumer(SupportedConsumers consumer, String url, boolean overrideFilter,
            Map<SupportedUnifiedEvents, Map<FilterCriteriaType, IncludeExclude>> filterCriteria);

    /**
     * Unregisters a consumer from a specific event type.
     *
     * @param eventType the event type to unregister from
     * @param consumer  the name of the consumer to unregister
     */
    void unregisterConsumer(SupportedUnifiedEvents eventType, SupportedConsumers consumer);

    /**
     * Retrieves the registration details of a consumer for a specific event type.
     *
     * @param eventType the event type
     * @param consumer  the name of the consumer
     * @return an Optional containing the Consumer registration if present, otherwise empty
     */
    Consumer getConsumerRegistration(SupportedUnifiedEvents eventType, SupportedConsumers consumer);
}