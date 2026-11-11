package org.artifactory.repo.interceptor.config;

import org.artifactory.addon.AddonsManager;
import org.artifactory.aol.AolService;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.entitlement.EntitlementType;
import org.artifactory.model.LocalRepoConfig;
import org.artifactory.model.RemoteRepoConfig;
import org.artifactory.repo.RepoConfigChange;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.spring.InternalArtifactoryContext;
import org.artifactory.test.ArtifactoryHomeBoundTest;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;

import static org.artifactory.repo.RepoConfigChange.ConfigAction.REPO_UPDATE;
import static org.artifactory.repo.RepoDetailsType.REMOTE;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;
import static org.testng.AssertJUnit.assertFalse;

/**
 * @author Tamar Rosen
 */
public class RedirectReposConfigInterceptorTest extends ArtifactoryHomeBoundTest {

    private RedirectConfigInterceptor interceptor;
    private AddonsManager addonsManager;
    private CentralConfigService centralConfigService;
    private AolService aolService;

    @BeforeMethod
    public void setup() {
        centralConfigService = mock(CentralConfigService.class);
        addonsManager = mock(AddonsManager.class);
        InternalArtifactoryContext contextMock = mock(InternalArtifactoryContext.class);
        aolService = mock(AolService.class);
        when(contextMock.beanForType(AolService.class)).thenReturn(aolService);
        interceptor = new RedirectConfigInterceptor(addonsManager, centralConfigService);
        ArtifactoryContextThreadBinder.bind(contextMock);
    }

    @AfterMethod
    public void cleanup() {
        ArtifactoryContextThreadBinder.unbind();
    }

    @Test
    public void testEnableCdnRedirectForAol() {
        when(aolService.isAol()).thenReturn(true);
        when(centralConfigService.isContextCreated()).thenReturn(true);
        LocalRepoConfig localRepoConfig = LocalRepoConfig.create("cdn-local-repo", RepoType.Generic);
        localRepoConfig.getRepoTypeConfig().setCdnRedirect(true);
        localRepoConfig.getRepoTypeConfig().setDownloadRedirect(true);

        interceptor.onBeforeSave(Map.of(RepoDetailsType.LOCAL,
                List.of(new RepoConfigChange("cdn-local-repo", RepoConfigChange.ConfigAction.REPO_CREATE,
                        localRepoConfig, null))));
        // we expect download redirect to be off but cdn redirect still on
        assertFalse(localRepoConfig.getRepoTypeConfig().isDownloadRedirect());
        assertTrue(localRepoConfig.getRepoTypeConfig().isCdnRedirect());
    }

    @Test
    public void testDisableCdnRedirectOnPrem() {
        when(aolService.isAol()).thenReturn(false);
        LocalRepoConfig localRepoConfig = LocalRepoConfig.create("cdn-local-repo", RepoType.Generic);
        localRepoConfig.getRepoTypeConfig().setCdnRedirect(true);
        localRepoConfig.getRepoTypeConfig().setDownloadRedirect(true);

        interceptor.onBeforeSave(Map.of(RepoDetailsType.LOCAL,
                List.of(new RepoConfigChange(
                        "cdn-local-repo", RepoConfigChange.ConfigAction.REPO_CREATE, localRepoConfig, null))));

        assertNull(localRepoConfig.getRepoTypeConfig().getCdnRedirectRepoConfig());
        assertFalse(localRepoConfig.getRepoTypeConfig().isCdnRedirect());
    }

    @Test
    public void testEnableDownloadRedirectWhenContextIsNotCreated() {
        when(centralConfigService.isContextCreated()).thenReturn(false);
        when(addonsManager.isEntitled(EntitlementType.BINARY_PROVIDER_DOWNLOAD_REDIRECT_MIX)).thenReturn(true);
        LocalRepoConfig localRepoConfig = LocalRepoConfig.create("redirect-local-repo", RepoType.Generic);
        localRepoConfig.getRepoTypeConfig().setDownloadRedirect(true);

        interceptor.onBeforeSave(Map.of(RepoDetailsType.LOCAL,
                List.of(new RepoConfigChange("redirect-local-repo", RepoConfigChange.ConfigAction.REPO_CREATE,
                        localRepoConfig,
                        null))));

        // we expect download redirect to remain on
        assertTrue(localRepoConfig.getRepoTypeConfig().isDownloadRedirect(),
                "Should not turn off redirect download option if not all beans were loaded and configuration service" +
                        "context wasn't created");
    }

