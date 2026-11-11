package org.artifactory.intransit.interceptor;

import org.apache.http.HttpStatus;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.release.bundle.ReleaseBundleAddon;
import org.artifactory.addon.release.bundle.exception.ReleaseBundleArtifactValidationException;
import org.artifactory.api.common.BasicStatusHolder;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.bundle.LightArtifactsBundle;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.status.MutableStatusHolder;
import org.artifactory.md.Properties;
import org.artifactory.model.common.RepoPathImpl;
import org.artifactory.properties.PropertiesImpl;
import org.artifactory.release.bundle.ReleaseBundleUtils;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.system.SystemRepos;
import org.artifactory.sapi.fs.VfsItem;
import org.artifactory.sapi.interceptor.CopyMoveCheckResult;
import org.artifactory.sapi.interceptor.context.BulkCopyMoveContext;
import org.artifactory.test.ArtifactoryHomeBoundTest;
import org.mockito.Mockito;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Factory;
import org.testng.annotations.Test;

import java.util.List;

import static org.artifactory.intransit.interceptor.IntransitInterceptor.REQUIRED_SCOPE_TOKEN;
import static org.artifactory.sapi.interceptor.CopyMoveCheckResult.ALLOW;
import static org.artifactory.sapi.interceptor.CopyMoveCheckResult.REJECT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.testng.AssertJUnit.assertFalse;

/**
 * @author Eli Skoran 09/11/2022
 */
public class IntransitInterceptorTest extends ArtifactoryHomeBoundTest {

    boolean enabledEnhancedSecurity;
    private AuthorizationService authorizationService;
    private SecurityService securityService;
    private ReleaseBundleAddon releaseBundleAddon;
    private IntransitInterceptor interceptor;

    @Factory
    public static Object[] entireTestParams() {
        return new Object[] {
                new IntransitInterceptorTest(true),
                new IntransitInterceptorTest(false)
        };
    }

    public IntransitInterceptorTest(boolean enabledEnhancedSecurity) {
        this.enabledEnhancedSecurity = enabledEnhancedSecurity;
    }

    @BeforeMethod
    public void setup() {
        authorizationService = mock(AuthorizationService.class);
        securityService = mock(SecurityService.class);
        releaseBundleAddon = mock(ReleaseBundleAddon.class);

        interceptor = new IntransitInterceptor(authorizationService, securityService);

        ArtifactoryContext artifactoryContext = Mockito.mock(ArtifactoryContext.class);
        ArtifactoryContextThreadBinder.bind(artifactoryContext);
        homeStub.setProperty(ConstantValues.enhancedDistributionRepoSecurityEnabled.getPropertyName(),
                String.valueOf(enabledEnhancedSecurity));
    }

    @AfterMethod
    public void cleanup() {
        verifyNoMoreInteractions(authorizationService);
        ArtifactoryContextThreadBinder.unbind();
    }

    @Test
    public void testBeforeCreate_notRelevant() {
        /*
            When create is invoked on an item that is not in _intransit this interceptor should just exit
         */
        // setup
        VfsItem item = mock(VfsItem.class);
        when(item.getRepoPath()).thenReturn(new RepoPathImpl("docker-local", "folder1/file2"));
        MutableStatusHolder statusHolder = new BasicStatusHolder();

        // test
        interceptor.beforeCreate(item, statusHolder);

        verify(item).getRepoPath();
    }

    @Test
    public void testAssertDeletePathAllowed_notRelevant() {
        /*
            When AssertDeletePathAllowed is invoked on an item that is not in _intransit this interceptor should just
             exit
         */
        // setup
        RepoPath repoPath = new RepoPathImpl("docker-local", "folderA/file1.bin");
        MutableStatusHolder statusHolder = new BasicStatusHolder();

        // test
        interceptor.assertDeletePathAllowed(repoPath, statusHolder);
    }

    @Test
    public void testCheckCopyMove_notRelevant_source_and_target() {
        /*
            When isCopyOrMoveAllowed is invoked on source or target that are not _intransit this interceptor should
            just exit
         */
        // setup
        VfsItem item = mock(VfsItem.class);
        when(item.getRepoPath()).thenReturn(new RepoPathImpl("docker-local-snapshot", "folder1/file2"));
        RepoPath targetRepoPath = new RepoPathImpl("docker-local", "folderA/file1.bin");
        MutableStatusHolder statusHolder = new BasicStatusHolder();

        // test
        interceptor.checkCopyMove(item, targetRepoPath, statusHolder);
    }

