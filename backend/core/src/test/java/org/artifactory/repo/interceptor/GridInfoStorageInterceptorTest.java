package org.artifactory.repo.interceptor;

import org.artifactory.aol.AolService;
import org.artifactory.api.common.BasicStatusHolder;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.security.SecurityService;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.RepoPathFactory;
import org.artifactory.sapi.fs.VfsItem;
import org.artifactory.sapi.interceptor.CopyMoveCheckResult;
import org.artifactory.sapi.interceptor.context.DeleteContext;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Hussein Alshqerat
 */
class GridInfoStorageInterceptorTest extends ArtifactoryHomeBoundJUnitTest {

    @Mock
    private SecurityService securityService;
    @Mock
    private ArtifactoryContext context;
    @Mock
    private VfsItem item;

    private GridInfoStorageInterceptor gridInfoStorageInterceptor;
    private AutoCloseable mocks;

    private final String repoKey = "jfrog-grid-info";

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        ArtifactoryContextThreadBinder.bind(context);
        gridInfoStorageInterceptor = new GridInfoStorageInterceptor(securityService);
    }

    @AfterEach
    void tearDown() throws Exception {
        ArtifactoryContextThreadBinder.unbind();
        mocks.close();
    }

    @Test
    void testBeforeCreate() {
        BasicStatusHolder statusHolder = new BasicStatusHolder();
        mockAuthService(true);
        mockItem(repoKey);
        gridInfoStorageInterceptor.beforeCreate(item, statusHolder);
        Assertions.assertFalse(statusHolder.isError());
    }

    @Test
    void testBeforeCreateFailsNotUserSystem() {
        BasicStatusHolder statusHolder = new BasicStatusHolder();
        mockAuthService(false);
        mockItem(repoKey);
        gridInfoStorageInterceptor.beforeCreate(item, statusHolder);
        Assertions.assertTrue(statusHolder.isError());
    }

    @Test
    void testAssertDeleteRepoAllowedHasErrors() {
        BasicStatusHolder statusHolder = new BasicStatusHolder();
        gridInfoStorageInterceptor.assertDeleteRepoAllowed(repoKey, statusHolder);
        Assertions.assertTrue(statusHolder.hasErrors());
        Assertions.assertTrue(statusHolder.getErrors().get(0).getMessage()
                .contains("Deletion of Grid Info repository " + repoKey));
    }

    @Test
    void testBeforeDeleteFailsForNonSystemUser() {
        BasicStatusHolder statusHolder = new BasicStatusHolder();
        mockAuthService(false);
        mockItem(repoKey);
        mockAolService(false, false);
        gridInfoStorageInterceptor.beforeDelete(item, statusHolder,
                new DeleteContext(RepoPathFactory.create(repoKey, "file")));
        Assertions.assertTrue(statusHolder.isError());
    }

    @Test
    void testBeforeDeleteAllowsSystemUser() {
        BasicStatusHolder statusHolder = new BasicStatusHolder();
        mockAuthService(true);
        mockItem(repoKey);
        gridInfoStorageInterceptor.beforeDelete(item, statusHolder,
                new DeleteContext(RepoPathFactory.create(repoKey, "file")));
        Assertions.assertFalse(statusHolder.isError());
    }

    @Test
    void testBeforeCreateNoOpForNonGridInfoRepo() {
        BasicStatusHolder statusHolder = new BasicStatusHolder();
        mockAuthService(false); // non-system user, but irrelevant for a non-grid repo
        mockItem("docker-local");

        gridInfoStorageInterceptor.beforeCreate(item, statusHolder);

        Assertions.assertFalse(statusHolder.isError());
    }

    @Test
    void testAssertDeleteRepoAllowedNoOpForNonGridInfoRepo() {
        BasicStatusHolder statusHolder = new BasicStatusHolder();

        gridInfoStorageInterceptor.assertDeleteRepoAllowed("docker-local", statusHolder);

        Assertions.assertFalse(statusHolder.hasErrors());
    }

    @Test
    void testBeforeDeleteNoOpWhenTriggeredByMove() {
        BasicStatusHolder statusHolder = new BasicStatusHolder();
        mockAuthService(false);
        mockItem(repoKey);
        DeleteContext moveCtx = new DeleteContext(RepoPathFactory.create(repoKey, "file")).triggeredByMove();

        gridInfoStorageInterceptor.beforeDelete(item, statusHolder, moveCtx);

        Assertions.assertFalse(statusHolder.isError());
    }

    @Test
    void testBeforeDeleteAllowsDashboardUser() {
        BasicStatusHolder statusHolder = new BasicStatusHolder();
        mockAuthService(false);
        mockItem(repoKey);
        mockAolService(true, true);

        gridInfoStorageInterceptor.beforeDelete(item, statusHolder,
                new DeleteContext(RepoPathFactory.create(repoKey, "file")));

        Assertions.assertFalse(statusHolder.isError());
    }

    @Test
    void testCheckCopyMove_sourceIsGridInfo_systemUserAllowed() {
        mockAuthService(true);
        mockItem(repoKey);
        RepoPath targetPath = RepoPathFactory.create("docker-local", "file");

        BasicStatusHolder status = new BasicStatusHolder();
        CopyMoveCheckResult result = gridInfoStorageInterceptor.checkCopyMove(item, targetPath, status);

        Assertions.assertEquals(CopyMoveCheckResult.ALLOW, result);
    }

    @Test
    void testCheckCopyMove_sourceIsGridInfo_nonSystemUserRejected() {
        mockAuthService(false);
        mockItem(repoKey);
        BasicStatusHolder status = new BasicStatusHolder();
        RepoPath targetPath = RepoPathFactory.create("docker-local", "file");

        CopyMoveCheckResult result = gridInfoStorageInterceptor.checkCopyMove(item, targetPath, status);

        Assertions.assertEquals(CopyMoveCheckResult.REJECT, result);
        Assertions.assertTrue(status.isError());
    }

    @Test
    void testCheckCopyMove_targetIsGridInfo_nonSystemUserRejected() {
        mockAuthService(false);
        mockItem("docker-local"); // source is NOT grid info
        BasicStatusHolder status = new BasicStatusHolder();
        RepoPath targetPath = RepoPathFactory.create(repoKey, "file");

        CopyMoveCheckResult result = gridInfoStorageInterceptor.checkCopyMove(item, targetPath, status);

        Assertions.assertEquals(CopyMoveCheckResult.REJECT, result);
        Assertions.assertTrue(status.isError());
    }

    @Test
    void testCheckCopyMove_neitherGridInfo_allowed() {
        mockItem("docker-local");
        RepoPath targetPath = RepoPathFactory.create("maven-local", "file");
        BasicStatusHolder status = new BasicStatusHolder();

        CopyMoveCheckResult result = gridInfoStorageInterceptor.checkCopyMove(item, targetPath, status);

        Assertions.assertEquals(CopyMoveCheckResult.ALLOW, result);
    }

    private void mockAuthService(boolean isSystemUser) {
        when(securityService.currentUsername()).thenReturn(
                isSystemUser ? SecurityService.USER_SYSTEM : "not-system");
    }

    private void mockItem(String key) {
        when(item.getRepoKey()).thenReturn(key);
        when(item.getRepoPath()).thenReturn(RepoPathFactory.create(key, "file"));
    }

    private void mockAolService(boolean isAol, boolean isDashboardUser) {
        AolService aolService = mock(AolService.class);
        when(context.beanForType(AolService.class)).thenReturn(aolService);
        when(aolService.isAol()).thenReturn(isAol);
        when(aolService.isDashboardUser()).thenReturn(isDashboardUser);
    }
}
