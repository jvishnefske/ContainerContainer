package org.artifactory.unifiedevent.mapper;

import org.artifactory.unifiedevent.UnifiedEventContextProvider;
import org.artifactory.unifiedevent.constants.SupportedUnifiedEvents;
import org.artifactory.unifiedevent.model.EventEnvelope;
import org.artifactory.unifiedevent.model.HeadersData;
import org.artifactory.unifiedevent.model.repo.RepoConfigEventData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RepoConfigEventEnvelopeMapperTest {

    @Mock
    private UnifiedEventContextProvider contextProvider;

    private RepoConfigEventEnvelopeMapper mapper;

    @BeforeEach
    void setUp() throws Exception {
        mapper = new RepoConfigEventEnvelopeMapperImpl();
        setContextProvider(mapper, contextProvider);
    }

    @Test
    void toRepoConfigEventEnvelopeV1_mapsEnvelopeCorrectly() {
        RepoConfigEventData eventData = RepoConfigEventData.builder()
                .context(new RepoConfigEventData.RepositoryEventContext("admin"))
                .metadata(new RepoConfigEventData.RepositoryMetadata("libs-release-local", "local",
                        "generic", false, null, false, false, List.of()))
                .build();

        when(contextProvider.generateEventId()).thenReturn("repo-event-1");
        when(contextProvider.buildSourceHeader()).thenReturn("artifactory://svc@node1");
        when(contextProvider.getCurrentTimestamp()).thenReturn("2025-01-01T00:00:00Z");
        when(contextProvider.getDataSchema(SupportedUnifiedEvents.AFTER_REPO_CREATE)).thenReturn("afterRepoCreate");
        when(contextProvider.getEventHeaders()).thenReturn(mock(HeadersData.class));

        EventEnvelope<RepoConfigEventData> envelope = mapper.toRepoConfigEventEnvelopeV1(
                SupportedUnifiedEvents.AFTER_REPO_CREATE, eventData, "libs-release-local");

        assertNotNull(envelope);
        assertEquals("1.0", envelope.specVersion());
        assertEquals("repo-event-1", envelope.id());
        assertEquals(SupportedUnifiedEvents.AFTER_REPO_CREATE, envelope.type());
        assertEquals("artifactory://svc@node1", envelope.source());
        assertEquals("2025-01-01T00:00:00Z", envelope.time());
        assertEquals("libs-release-local", envelope.subject());
        assertEquals("application/json", envelope.dataContentType());
        assertEquals("afterRepoCreate", envelope.dataSchema());
        assertEquals(eventData, envelope.data());
    }

    private static void setContextProvider(RepoConfigEventEnvelopeMapper mapperInstance,
            UnifiedEventContextProvider provider) throws Exception {
        Field field = RepoConfigEventEnvelopeMapper.class.getDeclaredField("contextProvider");
        field.setAccessible(true);
        field.set(mapperInstance, provider);
    }
}