    @Test
    public void testBeforeCreate_regularUser() throws ReleaseBundleArtifactValidationException {
        /*
            When create is invoked on an item in _intransit by a user without the required scope token, the operation
             should fail - if the feature is enabled
         */
        // setup
        VfsItem item = mock(VfsItem.class);
        RepoPath repoPath = new RepoPathImpl(SystemRepos.IN_TRANSIT_REPO_KEY, "rb1/1.0.0/folderA/file1.bin");
        when(item.getRepoPath()).thenReturn(repoPath);
        when(item.isFile()).thenReturn(true);
        when(authorizationService.isGrantedByAccessToken(REQUIRED_SCOPE_TOKEN)).thenReturn(false);
        when(securityService.currentUsername()).thenReturn("admin");

        AddonsManager addonsManager = mock(AddonsManager.class);
        when(ContextHelper.get().beanForType(AddonsManager.class)).thenReturn(addonsManager);
        when(addonsManager.addonByType(ReleaseBundleAddon.class)).thenReturn(releaseBundleAddon);
        RepoPath targetRepo = ReleaseBundleUtils.extractTargetPathFromIntransitPath(repoPath);
        when(releaseBundleAddon.isArtifactPartOfTargetBundle("rb1", "1.0.0", targetRepo.toPath()))
                .thenReturn(true);

        MutableStatusHolder statusHolder = new BasicStatusHolder();

        // test
        interceptor.beforeCreate(item, statusHolder);

        if (enabledEnhancedSecurity) {
            assertTrue(statusHolder.isError());
            assertEquals(statusHolder.getStatusMsg(), "upload to " + SystemRepos.IN_TRANSIT_REPO_KEY + " is forbidden");
            assertNotNull(statusHolder.getCancelException());
            assertEquals(HttpStatus.SC_FORBIDDEN, statusHolder.getStatusCode());

            verify(authorizationService).isGrantedByAccessToken(REQUIRED_SCOPE_TOKEN);
            verify(securityService).currentUsername();
        } else {
            assertFalse(statusHolder.isError());
        }
        verify(item).getRepoPath();
        verify(item).isFile();
    }

    @Test
    public void testAssertDeletePathAllowed_regularUser() {
        /*
            When AssertDeletePathAllowed is invoked on an item in _intransit by a user without the required scope
            token, the operation should fail
         */
        // setup
        RepoPath repoPath = new RepoPathImpl(SystemRepos.IN_TRANSIT_REPO_KEY, "rb1/1.0.0/folderA/file1.bin");
        when(authorizationService.isGrantedByAccessToken(REQUIRED_SCOPE_TOKEN)).thenReturn(false);
        when(securityService.currentUsername()).thenReturn("admin");
        MutableStatusHolder statusHolder = new BasicStatusHolder();

        // test
        interceptor.assertDeletePathAllowed(repoPath, statusHolder);

        if (enabledEnhancedSecurity) {
            assertTrue(statusHolder.isError());
            assertEquals(statusHolder.getStatusMsg(),
                    "external operations on " + SystemRepos.IN_TRANSIT_REPO_KEY + " are forbidden");
            assertNotNull(statusHolder.getCancelException());
            assertEquals(HttpStatus.SC_FORBIDDEN, statusHolder.getStatusCode());

            verify(authorizationService).isGrantedByAccessToken(REQUIRED_SCOPE_TOKEN);
            verify(securityService).currentUsername();
        } else {
            assertFalse(statusHolder.isError());
        }
    }

    @Test(dataProvider = "trueFalseProvider")
    public void testBeforeCopyMoveFiles_source_regularUser(boolean copy) {
        /*
            When BeforeCopyFiles is invoked on an item from _intransit by a user without the required scope
            token, the operation should fail
         */
        // setup
        VfsItem item = mock(VfsItem.class);
        when(item.getRepoPath()).thenReturn(new RepoPathImpl(SystemRepos.IN_TRANSIT_REPO_KEY, "folder1/file2"));
        RepoPath targetRepoPath = new RepoPathImpl("docker-local", "folderA/file1.bin");
        MutableStatusHolder statusHolder = new BasicStatusHolder();
        BulkCopyMoveContext context = new BulkCopyMoveContext(List.of(item), List.of(targetRepoPath), statusHolder);

        when(authorizationService.isGrantedByAccessToken(REQUIRED_SCOPE_TOKEN)).thenReturn(false);
        when(securityService.currentUsername()).thenReturn("admin");

        // test
        String operation;
        if (copy) {
            interceptor.beforeCopyFiles(context);
            operation = "copying to/from ";
        } else {
            interceptor.beforeMoveFiles(context);
            operation = "moving to/from ";
        }

        if (enabledEnhancedSecurity) {
            assertTrue(statusHolder.isError());
            assertNotNull(statusHolder.getCancelException());
            assertEquals(statusHolder.getStatusMsg(), operation + SystemRepos.IN_TRANSIT_REPO_KEY + " is forbidden");
            assertEquals(HttpStatus.SC_FORBIDDEN, statusHolder.getStatusCode());

            verify(authorizationService).isGrantedByAccessToken(REQUIRED_SCOPE_TOKEN);
            verify(securityService).currentUsername();
        } else {
            assertFalse(statusHolder.isError());
        }
    }

    @Test
    public void testBeforeMove_source_regularUser() {
        /*
            When IsCopyOrMoveAllowed is invoked on an item from _intransit by a user without the required scope
            token, the operation should fail
         */
        // setup
        VfsItem item = mock(VfsItem.class);
        when(item.getRepoPath()).thenReturn(new RepoPathImpl(SystemRepos.IN_TRANSIT_REPO_KEY, "folder1/file2"));
        RepoPath targetRepoPath = new RepoPathImpl("docker-local", "folderA/file1.bin");
        MutableStatusHolder statusHolder = new BasicStatusHolder();
        Properties properties = new PropertiesImpl();

        when(authorizationService.isGrantedByAccessToken(REQUIRED_SCOPE_TOKEN)).thenReturn(false);
        when(securityService.currentUsername()).thenReturn("admin");

        // test
        interceptor.beforeMove(item, targetRepoPath, statusHolder, properties);

        if (enabledEnhancedSecurity) {
            assertTrue(statusHolder.isError());
            assertNotNull(statusHolder.getCancelException());
            assertEquals(statusHolder.getStatusMsg(),
                    "moving to/from " + SystemRepos.IN_TRANSIT_REPO_KEY + " is forbidden");
            assertEquals(HttpStatus.SC_FORBIDDEN, statusHolder.getStatusCode());

            verify(authorizationService).isGrantedByAccessToken(REQUIRED_SCOPE_TOKEN);
            verify(securityService).currentUsername();
        } else {
            assertFalse(statusHolder.isError());
        }
    }

