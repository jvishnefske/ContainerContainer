package org.artifactory.repo.service.flexible.tools;

import com.jfrog.sysconf.SysConfig;
import org.artifactory.api.common.MoveMultiStatusHolder;
import org.artifactory.aql.api.domain.sensitive.AqlApiItem;
import org.artifactory.aql.api.internal.AqlBase;
import org.artifactory.aql.model.AqlSortTypeEnum;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.common.property.ArtifactorySystemProperties;
import org.artifactory.fs.ItemInfo;
import org.artifactory.model.common.RepoPathImpl;
import org.artifactory.repo.LocalRepo;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.RepoRepoPath;
import org.artifactory.repo.StoringRepo;
import org.artifactory.repo.interceptor.StorageInterceptors;
import org.artifactory.repo.service.flexible.MoveCopyItemInfo;
import org.artifactory.repo.service.flexible.context.MoveCopyContext;
import org.artifactory.repo.service.flexible.listeners.MoveCopyListeners;
import org.artifactory.repo.service.flexible.validators.MoveCopyValidator;
import org.artifactory.sapi.fs.VfsItem;
import org.artifactory.sapi.interceptor.CopyMoveCheckResult;
import org.artifactory.storage.db.fs.session.SqlStorageSession;
import org.artifactory.storage.fs.MutableVfsItem;
import org.artifactory.storage.fs.lock.SessionLockEntry;
import org.artifactory.storage.fs.session.StorageSession;
import org.artifactory.storage.fs.session.StorageSessionHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.artifactory.repo.service.flexible.tools.MoveCopyTestUtils.prepareCopyOrMoveOverrideMocks;
import static org.artifactory.util.evidence.EvidenceSystemYamlConstants.EVIDENCE_ENABLED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MoveCopyHelperTest {

    @Mock
    private RepoPath repoPathMock;
    @Mock
    private LocalRepo repoMock;
    @Mock
    private MutableVfsItem mutableVfsItemMock;
    @Mock
    private SqlStorageSession sqlStorageSessionMock;
    @Mock
    private SessionLockEntry sessionLockEntryMock;
    @Mock
    private MoveMultiStatusHolder moveMultiStatusHolderMock;
    @Mock
    private MoveCopyContext moveCopyContextMock;
    @Mock
    private SysConfig sysConfigMock;
    @Mock
    private StorageInterceptors storageInterceptorsMock;
    @Mock
    private MoveMultiStatusHolder statusMock;
    @Mock
    private ArtifactorySystemProperties artifactorySystemProperties;
    @Mock
    private ArtifactoryHome artifactoryHome;
    private AutoCloseable mocks;

    @BeforeEach
    public void setup() {
        mocks = MockitoAnnotations.openMocks(this);
        when(artifactoryHome.getArtifactoryProperties()).thenReturn(artifactorySystemProperties);
        when(artifactorySystemProperties.getBooleanProperty(any())).thenReturn(true);
        when(artifactoryHome.getSysConfig()).thenReturn(sysConfigMock);
        ArtifactoryHome.bind(artifactoryHome);
    }

    @AfterEach
    public void tearDown() throws Exception {
        mocks.close();
    }

    @ParameterizedTest
    @ValueSource(strings = {"generic-local", "generic-local/"})
    void createSourceQuery_WithTrashAndRepoLevelPath_EvidenceEnabled(String path) {
        when(sysConfigMock.getBoolean(eq(EVIDENCE_ENABLED), anyBoolean())).thenReturn(true);
        when(repoPathMock.getRepoKey()).thenReturn("auto-trashcan");
        when(repoPathMock.getParent()).thenReturn(new RepoPathImpl("generic-local", ""));
        when(repoPathMock.getPath()).thenReturn(path);
        when(moveCopyContextMock.getSourceRepoPath()).thenReturn(repoPathMock);
        AqlApiItem actual = MoveCopyHelper.createSourceQuery(moveCopyContextMock, AqlSortTypeEnum.asc);
        AqlBase.AndClause andClause = (AqlBase.AndClause) actual.get().get(2).get().get(0);
        assertEquals(andClause.get().size(), 4);
        AqlBase.AndClause evdAndClause = (AqlBase.AndClause) andClause.get().get(3);
        String actualNativeSql = evdAndClause.toNative(0);
        String expectedNativeSql = "\"$and\":[\n" +
                "  {\"path\":{\"$ne\":\"generic-local/.evidence\"}},\n" +
                "  {\"path\":{\"$nmatch\":\"generic-local/.evidence/*\"}},\n" +
                "  {\"$or\":[\n" +
                "    {\"path\":{\"$ne\":\"generic-local\"}},\n" +
                "    {\"name\":{\"$ne\":\".evidence\"}}\n" +
                "  ]}\n" +
                "]";
        assertEquals(actualNativeSql, expectedNativeSql);
    }

    @Test
    void createSourceQuery_WithTrashAndRepoLevelPath_EvidenceDisabled() {
        when(sysConfigMock.getBoolean(eq(EVIDENCE_ENABLED), anyBoolean())).thenReturn(false);
        when(repoPathMock.getRepoKey()).thenReturn("auto-trashcan");
        when(repoPathMock.getParent()).thenReturn(new RepoPathImpl("commons-qa-generic-local", ""));
        when(repoPathMock.getPath()).thenReturn("commons-qa-generic-local");
        when(moveCopyContextMock.getSourceRepoPath()).thenReturn(repoPathMock);
        AqlApiItem actual = MoveCopyHelper.createSourceQuery(moveCopyContextMock, AqlSortTypeEnum.asc);
        AqlBase.AndClause andClause = (AqlBase.AndClause) actual.get().get(2).get().get(0);
        assertEquals(andClause.get().size(), 3);
    }

    @Test
    void createSourceQuery_WithNoTrashAndRepoLevelPath() {
        when(sysConfigMock.getBoolean(eq(EVIDENCE_ENABLED), anyBoolean())).thenReturn(true);
        when(repoPathMock.getRepoKey()).thenReturn("generic-local");
        when(repoPathMock.getPath()).thenReturn("");
        when(moveCopyContextMock.getSourceRepoPath()).thenReturn(repoPathMock);
        when(repoPathMock.getParent()).thenReturn(null);
        when(moveCopyContextMock.getSourceRepoPath()).thenReturn(repoPathMock);
        AqlApiItem actual = MoveCopyHelper.createSourceQuery(moveCopyContextMock, AqlSortTypeEnum.asc);
        AqlBase.AndClause andClause = (AqlBase.AndClause) actual.get().get(2).get().get(0);
        assertEquals(andClause.get().size(), 3);
    }

    @Test
    void createSourceQuery_WithTrashAndNoRepoLevelPath() {
        when(sysConfigMock.getBoolean(eq(EVIDENCE_ENABLED), anyBoolean())).thenReturn(true);
        when(repoPathMock.getRepoKey()).thenReturn("auto-trashcan");
        when(repoPathMock.getParent()).thenReturn(new RepoPathImpl("generic-local", "x"));
        when(repoPathMock.getPath()).thenReturn("generic-local/x");
        when(moveCopyContextMock.getSourceRepoPath()).thenReturn(repoPathMock);
        AqlApiItem actual = MoveCopyHelper.createSourceQuery(moveCopyContextMock, AqlSortTypeEnum.asc);
        AqlBase.AndClause andClause = (AqlBase.AndClause) actual.get().get(2).get().get(0);
        assertEquals(andClause.get().size(), 3);
    }

    @Test
    void testRunCheckCopyMoveInterceptorAllow() {
        when(storageInterceptorsMock.checkCopyMove(any(), any(), any())).thenReturn(CopyMoveCheckResult.ALLOW);
        List<MoveCopyItemInfo> invalidList = new ArrayList<>();
        List<MoveCopyItemInfo> validItems = new ArrayList<>();
        MoveCopyItemInfo mock = mock(MoveCopyItemInfo.class);
        validItems.add(mock);
        MoveCopyHelper.checkCopyMoveInterceptors(
                validItems, invalidList, statusMock, storageInterceptorsMock);
        verify(statusMock, never()).error(anyString(), anyInt(), any());
    }

    @Test
    void testRunCheckCopyMoveInterceptorReject() {
        when(storageInterceptorsMock.checkCopyMove(any(), any(), any())).thenReturn(CopyMoveCheckResult.REJECT);
        MoveMultiStatusHolder moveMultiStatusHolder = new MoveMultiStatusHolder();
        List<MoveCopyItemInfo> invalidList = new ArrayList<>();
        List<MoveCopyItemInfo> validItems = new ArrayList<>();
        MoveCopyItemInfo mock = mock(MoveCopyItemInfo.class);
        validItems.add(mock);
        MoveCopyHelper.checkCopyMoveInterceptors(validItems, invalidList, moveMultiStatusHolder,
                storageInterceptorsMock);
        assertTrue(validItems.isEmpty());
        assertEquals(1, invalidList.size());
    }

    @Test
    @SuppressWarnings("unchecked")
    void validateDeleteAndSaveSession_failFastRevertsUnvalidatedStagedChildren() {
        when(artifactorySystemProperties.getBooleanProperty(any())).thenReturn(false);
        when(artifactorySystemProperties.getBooleanProperty(ConstantValues.flatCopyMoveFullRevertOnAbort))
                .thenReturn(true);

        MutableVfsItem folderMutable = mock(MutableVfsItem.class);
        MutableVfsItem childMutable = mock(MutableVfsItem.class);
        MoveCopyItemInfo folderItem = stagedItem(folderMutable);
        MoveCopyItemInfo childItem = stagedItem(childMutable);
        List<MoveCopyItemInfo> itemsInfos = List.of(folderItem, childItem);

        MoveCopyContext context = mock(MoveCopyContext.class);
        when(context.isFailFast()).thenReturn(true);

        MoveCopyValidator denyingValidator = mock(MoveCopyValidator.class);
        when(denyingValidator.isInterested(any(), any())).thenReturn(true);
        when(denyingValidator.validate(any(), any(), any())).thenAnswer(invocation -> {
            MoveMultiStatusHolder status = invocation.getArgument(1);
            status.error("User doesn't have permissions to read source", 403,
                    org.slf4j.LoggerFactory.getLogger(MoveCopyHelperTest.class));
            return false;
        });

        StorageSession mockStorageSession = mock(StorageSession.class);
        try (MockedStatic<StorageSessionHolder> sessionHolder = Mockito.mockStatic(StorageSessionHolder.class)) {
            sessionHolder.when(StorageSessionHolder::getSession).thenReturn(mockStorageSession);

            MoveMultiStatusHolder result = MoveCopyHelper.validateDeleteAndSaveSession(
                    itemsInfos,
                    context,
                    List.of(),
                    List.of(denyingValidator),
                    mock(StorageInterceptors.class));

            assertTrue(result.isError());
            verify(folderMutable).revertChanges();
            verify(childMutable).revertChanges();
            verify(mockStorageSession).save();
            verify(denyingValidator, times(1)).validate(eq(folderItem), any(), eq(context));
            verify(denyingValidator, never()).validate(eq(childItem), any(), eq(context));
        }
    }

    /**
     * When a later item fails under failFast, earlier items already marked valid must also be reverted.
     */
    @Test
    @SuppressWarnings("unchecked")
    void validateDeleteAndSaveSession_failFastRevertsPreviouslyValidatedItems() {
        when(artifactorySystemProperties.getBooleanProperty(any())).thenReturn(false);
        when(artifactorySystemProperties.getBooleanProperty(ConstantValues.flatCopyMoveFullRevertOnAbort))
                .thenReturn(true);

        MutableVfsItem firstMutable = mock(MutableVfsItem.class);
        MutableVfsItem secondMutable = mock(MutableVfsItem.class);
        MoveCopyItemInfo firstItem = stagedItem(firstMutable);
        MoveCopyItemInfo secondItem = stagedItem(secondMutable);
        List<MoveCopyItemInfo> itemsInfos = List.of(firstItem, secondItem);

        MoveCopyContext context = mock(MoveCopyContext.class);
        when(context.isFailFast()).thenReturn(true);

        MoveCopyValidator validator = mock(MoveCopyValidator.class);
        when(validator.isInterested(any(), any())).thenReturn(true);
        when(validator.validate(eq(firstItem), any(), any())).thenReturn(true);
        when(validator.validate(eq(secondItem), any(), any())).thenAnswer(invocation -> {
            MoveMultiStatusHolder status = invocation.getArgument(1);
            status.error("denied on second item", 403,
                    org.slf4j.LoggerFactory.getLogger(MoveCopyHelperTest.class));
            return false;
        });

        StorageSession mockStorageSession = mock(StorageSession.class);
        try (MockedStatic<StorageSessionHolder> sessionHolder = Mockito.mockStatic(StorageSessionHolder.class)) {
            sessionHolder.when(StorageSessionHolder::getSession).thenReturn(mockStorageSession);

            MoveMultiStatusHolder result = MoveCopyHelper.validateDeleteAndSaveSession(
                    itemsInfos,
                    context,
                    List.of(),
                    List.of(validator),
                    mock(StorageInterceptors.class));

            assertTrue(result.isError());
            verify(firstMutable).revertChanges();
            verify(secondMutable).revertChanges();
            verify(mockStorageSession).save();
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void validateDeleteAndSaveSession_failFastKeepsLegacyPartialRevert() {
        when(artifactorySystemProperties.getBooleanProperty(any())).thenReturn(false);

        MutableVfsItem folderMutable = mock(MutableVfsItem.class);
        MutableVfsItem childMutable = mock(MutableVfsItem.class);
        MoveCopyItemInfo folderItem = stagedItem(folderMutable);
        MoveCopyItemInfo childItem = stagedItem(childMutable);
        List<MoveCopyItemInfo> itemsInfos = List.of(folderItem, childItem);

        MoveCopyContext context = mock(MoveCopyContext.class);
        when(context.isFailFast()).thenReturn(true);

        MoveCopyValidator denyingValidator = mock(MoveCopyValidator.class);
        when(denyingValidator.isInterested(any(), any())).thenReturn(true);
        when(denyingValidator.validate(any(), any(), any())).thenAnswer(invocation -> {
            MoveMultiStatusHolder status = invocation.getArgument(1);
            status.error("User doesn't have permissions to read source", 403,
                    org.slf4j.LoggerFactory.getLogger(MoveCopyHelperTest.class));
            return false;
        });

        StorageSession mockStorageSession = mock(StorageSession.class);
        try (MockedStatic<StorageSessionHolder> sessionHolder = Mockito.mockStatic(StorageSessionHolder.class)) {
            sessionHolder.when(StorageSessionHolder::getSession).thenReturn(mockStorageSession);

            MoveMultiStatusHolder result = MoveCopyHelper.validateDeleteAndSaveSession(
                    itemsInfos,
                    context,
                    List.of(),
                    List.of(denyingValidator),
                    mock(StorageInterceptors.class));

            assertTrue(result.isError());
            verify(folderMutable).revertChanges();
            verify(childMutable, never()).revertChanges();
        }
    }

    @SuppressWarnings("unchecked")
    private static MoveCopyItemInfo stagedItem(MutableVfsItem mutableTarget) {
        MoveCopyItemInfo item = mock(MoveCopyItemInfo.class);
        VfsItem<ItemInfo> sourceItem = mock(VfsItem.class);
        when(sourceItem.isFile()).thenReturn(false);
        when(sourceItem.isFolder()).thenReturn(true);
        when(item.getSourceItem()).thenReturn(sourceItem);
        when(item.getMutableTargetItem()).thenReturn(mutableTarget);
        return item;
    }

    @ParameterizedTest
    @MethodSource("provideTestArguments")
    void testValidateDeleteAndSaveSessionCopyMoveInterceptors(boolean reject,
            boolean flatCopyMoveCheckCopyMoveInterceptorsParam) {
        when(artifactorySystemProperties.getBooleanProperty(any())).thenReturn(
                flatCopyMoveCheckCopyMoveInterceptorsParam);
        List<MoveCopyItemInfo> itemsInfos = List.of(mock(MoveCopyItemInfo.class));
        MoveCopyContext context = mock(MoveCopyContext.class);
        List<MoveCopyListeners> moveCopyListeners = List.of(mock(MoveCopyListeners.class));
        List<MoveCopyValidator> validators = List.of(mock(MoveCopyValidator.class));
        StorageInterceptors storageInterceptors = mock(StorageInterceptors.class);
        StorageSession mockStorageSession = getStorageSession(itemsInfos, storageInterceptors);
        try (MockedStatic<StorageSessionHolder> mockedStaticSessionHolder = Mockito.mockStatic(
                StorageSessionHolder.class)) {
            mockedStaticSessionHolder.when(StorageSessionHolder::getSession).thenReturn(mockStorageSession);
            try (MockedStatic<MoveCopyHelper> mockedStaticHelper = Mockito.mockStatic(MoveCopyHelper.class)) {
                try (MockedStatic<MoveCopyDeleteHelper> mockedStaticDeleteHelper = Mockito.mockStatic(
                        MoveCopyDeleteHelper.class)) {
                    mockedStaticHelper.when(() -> MoveCopyHelper.validate(any(), any(), any(), any()))
                            .thenReturn(true); // Assume validation is always true
                    mockedStaticHelper.when(() -> MoveCopyHelper.shouldStopProcess(any(), any(), any()))
                            .thenReturn(false); // Assume process should not stop
                    if (reject) {
                        mockedStaticHelper.when(() -> MoveCopyHelper.shouldStopProcess(any(), any(), any()))
                                .thenReturn(false)
                                .thenReturn(true);
                        mockedStaticHelper.when(
                                        () -> MoveCopyHelper.validateDeleteAndSaveSession(any(), any(), any(), any(),
                                                any()))
                                .thenCallRealMethod();
                        mockedStaticHelper.when(
                                        () -> MoveCopyHelper.checkCopyMoveInterceptors(any(), any(),
                                                any(), any()))
                                .thenCallRealMethod();
                        MoveMultiStatusHolder result = MoveCopyHelper.validateDeleteAndSaveSession(
                                itemsInfos, context, moveCopyListeners, validators, storageInterceptors);
                        assertEquals(0, result.getMovedArtifactsCount());
                        if (flatCopyMoveCheckCopyMoveInterceptorsParam) {
                            mockedStaticHelper.verify(
                                    () -> MoveCopyHelper.notifyMoveCopyFiles(any(), any(), any(), any(), any()),
                                    never());
                        } else {
                            mockedStaticHelper.verify(
                                    () -> MoveCopyHelper.notifyMoveCopyFiles(any(), any(), any(), any(), any()),
                                    times(1));
                        }
                    } else {
                        mockedStaticHelper.when(
                                        () -> MoveCopyHelper.validateDeleteAndSaveSession(any(), any(), any(), any(),
                                                any()))
                                .thenCallRealMethod();
                        mockedStaticHelper.when(
                                        () -> MoveCopyHelper.checkCopyMoveInterceptors(any(), any(),
                                                any(), any()))
                                .thenAnswer(invocationOnMock -> null);
                        mockedStaticDeleteHelper.when(() -> MoveCopyDeleteHelper.deleteFilesIfMove(any(), any(),
                                        any()))
                                .thenAnswer(invocationOnMock -> null);
                        MoveMultiStatusHolder result = MoveCopyHelper.validateDeleteAndSaveSession(
                                itemsInfos, context, moveCopyListeners, validators, storageInterceptors);
                        assertEquals(0, result.getMovedArtifactsCount());
                    }
                }
            }
        }
    }

    private static Stream<Arguments> provideTestArguments() {
        return Stream.of(
                Arguments.of(true, true),
                Arguments.of(false, true),
                Arguments.of(true, false),
                Arguments.of(false, false)
        );
    }

    private static StorageSession getStorageSession(List<MoveCopyItemInfo> itemsInfos,
            StorageInterceptors storageInterceptors) {
        MutableVfsItem mutableVfsItem = mock(MutableVfsItem.class);
        MoveCopyItemInfo mockItemInfo = itemsInfos.get(0);
        VfsItem<ItemInfo> folder = mock(VfsItem.class);
        when(mockItemInfo.getSourceItem()).thenReturn(folder);
        when(folder.isFolder()).thenReturn(true);
        when(mockItemInfo.getMutableTargetItem()).thenReturn(mutableVfsItem);
        when(mockItemInfo.getSourceItem().isFolder()).thenReturn(true);
        RepoRepoPath<LocalRepo<?, ?>> mockTargetRrp = mock(RepoRepoPath.class);
        when(storageInterceptors.checkCopyMove(any(), any(), any())).thenReturn(CopyMoveCheckResult.REJECT);
        when(mockItemInfo.getTargetRrp()).thenReturn(mockTargetRrp);
        when(mockTargetRrp.getRepo()).thenReturn(mock(LocalRepo.class));
        when(mockTargetRrp.getRepoPath()).thenReturn(mock(RepoPath.class));
        StorageSession mockStorageSession = mock(StorageSession.class);
        return mockStorageSession;
    }

    @ParameterizedTest
    @MethodSource("overrideTargetFileProvider")
    void testOverrideTargetFileIfExist(boolean returnTargetItem,
            boolean basicStatusHolderErrorLog,
            int saveInvokeNum,
            int setWriteInvokeNum,
            int deleteInvokeNum,
            int createOrGetFileInvokeNum,
            int statusHolderErrorInvokeNum) {

        RepoRepoPath<StoringRepo> localRepoRepoRepoPath =
                prepareCopyOrMoveOverrideMocks(repoPathMock, repoMock, mutableVfsItemMock, sessionLockEntryMock,
                        sqlStorageSessionMock, moveMultiStatusHolderMock, returnTargetItem);

        MoveCopyHelper
                .overrideTargetFileIfExist(localRepoRepoRepoPath, repoMock, moveMultiStatusHolderMock,
                        basicStatusHolderErrorLog);

        verify(sessionLockEntryMock, times(saveInvokeNum)).save();
        verify(sessionLockEntryMock, times(setWriteInvokeNum)).setWriteFsItem(any());
        verify(mutableVfsItemMock, times(deleteInvokeNum)).delete(any());
        verify(repoMock, times(createOrGetFileInvokeNum)).createOrGetFile(any());
        verify(moveMultiStatusHolderMock, times(statusHolderErrorInvokeNum)).error(any(), any());
    }

    private static Stream<Arguments> overrideTargetFileProvider() {
        return Stream.of(
                Arguments.of(true, false, 1, 2, 1, 1, 0),
                Arguments.of(false, true, 0, 0, 0, 0, 1)
        );
    }
}