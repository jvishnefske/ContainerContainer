package org.artifactory.sh.cleanup.minor;

import com.google.common.collect.ImmutableList;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.repo.trash.TrashService;
import org.artifactory.sh.cleanup.GCFunction;
import org.artifactory.sh.cleanup.GCService;
import org.artifactory.sh.cleanup.GarbageCollectorStrategy;
import org.artifactory.sh.cleanup.events.EventsGcConfiguration;
import org.artifactory.storage.GCCandidate;
import org.jfrog.storage.binstore.ifc.GarbageCollectorInfo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TrashAndBinariesGCProviderTest {
    private Supplier<TrashAndBinariesGCProvider> gcProvider;
    @Mock
    private RepositoryService repositoryService;
    @Mock
    private TrashService trashService;
    @Mock
    private GCService gcService;
    private AutoCloseable mocks;
    private MockedStatic<EventsGcConfiguration> eventsGcConfigurationMock;

    @BeforeEach
    void setup() {
        mocks = MockitoAnnotations.openMocks(this);
        gcProvider = this::providerFactory;
        eventsGcConfigurationMock = Mockito.mockStatic(EventsGcConfiguration.class);
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
        eventsGcConfigurationMock.close();
    }

    @Test
    void testEventsGCDisabled_zeroEventsGcCandidates() {
        GarbageCollectorInfo gcInfo =
                new GarbageCollectorInfo(GarbageCollectorStrategy.TRASH_AND_BINARIES.name(), 10);
        GCFunction cleanupAction = gcProvider.get().getAction();
        when(repositoryService.existsBySha1("ccc")).thenReturn(false);
        GCCandidate candidate = new GCCandidate(null, "ccc", "ccc", "ccc", 55);
        cleanupAction.accept(candidate, gcInfo);
        verify(gcService, times(1)).executeBinaryCleaner(candidate, gcInfo);
        assertEquals(0, gcInfo.getEventsGcCandidates().get());
    }

    @Test
    void testEventsGCEnabled_zeroBinariesCleaned() {
        eventsGcConfigurationMock.when(EventsGcConfiguration::useEventGCinMinorGC).thenReturn(true);
        GCFunction cleanupAction = gcProvider.get().getAction();
        GarbageCollectorInfo gcInfo =
                new GarbageCollectorInfo(GarbageCollectorStrategy.TRASH_AND_BINARIES.name(), 10);
        GCCandidate candidate = new GCCandidate(null, "aaa", "aaa", "aaa", 55);
        cleanupAction.accept(candidate, gcInfo);
        verify(gcService, times(0)).executeBinaryCleaner(candidate, gcInfo);
        assertEquals(1, gcInfo.getEventsGcCandidates().get());
    }

    private List<GCCandidate> getGcCandidates() {
        return ImmutableList.of(
                new GCCandidate(null, "aaa", "aaa", "aaa", 55),
                new GCCandidate(null, "bbb", "bbb", "bbb", 55),
                new GCCandidate(null, "ccc", "ccc", "ccc", 55),
                new GCCandidate(null, "ddd", "ddd", "ddd", 55)
        );
    }

    @Test
    void testGetAction() {
        GCFunction cleanupAction = gcProvider.get().getAction();

        GarbageCollectorInfo gcInfo =
                new GarbageCollectorInfo(GarbageCollectorStrategy.TRASH_AND_BINARIES.name(), 10);
        List<GCCandidate> candidatesForDeletion = getGcCandidates();

        when(repositoryService.existsBySha1("aaa")).thenReturn(true);
        when(repositoryService.existsBySha1("bbb")).thenReturn(true);
        when(repositoryService.existsBySha1("ccc")).thenReturn(false);
        when(repositoryService.existsBySha1("ddd")).thenReturn(false);

        when(gcService.executeBinaryCleaner(candidatesForDeletion.get(2), gcInfo))
                .thenReturn(true);
        when(gcService.executeBinaryCleaner(candidatesForDeletion.get(3), gcInfo))
                .thenReturn(false);

        for (int i = 0; i < candidatesForDeletion.size(); i++) {
            GCCandidate candidate = candidatesForDeletion.get(i);
            boolean binaryDeleted = cleanupAction.accept(candidate, gcInfo);
            verify(trashService).undeployFromTrash(candidate);
            verify(repositoryService).existsBySha1(candidate.sha1());
            if (i < 2) {
                verify(gcService, times(0))
                        .executeBinaryCleaner(candidate, gcInfo);
                assertFalse(binaryDeleted);
            } else {
                verify(gcService, times(1))
                        .executeBinaryCleaner(candidate, gcInfo);
                if (i == 2) {
                    assertTrue(binaryDeleted);
                } else {
                    assertFalse(binaryDeleted);
                }
            }
        }
    }

    @Test
    void testGetBatch() {
        when(trashService.getGCCandidatesFromTrash()).thenReturn(getGcCandidates());
        List<GCCandidate> candidatesForDeletion = gcProvider.get().getBatch();
        verify(trashService, times(1)).getGCCandidatesFromTrash();
        assertEquals(4, candidatesForDeletion.size());
    }

    @Test
    void testGetName() {
        assertEquals(GarbageCollectorStrategy.TRASH_AND_BINARIES.name(), gcProvider.get().getName());
    }

    @Test
    void testShouldReportAfterBatch() {
        assertTrue(gcProvider.get().shouldReportAfterBatch());
    }

    private TrashAndBinariesGCProvider providerFactory() {
        return new TrashAndBinariesGCProvider(repositoryService, trashService, gcService);
    }
}