    @Test
    public void testCheckCopyMove_source_regularUser() {
        /*
            When IsCopyOrMoveAllowed is invoked on an item from _intransit by a user without the required scope
            token, the operation should fail
         */
        // setup
        VfsItem item = mock(VfsItem.class);
        when(item.getRepoPath()).thenReturn(new RepoPathImpl(SystemRepos.IN_TRANSIT_REPO_KEY, "folder1/file2"));
        RepoPath targetRepoPath = new RepoPathImpl("docker-local", "folderA/file1.bin");
        MutableStatusHolder statusHolder = new BasicStatusHolder();

        when(authorizationService.isGrantedByAccessToken(REQUIRED_SCOPE_TOKEN)).thenReturn(false);
        when(securityService.currentUsername()).thenReturn("admin");

        // test
        CopyMoveCheckResult result = interceptor.checkCopyMove(item, targetRepoPath, statusHolder);
        if (enabledEnhancedSecurity) {
            verify(authorizationService).isGrantedByAccessToken(REQUIRED_SCOPE_TOKEN);
            verify(securityService).currentUsername();

            assertEquals(REJECT, result);
            assertTrue(statusHolder.isError());
        } else {
            assertEquals(ALLOW, result);
            assertFalse(statusHolder.isError());
        }
    }

    @Test(dataProvider = "trueFalseProvider")
    public void testBeforeCopyMoveFiles_target_regularUser(boolean copy) {
        /*
            When BeforeCopyFiles is invoked when moving into _intransit by a user regardless of the token, the
            operation should fail
         */
        // setup
        VfsItem item = mock(VfsItem.class);
        when(item.getRepoPath()).thenReturn(new RepoPathImpl("docker-local", "folder1/file2"));
        RepoPath targetRepoPath = new RepoPathImpl(SystemRepos.IN_TRANSIT_REPO_KEY, "folderA/file1.bin");
        MutableStatusHolder statusHolder = new BasicStatusHolder();
        BulkCopyMoveContext context = new BulkCopyMoveContext(List.of(item), List.of(targetRepoPath), statusHolder);

        when(securityService.currentUsername()).thenReturn("admin");

        // test
        String operation;
        if (copy) {
            interceptor.beforeCopyFiles(context);
            operation = "copying to/from ";
        } else {
            interceptor.beforeMoveFiles(context);
            operation = "moving to/from ";
        }

        if (enabledEnhancedSecurity) {
            assertTrue(statusHolder.isError());
            assertNotNull(statusHolder.getCancelException());
            assertEquals(statusHolder.getStatusMsg(), operation + SystemRepos.IN_TRANSIT_REPO_KEY + " is forbidden");
            assertEquals(HttpStatus.SC_FORBIDDEN, statusHolder.getStatusCode());
            verify(securityService).currentUsername();
        } else {
            assertFalse(statusHolder.isError());
        }

    }

    @Test
    public void testBeforeMove_target_regularUser() {
        /*
            When BeforeMove is invoked when moving into _intransit by a user regardless of the token, the
            operation should fail
         */
        // setup
        VfsItem item = mock(VfsItem.class);
        when(item.getRepoPath()).thenReturn(new RepoPathImpl("docker-local", "folder1/file2"));

        RepoPath targetRepoPath = new RepoPathImpl(SystemRepos.IN_TRANSIT_REPO_KEY, "folderA/file1.bin");
        MutableStatusHolder statusHolder = new BasicStatusHolder();
        Properties properties = new PropertiesImpl();

        when(securityService.currentUsername()).thenReturn("admin");

        // test
        interceptor.beforeMove(item, targetRepoPath, statusHolder, properties);

        if (enabledEnhancedSecurity) {
            assertTrue(statusHolder.isError());
            assertNotNull(statusHolder.getCancelException());
            assertEquals(statusHolder.getStatusMsg(),
                    "moving to/from " + SystemRepos.IN_TRANSIT_REPO_KEY + " is forbidden");
            assertEquals(HttpStatus.SC_FORBIDDEN, statusHolder.getStatusCode());
            verify(securityService).currentUsername();
        } else {
            assertFalse(statusHolder.isError());
        }

    }

    @Test
    public void testCheckCopyMove_target_regularUser() {
        /*
            When IsCopyOrMoveAllowed is invoked when copying into _intransit by a user regardless of the token, the
            operation should fail
         */
        // setup
        VfsItem item = mock(VfsItem.class);
        when(item.getRepoPath()).thenReturn(new RepoPathImpl("docker-local", "folder1/file2"));

        RepoPath targetRepoPath = new RepoPathImpl(SystemRepos.IN_TRANSIT_REPO_KEY, "folderA/file1.bin");
        MutableStatusHolder statusHolder = new BasicStatusHolder();

        when(securityService.currentUsername()).thenReturn("admin");

        // test
        CopyMoveCheckResult result = interceptor.checkCopyMove(item, targetRepoPath, statusHolder);

        if (enabledEnhancedSecurity) {
            verify(securityService).currentUsername();
            assertEquals(REJECT, result);
            assertTrue(statusHolder.isError());
        } else {
            assertEquals(ALLOW, result);
            assertFalse(statusHolder.isError());
        }
    }

