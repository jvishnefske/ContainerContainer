package org.artifactory.unifiedevent.builder;

import org.artifactory.unifiedevent.UnifiedEventContextProvider;
import org.artifactory.unifiedevent.constants.SupportedUnifiedEvents;
import org.artifactory.unifiedevent.model.EventEnvelope;
import org.artifactory.unifiedevent.model.HeadersData;
import org.artifactory.unifiedevent.model.artifact.ArtifactEventData;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EventEnvelopeBuilderTest {

    @Test
    void testBuildEventEnvelope() {
        UnifiedEventContextProvider contextProvider = mock(UnifiedEventContextProvider.class);
        ArtifactEventData downloadData = mock(ArtifactEventData.class);
        SupportedUnifiedEvents eventType = SupportedUnifiedEvents.BEFORE_DOWNLOAD;
        String subject = "test-repo/test/path";

        HeadersData headersData = HeadersData.builder().build();

        when(contextProvider.generateEventId()).thenReturn("test-event-id-12345");
        when(contextProvider.getCurrentTimestamp()).thenReturn("2025-11-26T10:30:00.123Z");
        when(contextProvider.getDataSchema(eventType)).thenReturn("beforeDownload");
        when(contextProvider.getEventHeaders()).thenReturn(headersData);

        EventEnvelope<ArtifactEventData> result = EventEnvelopeBuilder.populateEventEnvelopeData(downloadData,
                subject, contextProvider, eventType);

        assertNotNull(result);
        assertEquals("1.0", result.specVersion());
        assertEquals("test-event-id-12345", result.id());
        assertEquals(SupportedUnifiedEvents.BEFORE_DOWNLOAD, result.type());
        assertEquals("artifactory", result.source());
        assertEquals("2025-11-26T10:30:00.123Z", result.time());
        assertEquals("test-repo/test/path", result.subject());
        assertEquals("beforeDownload", result.dataSchema());
        assertEquals("application/json", result.dataContentType());
        assertEquals(headersData, result.eventHeaders());
        assertEquals(downloadData, result.data());
    }
}

