package org.artifactory.repo.service.flexible;

import org.apache.http.HttpStatus;
import org.artifactory.api.common.MoveMultiStatusHolder;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.aql.AqlService;
import org.artifactory.aql.api.domain.sensitive.AqlApiItem;
import org.artifactory.aql.result.AqlLazyResult;
import org.artifactory.aql.result.rows.AqlItem;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.exception.CancelException;
import org.artifactory.fs.ItemInfo;
import org.artifactory.fs.MutableItemInfo;
import org.artifactory.model.RepoConfig;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.cache.expirable.CacheExpiryImpl;
import org.artifactory.repo.interceptor.StorageInterceptors;
import org.artifactory.repo.service.InternalRepositoryService;
import org.artifactory.repo.service.flexible.context.MoveCopyContext;
import org.artifactory.repo.service.flexible.interfaces.FlatMoveCopyServiceInternal;
import org.artifactory.repo.service.flexible.tools.MoveCopyHelper;
import org.artifactory.sapi.fs.VfsItem;
import org.artifactory.spring.InternalArtifactoryContext;
import org.artifactory.spring.InternalContextHelper;
import org.artifactory.storage.fs.MutableVfsItem;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.stream.Stream;

import static org.artifactory.common.ConstantValues.excludeLocalGeneratedFlatCopyMove;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * @author Amir Avidan
 */
class FlatMoveCopyServiceImplTest extends ArtifactoryHomeBoundJUnitTest {

    @Mock
    private InternalRepositoryService repositoryService;
    @Mock
    private AqlService aqlService;
    @Mock
    private AuthorizationService authService;
    @Mock
    private StorageInterceptors storageInterceptors;
    @Mock
    private BulkProvider bulkProvider;
    @Mock
    private MoveCopyContext context;
    @Mock
    private CacheExpiryImpl cacheExpiry;
    @Mock
    private FlatMoveCopyServiceInternal flatMoveCopyServiceInternal;
    @Mock
    private AqlApiItem aqlApiItem;
    @Mock
    private ResultSet resultSet;
    @Mock
    AqlLazyResult<AqlItem> aqlLazyResult;
    MoveMultiStatusHolder statusHolder;

    private MockedStatic<ContextHelper> mockedContextHelper;

    private AutoCloseable autoCloseable;
    private FlatMoveCopyServiceImpl flatMoveCopyService;

    @BeforeEach
    void setUp() {
        autoCloseable = MockitoAnnotations.openMocks(this);
        mockedContextHelper = mockStatic(ContextHelper.class);
        InternalArtifactoryContext artifactoryContext = Mockito.mock(InternalArtifactoryContext.class);
        mockedContextHelper.when(InternalContextHelper::get).thenReturn(artifactoryContext);
        flatMoveCopyService =
                new FlatMoveCopyServiceImpl(repositoryService, aqlService, authService, storageInterceptors);
        flatMoveCopyService.setCacheExpiryImpl(cacheExpiry);
        when(artifactoryContext.beanForType(FlatMoveCopyServiceInternal.class)).thenReturn(flatMoveCopyServiceInternal);
        statusHolder = new MoveMultiStatusHolder();
    }

    @AfterEach
    public void tearDown() throws Exception {
        mockedContextHelper.close();
        autoCloseable.close();
    }

    @ParameterizedTest
    @MethodSource("provideTestArguments")
    void testExecuteMoveCopyOnBulk(boolean isLocalGenerated, int size, RepoType repoType,
            String excludeLocalGeneratedFlatCopyMoveParam) {
        VfsItem<ItemInfo> sourceItem = mock(VfsItem.class);
        VfsItem<ItemInfo> originalTargetItem = mock(VfsItem.class);
        MutableVfsItem<MutableItemInfo> targetItem = mock(MutableVfsItem.class);
        MoveCopyItems itemInfo = new MoveCopyItems(sourceItem, originalTargetItem, targetItem);
        RepoPath repoPath = mock(RepoPath.class);
        RepoConfig repoConfig = mock(RepoConfig.class);
        when(repoPath.getPath()).thenReturn("path");
        when(sourceItem.getRepoPath()).thenReturn(repoPath);
        when(bulkProvider.getBulk()).thenReturn(List.of(itemInfo));
        when(repositoryService.getRepositoryConfigByKey(any())).thenReturn(repoConfig);
        when(repoConfig.getPackageType()).thenReturn(repoType);
        if (repoType == null) {
            when(repositoryService.getRepositoryConfigByKey(any())).thenReturn(null);
        }
        when(cacheExpiry.isLocalGenerated(any(), any(), any())).thenReturn(isLocalGenerated);
        getOrCreateArtifactoryHomeStub().setProperty(excludeLocalGeneratedFlatCopyMove.getPropertyName(),
                excludeLocalGeneratedFlatCopyMoveParam);
        try (MockedStatic<MoveCopyHelper> mockedStaticHelper = Mockito.mockStatic(MoveCopyHelper.class)) {
            mockedStaticHelper.when(
                    () -> MoveCopyHelper.validateDeleteAndSaveSession(any(), any(),
                            any(), any(), any())).thenAnswer(invocationOnMock -> null);
            flatMoveCopyService.executeMoveCopyOnBulk(bulkProvider, context);
            ArgumentCaptor<List> itemInfosCaptor = ArgumentCaptor.forClass(List.class);
            mockedStaticHelper.verify(
                    () -> MoveCopyHelper.validateDeleteAndSaveSession(
                            itemInfosCaptor.capture(),
                            any(),
                            any(),
                            any(),
                            any()
                    )
            );
            assert itemInfosCaptor.getValue().size() == size;
        }
    }

    @ParameterizedTest
    @MethodSource("executeMoveCopyTasksInBulks_exceptionCaughtProvider")
    void executeMoveCopyTasksInBulks_exceptionCaught(Exception ex, int expectedStatusCode) throws SQLException {
        doThrow(ex)
                .when(flatMoveCopyServiceInternal)
                .executeMoveCopyOnBulk(any(), eq(context));
        when(aqlLazyResult.getResultSet()).thenReturn(resultSet);
        when(aqlService.executeQueryLazy(aqlApiItem)).thenReturn(aqlLazyResult);
        when(resultSet.next()).thenReturn(true);

        flatMoveCopyService.executeMoveCopyTasksInBulks(context, aqlApiItem, statusHolder);
        assertEquals(1, statusHolder.getErrors().size());
        assertEquals(ex, statusHolder.getErrors().get(0).getException());
        assertEquals(expectedStatusCode, statusHolder.getStatusCode());
    }

    static Stream<Arguments> provideTestArguments() {
        return Stream.of(
                Arguments.of(true, 0, RepoType.Generic, "true"),
                Arguments.of(false, 1, RepoType.Generic, "true"),
                Arguments.of(true, 1, RepoType.Generic, "false"),
                Arguments.of(false, 1, RepoType.Generic, "false"),
                Arguments.of(false, 0, null, "true")
        );
    }

    public static Stream<Arguments> executeMoveCopyTasksInBulks_exceptionCaughtProvider() {
        return Stream.of(
                Arguments.of(new CancelException("CancelException",
                        HttpStatus.SC_CONFLICT), HttpStatus.SC_CONFLICT),
                Arguments.of(new RuntimeException("RuntimeException"), HttpStatus.SC_NOT_IMPLEMENTED)
        );
    }
}