    @Test
    public void testBeforeCreate_systemUser_irrelevantFile() throws ReleaseBundleArtifactValidationException {
        /*
            When create is invoked on an item in _intransit by a systemUser but on a file that
             is not part of a bundle, the operation should be blocked
         */

        // setup
        AddonsManager addonsManager = mock(AddonsManager.class);
        when(ContextHelper.get().beanForType(AddonsManager.class)).thenReturn(addonsManager);
        when(addonsManager.addonByType(ReleaseBundleAddon.class)).thenReturn(releaseBundleAddon);

        VfsItem item = mock(VfsItem.class);
        RepoPathImpl intransitRepoPath =
                new RepoPathImpl(SystemRepos.IN_TRANSIT_REPO_KEY, "rb1/1.0.0/docker-local/manifest.json");
        RepoPath targetRepo = ReleaseBundleUtils.extractTargetPathFromIntransitPath(intransitRepoPath);

        when(item.getRepoPath()).thenReturn(intransitRepoPath);
        when(item.isFile()).thenReturn(true);
        when(securityService.currentUsername()).thenReturn("_system_");
        when(releaseBundleAddon.isArtifactPartOfTargetBundle("rb1", "1.0.0", targetRepo.toPath()))
                .thenReturn(false);
        MutableStatusHolder statusHolder = new BasicStatusHolder();

        // test
        interceptor.beforeCreate(item, statusHolder);

        verify(item).getRepoPath();
        verify(item).isFile();

        if (enabledEnhancedSecurity) {
            verify(securityService).currentUsername();
        }

        verify(releaseBundleAddon).isArtifactPartOfTargetBundle("rb1", "1.0.0", targetRepo.toPath());
        assertTrue(statusHolder.isError());
        assertNotNull(statusHolder.getCancelException());
        assertEquals(statusHolder.getStatusMsg(), "upload to " + SystemRepos.IN_TRANSIT_REPO_KEY +
                " is only allowed for artifacts that are part of a release bundle");
        assertEquals(HttpStatus.SC_FORBIDDEN, statusHolder.getStatusCode());
    }

    @Test
    public void testBeforeCreate_systemUser_failed_validation() throws ReleaseBundleArtifactValidationException {
        /*
            When create is invoked on an item in _intransit by a systemUser but on a file that
             is not part of a bundle, the operation should be blocked
         */

        // setup
        AddonsManager addonsManager = mock(AddonsManager.class);
        when(ContextHelper.get().beanForType(AddonsManager.class)).thenReturn(addonsManager);
        when(addonsManager.addonByType(ReleaseBundleAddon.class)).thenReturn(releaseBundleAddon);

        VfsItem item = mock(VfsItem.class);
        RepoPathImpl intransitRepoPath =
                new RepoPathImpl(SystemRepos.IN_TRANSIT_REPO_KEY, "rb1/1.0.0/docker-local/manifest.json");
        RepoPath targetRepo = ReleaseBundleUtils.extractTargetPathFromIntransitPath(intransitRepoPath);

        when(item.getRepoPath()).thenReturn(intransitRepoPath);
        when(item.isFile()).thenReturn(true);
        when(securityService.currentUsername()).thenReturn("_system_");
        when(releaseBundleAddon.isArtifactPartOfTargetBundle("rb1", "1.0.0", targetRepo.toPath()))
                .thenThrow(new ReleaseBundleArtifactValidationException(new RuntimeException("an error occurred")));
        MutableStatusHolder statusHolder = new BasicStatusHolder();

        // test
        interceptor.beforeCreate(item, statusHolder);

        verify(item).getRepoPath();
        verify(item).isFile();

        if (enabledEnhancedSecurity) {
            verify(securityService).currentUsername();
        }

        verify(releaseBundleAddon).isArtifactPartOfTargetBundle("rb1", "1.0.0", targetRepo.toPath());
        assertTrue(statusHolder.isError());
        assertEquals(HttpStatus.SC_INTERNAL_SERVER_ERROR, statusHolder.getStatusCode());
        assertNotNull(statusHolder.getCancelException());
        assertEquals("Failed validating artifact docker-local/manifest.json" +
                        " which is taking part of the release bundle rb1/1.0.0",
                statusHolder.getStatusMsg());
    }

    @Test
    public void testBeforeCreate_systemUser() throws ReleaseBundleArtifactValidationException {
        /*
            When create is invoked on an item in _intransit by a systemUser, the operation will not be blocked
         */

        // setup
        AddonsManager addonsManager = mock(AddonsManager.class);
        when(ContextHelper.get().beanForType(AddonsManager.class)).thenReturn(addonsManager);
        when(addonsManager.addonByType(ReleaseBundleAddon.class)).thenReturn(releaseBundleAddon);

        VfsItem item = mock(VfsItem.class);
        RepoPathImpl intransitRepoPath =
                new RepoPathImpl(SystemRepos.IN_TRANSIT_REPO_KEY, "rb1/1.0.0/docker-local/manifest.json");
        RepoPath targetRepo = ReleaseBundleUtils.extractTargetPathFromIntransitPath(intransitRepoPath);

        when(item.isFile()).thenReturn(true);
        when(item.getRepoPath()).thenReturn(intransitRepoPath);
        when(securityService.currentUsername()).thenReturn("_system_");
        when(releaseBundleAddon.isArtifactPartOfTargetBundle("rb1", "1.0.0", targetRepo.toPath())).thenReturn(true);
        MutableStatusHolder statusHolder = new BasicStatusHolder();

        // test
        interceptor.beforeCreate(item, statusHolder);

        verify(item).getRepoPath();
        verify(item).isFile();

        if (enabledEnhancedSecurity) { // if feature is disalbed no need to check for users
            verify(securityService).currentUsername();
        }
        verify(releaseBundleAddon).isArtifactPartOfTargetBundle("rb1", "1.0.0", targetRepo.toPath());
    }

