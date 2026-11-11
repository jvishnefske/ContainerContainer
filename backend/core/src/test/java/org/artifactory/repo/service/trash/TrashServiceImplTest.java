/*
 *
 * Artifactory is a binaries repository manager.
 * Copyright (C) 2019 JFrog Ltd.
 *
 * Artifactory is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 * Artifactory is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Artifactory.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package org.artifactory.repo.service.trash;

import lombok.NonNull;
import org.artifactory.api.common.BasicStatusHolder;
import org.artifactory.api.common.MoveMultiStatusHolder;
import org.artifactory.common.status.StatusEntry;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.properties.PropertiesService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.config.ConfigurationException;
import org.artifactory.model.LocalConfig;
import org.artifactory.descriptor.repo.RepoDescriptor;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.fs.FileInfo;
import org.artifactory.md.Properties;
import org.artifactory.model.common.RepoPathImpl;
import org.artifactory.properties.PropertiesImpl;
import org.artifactory.repo.InternalRepoPathFactory;
import org.artifactory.repo.Repo;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.RepoPathFactory;
import org.artifactory.repo.RepoRepoPath;
import org.artifactory.repo.db.DbLocalRepo;
import org.artifactory.repo.service.InternalRepositoryService;
import org.artifactory.repo.trash.TrashGCHelper;
import static org.artifactory.sh.cleanup.events.EventsGcConfiguration.useEventGCinMinorGC;
import org.artifactory.storage.GCCandidate;
import org.artifactory.storage.db.fs.session.SqlStorageSession;
import org.artifactory.storage.fs.lock.SessionLockEntry;
import org.artifactory.storage.fs.session.StorageSessionHolder;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.annotations.DataProvider;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Calendar;
import java.util.EnumMap;
import java.util.List;

import static org.artifactory.repo.system.SystemRepos.TRASH_REPO_KEY;
import static org.artifactory.repo.trash.TrashService.PROP_TRASH_TIME;
import static org.artifactory.repo.trash.TrashService.TRASH_KEY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.openMocks;

/**
 * @author dudim
 * Retention days cannot exceed 5 years value.
 * when executing propertiesDao.getGCCandidateQuery we using less('<') comparator on strings (prop_value < validfrom).
 * we assume validFrom is with length of 10 chars (since 2002 epoch time length is 10 digits),
 * in this test we assert when setting the Retention days to over 5 years(1825 days) we will return 0
 * otherwise we will delete all trashcan content.
 */
class TrashServiceImplTest extends ArtifactoryHomeBoundJUnitTest {

