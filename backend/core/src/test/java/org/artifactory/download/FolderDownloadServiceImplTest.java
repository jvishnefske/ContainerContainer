package org.artifactory.download;

import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.xray.XrayAddon;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.download.FolderDownloadInfo;
import org.artifactory.api.repo.exception.BlackedOutException;
import org.artifactory.api.repo.storage.FolderSummaryInfo;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.descriptor.config.CentralConfigDescriptor;
import org.artifactory.descriptor.download.FolderDownloadConfigDescriptor;
import org.artifactory.fs.ItemInfo;
import org.artifactory.model.RealRepoConfig;
import org.artifactory.model.RemoteRepositoryRepoTypeConfig;
import org.artifactory.model.common.RepoPathImpl;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.RepoPathFactory;
import org.artifactory.repo.service.InternalRepositoryService;
import org.artifactory.storage.fs.service.FileService;
import org.jfrog.common.archive.ArchiveType;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.artifactory.repo.system.SystemRepos.SUPPORT_BUNDLE_REPO_KEY;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;

/**
 * @author Tom Arie
 */
public class FolderDownloadServiceImplTest {

    // default config params
    boolean enabled = true;
    boolean enabledForAnonymous = false;
    int maxDownloadSizeMb = 1024;
    long maxFiles = 5000;
    int maxConcurrentRequests = 10;
    boolean enabledEmptyDirectories = false;

    @Mock
    private FileService fileService;

    @Mock
    private AddonsManager addonsManager;

    private CentralConfigDescriptor oldCentralConfigDescriptor;

    @Mock
    private ArtifactoryContext context;

    @Mock
    private CentralConfigService centralConfigService;

    @Mock
    private InternalRepositoryService repositoryService;

    @Mock
    private AuthorizationService authService;

    @Spy
    @InjectMocks
    private FolderDownloadServiceImpl folderDownloadService;
    private AutoCloseable mocks;

    @BeforeMethod
    private void setup() {
        mocks = MockitoAnnotations.openMocks(this);
        when(context.beanForType(CentralConfigService.class)).thenReturn(centralConfigService);
        ArtifactoryContextThreadBinder.bind(context);
        FolderDownloadConfigDescriptor oldFolderDownloadConfigDescriptor =
                getFolderDownloadDescriptor(enabled, enabledForAnonymous, maxDownloadSizeMb,
                        maxFiles, maxConcurrentRequests, enabledEmptyDirectories);
        oldCentralConfigDescriptor = mockConfigDescriptor(oldFolderDownloadConfigDescriptor);
    }

    @AfterMethod
    private void cleanup() throws Exception {
        ArtifactoryContextThreadBinder.unbind();
        mocks.close();
    }

    @Test(dataProvider = "provideFolderDownloadConfigValues")
    public void testReload(boolean enabled, boolean enabledForAnonymous, int maxDownloadSizeMb, long maxFiles,
            int maxConcurrentRequests, boolean enabledEmptyDirectories, int numOfSetParamsInvokes) {
        //given:
        FolderDownloadConfigDescriptor newFolderDownloadDescriptor = getFolderDownloadDescriptor(enabled,
                enabledForAnonymous, maxDownloadSizeMb, maxFiles, maxConcurrentRequests, enabledEmptyDirectories);
        FolderDownloadConfigDescriptor spyNewFolderDownloadDescriptor = Mockito.spy(newFolderDownloadDescriptor);
        CentralConfigDescriptor centralConfigDescriptor = mockConfigDescriptor(spyNewFolderDownloadDescriptor);
        when(centralConfigService.getDescriptor()).thenReturn(centralConfigDescriptor);
        folderDownloadService.init();
        //when:
        folderDownloadService.reload(oldCentralConfigDescriptor, null);
        //then:
        verify(folderDownloadService, times(numOfSetParamsInvokes)).
                setFolderDownloadConfigBaseParams(spyNewFolderDownloadDescriptor);
    }