    @Test
    public void testBeforeCreate_systemUser_folder() {
        /*
            When create is invoked on an folder in _intransit by a systemUser , the operation will not be blocked
            even if this folder is not part a release bundle.
         */

        // setup
        VfsItem item = mock(VfsItem.class);
        RepoPathImpl intransitRepoPath =
                new RepoPathImpl(SystemRepos.IN_TRANSIT_REPO_KEY, "rb1/1.0.0/docker-local/manifest.json");
        RepoPath targetRepo = ReleaseBundleUtils.extractTargetPathFromIntransitPath(intransitRepoPath);
        List<LightArtifactsBundle> mockedBundles = List.of(mock(LightArtifactsBundle.class));

        when(item.isFile()).thenReturn(false);
        when(item.getRepoPath()).thenReturn(intransitRepoPath);
        when(securityService.currentUsername()).thenReturn("_system_");
        MutableStatusHolder statusHolder = new BasicStatusHolder();

        // test
        interceptor.beforeCreate(item, statusHolder);

        verify(item).getRepoPath();
        verify(item).isFile();

        assertFalse(statusHolder.isError());
    }

    @Test
    public void testAssertDeletePathAllowed_systemUser() {
        /*
            When AssertDeletePathAllowed is invoked on an item in _intransit by a systemUser, the operation will not
            be blocked
         */

        // setup
        RepoPath repoPath = new RepoPathImpl(SystemRepos.IN_TRANSIT_REPO_KEY, "rb1/1.0.0/folderA/file1.bin");
        when(securityService.currentUsername()).thenReturn("_system_");
        MutableStatusHolder statusHolder = new BasicStatusHolder();

        // test
        interceptor.assertDeletePathAllowed(repoPath, statusHolder);

        if (enabledEnhancedSecurity) {
            verify(securityService).currentUsername();
        }
        assertFalse(statusHolder.isError());
    }

    @Test(dataProvider = "trueFalseProvider")
    public void testBeforeCopyMoveFiles_source_systemUser(boolean copy) {
        /*
            When BeforeCopyFiles is invoked on an item from _intransit by a systemUser, the operation will not
            be blocked
         */

        // setup
        VfsItem item = mock(VfsItem.class);
        when(item.getRepoPath()).thenReturn(new RepoPathImpl(SystemRepos.IN_TRANSIT_REPO_KEY, "folder1/file2"));
        RepoPath targetRepoPath = new RepoPathImpl("docker-local", "folderA/file1.bin");
        MutableStatusHolder statusHolder = new BasicStatusHolder();
        BulkCopyMoveContext context = new BulkCopyMoveContext(List.of(item), List.of(targetRepoPath), statusHolder);

        when(securityService.currentUsername()).thenReturn("_system_");

        // test
        if (copy) {
            interceptor.beforeCopyFiles(context);
        } else {
            interceptor.beforeMoveFiles(context);
        }

        if (enabledEnhancedSecurity) {
            verify(securityService).currentUsername();
        }
        assertFalse(statusHolder.isError());
    }

    @Test
    public void testBeforeMove_source_systemUser() {
        /*
            When BeforeMove is invoked on an item from _intransit by a systemUser, the operation will not
            be blocked
         */

        // setup
        VfsItem item = mock(VfsItem.class);
        when(item.getRepoPath()).thenReturn(new RepoPathImpl(SystemRepos.IN_TRANSIT_REPO_KEY, "folder1/file2"));
        RepoPath targetRepoPath = new RepoPathImpl("docker-local", "folderA/file1.bin");
        MutableStatusHolder statusHolder = new BasicStatusHolder();
        Properties properties = new PropertiesImpl();

        when(securityService.currentUsername()).thenReturn("_system_");

        // test
        interceptor.beforeMove(item, targetRepoPath, statusHolder, properties);

        if (enabledEnhancedSecurity) {
            verify(securityService).currentUsername();
        }
        assertFalse(statusHolder.isError());
    }

    @Test
    public void testCheckCopyMove_source_systemUser() {
        /*
            When IsCopyOrMoveAllowed is invoked on an item from _intransit by a systemUser, the operation will not
            be blocked
         */

        // setup
        VfsItem item = mock(VfsItem.class);
        when(item.getRepoPath()).thenReturn(new RepoPathImpl(SystemRepos.IN_TRANSIT_REPO_KEY, "folder1/file2"));
        RepoPath targetRepoPath = new RepoPathImpl("docker-local", "folderA/file1.bin");
        MutableStatusHolder statusHolder = new BasicStatusHolder();

        when(securityService.currentUsername()).thenReturn("_system_");

        // test
        interceptor.checkCopyMove(item, targetRepoPath, statusHolder);

        if (enabledEnhancedSecurity) {
            verify(securityService).currentUsername();
        }
        assertFalse(statusHolder.isError());
    }