    @Mock
    private InternalRepositoryService repoService;
    @Mock
    private SecurityService securityService;
    @Mock
    private CentralConfigService configService;
    @Mock
    private PropertiesService propertiesService;
    @Mock
    private org.artifactory.storage.fs.service.PropertiesService storagePropsService;
    @Mock
    private NonTrashableItems nonTrashableItems;
    @Mock
    private TrashGCHelper trashGCHelper;
    private TrashServiceImpl trashService;
    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = openMocks(this);
        trashService = new TrashServiceImpl(repoService, securityService, configService, propertiesService,
                storagePropsService, nonTrashableItems, trashGCHelper);
    }

    @AfterEach
    public void tearDown() throws Exception {
        StorageSessionHolder.removeSession();
        ArtifactoryContextThreadBinder.unbind();
        mocks.close();
    }

    /**
     * The time that a file was stored in a trashcan is saved in the DB as millis as text.
     * The GC (trashcan cleanup phase) search for files that are in the trashcan for more than x days, and delete these.
     * The date (timestamp) comparison is done in the DB, on the text values, therefore, it is mandatory that all
     * timestamps will be at the same length (same number of digits)
     * We decided that the min allowed date (oldest time of file in trashcan) is 2003 January 1
     */
    @ParameterizedTest
    @MethodSource("provideInvalidTimestamps")
    public void testValidateRetentionPeriodTimestampInvalidTimestamps(long timestamp) {
        assertThrows(ConfigurationException.class, () -> trashService.validateRetentionPeriodTimestamp(timestamp),
                "Illegal retention date. Retention period goes back too far.");
    }

    @DataProvider
    public static Object[][] provideInvalidTimestamps() {
        Object[][] objects = new Object[12][];
        Calendar cal = Calendar.getInstance();
        int i = 0;
        for (int year = 1990; year < 2002; year++) {
            cal.set(year, 01, 01);
            objects[i] = new Object[] {cal.getTime().toInstant().toEpochMilli()};
            i++;
        }
        return objects;
    }

    @ParameterizedTest
    @MethodSource("provideValidTimestamps")
    public void testValidateRetentionPeriodTimestamp(long timestamp) {
        trashService.validateRetentionPeriodTimestamp(timestamp);
    }

    @DataProvider
    public static Object[][] provideValidTimestamps() {
        Object[][] objects = new Object[27][];
        Calendar cal = Calendar.getInstance();
        int i = 0;
        for (int year = 2003; year < 2030; year++) {
            cal.set(year, 01, 02);
            objects[i] = new Object[] {cal.getTime().toInstant().toEpochMilli()};
            i++;
        }
        return objects;
    }

    @Test
    void testUndeployIfAncestorFilesInTrash() {
        ArtifactoryContext context = mock(ArtifactoryContext.class);
        when(repoService.getFileInfo(any())).thenReturn(mock(FileInfo.class));
        when(repoService.undeploy(any())).thenReturn(mock(BasicStatusHolder.class));
        DbLocalRepo<?, ?> dbLocalRepoMock = mock(DbLocalRepo.class);
        RepoRepoPath<Repo> targetRepoRepoPath =
                new RepoRepoPath<>(dbLocalRepoMock, new RepoPathImpl("auto-trashcan", "/parent/foo.bar"));
        when(repoService.getRepoRepoPath(any())).thenReturn(targetRepoRepoPath);
        when(context.beanForType(InternalRepositoryService.class)).thenReturn(repoService);
        ArtifactoryContextThreadBinder.bind(context);

        SqlStorageSession sqlStorageSessionMock = mock(SqlStorageSession.class);
        SessionLockEntry sessionLockEntryMock = mock(SessionLockEntry.class);
        when(sqlStorageSessionMock.getLockEntry(any())).thenReturn(sessionLockEntryMock);
        StorageSessionHolder.setSession(sqlStorageSessionMock);

        trashService.undeployIfAncestorFilesInTrash(new RepoPathImpl("auto-trashcan", "/parent/foo.bar"));

        verify(sessionLockEntryMock, times(2)).setWriteFsItem(any());
        verify(dbLocalRepoMock, times(1)).createOrGetFolder(any());
    }


    @Test
    void restoreBulkSnapshotsAndReappliesTargetRootPropertiesOnWholeRepoRestore() {
        String restoreRepo = "restore-repo";
        RepoPath trashedRepoRoot = RepoPathFactory.create(TRASH_REPO_KEY, restoreRepo);
        RepoPath targetRepoRoot = RepoPathFactory.create(restoreRepo);
        Properties targetRootProperties = new PropertiesImpl();
        targetRootProperties.putAll("root.retention", new String[] {"root"});
        when(repoService.localOrFederatedRepoConfigByKey(restoreRepo)).thenReturn(mock(LocalConfig.class));
        when(propertiesService.getProperties(targetRepoRoot)).thenReturn(targetRootProperties);
        when(repoService.moveFlat(any(RepoPath.class), eq(restoreRepo), nullable(String.class),
                any(Properties.class), any(), eq(false), eq(true), eq(1000)))
                .thenReturn(new MoveMultiStatusHolder());

        trashService.restoreBulk(trashedRepoRoot, restoreRepo, null, 1000);

        InOrder ordered = inOrder(propertiesService, repoService);
        ordered.verify(propertiesService).getProperties(targetRepoRoot);
        ordered.verify(repoService).moveFlat(any(RepoPath.class), eq(restoreRepo), nullable(String.class),
                any(Properties.class), any(), eq(false), eq(true), eq(1000));
        ordered.verify(propertiesService).updateProperties(targetRepoRoot, targetRootProperties);
    }

    @Test
    void restoreBulkPassesEmptyPropsAppliedToEachMovedItemOnWholeRepoRestore() {
        String restoreRepo = "restore-repo";
        RepoPath trashedRepoRoot = RepoPathFactory.create(TRASH_REPO_KEY, restoreRepo);
        Properties targetRootProperties = new PropertiesImpl();
        targetRootProperties.putAll("root.retention", new String[] {"root"});
        when(repoService.localOrFederatedRepoConfigByKey(restoreRepo)).thenReturn(mock(LocalConfig.class));
        when(propertiesService.getProperties(RepoPathFactory.create(restoreRepo))).thenReturn(targetRootProperties);
        when(repoService.moveFlat(any(RepoPath.class), eq(restoreRepo), nullable(String.class),
                any(Properties.class), any(), eq(false), eq(true), eq(1000)))
                .thenReturn(new MoveMultiStatusHolder());

        MoveFlatProperties moveFlatProperties = captureRestoreBulkMoveFlatProperties(
                trashedRepoRoot, restoreRepo, null);

        assertEquals(0, moveFlatProperties.propsAppliedToEachMovedItem().keySet().size(),
                "Whole-repo restore must not pass target root props as moveFlat's per-item overlay");
    }

    @Test
    void restoreBulkPassesEmptyPropsAppliedToEachMovedItemAndSkipsRootHandlingOnInnerRestore() {
        String restoreRepo = "restore-repo";
        String innerRestorePath = "/folder1";
        RepoPath trashedFolder = RepoPathFactory.create(TRASH_REPO_KEY, restoreRepo + "/folder1");
        RepoPath targetRepoRoot = RepoPathFactory.create(restoreRepo);
        Properties targetRootProperties = new PropertiesImpl();
        targetRootProperties.putAll("root.retention", new String[] {"root"});
        when(repoService.localOrFederatedRepoConfigByKey(restoreRepo)).thenReturn(mock(LocalConfig.class));
        when(propertiesService.getProperties(targetRepoRoot)).thenReturn(targetRootProperties);
        when(repoService.moveFlat(any(RepoPath.class), eq(restoreRepo), eq(innerRestorePath),
                any(Properties.class), any(), eq(false), eq(true), eq(1000)))
                .thenReturn(new MoveMultiStatusHolder());

        MoveFlatProperties moveFlatProperties = captureRestoreBulkMoveFlatProperties(
                trashedFolder, restoreRepo, innerRestorePath);

        assertEquals(0, moveFlatProperties.propsAppliedToEachMovedItem().keySet().size(),
                "Inner restore must not pass target root props as moveFlat's per-item overlay");
        verify(propertiesService, never()).updateProperties(eq(targetRepoRoot), any());
    }

    private MoveFlatProperties captureRestoreBulkMoveFlatProperties(RepoPath repoPath, String restoreRepo,
            String restorePath) {
        ArgumentCaptor<Properties> propsAppliedToEachMovedItem = ArgumentCaptor.forClass(Properties.class);
        ArgumentCaptor<List<String>> removeProps = captureStringList();

        trashService.restoreBulk(repoPath, restoreRepo, restorePath, 1000);

        verify(repoService).moveFlat(eq(repoPath), eq(restoreRepo), eq(restorePath),
                propsAppliedToEachMovedItem.capture(), removeProps.capture(), eq(false), eq(true), eq(1000));
        return new MoveFlatProperties(propsAppliedToEachMovedItem.getValue(), removeProps.getValue());
    }

    // Whole-repo restore detection boundary. Mirrors RepoPathImpl path normalization
    // (PathUtils.trimSlashes ∘ StringUtils.trimToEmpty): null / "" / "/" → root; "/." → "."  which is non-empty
    // so the current code treats it as inner. No production caller passes "/." today (REST and UI pass null or a
    // clean inner path); this row is a defensive boundary lock — if a future change normalizes "/." to root, this
    // assertion is the place that has to be revisited.
    @ParameterizedTest(name = "restorePath={0} -> snapshot/reapply fires={1}")
    @CsvSource(nullValues = "NULL", value = {
            "NULL,           true",
            "'',             true",
            "/,              true",
            "/.,             false",
            "folder1,        false",
            "folder1/child,  false"
    })
    void restoreBulkSnapshotAndReapply_firesIffTargetPathIsRepoRoot(String restorePath,
            boolean expectSnapshotAndReapply) {
        String restoreRepo = "restore-repo";
        RepoPath sourcePath = RepoPathFactory.create(TRASH_REPO_KEY, restoreRepo);
        RepoPath targetRepoRoot = RepoPathFactory.create(restoreRepo);
        Properties targetRootSnapshot = new PropertiesImpl();
        targetRootSnapshot.putAll("root.retention", new String[] {"root"});
        when(repoService.localOrFederatedRepoConfigByKey(restoreRepo)).thenReturn(mock(LocalConfig.class));
        when(propertiesService.getProperties(targetRepoRoot)).thenReturn(targetRootSnapshot);
        when(repoService.moveFlat(any(RepoPath.class), eq(restoreRepo), nullable(String.class),
                any(Properties.class), any(), eq(false), eq(true), eq(1000)))
                .thenReturn(new MoveMultiStatusHolder());

        trashService.restoreBulk(sourcePath, restoreRepo, restorePath, 1000);

        if (expectSnapshotAndReapply) {
            verify(propertiesService).getProperties(targetRepoRoot);
            verify(propertiesService).updateProperties(targetRepoRoot, targetRootSnapshot);
        } else {
            verify(propertiesService, never()).getProperties(eq(targetRepoRoot));
            verify(propertiesService, never()).updateProperties(eq(targetRepoRoot), any());
        }
    }

    @Test
    void restoreBulkReportsErrorWhenUpdatePropertiesReturnsNullOnWholeRepoRestore() {
        String restoreRepo = "restore-repo";
        RepoPath trashedRepoRoot = RepoPathFactory.create(TRASH_REPO_KEY, restoreRepo);
        RepoPath targetRepoRoot = RepoPathFactory.create(restoreRepo);
        Properties targetRootSnapshot = new PropertiesImpl();
        targetRootSnapshot.putAll("root.retention", new String[] {"root"});
        when(repoService.localOrFederatedRepoConfigByKey(restoreRepo)).thenReturn(mock(LocalConfig.class));
        when(propertiesService.getProperties(targetRepoRoot)).thenReturn(targetRootSnapshot);
        when(repoService.moveFlat(any(RepoPath.class), eq(restoreRepo), nullable(String.class),
                any(Properties.class), any(), eq(false), eq(true), eq(1000)))
                .thenReturn(new MoveMultiStatusHolder());
        when(propertiesService.updateProperties(targetRepoRoot, targetRootSnapshot)).thenReturn(null);

        MoveMultiStatusHolder finalStatus = trashService.restoreBulk(trashedRepoRoot, restoreRepo, null, 1000);

        assertTrue(finalStatus.isError(),
                "finalStatus.isError() must be true when updateProperties returns null after a successful moveFlat");
        StatusEntry lastError = finalStatus.getLastError();
        assertNotNull(lastError, "Expected an error entry on finalStatus when updateProperties returns null");
        assertTrue(lastError.getMessage().contains(restoreRepo),
                "Error message must reference the restore repo: " + lastError.getMessage());
    }

    @Test
    void restoreBulkReportsErrorWhenUpdatePropertiesThrowsRuntimeExceptionOnWholeRepoRestore() {
        String restoreRepo = "restore-repo";
        RepoPath trashedRepoRoot = RepoPathFactory.create(TRASH_REPO_KEY, restoreRepo);
        RepoPath targetRepoRoot = RepoPathFactory.create(restoreRepo);
        Properties targetRootSnapshot = new PropertiesImpl();
        targetRootSnapshot.putAll("root.retention", new String[] {"root"});
        RuntimeException reapplyFailure = new RuntimeException("update-failed");
        when(repoService.localOrFederatedRepoConfigByKey(restoreRepo)).thenReturn(mock(LocalConfig.class));
        when(propertiesService.getProperties(targetRepoRoot)).thenReturn(targetRootSnapshot);
        when(repoService.moveFlat(any(RepoPath.class), eq(restoreRepo), nullable(String.class),
                any(Properties.class), any(), eq(false), eq(true), eq(1000)))
                .thenReturn(new MoveMultiStatusHolder());
        when(propertiesService.updateProperties(targetRepoRoot, targetRootSnapshot)).thenThrow(reapplyFailure);

        MoveMultiStatusHolder finalStatus = trashService.restoreBulk(trashedRepoRoot, restoreRepo, null, 1000);

        assertTrue(finalStatus.isError(),
                "finalStatus.isError() must be true when updateProperties throws RuntimeException after a successful "
                        + "moveFlat");
        StatusEntry lastError = finalStatus.getLastError();
        assertNotNull(lastError, "Expected an error entry on finalStatus when updateProperties throws");
        assertTrue(lastError.getMessage().contains(restoreRepo),
                "Error message must reference the restore repo: " + lastError.getMessage());
        assertTrue(lastError.getMessage().contains("update-failed"),
                "Error message must include the thrown exception's message: " + lastError.getMessage());
        assertSame(reapplyFailure, lastError.getException(),
                "Recorded StatusEntry exception must be the thrown RuntimeException instance");
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private ArgumentCaptor<List<String>> captureStringList() {
        return ArgumentCaptor.forClass((Class) List.class);
    }

    private record MoveFlatProperties(Properties propsAppliedToEachMovedItem, List<String> removeProps) {
    }

    @Test
    void testEmpty() {
        trashService.empty();
        verify(repoService, times(1)).undeployMultiTransaction(InternalRepoPathFactory.repoRootPath(TRASH_REPO_KEY));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testNoTrashTimeForDir(boolean isFile)
            throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        RepoPath repoPath = mock(RepoPath.class);
        when(repoPath.isFile()).thenReturn(isFile);
        when(repoPath.getRepoKey()).thenReturn("repoKey");

        when(propertiesService.getProperties(repoPath)).thenReturn(new PropertiesImpl());

        RepoDescriptor repoDescriptor = mock(RepoDescriptor.class);
        RepoType repoType = mock(RepoType.class);
        when(repoType.getType()).thenReturn("");
        when(repoDescriptor.getType()).thenReturn(repoType);
        when(repoService.repoDescriptorByKey("repoKey")).thenReturn(repoDescriptor);
        when(repoService.exists(any())).thenReturn(true);

        Method trashAsSystemMethod =
                TrashServiceImpl.class.getDeclaredMethod("trashAsSystem", RepoPath.class, String.class);
        trashAsSystemMethod.setAccessible(true);
        trashAsSystemMethod.invoke(trashService, repoPath, "");

        ArgumentCaptor<Properties> argument = ArgumentCaptor.forClass(Properties.class);
        verify(propertiesService).setProperties(any(), argument.capture(), anyBoolean());
        assertEquals(isFile, !argument.getValue().get(PROP_TRASH_TIME).isEmpty());
    }


    @ParameterizedTest
    @CsvSource({
            "false, false, false",
            "false, true, false",
            "true, false, false",
            "true, true, true"
    })
    void testUseEventGCinMinorGC(boolean gcEventsCleanupEnabled, boolean use_event_gc, boolean expectedResult) {
        EnumMap<ConstantValues, Boolean> artifactoryBooleanProperties =
                (EnumMap<ConstantValues, Boolean>) ReflectionTestUtils.getField(
                        ArtifactoryHome.get().getArtifactoryProperties(), "artifactoryBooleanProperties");
        artifactoryBooleanProperties.put(ConstantValues.gcEventsCleanupEnabled, gcEventsCleanupEnabled);
        System.setProperty("artifactory.gc.minor.use_event_gc", String.valueOf(use_event_gc));
        assertEquals(expectedResult, useEventGCinMinorGC());
    }

    @Test
    void testUndeployFromTrash() {
        RepoPath repoPath = createMockRepoPath("some key");
        GCCandidate gcCandidate = new GCCandidate(repoPath, "sha1", "sha2", "md5", 0L);

        trashService.undeployFromTrash(gcCandidate);
        verify(repoService, times(0)).undeploy(any(RepoPath.class), anyBoolean(), anyBoolean());
        verify(repoService, times(0)).gcUndeploy(any(RepoPath.class), anyBoolean(), anyBoolean());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testUndeployFromTrash(boolean gcEventsCleanupEnabled) {
        EnumMap<ConstantValues, Boolean> artifactoryBooleanProperties =
                (EnumMap<ConstantValues, Boolean>) ReflectionTestUtils.getField(
                        ArtifactoryHome.get().getArtifactoryProperties(), "artifactoryBooleanProperties");
        artifactoryBooleanProperties.put(ConstantValues.gcEventsCleanupEnabled, gcEventsCleanupEnabled);

        RepoPath repoPath = createMockRepoPath(TRASH_KEY);
        GCCandidate gcCandidate = new GCCandidate(repoPath, "sha1", "sha2", "md5", 0L);

        trashService.undeployFromTrash(gcCandidate);

        // verify
        if(gcEventsCleanupEnabled) {
            verify(repoService, times(1)).undeploy(any(RepoPath.class), anyBoolean(), anyBoolean());
            verify(repoService, times(0)).gcUndeploy(any(RepoPath.class), anyBoolean(), anyBoolean());
        } else {
            verify(repoService, times(0)).undeploy(any(RepoPath.class), anyBoolean(), anyBoolean());
            verify(repoService, times(1)).gcUndeploy(any(RepoPath.class), anyBoolean(), anyBoolean());
        }
    }

    @NonNull
    private static RepoPath createMockRepoPath(String repoKey) {
        RepoPath repoPath = mock(RepoPath.class);
        when(repoPath.isFile()).thenReturn(true);
        when(repoPath.isRoot()).thenReturn(false);
        when(repoPath.getRepoKey()).thenReturn(repoKey);
        return repoPath;
    }

    @Test
    void testRestoreCallsTrashPropertiesUtils() {
        // Given
        RepoPath repoPath = mock(RepoPath.class);
        when(repoService.localOrFederatedRepoConfigByKey("restoreRepo")).thenReturn(mock(LocalConfig.class));
        when(repoService.moveMultiTx(any(), any(), anyBoolean(), anyBoolean(), anyBoolean()))
                .thenReturn(new MoveMultiStatusHolder());
        
        // When
        try (MockedStatic<TrashPropertiesUtils> mockedUtils = mockStatic(TrashPropertiesUtils.class)) {
            trashService.restore(repoPath, "restoreRepo", "restorePath");
            
            // Then
            mockedUtils.verify(() -> TrashPropertiesUtils.cleanupTrashProperties(propertiesService, repoPath));
        }
    }
}
