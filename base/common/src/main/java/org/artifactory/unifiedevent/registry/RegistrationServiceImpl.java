package org.artifactory.unifiedevent.registry;

import lombok.AllArgsConstructor;
import org.artifactory.exception.http.NotFoundException;
import org.artifactory.unifiedevent.constants.FilterCriteriaType;
import org.artifactory.unifiedevent.constants.SupportedConsumers;
import org.artifactory.unifiedevent.constants.SupportedOutbound;
import org.artifactory.unifiedevent.constants.SupportedUnifiedEvents;
import org.artifactory.unifiedevent.model.IncludeExclude;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

@Service
@AllArgsConstructor
public class RegistrationServiceImpl implements RegistrationService {

    protected ConsumerConfig consumerConfig;

    @Override
    public void registerConsumer(SupportedConsumers consumerName, String url, boolean overrideFilter,
            Map<SupportedUnifiedEvents, Map<FilterCriteriaType, IncludeExclude>> filterCriteria) {
        filterCriteria.forEach((event, incomingCriteria) -> {
            Consumer updatedConsumer;
            Map<FilterCriteriaType, IncludeExclude> updatedFilters = incomingCriteria;
            try {
                Consumer eventConsumer = getConsumerRegistration(event, consumerName);
                updatedFilters = overrideFilter ? incomingCriteria
                        : appendConsumerFilter(eventConsumer.filter(), incomingCriteria);
                String endpoint = url;
                if (url == null) {
                    endpoint = eventConsumer.endpoint();
                }
                updatedConsumer = eventConsumer.toBuilder()
                        .endpoint(endpoint)
                        .filter(updatedFilters)
                        .build();
                consumerConfig.removeConsumersForAction(event, eventConsumer);
            } catch (NotFoundException e) {
                updatedConsumer = Consumer.builder()
                        .name(consumerName)
                        .endpoint(url)
                        .outbound(SupportedOutbound.HTTP)
                        .filter(updatedFilters)
                        .build();
            }
            consumerConfig.setConsumerForAction(event, updatedConsumer, false);
        });
    }

    @Override
    public void unregisterConsumer(SupportedUnifiedEvents eventType, SupportedConsumers name) {
        Consumer eventConsumer = getConsumerRegistration(eventType, name);
        consumerConfig.removeConsumersForAction(eventType, eventConsumer);
    }

    @Override
    public Consumer getConsumerRegistration(SupportedUnifiedEvents eventType,
            SupportedConsumers consumerName) {
        List<Consumer> eventConsumers = consumerConfig.getConsumersForAction(eventType);
        Optional<Consumer> eventConsumer = eventConsumers.stream()
                .filter(consumer -> consumer.name().equals(consumerName))
                .findFirst();
        if (eventConsumer.isEmpty()) {
            throw new NotFoundException("Consumer not found: " + consumerName);
        }
        return eventConsumer.get();
    }

    protected Map<FilterCriteriaType, IncludeExclude> appendConsumerFilter(
            Map<FilterCriteriaType, IncludeExclude> existingFilters,
            Map<FilterCriteriaType, IncludeExclude> incomingCriteria) {
        if (existingFilters == null) {
            return incomingCriteria;
        }
        if (incomingCriteria == null) {
            return existingFilters;
        }
        Map<FilterCriteriaType, IncludeExclude> mergedFilters = new EnumMap<>(existingFilters);
        incomingCriteria.forEach((filterType, incoming) -> {
            IncludeExclude existing = mergedFilters.getOrDefault(filterType,
                    IncludeExclude.builder().build());
            mergedFilters.put(filterType, IncludeExclude.builder()
                    .include(mergeListsAsSet(getOrEmpty(existing.include()), getOrEmpty(incoming.include())))
                    .exclude(mergeListsAsSet(getOrEmpty(existing.exclude()), getOrEmpty(incoming.exclude())))
                    .build());
        });
        return mergedFilters;
    }

    protected List<String> getOrEmpty(List<String> list) {
        return list != null ? list : new ArrayList<>();
    }

    protected List<String> mergeListsAsSet(List<String> list1, List<String> list2) {
        return Stream.concat(list1.stream(), list2.stream()).distinct().toList();
    }
}
