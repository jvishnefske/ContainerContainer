package org.artifactory.repo.interceptor;

import org.artifactory.aol.AolService;
import org.artifactory.api.common.BasicStatusHolder;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.security.SecurityService;
import org.artifactory.repo.RepoPathFactory;
import org.artifactory.sapi.fs.VfsItem;
import org.artifactory.sapi.interceptor.context.DeleteContext;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.provider.Arguments;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.stream.Stream;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Shahar Palmor
 */
class FullSyncStorageInterceptorTest extends ArtifactoryHomeBoundJUnitTest {

    @Mock
    private SecurityService securityService;
    @Mock
    private ArtifactoryContext context;
    @Mock
    private VfsItem item;
    private FullSyncStorageInterceptor fullSyncStorageInterceptor;
    private AutoCloseable mocks;

    private String repoKey = "jfrog-full-sync-info";

    @BeforeEach
    public void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        ArtifactoryContextThreadBinder.bind(context);
        fullSyncStorageInterceptor = new FullSyncStorageInterceptor(securityService);
    }

    @AfterEach
    public void tearDown() throws Exception {
        ArtifactoryContextThreadBinder.unbind();
        mocks.close();
    }

    @Test
    public void testBeforeCreate() {
        BasicStatusHolder statusHolder = new BasicStatusHolder();
        mockAuthService(true);
        mockItem(repoKey);
        fullSyncStorageInterceptor.beforeCreate(item, statusHolder);
        Assertions.assertFalse(statusHolder.isError());
    }

    @Test
    public void testBeforeCreateFailsNotUserSystem() {
        BasicStatusHolder statusHolder = new BasicStatusHolder();
        mockAuthService(false);
        mockItem(repoKey);
        fullSyncStorageInterceptor.beforeCreate(item, statusHolder);
        Assertions.assertTrue(statusHolder.isError());
    }

    @Test
    public void testAssertDeleteRepoAllowedHasErrors() {
        BasicStatusHolder statusHolder = new BasicStatusHolder();
        mockAuthService(true);
        fullSyncStorageInterceptor.assertDeleteRepoAllowed(repoKey, statusHolder);
        Assertions.assertTrue(statusHolder.hasErrors());
        Assertions.assertTrue(statusHolder.getErrors().get(0).getMessage()
                .contains("Deletion of Full Sync Info repository " + repoKey));
    }

    @Test
    public void testAssertDeleteRepoAllowedPass() {
        BasicStatusHolder statusHolder = new BasicStatusHolder();
        mockAuthService(false);
        fullSyncStorageInterceptor.assertDeleteRepoAllowed("not-" + repoKey, statusHolder);
        Assertions.assertFalse(statusHolder.hasErrors());
    }

    @Test
    public void testBeforeDeletePasses() {
        BasicStatusHolder statusHolder = new BasicStatusHolder();
        mockItem(repoKey);
        AolService aolService = mock(AolService.class);
        when(context.beanForType(AolService.class)).thenReturn(aolService);
        when(aolService.isAol()).thenReturn(true);
        when(aolService.isDashboardUser()).thenReturn(true);
        fullSyncStorageInterceptor.beforeDelete(item, statusHolder, new DeleteContext(item.getRepoPath()));
        Assertions.assertFalse(statusHolder.isError());
    }

    @Test
    public void testBeforeDeleteFails() {
        BasicStatusHolder statusHolder = new BasicStatusHolder();
        mockItem(repoKey);
        AolService aolService = mock(AolService.class);
        when(context.beanForType(AolService.class)).thenReturn(aolService);
        when(aolService.isAol()).thenReturn(false);
        when(aolService.isDashboardUser()).thenReturn(false);
        fullSyncStorageInterceptor.beforeDelete(item, statusHolder, new DeleteContext(item.getRepoPath()));
        Assertions.assertTrue(statusHolder.isError());
    }

    @Test
    public void testIsCopyOrMoveAllowedFromFSCheckAllowed() {
        BasicStatusHolder statusHolder = new BasicStatusHolder();
        mockAuthService(true);
        mockItem(repoKey);
        fullSyncStorageInterceptor.checkCopyMove(item, RepoPathFactory.create("test"), statusHolder);
        Assertions.assertFalse(statusHolder.isError());
    }

    @Test
    public void testIsCopyOrMoveAllowedFromFSCheckNotAllowed() {
        BasicStatusHolder statusHolder = new BasicStatusHolder();
        mockAuthService(false);
        mockItem(repoKey);
        fullSyncStorageInterceptor.checkCopyMove(item, RepoPathFactory.create("test"), statusHolder);
        Assertions.assertTrue(statusHolder.isError());
    }

    @Test
    public void testIsCopyOrMoveAllowedToFSCheckAllowed() {
        BasicStatusHolder statusHolder = new BasicStatusHolder();
        mockAuthService(true);
        fullSyncStorageInterceptor.checkCopyMove(item, RepoPathFactory.create(repoKey), statusHolder);
        Assertions.assertFalse(statusHolder.isError());
    }

    @Test
    public void testIsCopyOrMoveAllowedToFSCheckNotAllowed() {
        BasicStatusHolder statusHolder = new BasicStatusHolder();
        mockAuthService(false);
        fullSyncStorageInterceptor.checkCopyMove(item, RepoPathFactory.create(repoKey), statusHolder);
        Assertions.assertTrue(statusHolder.isError());
    }

    private void mockAuthService(boolean isSystem) {
        when(securityService.currentUsername())
                .thenReturn(isSystem ? SecurityService.USER_SYSTEM : "notsystem");
    }

    private void mockItem(String repoKey) {
        when(item.getRepoKey()).thenReturn(repoKey);
    }

    private static Stream<Arguments> isSystem() {
        return Stream.of(Arguments.of(true),
                Arguments.of(false));
    }


}