    @Test
    public void testLoadRedirectDownloadWithContextCreatedWithSupportedLicense() {
        when(centralConfigService.isContextCreated()).thenReturn(true);
        when(addonsManager.isEntitled(EntitlementType.BINARY_PROVIDER_DOWNLOAD_REDIRECT_MIX)).thenReturn(true);
        LocalRepoConfig localRepoConfig = LocalRepoConfig.create("redirect-local-repo", RepoType.Generic);
        localRepoConfig.getRepoTypeConfig().setDownloadRedirect(true);

        interceptor.onBeforeSave(Map.of(RepoDetailsType.LOCAL,
                List.of(new RepoConfigChange("redirect-local-repo", RepoConfigChange.ConfigAction.REPO_CREATE,
                        localRepoConfig,
                        null))));

        // we expect download redirect to remain on
        assertTrue(localRepoConfig.getRepoTypeConfig().isDownloadRedirect(),
                "Should not turn off redirect download option with supported licensed");
    }

    @Test
    public void testLoadRedirectDownloadWithOutEnterprisePlusOrEdgeLicense() {
        LocalRepoConfig localRepoConfig = LocalRepoConfig.create("redirect-local-repo", RepoType.Generic);
        localRepoConfig.getRepoTypeConfig().setDownloadRedirect(true);
        when(addonsManager.isEntitled(EntitlementType.BINARY_PROVIDER_DOWNLOAD_REDIRECT_MIX)).thenReturn(false);
        when(centralConfigService.isContextCreated()).thenReturn(true);
        interceptor.onBeforeSave(Map.of(RepoDetailsType.LOCAL,
                List.of(new RepoConfigChange("redirect-local-repo", RepoConfigChange.ConfigAction.REPO_CREATE,
                        localRepoConfig,
                        null))));
        assertNull(localRepoConfig.getRepoTypeConfig().getDownloadRedirectConfig(),
                "Should turn off redirect download option");
    }

    @Test
    public void testNotAol() {
        try {
            when(aolService.isAol()).thenReturn(false);
            RemoteRepoConfig oldRemoteRepo = createRemoteRepo(true);
            RemoteRepoConfig newRemoteRepo = createRemoteRepo(true);
            newRemoteRepo.getRepoTypeConfig().setUrl("http://localhost:8080/artifactory");

            interceptor.onBeforeSave(Map.of(REMOTE,
                    List.of(new RepoConfigChange("Repo-Valid-Cert-true", REPO_UPDATE, newRemoteRepo, oldRemoteRepo))));
        } finally {
            when(aolService.isAol()).thenReturn(true);
        }
    }

    @Test
    public void testDoNothingIfNoRedirectRepos() {
        when(aolService.isAol()).thenReturn(true);
        when(centralConfigService.isContextCreated()).thenReturn(true);
        when(addonsManager.isEntitled(EntitlementType.BINARY_PROVIDER_DOWNLOAD_REDIRECT_MIX)).thenReturn(true);
        interceptor.onBeforeSave(Map.of());
    }

    private RemoteRepoConfig createRemoteRepo(boolean withValidCert) {
        RemoteRepoConfig remoteRepoConfig =
                RemoteRepoConfig.create("Repo-Valid-Cert-" + withValidCert, RepoType.Generic);
        remoteRepoConfig.getRepoTypeConfig().setUrl("http://someurl.com");
        remoteRepoConfig.getRepoTypeConfig().setClientTlsCertificate(withValidCert ? "ValidTls" : "NotValidTLS");
        return remoteRepoConfig;
    }
}