    @Test(dataProvider = "trueFalseProvider")
    public void testBeforeCopyMoveFiles_target_systemUser(boolean copy) {
        /*
            When beforeCopyFiles is invoked on item being moved to _intransit by a systemUser, the operation
            will not  be blocked
         */

        // setup
        VfsItem item = mock(VfsItem.class);
        when(item.getRepoPath()).thenReturn(new RepoPathImpl("docker-local", "folder1/file2"));
        RepoPath targetRepoPath = new RepoPathImpl(SystemRepos.IN_TRANSIT_REPO_KEY, "folderA/file1.bin");
        MutableStatusHolder statusHolder = new BasicStatusHolder();
        BulkCopyMoveContext context = new BulkCopyMoveContext(List.of(item), List.of(targetRepoPath), statusHolder);

        when(securityService.currentUsername()).thenReturn("_system_");

        // test
        if (copy) {
            interceptor.beforeCopyFiles(context);
        } else {
            interceptor.beforeMoveFiles(context);
        }

        if (enabledEnhancedSecurity) {
            verify(securityService).currentUsername();
        }
        assertFalse(statusHolder.isError());
    }

    @Test
    public void testBeforeMove_target_systemUser() {
        /*
            When BeforeMove is invoked on item being moved to _intransit by a systemUser, the operation
            will not  be blocked
         */

        // setup
        VfsItem item = mock(VfsItem.class);
        when(item.getRepoPath()).thenReturn(new RepoPathImpl("docker-local", "folder1/file2"));
        RepoPath targetRepoPath = new RepoPathImpl(SystemRepos.IN_TRANSIT_REPO_KEY, "folderA/file1.bin");
        MutableStatusHolder statusHolder = new BasicStatusHolder();
        Properties properties = new PropertiesImpl();

        when(securityService.currentUsername()).thenReturn("_system_");

        // test
        interceptor.beforeMove(item, targetRepoPath, statusHolder, properties);

        if (enabledEnhancedSecurity) {
            verify(securityService).currentUsername();
        }
        assertFalse(statusHolder.isError());
    }

    @Test
    public void testCheckCopyMove_target_systemUser() {
        /*
            When IsCopyOrMoveAllowed is invoked on item being copied to _intransit by a systemUser, the operation
            will not  be blocked
         */

        // setup
        VfsItem item = mock(VfsItem.class);
        when(item.getRepoPath()).thenReturn(new RepoPathImpl("docker-local", "folder1/file2"));
        RepoPath targetRepoPath = new RepoPathImpl(SystemRepos.IN_TRANSIT_REPO_KEY, "folderA/file1.bin");
        MutableStatusHolder statusHolder = new BasicStatusHolder();

        when(securityService.currentUsername()).thenReturn("_system_");

        // test
        CopyMoveCheckResult result = interceptor.checkCopyMove(item, targetRepoPath, statusHolder);

        if (enabledEnhancedSecurity) {
            verify(securityService).currentUsername();
        }

        assertEquals(ALLOW, result);
        assertFalse(statusHolder.isError());
    }

    @Test
    public void testBeforeCreate_withCorrectScope() throws ReleaseBundleArtifactValidationException {
        /*
            When create is invoked on an item in _intransit by a user with the required scope token, the operation
            will not be blocked
         */
        // setup
        AddonsManager addonsManager = mock(AddonsManager.class);
        when(ContextHelper.get().beanForType(AddonsManager.class)).thenReturn(addonsManager);
        when(addonsManager.addonByType(ReleaseBundleAddon.class)).thenReturn(releaseBundleAddon);

        VfsItem item = mock(VfsItem.class);
        RepoPathImpl intransitRepoPath =
                new RepoPathImpl(SystemRepos.IN_TRANSIT_REPO_KEY, "rb1/1.0.0/docker-local/manifest.json");
        RepoPath targetRepo = ReleaseBundleUtils.extractTargetPathFromIntransitPath(intransitRepoPath);

        when(item.getRepoPath()).thenReturn(intransitRepoPath);
        when(item.isFile()).thenReturn(true);
        when(securityService.currentUsername()).thenReturn("user1");
        when(authorizationService.isGrantedByAccessToken(REQUIRED_SCOPE_TOKEN)).thenReturn(true);
        when(releaseBundleAddon.isArtifactPartOfTargetBundle("rb1", "1.0.0", targetRepo.toPath())).thenReturn(true);
        MutableStatusHolder statusHolder = new BasicStatusHolder();

        // test
        interceptor.beforeCreate(item, statusHolder);

        verify(item).getRepoPath();
        verify(item).isFile();

        if (enabledEnhancedSecurity) {
            verify(securityService).currentUsername();
            verify(authorizationService).isGrantedByAccessToken(REQUIRED_SCOPE_TOKEN);
        }

        verify(releaseBundleAddon).isArtifactPartOfTargetBundle("rb1", "1.0.0", targetRepo.toPath());
        assertFalse(statusHolder.isError());
    }

