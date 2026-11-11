package org.artifactory.repo.interceptor;

import org.artifactory.api.common.BasicStatusHolder;
import org.artifactory.api.security.SecurityService;
import org.artifactory.common.status.StatusEntry;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.RepoPathFactory;
import org.artifactory.sapi.fs.VfsItem;
import org.artifactory.sapi.interceptor.context.DeleteContext;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.artifactory.repo.system.SystemRepos.LOG_SHIPPING_REPO_KEY;
import static org.artifactory.repo.system.SystemRepos.USAGE_REPO_KEY;
import static org.artifactory.sapi.interceptor.CopyMoveCheckResult.ALLOW;
import static org.artifactory.sapi.interceptor.CopyMoveCheckResult.REJECT;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * @author Tomer Slutsky
 */
public class ObservabilityInterceptorTest {

    @Mock
    private SecurityService securityService;
    @Mock
    private VfsItem item;
    private ObservabilityInterceptorForTest observabilityInterceptor;
    private AutoCloseable mocks;

    @DataProvider
    public static Object[][] deleteProvider() {
        return new Object[][] {
                {LOG_SHIPPING_REPO_KEY, false},
                {LOG_SHIPPING_REPO_KEY, true},
                {USAGE_REPO_KEY, true},
                {USAGE_REPO_KEY, false}
        };
    }

    @DataProvider
    public static Object[][] beforeCreateProviderFail() {
        return new Object[][] {
                {LOG_SHIPPING_REPO_KEY, false},
                {USAGE_REPO_KEY, false},
        };
    }

    @DataProvider
    public static Object[][] beforeCreateProviderSuccess() {
        return new Object[][] {
                {LOG_SHIPPING_REPO_KEY, true},
                {USAGE_REPO_KEY, true},
                {"random", true},
                {"random", false},
        };
    }

    @DataProvider
    public static Object[][] beforeDeleteProviderSuccess() {
        return new Object[][] {
                {LOG_SHIPPING_REPO_KEY},
                {"random"}
        };
    }

    @DataProvider
    public static Object[][] copyMoveProviderFailFrom() {
        return new Object[][] {
                {LOG_SHIPPING_REPO_KEY, "random", false},
                {USAGE_REPO_KEY, "random", false},
                {USAGE_REPO_KEY, LOG_SHIPPING_REPO_KEY, false},
                {LOG_SHIPPING_REPO_KEY, LOG_SHIPPING_REPO_KEY, false}
        };
    }

    @DataProvider
    public static Object[][] copyMoveProviderFailTo() {
        return new Object[][] {
                {"random", LOG_SHIPPING_REPO_KEY, false},
                {"random", USAGE_REPO_KEY, false}
        };
    }

    @DataProvider
    public static Object[][] copyMoveProviderAllowed() {
        return new Object[][] {
                {"random", LOG_SHIPPING_REPO_KEY, true},
                {"random", USAGE_REPO_KEY, true},
                {LOG_SHIPPING_REPO_KEY, LOG_SHIPPING_REPO_KEY, true},
                {USAGE_REPO_KEY, USAGE_REPO_KEY, true}
        };
    }