    @Test(expectedExceptions = BlackedOutException.class)
    public void testBlackedOutException() throws BlackedOutException {
        String repoKey = "local";
        RealRepoConfig repoConfig = Mockito.mock(RealRepoConfig.class);
        when(repoConfig.getRepoTypeConfig()).thenReturn(Mockito.mock(RemoteRepositoryRepoTypeConfig.class));
        when(repoConfig.getRepoTypeConfig().isBlackedOut()).thenReturn(true);
        when(repoConfig.isLocal()).thenReturn(true);
        when(repositoryService.getRepositoryConfigByKey(repoKey)).thenReturn(repoConfig);

        FolderDownloadConfigDescriptor newFolderDownloadDescriptor = getFolderDownloadDescriptor(enabled,
                enabledForAnonymous, maxDownloadSizeMb, maxFiles, maxConcurrentRequests, enabledEmptyDirectories);
        folderDownloadService.setFolderDownloadConfigBaseParams(newFolderDownloadDescriptor);

        folderDownloadService.downloadFolder(RepoPathFactory.create(repoKey), ArchiveType.ZIP, true);
    }

    /**
     * Coverage/regression guard for the disabled-service branch: when the repo is not jfrog-support-bundle
     * and serviceEnabled is false, downloadFolder() must throw FolderDownloadException with SC_FORBIDDEN.
     * Locks in that the RTDEV-94103 refactor left the pre-existing "functionality is disabled" gate
     * intact for regular repos.
     */
    @Test(expectedExceptions = FolderDownloadException.class,
            expectedExceptionsMessageRegExp = ".*functionality is disabled.*")
    public void testDownloadFolderDeniedWhenServiceDisabled() throws BlackedOutException {
        folderDownloadService.setFolderDownloadConfigBaseParams(
                getFolderDownloadDescriptor(false, enabledForAnonymous, maxDownloadSizeMb, maxFiles,
                        maxConcurrentRequests, enabledEmptyDirectories));

        folderDownloadService.downloadFolder(RepoPathFactory.create("regular-repo"), ArchiveType.ZIP, false);
    }

    /**
     * RTDEV-94103: the size-limit shortcut for jfrog-support-bundle must still run assertPath(), so callers
     * that cannot read the repo (non-admin without INTERNAL_SUPPORT_BUNDLE_READ scope) are rejected with 403.
     */
    @Test(expectedExceptions = FolderDownloadException.class,
            expectedExceptionsMessageRegExp = ".*don't have the required permissions.*")
    public void testSupportBundleDownloadDeniedWhenCanReadFalse() throws BlackedOutException {
        mockLocalRepoConfig(SUPPORT_BUNDLE_REPO_KEY);
        when(authService.canRead(Mockito.any(RepoPath.class))).thenReturn(false);

        folderDownloadService.downloadFolder(RepoPathFactory.create(SUPPORT_BUNDLE_REPO_KEY), ArchiveType.ZIP, false);
    }

    /**
     * RTDEV-94103 regression guard: when canRead is true (admin or scoped token) the request must still reach
     * the support-bundle size-limit shortcut, and canRead must have been invoked before it - locking in the
     * "assertPath before shortcut" ordering that closes the bypass.
     */
    @Test
    public void testSupportBundleDownloadAllowedWhenCanReadTrue() throws BlackedOutException {
        mockLocalRepoConfig(SUPPORT_BUNDLE_REPO_KEY);
        when(authService.canRead(Mockito.any(RepoPath.class))).thenReturn(true);
        Mockito.doReturn(null).when(folderDownloadService)
                .downloadFolderNoSizeLimit(Mockito.any(RepoPath.class), Mockito.any(ArchiveType.class),
                        Mockito.anyBoolean());

        RepoPath path = RepoPathFactory.create(SUPPORT_BUNDLE_REPO_KEY);
        folderDownloadService.downloadFolder(path, ArchiveType.ZIP, false);

        verify(authService).canRead(path);
        verify(folderDownloadService).downloadFolderNoSizeLimit(path, ArchiveType.ZIP, false);
    }

    /**
     * RTDEV-94103: the reporter's actual repro targeted an exact file (...artifactory-*.zip). assertPath's
     * folder-vs-file guard must reject the request with 400 before the shortcut runs, closing the file-root
     * gap where ItemTree.getRootNode() would otherwise return a FileNode without a security filter.
     */
    @Test(expectedExceptions = FolderDownloadException.class,
            expectedExceptionsMessageRegExp = ".*is not a folder.*")
    public void testSupportBundleDownloadRejectedForExactFileTarget() throws BlackedOutException {
        mockLocalRepoConfig(SUPPORT_BUNDLE_REPO_KEY);
        ItemInfo fileItem = mock(ItemInfo.class);
        when(fileItem.isFolder()).thenReturn(false);
        when(repositoryService.getItemInfo(Mockito.any(RepoPath.class))).thenReturn(fileItem);

        folderDownloadService.downloadFolder(
                RepoPathFactory.create(SUPPORT_BUNDLE_REPO_KEY, "jfrt/x/artifactory/artifactory-x.zip"),
                ArchiveType.ZIP, false);
    }

