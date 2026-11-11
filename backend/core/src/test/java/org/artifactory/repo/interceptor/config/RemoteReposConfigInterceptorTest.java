package org.artifactory.repo.interceptor.config;

import org.artifactory.addon.AddonType;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.webstart.ArtifactWebstartAddon;
import org.artifactory.aol.AolService;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.common.ConstantValues;
import org.artifactory.config.ConfigurationException;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.model.RemoteRepoConfig;
import org.artifactory.repo.RepoConfigChange;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.repo.http.DenyListCacheHolderService;
import org.artifactory.request.UrlVerifier;
import org.artifactory.spring.InternalArtifactoryContext;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.artifactory.repo.RepoConfigChange.ConfigAction.REPO_CREATE;
import static org.artifactory.repo.RepoConfigChange.ConfigAction.REPO_UPDATE;
import static org.artifactory.repo.RepoDetailsType.REMOTE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

/**
 * @author Tamar Rosen
 */
public class RemoteReposConfigInterceptorTest extends ArtifactoryHomeBoundJUnitTest {

    private AddonsManager addonsManager;
    private CentralConfigService centralConfigService;
    private AutoCloseable mocks;

    @BeforeEach
    public void setup() {
        mocks = MockitoAnnotations.openMocks(this);
        InternalArtifactoryContext contextMock = mock(InternalArtifactoryContext.class);
        AolService aolService = mock(AolService.class);
        when(aolService.isAol()).thenReturn(true);
        when(contextMock.beanForType(AolService.class)).thenReturn(aolService);
        ArtifactoryContextThreadBinder.bind(contextMock);
        addonsManager = mock(AddonsManager.class);
        centralConfigService = mock(CentralConfigService.class);
        setStringSystemProperty(ConstantValues.whitelistRemoteRepoUrls,
                ConstantValues.whitelistRemoteRepoUrls.getDefValue());
    }

    @AfterEach
    public void tearDown() throws Exception {
        ArtifactoryContextThreadBinder.unbind();
        setStringSystemProperty(ConstantValues.centralConfigRemoteRepoUrlValidation, String.valueOf(false));
        mocks.close();
    }