    @BeforeMethod
    public void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        observabilityInterceptor = new ObservabilityInterceptorForTest(securityService);
    }

    @AfterMethod
    public void tearDown() throws Exception {
        mocks.close();
    }

    @Test(dataProvider = "deleteProvider")
    public void testAssertDeleteRepoNotAllowed(String repoKey, boolean isSystem) {
        BasicStatusHolder statusHolder = new BasicStatusHolder();
        mockAuthService(isSystem);
        observabilityInterceptor.assertDeleteRepoAllowed(repoKey, statusHolder);
        assertStatusHolder(statusHolder, "Deletion of Observability repository " + repoKey + " is not allowed.");
    }

    @Test
    public void testAssertDeleteRepoPassesOnRegularRepo() {
        BasicStatusHolder statusHolder = new BasicStatusHolder();
        mockAuthService(false);
        observabilityInterceptor.assertDeleteRepoAllowed("random", statusHolder);
        assertFalse(statusHolder.isWarning());
    }

    @Test(dataProvider = "beforeCreateProviderFail")
    public void testBeforeCreateFails(String repoKey, boolean isSystem) {
        BasicStatusHolder statusHolder = new BasicStatusHolder();
        mockAuthService(isSystem);
        mockItem(repoKey);
        observabilityInterceptor.beforeCreate(item, statusHolder);
        assertStatusHolder(statusHolder, "Deployment into " + repoKey + " repo is forbidden");

    }

    @Test(dataProvider = "beforeCreateProviderSuccess")
    public void testBeforeCreatePasses(String repoKey, boolean isSystem) {
        BasicStatusHolder statusHolder = new BasicStatusHolder();
        mockAuthService(isSystem);
        mockItem(repoKey);
        observabilityInterceptor.beforeCreate(item, statusHolder);
        assertFalse(statusHolder.isWarning());

    }

    @Test(dataProvider = "beforeDeleteProviderSuccess")
    public void testBeforeDeletePasses(String repoKey) {
        BasicStatusHolder statusHolder = new BasicStatusHolder();
        mockItem(repoKey);
        observabilityInterceptor.beforeDelete(item, statusHolder, new DeleteContext(item.getRepoPath()));
        assertFalse(statusHolder.isWarning());
    }

    @Test
    public void testBeforeDeleteFails() {
        BasicStatusHolder statusHolder = new BasicStatusHolder();
        mockItem(USAGE_REPO_KEY);
        observabilityInterceptor.beforeDelete(item, statusHolder, new DeleteContext(item.getRepoPath()));
        assertStatusHolder(statusHolder,
                "Deletion of artifacts from repository " + USAGE_REPO_KEY + " is not allowed.");

    }

    @Test
    public void testBeforeDeletePassesUsage() {
        BasicStatusHolder statusHolder = new BasicStatusHolder();
        mockItem(USAGE_REPO_KEY);
        mockAuthService(true);
        observabilityInterceptor.beforeDelete(item, statusHolder, new DeleteContext(item.getRepoPath()));
        assertFalse(statusHolder.isWarning());
    }

    @Test(dataProvider = "copyMoveProviderFailFrom")
    public void testCheckCopyMoveFailsFrom(String repoKeyFrom, String repoKeyTo, boolean isSystem) {
        BasicStatusHolder statusHolder = new BasicStatusHolder();
        mockAuthService(isSystem);
        mockItem(repoKeyFrom);
        RepoPath repoPath = RepoPathFactory.create(repoKeyTo, "");
        assertEquals(observabilityInterceptor.checkCopyMove(item, repoPath, statusHolder), REJECT);
        assertStatusHolder(statusHolder, "Copy/Move from " + repoKeyFrom + " repo is forbidden");
    }

    @Test(dataProvider = "copyMoveProviderFailTo")
    public void testCheckCopyMoveFailsTo(String repoKeyFrom, String repoKeyTo, boolean isSystem) {
        BasicStatusHolder statusHolder = new BasicStatusHolder();
        mockAuthService(isSystem);
        mockItem(repoKeyFrom);
        RepoPath repoPath = RepoPathFactory.create(repoKeyTo, "");
        assertEquals(observabilityInterceptor.checkCopyMove(item, repoPath, statusHolder), REJECT);
        assertStatusHolder(statusHolder, "Copy/Move into " + repoKeyTo + " repo is forbidden");
    }

    @Test(dataProvider = "copyMoveProviderAllowed")
    public void testCheckCopyMove(String repoKeyFrom, String repoKeyTo, boolean isSystem) {
        BasicStatusHolder statusHolder = new BasicStatusHolder();
        mockAuthService(isSystem);
        mockItem(repoKeyFrom);
        RepoPath repoPath = RepoPathFactory.create(repoKeyTo, "");
        assertEquals(observabilityInterceptor.checkCopyMove(item, repoPath, statusHolder), ALLOW);
        assertFalse(statusHolder.isWarning());
    }

    @Test
    public void testDeleteAllowedForDashboardUser() {
        observabilityInterceptor.isDashboardUser = true;
        BasicStatusHolder statusHolder = new BasicStatusHolder();
        mockItem(USAGE_REPO_KEY);
        mockAuthService(false);
        observabilityInterceptor.beforeDelete(item, statusHolder, new DeleteContext(item.getRepoPath()));
        assertFalse(statusHolder.isWarning());
    }

    private void assertStatusHolder(BasicStatusHolder statusHolder, String message) {
        assertTrue(statusHolder.isWarning());
        assertEquals(statusHolder.getWarnings().size(), 1);
        StatusEntry lastWarning = statusHolder.getLastWarning();
        assertEquals(lastWarning.getStatusCode(), 403);
        assertEquals(lastWarning.getMessage(), message);
    }

    private void mockItem(String repoKey) {
        when(item.getRepoKey()).thenReturn(repoKey);
    }

    private void mockAuthService(boolean isSystem) {
        when(securityService.currentUsername())
                .thenReturn(isSystem ? SecurityService.USER_SYSTEM : "notsystem");
    }

    private static class ObservabilityInterceptorForTest extends ObservabilityInterceptor {

        private boolean isDashboardUser = false;

        public ObservabilityInterceptorForTest(SecurityService securityService) {
            super(securityService);
        }

        @Override
        boolean isDashboardUser() {
            return isDashboardUser;
        }
    }
}