    private void mockLocalRepoConfig(String repoKey) {
        RealRepoConfig repoConfig = Mockito.mock(RealRepoConfig.class);
        when(repoConfig.getRepoTypeConfig()).thenReturn(Mockito.mock(RemoteRepositoryRepoTypeConfig.class));
        when(repoConfig.getRepoTypeConfig().isBlackedOut()).thenReturn(false);
        when(repoConfig.isLocal()).thenReturn(true);
        when(repositoryService.getRepositoryConfigByKey(repoKey)).thenReturn(repoConfig);
    }

    private CentralConfigDescriptor mockConfigDescriptor(
            FolderDownloadConfigDescriptor spyNewFolderDownloadDescriptor) {
        CentralConfigDescriptor centralConfigDescriptor = mock(CentralConfigDescriptor.class);
        when(centralConfigDescriptor.getFolderDownloadConfig()).thenReturn(spyNewFolderDownloadDescriptor);
        return centralConfigDescriptor;
    }

    private FolderDownloadConfigDescriptor getFolderDownloadDescriptor(boolean enabled, boolean enabledForAnonymous,
            int maxDownloadSizeMb, long maxFiles, int maxConcurrentRequests, boolean enabledEmptyDirectories) {
        FolderDownloadConfigDescriptor folderDownloadDescriptor = new FolderDownloadConfigDescriptor();
        folderDownloadDescriptor.setEnabled(enabled);
        folderDownloadDescriptor.setEnabledForAnonymous(enabledForAnonymous);
        folderDownloadDescriptor.setMaxDownloadSizeMb(maxDownloadSizeMb);
        folderDownloadDescriptor.setMaxFiles(maxFiles);
        folderDownloadDescriptor.setMaxConcurrentRequests(maxConcurrentRequests);
        folderDownloadDescriptor.setEnabledEmptyDirectories(enabledEmptyDirectories);
        return folderDownloadDescriptor;
    }

    @DataProvider
    public static Object[][] provideFolderDownloadConfigValues() {
        return new Object[][] {
                // latest value represents the amount of calls performed
                // to set folder download config values. if two configs are different
                // we are expecting to receive 2 calls, otherwise, 1 because setFolderDownloadConfigBaseParams
                // is invoked on both init of folderDownload config and after two configs are different
                // {true, false, 1024, 5000, 10, false, 1} - default config for testing
                // enabled, enabledForAnonymous, maxDownloadSizeMb, maxFiles,
                // maxConcurrentRequests, enabledEmptyDirectories, numOfSetParamsInvokes
                {false, false, 1024, 5000, 10, false, 2},
                {true, false, 1024, 5000, 10, true, 2},
                {true, true, 1024, 5000, 10, true, 2},
                {true, false, 1000, 5000, 10, true, 2},
                {true, false, 1024, 500, 10, true, 2},
                {true, false, 1024, 5000, 8, true, 2},
                {true, false, 1024, 5000, 10, false, 1}
        };
    }

    @Test
    public void testCollectFolderInfo() {
        FolderSummaryInfo folderSummaryInfo = mock(FolderSummaryInfo.class);
        RepoPath repoPath = new RepoPathImpl("repo-local", "org/test");

        when(fileService.getFilesCountAndSize(repoPath)).thenReturn(folderSummaryInfo);
        when(folderSummaryInfo.getFolderSize()).thenReturn(104857600L);
        when(folderSummaryInfo.getFileCount()).thenReturn(100L);

        XrayAddon xrayAddon = mock(XrayAddon.class);
        when(addonsManager.addonByType(XrayAddon.class)).thenReturn(xrayAddon);

        FolderDownloadInfo result = folderDownloadService.collectFolderInfo(repoPath);

        verify(fileService).getFilesCountAndSize(repoPath);

        assertEquals(result.getSizeMb(), 100);
        assertEquals(result.getTotalFiles(), 100L);
    }
}
