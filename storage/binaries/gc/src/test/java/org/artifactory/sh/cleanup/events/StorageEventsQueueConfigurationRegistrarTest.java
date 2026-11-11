package org.artifactory.sh.cleanup.events;

import com.google.common.hash.Hashing;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.checksum.ChecksumType;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.event.EventHintType;
import org.artifactory.event.EventType;
import org.artifactory.event.queue.QueueEvent;
import org.artifactory.sh.cleanup.GCService;
import org.artifactory.storage.GCCandidate;
import org.artifactory.storage.event.StorageEventInfo;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.jfrog.storage.events.DeletedBinariesEvent;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.testng.Assert;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author TamirH
 */
@ExtendWith(MockitoExtension.class)
class StorageEventsQueueConfigurationRegistrarTest extends ArtifactoryHomeBoundJUnitTest {
    @Captor
    ArgumentCaptor<GCCandidate> gcCandidateArgumentCaptor;

    @Captor
    ArgumentCaptor<DeletedBinariesEvent> deletedBinariesEventCaptor;

    @AfterAll
    static void after() {
        ArtifactoryHome.unbind();
    }

    @Test void convertAndOptimizeEvents() {
        StorageEventInfo.Data gcData = StorageEventInfo.Data.empty();
        final GCService gcService = mock(GCService.class);
        final ApplicationEventPublisher appEventPublisher = mock(ApplicationEventPublisher.class);
        final RepositoryService repositoryService = mock(RepositoryService.class);
        lenient().when(repositoryService.existsBySha1(anyString())).thenReturn(false);
        final StorageEventsService storageEvents = mock(StorageEventsService.class);
        List<StorageEventInfo> fromDB = List.of(
                new StorageEventInfo(2L, 2L, EventType.delete, "B", "DBsha256", 142L, gcData),
                new StorageEventInfo(4L, 4L, EventType.delete, "C", "DBsha256", 142L, gcData),
                new StorageEventInfo(5L, 5L, EventType.delete, "A", "DBsha256", 142L, gcData)
        );
        lenient().when(storageEvents.getLatestEventsBySHA1(any())).thenReturn(fromDB);
        final StorageEventsQueueConfigurationRegistrar x =
                spy(new StorageEventsQueueConfigurationRegistrar(gcService, appEventPublisher,
                        storageEvents, repositoryService));
        final List<QueueEvent> events = List.of(
                mockQueueEvent(1, "A"),
                mockQueueEvent(2, "B"),
                mockQueueEvent(3, "A"));

        final List<StorageEventInfo> actual = x.convertAndDeduplicateEvents(events);

        final StorageEventInfo eventEvent = mockStorageEventInfo(2, "B");

        final List<StorageEventInfo> expected = List.of(eventEvent);
        assertEquals(expected.size(), actual.size());
        List<String> expectedIDs = expected.stream().map(StorageEventInfo::sha1).toList();
        List<String> actualIDs = actual.stream().map(StorageEventInfo::sha1).toList();
        assertTrue(expectedIDs.containsAll(actualIDs) && actualIDs.containsAll(expectedIDs));
    }

    private QueueEvent mockQueueEvent(int id, String sha1) {
        QueueEvent event = mock(QueueEvent.class);
        when(event.getEventId()).thenReturn((long)id);
        when(event.getType()).thenReturn(EventType.delete);
        when(event.getHints()).thenReturn(Set.of(
                EventHintType.Sha1Checksum.getPrefix() + sha1,
                EventHintType.Sha256Checksum.getPrefix() + "sha256",
                EventHintType.BinLength.getPrefix() + "42",
                StorageEventInfo.Data.withGCDataHintPrefix(StorageEventInfo.Data.empty())));
        return event;
    }

    @ParameterizedTest
    @MethodSource("retainLastElements")
    void retainLastElements(List<StorageEventInfo> list, List<StorageEventInfo> expected) {
        List<StorageEventInfo> actual = StorageEventsQueueConfigurationRegistrar.retainLastElements(list);

        assertEquals(expected.size(), actual.size());
        List<String> expectedIDs = expected.stream().map(StorageEventInfo::toString).toList();
        List<String> actualIDs = actual.stream().map(StorageEventInfo::toString).toList();
        assertTrue(expectedIDs.containsAll(actualIDs) && actualIDs.containsAll(expectedIDs));
    }

