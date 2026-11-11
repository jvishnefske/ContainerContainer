package org.artifactory.unifiedevent;

import org.artifactory.addon.replication.ReplicationTargetInfo;
import org.artifactory.exception.UnifiedEventException;
import org.artifactory.fs.ItemInfo;
import org.artifactory.md.Properties;
import org.artifactory.properties.PropertiesImpl;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.repo.RepoPath;
import org.artifactory.request.ArtifactoryRequest;
import org.artifactory.sapi.fs.VfsItem;
import org.artifactory.unifiedevent.api.request.ArtifactResourceContext;
import org.artifactory.unifiedevent.constants.SupportedUnifiedEvents;
import org.artifactory.unifiedevent.mapper.ArtifactDataMapper;
import org.artifactory.unifiedevent.mapper.RepoConfigEventEnvelopeMapper;
import org.artifactory.unifiedevent.model.DispatcherResponse;
import org.artifactory.unifiedevent.model.EventEnvelope;
import org.artifactory.unifiedevent.model.HeadersData;
import org.artifactory.unifiedevent.model.ResponseContextData;
import org.artifactory.unifiedevent.model.UserContextData;
import org.artifactory.unifiedevent.model.artifact.ArtifactData;
import org.artifactory.unifiedevent.model.artifact.ArtifactEventData;
import org.artifactory.unifiedevent.model.repo.RepoConfigEventData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UnifiedEventServiceImplTest {

    @Mock
    private EventDispatcherHandler handler;

    @Mock
    private UnifiedEventContextProvider contextProvider;

    @Mock
    private ArtifactDataMapper artifactDataMapper;

    @Mock
    private RepoConfigEventEnvelopeMapper repoConfigEventEnvelopeMapper;

    @InjectMocks
    private UnifiedEventServiceImpl unifiedEventService;

    private static Stream<Arguments> sendArtifactEventSource() {
        return Stream.of(
                Arguments.of(List.of(DispatcherResponse.builder().shouldProceed(true).build()), true,
                        SupportedUnifiedEvents.BEFORE_DOWNLOAD),
                Arguments.of(List.of(DispatcherResponse.builder().shouldProceed(true).data(null).build()),
                        true, SupportedUnifiedEvents.BEFORE_REMOTE_DOWNLOAD),
                Arguments.of(List.of(DispatcherResponse.builder().shouldProceed(true).data(
                                ResponseContextData.builder().headers(null).build()).build()), true,
                        SupportedUnifiedEvents.BEFORE_REMOTE_INFO),
                Arguments.of(List.of(DispatcherResponse.builder().shouldProceed(true).data(
                                ResponseContextData.builder().headers(new HashMap<>()).build()).build()), true,
                        SupportedUnifiedEvents.BEFORE_DOWNLOAD),
                Arguments.of(List.of(DispatcherResponse.builder().shouldProceed(false).build()), false,
                        SupportedUnifiedEvents.BEFORE_REMOTE_DOWNLOAD),
                Arguments.of(null, true, SupportedUnifiedEvents.BEFORE_DOWNLOAD)
        );
    }

    @ParameterizedTest
    @MethodSource("sendArtifactEventSource")
    void testSendDownloadEvent(List<DispatcherResponse> responses, boolean shouldProceed,
            SupportedUnifiedEvents eventType) {
        ArtifactoryRequest request = mock(ArtifactoryRequest.class);
        when(request.getRepoKey()).thenReturn("repo-key");
        when(request.getLastModified()).thenReturn(1L);
        when(request.getPath()).thenReturn("repo-path");

        RepoPath repoPath = mock(RepoPath.class);

        // Mock contextProvider methods needed by ArtifactUnifiedEventBuilder
        when(contextProvider.getUserContext()).thenReturn(mock(UserContextData.class));
        when(contextProvider.getRepoType(any())).thenReturn(RepoDetailsType.LOCAL);
        when(contextProvider.generateEventId()).thenReturn("test-id");
        when(contextProvider.getCurrentTimestamp()).thenReturn("2025-01-01T00:00:00Z");
        when(contextProvider.getDataSchema(any())).thenReturn(eventType.getId());
        when(contextProvider.getEventHeaders()).thenReturn(mock(HeadersData.class));
        when(contextProvider.getSubjectForArtifactData(any(ArtifactData.class)))
                .thenReturn("repo-key/repo-path");

        when(handler.dispatchEvent(any())).thenReturn(responses);
        if (shouldProceed) {
            DispatcherResponse response =
                    unifiedEventService.sendArtifactEvent(SupportedUnifiedEvents.BEFORE_DOWNLOAD, request, repoPath);
            assertTrue(response.shouldProceed());
        } else {
            assertThrowsExactly(UnifiedEventException.class,
                    () -> unifiedEventService.sendArtifactEvent(SupportedUnifiedEvents.BEFORE_DOWNLOAD, request,
                            repoPath));
        }
    }


    private static Stream<Arguments> sendStorageEventSource() {
        return Stream.of(
                Arguments.of(List.of(DispatcherResponse.builder().shouldProceed(true).build()), true,
                        SupportedUnifiedEvents.BEFORE_CREATE),
                Arguments.of(List.of(DispatcherResponse.builder().shouldProceed(false).build()), false,
                        SupportedUnifiedEvents.BEFORE_CREATE),
                Arguments.of(null, true, SupportedUnifiedEvents.BEFORE_CREATE)
        );
    }

    @ParameterizedTest
    @MethodSource("sendStorageEventSource")
    void testSendStorageEvent(List<DispatcherResponse> responses, boolean shouldProceed,
            SupportedUnifiedEvents eventType) {
        VfsItem<? extends ItemInfo> item = mock(VfsItem.class);
        RepoPath repoPath = mock(RepoPath.class);
        ItemInfo itemInfo = mock(ItemInfo.class);
        when(contextProvider.getUserContext()).thenReturn(mock(UserContextData.class));
        when(contextProvider.getRepoType(any())).thenReturn(RepoDetailsType.LOCAL);
        when(contextProvider.generateEventId()).thenReturn("test-id");
        when(contextProvider.getCurrentTimestamp()).thenReturn("2025-01-01T00:00:00Z");
        when(contextProvider.getDataSchema(any())).thenReturn("beforeDownload");
        when(contextProvider.getEventHeaders()).thenReturn(mock(HeadersData.class));
        when(contextProvider.getSubjectForArtifactData(any(ArtifactData.class)))
                .thenReturn("repo-key/repo-path");
        Properties properties = new PropertiesImpl();
        properties.put("key", "value");
        properties.put("key2", "value2");
        when(item.getRepoKey()).thenReturn("repo-key");
        when(item.getCreated()).thenReturn(123L);
        when(item.getRepoPath()).thenReturn(repoPath);
        when(repoPath.getPath()).thenReturn("repo-path");
        when(repoPath.isFolder()).thenReturn(true);
        when(itemInfo.getLastModified()).thenReturn(456L);
        when(itemInfo.getCreatedBy()).thenReturn("unit-test");
        when(item.getInfo()).thenReturn(itemInfo);
        when(item.getProperties()).thenReturn(properties);
        when(handler.dispatchEvent(any())).thenReturn(responses);

        if (shouldProceed) {
            DispatcherResponse response = unifiedEventService.sendStorageEvent(eventType, item, null, null);
            assertTrue(response.shouldProceed());
        } else {
            assertThrowsExactly(UnifiedEventException.class,
                    () -> unifiedEventService.sendStorageEvent(eventType, item, null, null));
        }
    }

    @ParameterizedTest
    @MethodSource("sendPropertyEventSource")
    void testSendPropertyEvent(List<DispatcherResponse> responses, boolean shouldProceed,
            SupportedUnifiedEvents eventType) {
        VfsItem<? extends ItemInfo> item = mock(VfsItem.class);
        RepoPath repoPath = mock(RepoPath.class);
        ItemInfo itemInfo = mock(ItemInfo.class);
        Properties properties = new PropertiesImpl();
        when(contextProvider.getUserContext()).thenReturn(mock(UserContextData.class));
        when(contextProvider.getRepoType(any())).thenReturn(RepoDetailsType.LOCAL);
        when(contextProvider.generateEventId()).thenReturn("test-id");
        when(contextProvider.getCurrentTimestamp()).thenReturn("2025-01-01T00:00:00Z");
        when(contextProvider.getDataSchema(any())).thenReturn(eventType.getId());
        when(contextProvider.getEventHeaders()).thenReturn(mock(HeadersData.class));
        when(contextProvider.getSubjectForArtifactData(any(ArtifactData.class)))
                .thenReturn("repo-key/repo-path");
        properties.put("key ", "value1");
        properties.put("key2", "value2");
        when(item.getRepoKey()).thenReturn("repo-key");
        when(item.getCreated()).thenReturn(123L);
        when(item.getProperties()).thenReturn(properties);
        when(item.getInfo()).thenReturn(itemInfo);
        when(itemInfo.getLastModified()).thenReturn(1L);
        when(itemInfo.getCreatedBy()).thenReturn("tester");
        when(item.getRepoPath()).thenReturn(repoPath);
        when(repoPath.getPath()).thenReturn("repo-path");
        when(repoPath.isFolder()).thenReturn(true);
        when(handler.dispatchEvent(any())).thenReturn(responses);
        if (shouldProceed) {
            unifiedEventService.sendPropertyEvent(eventType,
                    item, "prop", List.of("value1", "value2"));
        } else {
            assertThrowsExactly(UnifiedEventException.class,
                    () -> unifiedEventService.sendPropertyEvent(eventType,
                            item, "prop", null));
        }
    }

    private static Stream<Arguments> sendPropertyEventSource() {
        return Stream.of(
                Arguments.of(List.of(DispatcherResponse.builder().shouldProceed(true).build()), true,
                        SupportedUnifiedEvents.BEFORE_PROPERTY_CREATE),
                Arguments.of(List.of(DispatcherResponse.builder().shouldProceed(false).build()), false,
                        SupportedUnifiedEvents.BEFORE_PROPERTY_DELETE),
                Arguments.of(null, true, SupportedUnifiedEvents.BEFORE_PROPERTY_CREATE)
        );
    }

    @Test
    void testSendReplicationEvent() {
        RepoPath repoPath = mock(RepoPath.class);
        ReplicationTargetInfo targetInfo = mock(ReplicationTargetInfo.class);
        UserContextData userContextData = mock(UserContextData.class);
        ArgumentCaptor<EventEnvelope<ArtifactEventData>> eventCaptor = ArgumentCaptor.forClass(EventEnvelope.class);

        when(repoPath.getRepoKey()).thenReturn("libs-release-local");
        when(repoPath.getPath()).thenReturn("com/acme/app/1.0/app-1.0.jar");
        when(repoPath.isFolder()).thenReturn(false);

        when(contextProvider.getRepoType("libs-release-local")).thenReturn(RepoDetailsType.LOCAL);
        when(contextProvider.getUserContext()).thenReturn(userContextData);
        when(contextProvider.getSubjectForArtifactData(any(ArtifactData.class)))
                .thenReturn("libs-release-local/com/acme/app/1.0/app-1.0.jar");
        when(contextProvider.getEventHeaders()).thenReturn(mock(HeadersData.class));
        when(contextProvider.generateEventId()).thenReturn("event-123");
        when(contextProvider.getCurrentTimestamp()).thenReturn("2025-01-01T00:00:00Z");
        when(contextProvider.getDataSchema(SupportedUnifiedEvents.BEFORE_FILE_REPLICATION))
                .thenReturn("artifact-replication");

        when(handler.dispatchEvent(any())).thenReturn(
                List.of(DispatcherResponse.builder().shouldProceed(true).build()));

        DispatcherResponse response = unifiedEventService.sendReplicationEvent(
                SupportedUnifiedEvents.BEFORE_FILE_REPLICATION, repoPath, targetInfo);

        assertTrue(response.shouldProceed());
        verify(handler).dispatchEvent(eventCaptor.capture());

        EventEnvelope<ArtifactEventData> capturedEvent = eventCaptor.getValue();
        assertEquals(SupportedUnifiedEvents.BEFORE_FILE_REPLICATION, capturedEvent.type());
        assertEquals("event-123", capturedEvent.id());
        assertEquals("libs-release-local/com/acme/app/1.0/app-1.0.jar", capturedEvent.subject());
        assertEquals("artifact-replication", capturedEvent.dataSchema());

        ArtifactEventData eventData = capturedEvent.data();
        assertNotNull(eventData);
        assertEquals(targetInfo, eventData.targetInfo());
        assertEquals(userContextData, eventData.context());

        ArtifactData artifact = eventData.artifact();
        assertNotNull(artifact);
        assertEquals("libs-release-local", artifact.repoKey());
        assertEquals("com/acme/app/1.0/app-1.0.jar", artifact.path());
        assertEquals(RepoDetailsType.LOCAL, artifact.repoType());

        verify(contextProvider).getRepoType("libs-release-local");
        verify(contextProvider).getUserContext();
        verify(contextProvider).getSubjectForArtifactData(any(ArtifactData.class));
        verify(contextProvider).getEventHeaders();
        verify(contextProvider).generateEventId();
        verify(contextProvider).getCurrentTimestamp();
        verify(contextProvider).getDataSchema(SupportedUnifiedEvents.BEFORE_FILE_REPLICATION);
    }

    /**
     * T9 / R8: TR1 requires AFTER repo CUD events to be produced through {@link UnifiedEventService}, fanning out via
     * {@link EventDispatcherHandler} to the Worker outbound and the JFBus async dispatcher, rather than through bespoke
     * Pro Worker handlers. The repo-specific, already-masked payload is built by the Pro caller as the OSS event-seam
     * type {@link RepoConfigEventData} (distinct from the Pro Worker request body) and passed with an explicit subject
     * (the repo key). This asserts {@code sendRepoConfigEvent} wraps that payload in a canonical repo envelope
     * (type = AFTER_REPO_*, subject = repo key, data-only body) and dispatches it through the handler.
     */
    @Test
    void sendRepoConfigEvent_dispatchesCanonicalRepoEnvelopeThroughHandler_whenRepoCreated() {
        RepoConfigEventData repoPayload = RepoConfigEventData.builder()
                .context(new RepoConfigEventData.RepositoryEventContext("admin"))
                .metadata(new RepoConfigEventData.RepositoryMetadata("libs-release-local", "local",
                        "generic", false, null, false, false, List.of()))
                .build();
        EventEnvelope<RepoConfigEventData> envelope = EventEnvelope.<RepoConfigEventData>builder()
                .type(SupportedUnifiedEvents.AFTER_REPO_CREATE)
                .subject("libs-release-local")
                .data(repoPayload)
                .build();

        when(repoConfigEventEnvelopeMapper.toRepoConfigEventEnvelopeV1(
                SupportedUnifiedEvents.AFTER_REPO_CREATE, repoPayload, "libs-release-local"))
                .thenReturn(envelope);
        when(handler.dispatchEvent(any()))
                .thenReturn(List.of(DispatcherResponse.builder().shouldProceed(true).build()));

        DispatcherResponse response = unifiedEventService.sendRepoConfigEvent(
                SupportedUnifiedEvents.AFTER_REPO_CREATE, repoPayload, "libs-release-local");

        assertTrue(response.shouldProceed());
        verify(repoConfigEventEnvelopeMapper).toRepoConfigEventEnvelopeV1(
                SupportedUnifiedEvents.AFTER_REPO_CREATE, repoPayload, "libs-release-local");
        ArgumentCaptor<EventEnvelope<?>> eventCaptor = ArgumentCaptor.forClass(EventEnvelope.class);
        verify(handler).dispatchEvent(eventCaptor.capture());
        EventEnvelope<?> dispatched = eventCaptor.getValue();
        assertEquals(SupportedUnifiedEvents.AFTER_REPO_CREATE, dispatched.type());
        assertEquals("libs-release-local", dispatched.subject());
        assertEquals(repoPayload, dispatched.data());
    }

    private static Stream<Arguments> sendRemoteArtifactEventSource() {
        return Stream.of(
                Arguments.of(List.of(DispatcherResponse.builder().shouldProceed(true).build()), true,
                        SupportedUnifiedEvents.BEFORE_REMOTE_DOWNLOAD),
                Arguments.of(List.of(DispatcherResponse.builder().shouldProceed(true).data(null).build()),
                        true, SupportedUnifiedEvents.BEFORE_REMOTE_INFO),
                Arguments.of(List.of(DispatcherResponse.builder().shouldProceed(false).build()), false,
                        SupportedUnifiedEvents.BEFORE_REMOTE_DOWNLOAD),
                Arguments.of(null, true, SupportedUnifiedEvents.BEFORE_REMOTE_DOWNLOAD)
        );
    }

    @ParameterizedTest
    @MethodSource("sendRemoteArtifactEventSource")
    void testSendRemoteArtifactEvent(List<DispatcherResponse> responses, boolean shouldProceed,
            SupportedUnifiedEvents eventType) {
        ArtifactResourceContext resourceContext = mock(ArtifactResourceContext.class);

        EventEnvelope<ArtifactEventData> mockEnvelope = mock(EventEnvelope.class);

        when(artifactDataMapper.toArtifactEventEnvelopeV1(eq(eventType), eq(resourceContext)))
                .thenReturn(mockEnvelope);
        when(handler.dispatchEvent(any())).thenReturn(responses);

        if (shouldProceed) {
            DispatcherResponse response =
                    unifiedEventService.sendRemoteArtifactEvent(eventType, resourceContext);
            assertTrue(response.shouldProceed());
        } else {
            assertThrowsExactly(UnifiedEventException.class,
                    () -> unifiedEventService.sendRemoteArtifactEvent(eventType, resourceContext));
        }

        verify(artifactDataMapper).toArtifactEventEnvelopeV1(eq(eventType), eq(resourceContext));
        ArgumentCaptor<EventEnvelope<ArtifactEventData>> eventCaptor = ArgumentCaptor.forClass(EventEnvelope.class);
        verify(handler).dispatchEvent(eventCaptor.capture());
        assertEquals(mockEnvelope, eventCaptor.getValue());
    }
}
