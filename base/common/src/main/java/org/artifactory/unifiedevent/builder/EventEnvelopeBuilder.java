package org.artifactory.unifiedevent.builder;

import lombok.experimental.UtilityClass;
import org.artifactory.unifiedevent.UnifiedEventContextProvider;
import org.artifactory.unifiedevent.constants.SupportedUnifiedEvents;
import org.artifactory.unifiedevent.model.EventEnvelope;
import org.springframework.http.MediaType;

import static org.artifactory.common.home.ArtifactoryHome.ARTIFACTORY_SERVICE_NAME;

@UtilityClass
public class EventEnvelopeBuilder {

    private static final String SPEC_VERSION = "1.0";
    private static final String EVENT_SOURCE = ARTIFACTORY_SERVICE_NAME;
    private static final String DATA_CONTENT_TYPE = MediaType.APPLICATION_JSON_VALUE;

    public static <T> EventEnvelope<T> populateEventEnvelopeData(T eventData,
            String subject,
            UnifiedEventContextProvider contextProvider,
            SupportedUnifiedEvents eventType) {
        return EventEnvelope.<T>builder()
                .specVersion(SPEC_VERSION)
                .id(contextProvider.generateEventId())
                .type(eventType)
                .subject(subject)
                .data(eventData)
                .source(EVENT_SOURCE)
                .time(contextProvider.getCurrentTimestamp())
                .dataSchema(contextProvider.getDataSchema(eventType))
                .dataContentType(DATA_CONTENT_TYPE)
                .eventHeaders(contextProvider.getEventHeaders())
                .build();
    }
}

