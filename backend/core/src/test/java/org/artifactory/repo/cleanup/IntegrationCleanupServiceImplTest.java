package org.artifactory.repo.cleanup;

import com.google.common.collect.TreeMultimap;
import org.artifactory.api.module.ModuleInfo;
import org.artifactory.api.module.ModuleInfoBuilder;
import org.artifactory.common.ConstantValues;
import org.artifactory.fs.ItemInfo;
import org.artifactory.model.common.RepoPathImpl;
import org.artifactory.model.fs.FileInfoImpl;
import org.artifactory.repo.LocalRepo;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.service.InternalRepositoryService;
import org.artifactory.repo.snapshot.SnapshotVersionsRetriever;
import org.artifactory.schedule.TaskService;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Calendar;
import java.util.concurrent.ConcurrentMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link IntegrationCleanupServiceImpl}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class IntegrationCleanupServiceImplTest extends ArtifactoryHomeBoundJUnitTest {

    private static final String REPO_KEY = "libs-snapshots-local";
    private static final String SNAPSHOT_FILE_PATH =
            "org/acme/widget/1.0-SNAPSHOT/widget-1.0-20200101.120000-1.jar";

    @Mock
    private InternalRepositoryService repositoryService;

    @Mock
    private FolderPruningService pruningService;

    @Mock
    private TaskService taskService;

    @InjectMocks
    private IntegrationCleanupServiceImpl integrationCleanupService;

    @BeforeEach
    void setUp() {
        // Quiet period must be 0 so clean() processes queued folders immediately (R-CFG-01)
        setLongSystemProperty(ConstantValues.integrationCleanupQuietPeriodSecs, 0L);
    }

    @AfterEach
    void tearDown() throws Exception {
        setLongSystemProperty(ConstantValues.integrationCleanupQuietPeriodSecs,
                Long.parseLong(ConstantValues.integrationCleanupQuietPeriodSecs.getDefValue()));
        ConcurrentMap<?, ?> foldersInTransit =
                (ConcurrentMap<?, ?>) ReflectionTestUtils.getField(integrationCleanupService, "foldersInTransit");
        if (foldersInTransit != null) {
            foldersInTransit.clear();
        }
    }

    /**
     * RTDEV-93991: reverting maxUniqueSnapshots to 0 (unlimited) must not delete previously queued folders.
     * R-CFG-01, R-IB-1.3 — config value 0 means unlimited, not "keep zero versions".
     */
    @Test
    void clean_shouldNotUndeploy_whenMaxUniqueSnapshotsRevertedToZero() {
        RepoPath queuedFile = new RepoPathImpl(REPO_KEY, SNAPSHOT_FILE_PATH);
        LocalRepo<?, ?> repo = mock(LocalRepo.class);
        when(repositoryService.localOrFederatedRepositoryByKey(REPO_KEY)).thenReturn(repo);
        when(repo.getMaxUniqueSnapshots()).thenReturn(0);

        integrationCleanupService.addItemToCache(queuedFile);

        try (MockedConstruction<SnapshotVersionsRetriever> retrieverConstruction =
                     mockConstruction(SnapshotVersionsRetriever.class)) {
            integrationCleanupService.clean();

            assertEquals(0, retrieverConstruction.constructed().size(),
                    "SnapshotVersionsRetriever must not run when maxUniqueSnapshots is 0");
        }

        verify(repositoryService, never()).undeploy(any(RepoPath.class), anyBoolean(), anyBoolean());
        assertTrue(getFoldersInTransit().isEmpty(), "Queued folder should be drained without deletion");
    }

    /**
     * Positive path: cleanup removes oldest unique snapshot(s) until at or under the configured limit.
     * R-IB-1.2 — non-zero limit branch; R-LOG-10 — only the oldest snapshot is undeployed.
     */
    @Test
    void clean_shouldUndeployOldestUniqueSnapshots_whenCountExceedsLimit() {
        RepoPath queuedFile = new RepoPathImpl(REPO_KEY, SNAPSHOT_FILE_PATH);
        LocalRepo<?, ?> repo = mock(LocalRepo.class);
        when(repositoryService.localOrFederatedRepositoryByKey(REPO_KEY)).thenReturn(repo);
        when(repo.getMaxUniqueSnapshots()).thenReturn(2);

        ModuleInfo deployedModule = new ModuleInfoBuilder()
                .organization("org.acme")
                .module("widget")
                .baseRevision("1.0")
                .build();
        when(repositoryService.getItemModuleInfo(queuedFile)).thenReturn(deployedModule);

        FileInfoImpl oldest = new FileInfoImpl(new RepoPathImpl(REPO_KEY,
                "org/acme/widget/1.0-SNAPSHOT/widget-1.0-20200101.120000-1.jar"));
        FileInfoImpl middle = new FileInfoImpl(new RepoPathImpl(REPO_KEY,
                "org/acme/widget/1.0-SNAPSHOT/widget-1.0-20200102.120000-2.jar"));
        FileInfoImpl newest = new FileInfoImpl(new RepoPathImpl(REPO_KEY,
                "org/acme/widget/1.0-SNAPSHOT/widget-1.0-20200103.120000-3.jar"));

        ModuleInfo noClassifier = new ModuleInfoBuilder()
                .organization("org.acme")
                .module("widget")
                .baseRevision("1.0")
                .build();
        when(repositoryService.getItemModuleInfo(oldest.getRepoPath())).thenReturn(noClassifier);
        when(repositoryService.getItemModuleInfo(middle.getRepoPath())).thenReturn(noClassifier);
        when(repositoryService.getItemModuleInfo(newest.getRepoPath())).thenReturn(noClassifier);

        TreeMultimap<Calendar, ItemInfo> versions = TreeMultimap.create();
        versions.put(calendarAt(1_000L), oldest);
        versions.put(calendarAt(2_000L), middle);
        versions.put(calendarAt(3_000L), newest);

        integrationCleanupService.addItemToCache(queuedFile);

        try (MockedConstruction<SnapshotVersionsRetriever> ignored = mockConstruction(
                SnapshotVersionsRetriever.class,
                (mock, context) -> when(mock.collectVersionsItems(any(), any(), anyBoolean())).thenReturn(versions))) {
            integrationCleanupService.clean();
        }

        verify(repositoryService).undeploy(eq(oldest.getRepoPath()), eq(false), eq(false));
        verify(repositoryService, never()).undeploy(eq(middle.getRepoPath()), anyBoolean(), anyBoolean());
        verify(repositoryService, never()).undeploy(eq(newest.getRepoPath()), anyBoolean(), anyBoolean());
        verify(pruningService).prune(oldest.getRepoPath().getParent());
        assertTrue(getFoldersInTransit().isEmpty());
    }

    @SuppressWarnings("unchecked")
    private ConcurrentMap<IntegrationCleanupCandidate, Long> getFoldersInTransit() {
        return (ConcurrentMap<IntegrationCleanupCandidate, Long>) ReflectionTestUtils.getField(
                integrationCleanupService, "foldersInTransit");
    }

    private static Calendar calendarAt(long epochMillis) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(epochMillis);
        return calendar;
    }
}