    private static Stream<Arguments> retainLastElements() {
        List<StorageEventInfo> listCase1 = List.of(
                mockStorageEventInfo(10, "aa"),
                mockStorageEventInfo(20, "aa"),
                mockStorageEventInfo(30, "aa"),
                mockStorageEventInfo(40, "bb"),
                mockStorageEventInfo(50, "bb"),
                mockStorageEventInfo(60, "aa"),
                mockStorageEventInfo(70, "cc"),
                mockStorageEventInfo(80, "dd"),
                mockStorageEventInfo(90, "dd")
        );
        List<StorageEventInfo> expectedCase1 = List.of(
                mockStorageEventInfo(50, "bb"),
                mockStorageEventInfo(60, "aa"),
                mockStorageEventInfo(70, "cc"),
                mockStorageEventInfo(90, "dd")
        );

        List<StorageEventInfo> listCase2 = List.of(mockStorageEventInfo(10, "aa"));
        List<StorageEventInfo> expectedCase2 = List.of(mockStorageEventInfo(10, "aa"));

        List<StorageEventInfo> listCase3 = List.of();
        List<StorageEventInfo> expectedCase3 = List.of();

        return Stream.of(
                Arguments.of(listCase1, expectedCase1),
                Arguments.of(listCase2, expectedCase2),
                Arguments.of(listCase3, expectedCase3));
    }

    private static StorageEventInfo mockStorageEventInfo(long eventId, String sha1) {
        StorageEventInfo event = mock(StorageEventInfo.class);
        lenient().when(event.eventId()).thenReturn(eventId);
        lenient().when(event.toString()).thenReturn(eventId + " & " + sha1);

        lenient().when(event.sha1()).thenReturn(sha1);
        lenient().when(event.sha256()).thenReturn("sha256");
        lenient().when(event.size()).thenReturn(42L);
        lenient().when(event.data()).thenReturn(null);
        return event;
    }

    @Test
    void testHandleEvents() {
        final GCService gcService = mock(GCService.class);
        final ApplicationEventPublisher appEventPublisher = mock(ApplicationEventPublisher.class);
        final RepositoryService repositoryService = mock(RepositoryService.class);
        when(repositoryService.existsBySha1(anyString())).thenReturn(false);
        final StorageEventsService storageEvents = mock(StorageEventsService.class);
        final StorageEventsQueueConfigurationRegistrar deleteQRegistrar =
                spy(new StorageEventsQueueConfigurationRegistrar(gcService, appEventPublisher,
                        storageEvents, repositoryService));

        List<QueueEvent> events = new ArrayList<>();
        List<StorageEventInfo> storageEventInfo = new ArrayList<>();
        Set<String> sha1s = new HashSet<>();
        Set<String> sha256s = new HashSet<>();
        for (int i = 0; i < 2; i++) {
            QueueEvent event = mock(QueueEvent.class);
            String sha256 = Hashing.sha256()
                    .hashString(String.valueOf(i), StandardCharsets.UTF_8)
                    .toString();
            String sha1 = Hashing.sha1()
                    .hashString(String.valueOf(i), StandardCharsets.UTF_8)
                    .toString();
            sha1s.add(sha1);
            sha256s.add(sha256);
            Set<String> hints = Set.of(EventHintType.Sha1Checksum.getPrefix() + sha1,
                    EventHintType.Sha256Checksum.getPrefix() + sha256,
                    EventHintType.BinLength.getPrefix() + "1");
            when(event.getHints()).thenReturn(hints);
            events.add(event);
            storageEventInfo.add(new StorageEventInfo(event.getType(), sha1, sha256, 1L,
                    StorageEventInfo.Data.getEventsGcData(hints)));
        }
        lenient().when(storageEvents.getLatestEventsBySHA1(any(List.class))).thenReturn(storageEventInfo);
        when(gcService.executeBinaryCleaner(any(GCCandidate.class), any())).thenReturn(true);
        doNothing().when(appEventPublisher).publishEvent(any());
        deleteQRegistrar.handleEvents(events);
        verify(gcService, times(2)).executeBinaryCleaner(gcCandidateArgumentCaptor.capture(), any());

        final List<GCCandidate> deleteBinaries = gcCandidateArgumentCaptor.getAllValues();
        for (GCCandidate candidate : deleteBinaries) {
            assertTrue(ChecksumType.sha1.isValid(candidate.sha1()));
            assertTrue(sha1s.contains(candidate.sha1()));
        }

        verify(appEventPublisher).publishEvent(deletedBinariesEventCaptor.capture());
        final List<String> deletedBinaries = deletedBinariesEventCaptor.getValue().getDeletedBinaries();
        Assertions.assertEquals(2, deletedBinaries.size());
        for (String deletedSha2 : deletedBinaries) {
            assertTrue(sha256s.contains(deletedSha2));
        }

        verify(storageEvents, times(1)).deleteByCursor(anyLong());
    }