    @Test
    public void testBeforeCreate_withCorrectScope_irrelevantFile() throws ReleaseBundleArtifactValidationException {
        /*
            When create is invoked on an item in _intransit by a user with the required scope token but on a file tha
             is not part of a bundle, the operation should be blocked
         */
        // setup
        AddonsManager addonsManager = mock(AddonsManager.class);
        when(ContextHelper.get().beanForType(AddonsManager.class)).thenReturn(addonsManager);
        when(addonsManager.addonByType(ReleaseBundleAddon.class)).thenReturn(releaseBundleAddon);

        VfsItem item = mock(VfsItem.class);
        RepoPathImpl intransitRepoPath =
                new RepoPathImpl(SystemRepos.IN_TRANSIT_REPO_KEY, "rb1/1.0.0/docker-local/manifest.json");
        RepoPath targetRepo = ReleaseBundleUtils.extractTargetPathFromIntransitPath(intransitRepoPath);

        when(item.getRepoPath()).thenReturn(intransitRepoPath);
        when(item.isFile()).thenReturn(true);
        when(securityService.currentUsername()).thenReturn("user1");
        when(authorizationService.isGrantedByAccessToken(REQUIRED_SCOPE_TOKEN)).thenReturn(true);
        when(releaseBundleAddon.isArtifactPartOfTargetBundle("rb1", "1.0.0", targetRepo.toPath())).thenReturn(
                false);
        MutableStatusHolder statusHolder = new BasicStatusHolder();

        // test
        interceptor.beforeCreate(item, statusHolder);

        verify(item).getRepoPath();
        verify(item).isFile();
        if (enabledEnhancedSecurity) {
            verify(securityService).currentUsername();
            verify(authorizationService).isGrantedByAccessToken(REQUIRED_SCOPE_TOKEN);
        }
        verify(releaseBundleAddon).isArtifactPartOfTargetBundle("rb1", "1.0.0", targetRepo.toPath());

        assertTrue(statusHolder.isError());
        assertNotNull(statusHolder.getCancelException());
        assertEquals(statusHolder.getStatusMsg(), "upload to " + SystemRepos.IN_TRANSIT_REPO_KEY +
                " is only allowed for artifacts that are part of a release bundle");
        assertEquals(HttpStatus.SC_FORBIDDEN, statusHolder.getStatusCode());
    }

    @Test
    public void testAssertDeletePathAllowed_withCorrectScope() {
        /*
            When AssertDeletePathAllowed is invoked on an item in _intransit by a user with the required scope token,
             the operation will not be blocked
         */
        // setup
        RepoPath repoPath = new RepoPathImpl(SystemRepos.IN_TRANSIT_REPO_KEY, "rb1/1.0.0/folderA/file1.bin");
        when(securityService.currentUsername()).thenReturn("user1");
        when(authorizationService.isGrantedByAccessToken(REQUIRED_SCOPE_TOKEN)).thenReturn(true);
        MutableStatusHolder statusHolder = new BasicStatusHolder();

        // test
        interceptor.assertDeletePathAllowed(repoPath, statusHolder);

        if (enabledEnhancedSecurity) {
            verify(securityService).currentUsername();
            verify(authorizationService).isGrantedByAccessToken(REQUIRED_SCOPE_TOKEN);
        }
        assertFalse(statusHolder.isError());
    }

    @Test
    public void testBeforeMove_source_withCorrectScope() {
        /*
            When beforeMove is invoked on an item in _intransit by a user with the required scope token,
             the operation will not be blocked
         */
        // setup
        VfsItem item = mock(VfsItem.class);
        when(item.getRepoPath()).thenReturn(new RepoPathImpl(SystemRepos.IN_TRANSIT_REPO_KEY, "folder1/file2"));
        RepoPath targetRepoPath = new RepoPathImpl("docker-local", "folderA/file1.bin");
        MutableStatusHolder statusHolder = new BasicStatusHolder();
        Properties properties = new PropertiesImpl();

        when(securityService.currentUsername()).thenReturn("user1");
        when(authorizationService.isGrantedByAccessToken(REQUIRED_SCOPE_TOKEN)).thenReturn(true);

        // test
        interceptor.beforeMove(item, targetRepoPath, statusHolder, properties);

        if (enabledEnhancedSecurity) {
            verify(securityService).currentUsername();
            verify(authorizationService).isGrantedByAccessToken(REQUIRED_SCOPE_TOKEN);
        }
        assertFalse(statusHolder.isError());
    }

    @Test(dataProvider = "trueFalseProvider")
    public void testBeforeCopyMoveFiles_source_withCorrectScope(boolean copy) {
        /*
            When beforeCopyFiles is invoked on an item in _intransit by a user with the required scope token,
             the operation will not be blocked
         */
        // setup
        VfsItem item = mock(VfsItem.class);
        when(item.getRepoPath()).thenReturn(new RepoPathImpl(SystemRepos.IN_TRANSIT_REPO_KEY, "folder1/file2"));
        RepoPath targetRepoPath = new RepoPathImpl("docker-local", "folderA/file1.bin");
        MutableStatusHolder statusHolder = new BasicStatusHolder();
        BulkCopyMoveContext context = new BulkCopyMoveContext(List.of(item), List.of(targetRepoPath), statusHolder);

        when(securityService.currentUsername()).thenReturn("user1");
        when(authorizationService.isGrantedByAccessToken(REQUIRED_SCOPE_TOKEN)).thenReturn(true);

        // test
        if (copy) {
            interceptor.beforeCopyFiles(context);
        } else {
            interceptor.beforeMoveFiles(context);
        }

        if (enabledEnhancedSecurity) {
            verify(securityService).currentUsername();
            verify(authorizationService).isGrantedByAccessToken(REQUIRED_SCOPE_TOKEN);
        }
        assertFalse(statusHolder.isError());
    }