    @Test
    public void testCreateRemoteWithBlackedUrl() {
        setStringSystemProperty(ConstantValues.whitelistRemoteRepoUrls, "");
        UrlVerifier urlVerifier = spy(new UrlVerifier(new DenyListCacheHolderService(), centralConfigService));
        when(urlVerifier.isRemoteRepoBlocked(any(), any())).thenReturn(true);
        RemoteReposConfigInterceptor interceptor = new RemoteReposConfigInterceptor(urlVerifier, addonsManager);
        RemoteRepoConfig remoteRepoConfig =
                RemoteRepoConfig.create("Remote-http://localhost:80/artifactory", RepoType.Generic);
        remoteRepoConfig.getRepoTypeConfig().setUrl("http://localhost:80/artifactory");

        Map<RepoDetailsType, List<RepoConfigChange>> repoConfigChanges = Map.of(
                REMOTE,
                List.of(new RepoConfigChange("Remote-http://localhost:80/artifactory", REPO_CREATE, remoteRepoConfig,
                        null))
        );
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                interceptor.onBeforeSave(repoConfigChanges)
        );
        assertEquals("Found a remote repository(ies) containing blacklisted URLs", exception.getMessage());
    }

    @Test
    public void testWhitelistRemoteUrl() {
        // given - repo with whitelist url
        setStringSystemProperty(ConstantValues.whitelistRemoteRepoUrls, "http://localhost");
        RemoteReposConfigInterceptor interceptor =
                new RemoteReposConfigInterceptor(new UrlVerifier(new DenyListCacheHolderService(),
                        centralConfigService), addonsManager);
        RemoteRepoConfig remoteRepoConfig =
                RemoteRepoConfig.create("Remote-http://localhost:80/artifactory", RepoType.Generic);
        remoteRepoConfig.getRepoTypeConfig().setUrl("http://localhost:80/artifactory");
        // when
        interceptor.onBeforeSave(Map.of(REMOTE,
                List.of(new RepoConfigChange("Remote-http://localhost:80/artifactory", REPO_CREATE, remoteRepoConfig,
                        null))));
        // then - save will be ok
        assertFalse(remoteRepoConfig.getRepoTypeConfig().isBlackedOut());
    }

    @Test
    public void testAssertNoMissingUrlInRemoteReposEnabledException() {
        setStringSystemProperty(ConstantValues.centralConfigRemoteRepoUrlValidation, "true");
        UrlVerifier urlVerifier = spy(new UrlVerifier(new DenyListCacheHolderService(), centralConfigService));
        when(urlVerifier.isRemoteRepoBlocked(any(), any())).thenReturn(false);
        RemoteReposConfigInterceptor interceptor = new RemoteReposConfigInterceptor(urlVerifier, addonsManager);
        RemoteRepoConfig remoteRepoConfig1 = RemoteRepoConfig.create("cool-remote1", RepoType.Generic);
        remoteRepoConfig1.getRepoTypeConfig().setUrl("http://cool1.com");

        RemoteRepoConfig remoteRepoConfig2 = RemoteRepoConfig.create("cool-remote2", RepoType.Generic);
        remoteRepoConfig2.getRepoTypeConfig().setUrl(null);
        ConfigurationException exception = assertThrows(ConfigurationException.class, () ->
                invokeOnBeforeSave(interceptor, remoteRepoConfig1, remoteRepoConfig2)
        );
        assertTrue(exception.getMessage().contains("Found a remote repository with a missing URL"));
    }

    private void invokeOnBeforeSave(RemoteReposConfigInterceptor interceptor, RemoteRepoConfig... remoteRepoConfigs) {
        interceptor.onBeforeSave(
                Map.of(REMOTE, Stream.of(remoteRepoConfigs)
                        .map(repoConfig -> new RepoConfigChange(repoConfig.getKey(), REPO_CREATE, repoConfig, null))
                        .toList())
        );
    }

    @Test
    public void testCleanClientCertificate() {
        // given - webstartAddon is supported
        when(addonsManager.isAddonSupported(AddonType.WEBSTART)).thenReturn(true);
        ArtifactWebstartAddon webstartAddon = mock(ArtifactWebstartAddon.class);
        // given - addon doesn't know any ssl certificate names
        when(webstartAddon.getSslCertNames()).thenReturn(List.of());
        when(addonsManager.addonByType(ArtifactWebstartAddon.class)).thenReturn(webstartAddon);
        RemoteReposConfigInterceptor interceptor =
                new RemoteReposConfigInterceptor(new UrlVerifier(new DenyListCacheHolderService(),
                        centralConfigService), addonsManager);
        RemoteRepoConfig remoteRepoConfig = RemoteRepoConfig.create("repoKey", RepoType.Generic);
        remoteRepoConfig.getRepoTypeConfig().setClientTlsCertificate("doesn't exist");
        interceptor.onBeforeSave(
                Map.of(REMOTE, List.of(new RepoConfigChange("remote-repo", REPO_CREATE, remoteRepoConfig, null))));
        assertNull(remoteRepoConfig.getRepoTypeConfig().getClientTlsCertificate());
    }

    @Test
    public void testDoNotCleanClientCertificateIfWebstartIsDisabled() {
        // given - webstartAddon is supported
        when(addonsManager.isAddonSupported(AddonType.WEBSTART)).thenReturn(false);
        ArtifactWebstartAddon webstartAddon = mock(ArtifactWebstartAddon.class);
        // given - addon doesn't know any ssl certificate names
        when(webstartAddon.getSslCertNames()).thenReturn(List.of());
        when(addonsManager.addonByType(ArtifactWebstartAddon.class)).thenReturn(webstartAddon);
        RemoteReposConfigInterceptor interceptor =
                new RemoteReposConfigInterceptor(new UrlVerifier(new DenyListCacheHolderService(),
                        centralConfigService), addonsManager);
        RemoteRepoConfig remoteRepoConfig = RemoteRepoConfig.create("repoKey", RepoType.Generic);
        remoteRepoConfig.getRepoTypeConfig().setClientTlsCertificate("doesn't exist");
        interceptor.onBeforeSave(
                Map.of(REMOTE, List.of(new RepoConfigChange("remote-repo", REPO_CREATE, remoteRepoConfig, null))));
        assertEquals("doesn't exist", remoteRepoConfig.getRepoTypeConfig().getClientTlsCertificate());
    }

    @Test
    public void testStrictPolicy() {
        // strict policy will fail by default, in cases of unresolvable URLs
        setStringSystemProperty(ConstantValues.remoteRepoBlockUrlStrictPolicy, Boolean.toString(true));
        RemoteReposConfigInterceptor interceptor =
                new RemoteReposConfigInterceptor(new UrlVerifier(new DenyListCacheHolderService(),
                        centralConfigService), addonsManager);

        RemoteRepoConfig remoteRepoConfig = RemoteRepoConfig.create("Remote-" + "\\o//", RepoType.Generic);
        remoteRepoConfig.getRepoTypeConfig().setUrl("\\o//");

        assertThrows(IllegalArgumentException.class, () -> invokeOnBeforeSave(interceptor, remoteRepoConfig));
    }

    private void invokeOnBeforeSave(RemoteReposConfigInterceptor interceptor, RemoteRepoConfig remoteRepoConfig) {
        interceptor.onBeforeSave(Map.of(REMOTE,
                List.of(new RepoConfigChange("Remote-" + "\\o//", REPO_CREATE, remoteRepoConfig, null))));
    }

    @Test
    public void testBlockedUrlsChangeUrl() {
        RemoteReposConfigInterceptor interceptor =
                new RemoteReposConfigInterceptor(new UrlVerifier(new DenyListCacheHolderService(),
                        centralConfigService), addonsManager);
        RemoteRepoConfig oldRemoteRepo = createRemoteRepo();
        RemoteRepoConfig newRemoteRepo = createRemoteRepo();
        newRemoteRepo.getRepoTypeConfig().setUrl("http://google.com/artifactory");

        interceptor.onBeforeSave(Map.of(REMOTE,
                List.of(new RepoConfigChange("Repo-Valid-Cert-true", REPO_UPDATE, newRemoteRepo, oldRemoteRepo))));

        interceptor =
                new RemoteReposConfigInterceptor(new UrlVerifier(new DenyListCacheHolderService(),
                        centralConfigService), addonsManager);

        newRemoteRepo.getRepoTypeConfig().setUrl("http://localhost:8080/artifactory");
        try {
            interceptor.onBeforeSave(Map.of(REMOTE,
                    List.of(new RepoConfigChange("Repo-Valid-Cert-true", REPO_UPDATE, newRemoteRepo, oldRemoteRepo))));
        } catch (IllegalArgumentException expected) {
            // this is good!
            assertEquals("Found a remote repository(ies) containing blacklisted URLs", expected.getMessage());
            return;
        }
        fail("Update URL should have been blocked!");
    }

    @ParameterizedTest
    @MethodSource("testAssertNoMissingUrlInRemoteReposOtherDataProvider")
    public void testAssertNoMissingUrlInRemoteReposOther(String enabled, String url1) {
        setStringSystemProperty(ConstantValues.centralConfigRemoteRepoUrlValidation, enabled);
        UrlVerifier urlVerifier = spy(new UrlVerifier(new DenyListCacheHolderService(), centralConfigService));
        when(urlVerifier.isRemoteRepoBlocked(any(), any())).thenReturn(false);
        RemoteReposConfigInterceptor interceptor = new RemoteReposConfigInterceptor(urlVerifier, addonsManager);
        RemoteRepoConfig remoteRepo1 = RemoteRepoConfig.create("cool-remote1", RepoType.Generic);
        remoteRepo1.getRepoTypeConfig().setUrl(url1);
        RemoteRepoConfig remoteRepo2 = RemoteRepoConfig.create("cool-remote2", RepoType.Generic);
        remoteRepo2.getRepoTypeConfig().setUrl("http://cool2.com");
        interceptor.onBeforeSave(Map.of(REMOTE,
                List.of(new RepoConfigChange("cool-remote1", REPO_CREATE, remoteRepo1, null),
                        new RepoConfigChange("cool-remote2", REPO_CREATE, remoteRepo2, null))));
    }

    private static Stream<Arguments> testAssertNoMissingUrlInRemoteReposOtherDataProvider() {
        return Stream.of(
                Arguments.of("true", "http://cool1.com"),
                Arguments.of("false", "http://cool1.com"),
                Arguments.of("false", null)
        );
    }

    private RemoteRepoConfig createRemoteRepo() {
        RemoteRepoConfig remoteRepoConfig =
                RemoteRepoConfig.create("Repo-Valid-Cert-" + true, RepoType.Generic);
        remoteRepoConfig.getRepoTypeConfig().setUrl("http://someurl.com");
        remoteRepoConfig.getRepoTypeConfig().setClientTlsCertificate("ValidTls");
        return remoteRepoConfig;
    }
}