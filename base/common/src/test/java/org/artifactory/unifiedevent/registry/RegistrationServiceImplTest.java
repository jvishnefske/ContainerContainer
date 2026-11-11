package org.artifactory.unifiedevent.registry;

import org.artifactory.exception.http.NotFoundException;
import org.artifactory.unifiedevent.constants.FilterCriteriaType;
import org.artifactory.unifiedevent.constants.SupportedConsumers;
import org.artifactory.unifiedevent.constants.SupportedOutbound;
import org.artifactory.unifiedevent.constants.SupportedUnifiedEvents;
import org.artifactory.unifiedevent.model.IncludeExclude;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RegistrationServiceImplTest {

    private ConsumerConfig consumerConfig;
    private RegistrationServiceImpl registrationService;

    @BeforeEach
    void setUp() {
        consumerConfig = mock(ConsumerConfig.class);
        registrationService = new RegistrationServiceImpl(consumerConfig);
    }

    @Test
    void testGetRegisteredConsumers() {
        Consumer mockedConsumer = mock(Consumer.class);
        when(mockedConsumer.name()).thenReturn(SupportedConsumers.WORKER_CLIENT);
        List<Consumer> consumers = List.of(mockedConsumer, mock(Consumer.class));
        SupportedUnifiedEvents event = mock(SupportedUnifiedEvents.class);
        when(consumerConfig.getConsumersForAction(event)).thenReturn(consumers);
        Consumer consumer = registrationService.getConsumerRegistration(event, SupportedConsumers.WORKER_CLIENT);
        assertEquals(consumer, mockedConsumer);
    }

    @Test
    void testGetRegisteredConsumers_NotFound() {
        Consumer mockedConsumer = mock(Consumer.class);
        when(mockedConsumer.name()).thenReturn(SupportedConsumers.WORKER);
        List<Consumer> consumers = List.of(mockedConsumer);
        SupportedUnifiedEvents event = mock(SupportedUnifiedEvents.class);
        when(consumerConfig.getConsumersForAction(event)).thenReturn(consumers);
        assertThrows(NotFoundException.class,
                () -> registrationService.getConsumerRegistration(event, SupportedConsumers.WORKER_CLIENT));
    }

    @Test
    void testUnregisterConsumer() {
        Consumer mockedConsumer = mock(Consumer.class);
        when(mockedConsumer.name()).thenReturn(SupportedConsumers.WORKER_CLIENT);
        List<Consumer> consumers = List.of(mockedConsumer, mock(Consumer.class));
        SupportedUnifiedEvents event = mock(SupportedUnifiedEvents.class);
        when(consumerConfig.getConsumersForAction(event)).thenReturn(consumers);
        doNothing().when(consumerConfig).removeConsumersForAction(event, mockedConsumer);
        registrationService.unregisterConsumer(event, SupportedConsumers.WORKER_CLIENT);
        verify(consumerConfig).removeConsumersForAction(event, mockedConsumer);
    }

    @Test
    void testUnregisterConsumer_NotFound() {
        Consumer mockedConsumer = mock(Consumer.class);
        when(mockedConsumer.name()).thenReturn(SupportedConsumers.WORKER);
        List<Consumer> consumers = List.of(mockedConsumer);
        SupportedUnifiedEvents event = mock(SupportedUnifiedEvents.class);
        when(consumerConfig.getConsumersForAction(event)).thenReturn(consumers);
        assertThrows(NotFoundException.class,
                () -> registrationService.unregisterConsumer(event, SupportedConsumers.WORKER_CLIENT));
    }

    // Below Unit Test are written by AI, will revisit to increase the branch coverage
    @Test
    void registerConsumer_shouldCreateNewConsumer_whenConsumerDoesNotExist() {
        SupportedUnifiedEvents event = SupportedUnifiedEvents.AFTER_CREATE;
        SupportedConsumers consumerName = SupportedConsumers.WORKER;
        String url = "http://example.com/webhook";
        Map<FilterCriteriaType, IncludeExclude> filterCriteria = new EnumMap<>(FilterCriteriaType.class);
        filterCriteria.put(FilterCriteriaType.REPO_KEY,
                IncludeExclude.builder().include(List.of("repo1", "repo2")).build());
        Map<SupportedUnifiedEvents, Map<FilterCriteriaType, IncludeExclude>> filterCriteriaMap = Map.of(
                event, filterCriteria);

        when(consumerConfig.getConsumersForAction(event)).thenReturn(List.of());

        registrationService.registerConsumer(consumerName, url, true, filterCriteriaMap);

        verify(consumerConfig).getConsumersForAction(event);
        verify(consumerConfig, never()).removeConsumersForAction(any(), any());
        verify(consumerConfig).setConsumerForAction(eq(event), argThat(consumer ->
                consumer.name() == consumerName
                        && consumer.endpoint().equals(url)
                        && consumer.outbound() == SupportedOutbound.HTTP
                        && consumer.filter().equals(filterCriteria)
        ), eq(false));
    }

    @Test
    void registerConsumer_shouldReplaceExistingConsumer_whenOverrideFilterIsTrue() {
        SupportedUnifiedEvents event = SupportedUnifiedEvents.AFTER_DELETE;
        SupportedConsumers consumerName = SupportedConsumers.WORKER;
        String existingUrl = "http://old-url.com";
        String newUrl = "http://new-url.com";
        Map<FilterCriteriaType, IncludeExclude> existingFilters = new EnumMap<>(FilterCriteriaType.class);
        existingFilters.put(FilterCriteriaType.REPO_KEY,
                IncludeExclude.builder().include(List.of("old-repo")).build());
        Map<FilterCriteriaType, IncludeExclude> newFilters = new EnumMap<>(FilterCriteriaType.class);
        newFilters.put(FilterCriteriaType.REPO_KEY,
                IncludeExclude.builder().include(List.of("new-repo")).build());
        Map<SupportedUnifiedEvents, Map<FilterCriteriaType, IncludeExclude>> filterCriteriaMap = Map.of(
                event, newFilters);

        Consumer existingConsumer = Consumer.builder()
                .name(consumerName)
                .endpoint(existingUrl)
                .outbound(SupportedOutbound.HTTP)
                .filter(existingFilters)
                .build();
        when(consumerConfig.getConsumersForAction(event)).thenReturn(List.of(existingConsumer));

        registrationService.registerConsumer(consumerName, newUrl, true, filterCriteriaMap);

        verify(consumerConfig).getConsumersForAction(event);
        verify(consumerConfig).removeConsumersForAction(event, existingConsumer);
        verify(consumerConfig).setConsumerForAction(eq(event), argThat(consumer ->
                consumer.name() == consumerName
                        && consumer.endpoint().equals(newUrl)
                        && consumer.filter().equals(newFilters)
        ), eq(false));
    }

    @Test
    void registerConsumer_shouldMergeFilters_whenOverrideFilterIsFalse() {
        SupportedUnifiedEvents event = SupportedUnifiedEvents.AFTER_DOWNLOAD;
        SupportedConsumers consumerName = SupportedConsumers.WORKER;
        String url = "http://example.com/webhook";
        Map<FilterCriteriaType, IncludeExclude> existingFilters = new EnumMap<>(FilterCriteriaType.class);
        existingFilters.put(FilterCriteriaType.REPO_KEY,
                IncludeExclude.builder()
                        .include(List.of("repo1", "repo2"))
                        .exclude(List.of("excluded-repo"))
                        .build());
        Map<FilterCriteriaType, IncludeExclude> incomingFilters = new EnumMap<>(FilterCriteriaType.class);
        incomingFilters.put(FilterCriteriaType.REPO_KEY,
                IncludeExclude.builder()
                        .include(List.of("repo2", "repo3"))
                        .exclude(List.of("another-excluded"))
                        .build());
        Map<SupportedUnifiedEvents, Map<FilterCriteriaType, IncludeExclude>> filterCriteriaMap = Map.of(
                event, incomingFilters);

        Consumer existingConsumer = Consumer.builder()
                .name(consumerName)
                .endpoint(url)
                .outbound(SupportedOutbound.HTTP)
                .filter(existingFilters)
                .build();
        when(consumerConfig.getConsumersForAction(event)).thenReturn(List.of(existingConsumer));

        registrationService.registerConsumer(consumerName, url, false, filterCriteriaMap);

        verify(consumerConfig).getConsumersForAction(event);
        verify(consumerConfig).removeConsumersForAction(event, existingConsumer);
        verify(consumerConfig).setConsumerForAction(eq(event), argThat(consumer -> {
            Map<FilterCriteriaType, IncludeExclude> mergedFilter = consumer.filter();
            IncludeExclude mergedIncludeExclude = mergedFilter.get(FilterCriteriaType.REPO_KEY);
            return consumer.name() == consumerName
                    && mergedIncludeExclude.include().containsAll(List.of("repo1", "repo2", "repo3"))
                    && mergedIncludeExclude.include().size() == 3
                    && mergedIncludeExclude.exclude().containsAll(List.of("excluded-repo", "another-excluded"))
                    && mergedIncludeExclude.exclude().size() == 2;
        }), eq(false));
    }

    @Test
    void registerConsumer_shouldProcessMultipleEvents_whenFilterCriteriaContainsMultipleEvents() {
        SupportedUnifiedEvents event1 = SupportedUnifiedEvents.AFTER_CREATE;
        SupportedUnifiedEvents event2 = SupportedUnifiedEvents.AFTER_DELETE;
        SupportedConsumers consumerName = SupportedConsumers.WORKER;
        String url = "http://example.com/webhook";
        Map<FilterCriteriaType, IncludeExclude> filters1 = new EnumMap<>(FilterCriteriaType.class);
        filters1.put(FilterCriteriaType.REPO_KEY,
                IncludeExclude.builder().include(List.of("repo1")).build());
        Map<FilterCriteriaType, IncludeExclude> filters2 = new EnumMap<>(FilterCriteriaType.class);
        filters2.put(FilterCriteriaType.REPO_TYPE,
                IncludeExclude.builder().include(List.of("maven")).build());
        Map<SupportedUnifiedEvents, Map<FilterCriteriaType, IncludeExclude>> filterCriteriaMap = Map.of(
                event1, filters1,
                event2, filters2);

        when(consumerConfig.getConsumersForAction(event1)).thenReturn(List.of());
        when(consumerConfig.getConsumersForAction(event2)).thenReturn(List.of());

        registrationService.registerConsumer(consumerName, url, true, filterCriteriaMap);

        verify(consumerConfig).getConsumersForAction(event1);
        verify(consumerConfig).getConsumersForAction(event2);
        verify(consumerConfig, times(2)).setConsumerForAction(any(), any(), eq(false));
    }

    @Test
    void registerConsumer_shouldUpdateMultipleEvents_whenConsumerExistsForMultipleEvents() {
        SupportedUnifiedEvents event1 = SupportedUnifiedEvents.AFTER_CREATE;
        SupportedUnifiedEvents event2 = SupportedUnifiedEvents.AFTER_DELETE;
        SupportedConsumers consumerName = SupportedConsumers.WORKER;
        String url = "http://example.com/webhook";
        Map<FilterCriteriaType, IncludeExclude> existingFilters1 = new EnumMap<>(FilterCriteriaType.class);
        existingFilters1.put(FilterCriteriaType.REPO_KEY,
                IncludeExclude.builder().include(List.of("old-repo1")).build());
        Map<FilterCriteriaType, IncludeExclude> existingFilters2 = new EnumMap<>(FilterCriteriaType.class);
        existingFilters2.put(FilterCriteriaType.REPO_KEY,
                IncludeExclude.builder().include(List.of("old-repo2")).build());
        Map<FilterCriteriaType, IncludeExclude> newFilters1 = new EnumMap<>(FilterCriteriaType.class);
        newFilters1.put(FilterCriteriaType.REPO_KEY,
                IncludeExclude.builder().include(List.of("new-repo1")).build());
        Map<FilterCriteriaType, IncludeExclude> newFilters2 = new EnumMap<>(FilterCriteriaType.class);
        newFilters2.put(FilterCriteriaType.REPO_KEY,
                IncludeExclude.builder().include(List.of("new-repo2")).build());
        Map<SupportedUnifiedEvents, Map<FilterCriteriaType, IncludeExclude>> filterCriteriaMap = Map.of(
                event1, newFilters1,
                event2, newFilters2);

        Consumer existingConsumer1 = Consumer.builder()
                .name(consumerName)
                .endpoint(url)
                .outbound(SupportedOutbound.HTTP)
                .filter(existingFilters1)
                .build();
        Consumer existingConsumer2 = Consumer.builder()
                .name(consumerName)
                .endpoint(url)
                .outbound(SupportedOutbound.HTTP)
                .filter(existingFilters2)
                .build();
        when(consumerConfig.getConsumersForAction(event1)).thenReturn(List.of(existingConsumer1));
        when(consumerConfig.getConsumersForAction(event2)).thenReturn(List.of(existingConsumer2));

        registrationService.registerConsumer(consumerName, url, true, filterCriteriaMap);

        verify(consumerConfig).getConsumersForAction(event1);
        verify(consumerConfig).getConsumersForAction(event2);
        verify(consumerConfig).removeConsumersForAction(event1, existingConsumer1);
        verify(consumerConfig).removeConsumersForAction(event2, existingConsumer2);
        verify(consumerConfig).setConsumerForAction(eq(event1), argThat(consumer ->
                consumer.filter().equals(newFilters1)
        ), eq(false));
        verify(consumerConfig).setConsumerForAction(eq(event2), argThat(consumer ->
                consumer.filter().equals(newFilters2)
        ), eq(false));
    }

    @Test
    void registerConsumer_shouldKeepExistingEndpoint_whenUrlIsNull() {
        SupportedUnifiedEvents event = SupportedUnifiedEvents.AFTER_CREATE;
        SupportedConsumers consumerName = SupportedConsumers.WORKER;
        String existingUrl = "http://existing-url.com";
        Map<FilterCriteriaType, IncludeExclude> existingFilters = new EnumMap<>(FilterCriteriaType.class);
        existingFilters.put(FilterCriteriaType.REPO_KEY,
                IncludeExclude.builder().include(List.of("old-repo")).build());
        Map<FilterCriteriaType, IncludeExclude> newFilters = new EnumMap<>(FilterCriteriaType.class);
        newFilters.put(FilterCriteriaType.REPO_KEY,
                IncludeExclude.builder().include(List.of("new-repo")).build());
        Map<SupportedUnifiedEvents, Map<FilterCriteriaType, IncludeExclude>> filterCriteriaMap = Map.of(
                event, newFilters);

        Consumer existingConsumer = Consumer.builder()
                .name(consumerName)
                .endpoint(existingUrl)
                .outbound(SupportedOutbound.HTTP)
                .filter(existingFilters)
                .build();
        when(consumerConfig.getConsumersForAction(event)).thenReturn(List.of(existingConsumer));

        registrationService.registerConsumer(consumerName, null, true, filterCriteriaMap);

        verify(consumerConfig).getConsumersForAction(event);
        verify(consumerConfig).removeConsumersForAction(event, existingConsumer);
        verify(consumerConfig).setConsumerForAction(eq(event), argThat(consumer ->
                consumer.name() == consumerName
                        && consumer.endpoint().equals(existingUrl)
                        && consumer.filter().equals(newFilters)
        ), eq(false));
    }

    @Test
    void appendConsumerFilter_shouldReturnIncomingCriteria_whenExistingFiltersIsNull() {
        Map<FilterCriteriaType, IncludeExclude> incomingCriteria = new EnumMap<>(FilterCriteriaType.class);
        incomingCriteria.put(FilterCriteriaType.REPO_KEY,
                IncludeExclude.builder().include(List.of("repo1")).build());

        Map<FilterCriteriaType, IncludeExclude> result =
                registrationService.appendConsumerFilter(null, incomingCriteria);

        assertEquals(incomingCriteria, result);
    }

    @Test
    void appendConsumerFilter_shouldReturnExistingFilters_whenIncomingCriteriaIsNull() {
        Map<FilterCriteriaType, IncludeExclude> existingFilters = new EnumMap<>(FilterCriteriaType.class);
        existingFilters.put(FilterCriteriaType.REPO_KEY,
                IncludeExclude.builder().include(List.of("repo1")).build());

        Map<FilterCriteriaType, IncludeExclude> result =
                registrationService.appendConsumerFilter(existingFilters, null);

        assertEquals(existingFilters, result);
    }

}