    @Test
    public void testCheckCopyMove_source_withCorrectScope() {
        /*
            When IsCopyOrMoveAllowed is invoked on an item in _intransit by a user with the required scope token,
             the operation will not be blocked
         */
        // setup
        VfsItem item = mock(VfsItem.class);
        when(item.getRepoPath()).thenReturn(new RepoPathImpl(SystemRepos.IN_TRANSIT_REPO_KEY, "folder1/file2"));
        RepoPath targetRepoPath = new RepoPathImpl("docker-local", "folderA/file1.bin");
        MutableStatusHolder statusHolder = new BasicStatusHolder();

        when(securityService.currentUsername()).thenReturn("user1");
        when(authorizationService.isGrantedByAccessToken(REQUIRED_SCOPE_TOKEN)).thenReturn(true);

        // test
        interceptor.checkCopyMove(item, targetRepoPath, statusHolder);

        if (enabledEnhancedSecurity) {
            verify(securityService).currentUsername();
            verify(authorizationService).isGrantedByAccessToken(REQUIRED_SCOPE_TOKEN);
        }
        assertFalse(statusHolder.isError());
    }

    @Test(dataProvider = "trueFalseProvider")
    public void testPropertiesAddRemove_notRelevant(boolean addProp) {
        /*
            When changing propes is invoked on an item that is not in _intransit this interceptor should just exit
         */
        // setup
        VfsItem item = mock(VfsItem.class);
        when(item.getRepoPath()).thenReturn(new RepoPathImpl("docker-local", "folder1/file2"));
        MutableStatusHolder statusHolder = new BasicStatusHolder();
        String key = "propKey";
        String[] values = {"v1", "v2"};

        // test
        if (addProp) {
            interceptor.beforePropertyCreate(item, statusHolder, key, values);
        } else {
            interceptor.beforePropertyDelete(item, statusHolder, key);
        }

        verify(item).getRepoPath();
    }

    @Test(dataProvider = "trueFalseProvider")
    public void testPropertiesAddRemove_regularUser(boolean addProp) {
        /*
            modifying properties on an _intrasit item is not allowed for any user
        */
        // setup
        VfsItem item = mock(VfsItem.class);
        when(item.getRepoPath()).thenReturn(new RepoPathImpl(SystemRepos.IN_TRANSIT_REPO_KEY, "folder1/file2"));
        MutableStatusHolder statusHolder = new BasicStatusHolder();
        String key = "propKey";
        String[] values = {"v1", "v2"};

        when(securityService.currentUsername()).thenReturn("admin");
        when(authorizationService.isGrantedByAccessToken(REQUIRED_SCOPE_TOKEN)).thenReturn(false);

        // test
        if (addProp) {
            interceptor.beforePropertyCreate(item, statusHolder, key, values);
        } else {
            interceptor.beforePropertyDelete(item, statusHolder, key);
        }

        if (enabledEnhancedSecurity) {
            assertTrue(statusHolder.isError());
            assertNotNull(statusHolder.getCancelException());
            assertEquals(statusHolder.getStatusMsg(),
                    "modifying properties on an item from " + SystemRepos.IN_TRANSIT_REPO_KEY + " is forbidden");
            assertEquals(HttpStatus.SC_FORBIDDEN, statusHolder.getStatusCode());

            verify(securityService).currentUsername();
            verify(authorizationService).isGrantedByAccessToken(REQUIRED_SCOPE_TOKEN);
        } else {
            assertFalse(statusHolder.isError());
        }
    }

    @Test(dataProvider = "trueFalseProvider")
    public void testPropertiesAddRemove_withCorrectScope(boolean addProp) {
        /*
            modifying properties on an _intrasit item is not allowed for any user
        */
        // setup
        VfsItem item = mock(VfsItem.class);
        when(item.getRepoPath()).thenReturn(new RepoPathImpl(SystemRepos.IN_TRANSIT_REPO_KEY, "folder1/file2"));
        MutableStatusHolder statusHolder = new BasicStatusHolder();
        String key = "propKey";
        String[] values = {"v1", "v2"};

        when(securityService.currentUsername()).thenReturn("admin");
        when(authorizationService.isGrantedByAccessToken(REQUIRED_SCOPE_TOKEN)).thenReturn(true);

        // test
        if (addProp) {
            interceptor.beforePropertyCreate(item, statusHolder, key, values);
        } else {
            interceptor.beforePropertyDelete(item, statusHolder, key);
        }
        if (enabledEnhancedSecurity) {
            verify(securityService).currentUsername();
            verify(authorizationService).isGrantedByAccessToken(REQUIRED_SCOPE_TOKEN);
        }
        assertFalse(statusHolder.isError());
        // verify
    }

    @Test(dataProvider = "trueFalseProvider")
    public void testPropertiesAddRemove_systemUser(boolean addProp) {
        /*
            Only _system_ user is allowed to change properties on an item from _intransit repo
         */
        // setup
        VfsItem item = mock(VfsItem.class);
        when(item.getRepoPath()).thenReturn(new RepoPathImpl(SystemRepos.IN_TRANSIT_REPO_KEY, "folder1/file2"));
        MutableStatusHolder statusHolder = new BasicStatusHolder();
        String key = "propKey";
        String[] values = {"v1", "v2"};

        when(securityService.currentUsername()).thenReturn("_system_");

        // test
        if (addProp) {
            interceptor.beforePropertyCreate(item, statusHolder, key, values);
        } else {
            interceptor.beforePropertyDelete(item, statusHolder, key);
        }

        // verify
        if (enabledEnhancedSecurity) {
            verify(securityService).currentUsername();
        }
        assertFalse(statusHolder.isError());
    }

    @DataProvider(name = "trueFalseProvider")
    public static Object[][] dataProvider() {
        return new Object[][] {
                {true},
                {false},
        };
    }
}