    @ParameterizedTest
    @MethodSource("testHandleEventsRetry")
    void testHandleEventsRetry(StorageEventInfo.Data gcData, StorageEventInfo.Data expectedGcData,
            String length, int expectedSize, String dedupEnabled) {
        getOrCreateArtifactoryHomeStub()
                .setProperty(ConstantValues.gcEventsCleanupDelayMillis, "100")
                .setProperty(ConstantValues.gcEventsMaxReties, "2")
                .setProperty(ConstantValues.gcEventsCleanupEventsDedupOptimizationEnabled, dedupEnabled);

        final GCService gcService = mock(GCService.class);
        final ApplicationEventPublisher appEventPublisher = mock(ApplicationEventPublisher.class);
        final RepositoryService repositoryService = mock(RepositoryService.class);
        lenient().when(repositoryService.existsBySha1(anyString())).thenReturn(false);
        final StorageEventsService storageEvents = mock(StorageEventsService.class);
        List<StorageEventInfo> fromDB = List.of(new StorageEventInfo(EventType.delete, "sha1", "sha256", 42L, gcData));
        lenient().when(storageEvents.getLatestEventsBySHA1(any())).thenReturn(fromDB);
        final StorageEventsQueueConfigurationRegistrar x =
                spy(new StorageEventsQueueConfigurationRegistrar(gcService, appEventPublisher,
                        storageEvents, repositoryService));

        QueueEvent event = mock(QueueEvent.class);
        String sha256 = "sha256";
        String sha1 = "sha1";
        when(event.getType()).thenReturn(EventType.delete);
        when(event.getHints()).thenReturn(
                Set.of(EventHintType.Sha1Checksum.getPrefix() + sha1,
                        EventHintType.Sha256Checksum.getPrefix() + sha256,
                        EventHintType.BinLength.getPrefix() + length,
                        StorageEventInfo.Data.withGCDataHintPrefix(gcData)));

        lenient().doThrow(new org.jfrog.storage.binstore.exceptions.BinaryStorageException("TEST"))
                .when(gcService).executeBinaryCleaner(any(GCCandidate.class), any());

        x.handleEvents(List.of(event));

        //noinspection unchecked
        ArgumentCaptor<List<StorageEventInfo>> argument = ArgumentCaptor.forClass(List.class);
        verify(storageEvents, times(1)).appendEvents(argument.capture());
        List<StorageEventInfo> failedGCEvents = argument.getValue();
        Assert.assertEquals(failedGCEvents.size(), expectedSize);
        if (expectedSize > 0) {
            StorageEventInfo failedGCEvent = failedGCEvents.get(0);
            Assertions.assertEquals(sha1, failedGCEvent.sha1());
            Assertions.assertEquals(sha256, failedGCEvent.sha256());
            Assertions.assertEquals(expectedGcData, failedGCEvent.data());
            Assert.assertTrue(x.getDeleteFailuresCount() > 0L);
        } else {
            if (gcData != null) {
                if (gcData.getRetryCount() < ConstantValues.gcEventsMaxReties.getInt()) {
                    Assert.assertEquals(x.getRetryExhaustedCount(), 0L);
                } else {
                    Assert.assertTrue(x.getRetryExhaustedCount() > 0L);
                }
            }
        }
    }

    private static Stream<Arguments> testHandleEventsRetry() {
        StorageEventInfo.Data firstRetry = new StorageEventInfo.Data(1);
        StorageEventInfo.Data secondRetry = new StorageEventInfo.Data(2);
        return Stream.of("false", "true")
                .flatMap(dedupEnabled -> Stream.of(
                        Arguments.of(null, firstRetry, "42", 1, dedupEnabled),        // no retries -> first retry
                        Arguments.of(firstRetry, secondRetry, "1", 1, dedupEnabled),  // first retry -> second retry
                        Arguments.of(secondRetry, null, "42", 0, dedupEnabled),
                        // second retry -> no retry (due to retry give-up)
                        Arguments.of(null, firstRetry, "abc", 1, dedupEnabled), // invalid "length" hint -> first retry
                        Arguments.of(null, firstRetry, "", 1, dedupEnabled) // empty "length" hint -> first retry
                ));
    }
}