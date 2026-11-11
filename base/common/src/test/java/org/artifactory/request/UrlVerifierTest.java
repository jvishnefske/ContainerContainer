package org.artifactory.request;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.common.ConstantValues;
import org.artifactory.repo.http.DenyListCacheHolderService;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.artifactory.util.AolUtils;
import org.jfrog.client.util.DenylistValidator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.slf4j.LoggerFactory;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

/**
 * @author Nadav Yogev
 */
class UrlVerifierTest extends ArtifactoryHomeBoundJUnitTest {
    @Mock
    private ArtifactoryContext context;
    private AutoCloseable mocks;
    private UrlVerifier urlVerifier;

    private CentralConfigService configService;

    @BeforeEach
    void setup() {
        mocks = MockitoAnnotations.openMocks(this);
        ArtifactoryContextThreadBinder.bind(context);
        // Use RETURNS_DEEP_STUBS to mock the entire chain without importing SysConfigHelper
        // This allows mocking configService.getSystemConfiguration().getSysConfig().helper().getRouterPort()
        configService = mock(CentralConfigService.class, RETURNS_DEEP_STUBS);
        when(configService.getSystemConfiguration().getSysConfig().helper().getRouterPort()).thenReturn(8046);
        urlVerifier = spy(new UrlVerifier(new DenyListCacheHolderService(), configService));
    }

    @AfterEach
    void teardown() throws Exception {
        mocks.close();
        ArtifactoryContextThreadBinder.unbind();
    }

    @ParameterizedTest
    @NullAndEmptySource
    void testIsRemoteRepoBlockedWithNullUrl(String url) {
        try (MockedStatic<AolUtils> mockedAolUtils = Mockito.mockStatic(AolUtils.class)) {
            mockedAolUtils.when(AolUtils::isAol).thenReturn(true);
            boolean result = urlVerifier.isRemoteRepoBlocked(url, "test");
            assertFalse(result, "URL should not be blocked");
        }
    }

    @Disabled("Flaky test")
    @Test
    void testLoopBack() {
        boolean loopback = urlVerifier.isRemoteRepoBlocked("http://localhost:8080/artifactory/dangerZone", "test");
        assertTrue(loopback);

        loopback = urlVerifier.isRemoteRepoBlocked("http://127.0.0.1:8080/artifactory/dangerZone", "test");
        assertTrue(loopback);

        loopback = urlVerifier.isRemoteRepoBlocked("http://[::1]:8080/artifactory/dangerZone", "test");
        assertTrue(loopback);

        loopback = urlVerifier.isRemoteRepoBlocked("http://jfrog.com/artifactory/dangerZone", "test");
        assertFalse(loopback);

        loopback = urlVerifier.isRemoteRepoBlocked("http://jfrog.com/artifactory/dangerZone", "test");
        assertFalse(loopback);

        // test whitelist (localhost with /)
        getBound().setProperty(ConstantValues.whitelistRemoteRepoUrls, "https://127.0.0.1,http://localhost/");
        loopback = urlVerifier.isRemoteRepoBlocked("http://localhost/artifactory/dangerZone", "test");
        assertFalse(loopback);
        loopback = urlVerifier.isRemoteRepoBlocked("http://127.0.0.1:8080/artifactory/dangerZone", "test");
        assertTrue(loopback);
        loopback = urlVerifier.isRemoteRepoBlocked("https://127.0.0.1:8080/artifactory/dangerZone", "test");
        assertFalse(loopback);
    }

    @ParameterizedTest
    @MethodSource("UnknownHostException")
    void testIsRemoteRepoBlockedHandleUnknownHostException(boolean isAol,
            boolean remoteRepoBlockUrlOnUnknownHost, boolean shouldBlock) throws UnknownHostException {
        final String url = "https://invalid-domain.com";
        final String repoKey = "test";
        getBound().setProperty(ConstantValues.remoteRepoBlockUrlOnUnknownHost,
                String.valueOf(remoteRepoBlockUrlOnUnknownHost));
        doThrow(new UnknownHostException("Unknown host")).when(urlVerifier).isRepoBlocked(any(), any(), any());
        try (MockedStatic<AolUtils> mockedAolUtils = Mockito.mockStatic(AolUtils.class)) {
            mockedAolUtils.when(AolUtils::isAol).thenReturn(isAol);
            assertEquals(urlVerifier.isRemoteRepoBlocked(url, repoKey), shouldBlock);
        }
    }

    static Stream<Arguments> UnknownHostException() {
        return Stream.of(
                Arguments.of(false, false, false),
                Arguments.of(false, true, false),
                Arguments.of(true, true, true),
                Arguments.of(true, false, false)
        );
    }

    @Test
    void testSiteAndLinkLocal() {
        getBound().setProperty(ConstantValues.remoteRepoBlockUrlStrictPolicy, "true");
        boolean loopback = urlVerifier.isRemoteRepoBlocked("http://10.0.0.1:8080/internal/dangerZone", "test");
        assertTrue(loopback);

        loopback = urlVerifier.isRemoteRepoBlocked("http://169.254.169.254/latest/meta-data/dangerZone", "test");
        assertTrue(loopback);
    }

    @ParameterizedTest
    @MethodSource("domainNameProvider")
    void shouldParseDomainName(String url, String expectedDomain) throws Exception {
        assertEquals(expectedDomain, UrlVerifier.getDomainName(url));
    }

    private static Stream<Arguments> domainNameProvider() {
        return Stream.of(
                Arguments.of("http://domain.com", "domain.com"),
                // The "www." is kept: it is a distinct name that may resolve elsewhere than the bare domain, and
                // this host is the one whose addresses get authorized (RTDEV-95692).
                Arguments.of("http://www.domain.com", "www.domain.com"),
                Arguments.of("https://www.domain.com", "www.domain.com"),
                Arguments.of("https://www.domain.com/one/two.jsp", "www.domain.com"),
                Arguments.of("https://192.168.2.3:8080/one/two.jsp", "192.168.2.3"),
                // java.net.URI rejects the "_" and reports no host, but the host is still recovered from the
                // authority - where previously the whole URL was used as the "domain".
                Arguments.of("https://www.domain_1.com/one/two.jsp", "www.domain_1.com"),
                // RTDEV-95692: URI.getHost() is null for every spelling below, yet each one is the host the
                // outbound HTTP client resolves and connects to.
                Arguments.of("http://%31%32%37%2E%30%2E%30%2E%31:8046/", "127.0.0.1"),
                Arguments.of("http://127%2e0%2e0%2e1:8046/", "127.0.0.1"),
                Arguments.of("http://127.1:8046/", "127.1"),
                Arguments.of("http://127.0.0.1.:8046/", "127.0.0.1"),
                // Userinfo is not part of the host: the connection target is what follows the "@".
                Arguments.of("http://registry.example@127.0.0.1:8046/", "127.0.0.1"),
                // Lower-cased, since both the JFrog-internal host list and the denylist are exact matches.
                Arguments.of("http://JPMS.JFROG.COM/", "jpms.jfrog.com"),
                Arguments.of("http://[::1]:8046/", "[::1]"),
                Arguments.of("http://[::ffff:127.0.0.1]:8046/", "[::ffff:127.0.0.1]")
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {"relative/path", "/just/a/path", "http://:8046/", "http://.:8046/"})
    void getDomainName_shouldThrow_whenNoHostCanBeDerived(String url) {
        assertThrows(UrlVerifier.UndeterminedHostException.class, () -> UrlVerifier.getDomainName(url));
    }

    @Disabled("Flaky test")
    @Test
    public void shouldExecuteCalculateRepoBlockedOnceForDomain() throws Exception {
        final String url = "http://domain.com/externalDomain";
        final String url1 = "https://domain.com/differentExternalUrl";
        final String domain = UrlVerifier.getDomainName(url);
        final String repoKey = "test";
        assertFalse(urlVerifier.isRemoteRepoBlocked(url, repoKey));
        assertFalse(urlVerifier.isRemoteRepoBlocked(url, repoKey));
        assertFalse(urlVerifier.isRemoteRepoBlocked(url1, repoKey));

        //result calculated only once for domain and retrieved two other times from the cache.
        Mockito.verify(urlVerifier, times(1)).isRepoBlocked(domain, url, repoKey);
    }

    @Disabled("Flaky test")
    @Test
    void shouldExecuteCalculateRepoBlockedThreeTimesForDifferentDomains() throws Exception {
        final String url1 = "http://google.com/externalDomain";
        final String url2 = "https://jfrog.com/differentExternalUrl";
        final String url3 = "https://apple.com/differentExternalUrl";

        final String domain1 = UrlVerifier.getDomainName(url1);
        final String domain2 = UrlVerifier.getDomainName(url2);
        final String domain3 = UrlVerifier.getDomainName(url3);
        final String repoKey = "test";
        assertFalse(urlVerifier.isRemoteRepoBlocked(url1, repoKey));
        assertFalse(urlVerifier.isRemoteRepoBlocked(url2, repoKey));
        assertFalse(urlVerifier.isRemoteRepoBlocked(url3, repoKey));

        //result calculated only once per domain.
        Mockito.verify(urlVerifier, times(1)).isRepoBlocked(domain1, url1, repoKey);
        Mockito.verify(urlVerifier, times(1)).isRepoBlocked(domain2, url2, repoKey);
        Mockito.verify(urlVerifier, times(1)).isRepoBlocked(domain3, url3, repoKey);

        //calculated three times altogether.
        Mockito.verify(urlVerifier, times(3)).isRepoBlocked(any(), any(), any());
    }

    @ParameterizedTest
    @MethodSource("provideBridgeUrlTestScenarios")
    void isLoopbackBridgeUrl_shouldReturnExpectedResult_basedOnUrlPattern(String url,
            boolean blockingCheckShouldBeSkipped, String description) {
        boolean isBridge = urlVerifier.isLoopbackBridgeUrl(url);
        assertEquals(blockingCheckShouldBeSkipped, isBridge, description);
    }

    @Test
    void isLoopbackBridgeUrl_shouldReturnFalse_whenExceptionOccurs() {
        // Arrange
        CentralConfigService failingConfigService = mock(CentralConfigService.class, RETURNS_DEEP_STUBS);
        when(failingConfigService.getSystemConfiguration())
                .thenThrow(new RuntimeException("Config error"));
        UrlVerifier verifierWithFailingConfig = new UrlVerifier(new DenyListCacheHolderService(), failingConfigService);
        String url = "http://localhost:8046/bridge-api";
        // Act
        boolean result = verifierWithFailingConfig.isLoopbackBridgeUrl(url);
        // Assert
        assertFalse(result);
    }

    @Test
    void isLoopbackBridgeUrl_shouldSaveRouterPort_afterFirstCall() {
        // Arrange
        String url1 = "http://localhost:8046/bridge-api";
        String url2 = "http://localhost:8046/bridge-service";
        // Act
        urlVerifier.isLoopbackBridgeUrl(url1);
        urlVerifier.isLoopbackBridgeUrl(url2);
        // Assert - Verify getRouterPort was only called once (saved after first call)
        Mockito.verify(configService.getSystemConfiguration().getSysConfig().helper(), times(1)).getRouterPort();
    }

    @Test
    void isRemoteRepoBlocked_shouldNotBlockBridgeUrl_whenUrlIsBridge() {
        // Arrange
        String url = "http://localhost:8046/bridge-api/endpoint";
        String repoKey = "test-repo";
        // Act
        try (MockedStatic<AolUtils> mockedAolUtils = Mockito.mockStatic(AolUtils.class)) {
            mockedAolUtils.when(AolUtils::isAol).thenReturn(true);
            boolean blocked = urlVerifier.isRemoteRepoBlocked(url, repoKey);
            // Assert
            assertFalse(blocked);
        }
    }

    @Test
    void isRemoteRepoBlocked_shouldBlockLoopbackUrl_whenUrlIsNotBridge() {
        // Arrange
        String url = "http://localhost:9999/not-bridge";
        String repoKey = "test-repo";
        // Act
        try (MockedStatic<AolUtils> mockedAolUtils = Mockito.mockStatic(AolUtils.class)) {
            mockedAolUtils.when(AolUtils::isAol).thenReturn(true);
            boolean blocked = urlVerifier.isRemoteRepoBlocked(url, repoKey);
            // Assert
            assertTrue(blocked);
        }
    }

    private static Stream<Arguments> provideBridgeUrlTestScenarios() {
        return Stream.of(
                // Valid bridge URLs with default port 8046 - blocking check should be skipped
                Arguments.of("http://localhost:8046/bridge-api", true,
                        "Standard bridge URL with correct port should be allowed"),
                Arguments.of("http://localhost:8046/bridge-test/path", true,
                        "Bridge URL with additional path should be allowed"),
                Arguments.of("http://localhost:8046/bridge-", true,
                        "Bridge URL with dash only should be allowed"),
                Arguments.of("http://127.0.0.1:8046/bridge-my-bridge", true,
                        "Bridge URL with 127.0.0.1 should be allowed"),
                Arguments.of("https://localhost:8046/bridge-api", true,
                        "HTTPS bridge URL should be allowed"),
                // Wrong port - blocking check should NOT be skipped
                Arguments.of("http://localhost:8080/bridge-api", false,
                        "Bridge URL with wrong port should be blocked"),
                Arguments.of("http://localhost:9999/bridge-test/path", false,
                        "Bridge URL with different port should be blocked"),
                Arguments.of("http://localhost/bridge-api", false,
                        "Bridge URL without port should be blocked"),
                // Invalid bridge patterns - blocking check should NOT be skipped
                Arguments.of("http://localhost:8046/bridgeapi", false,
                        "URL without dash after 'bridge' should be blocked"),
                Arguments.of("http://localhost:8046/api-bridge", false,
                        "URL with 'bridge' not at start of path should be blocked"),
                // Not a loopback domain - blocking should not be skipped
                Arguments.of("http://192.168.1.1:8046/api-bridge", false,
                        "URLs with non-loopback domains should not be considered as loopback URLs")
        );
    }

    @ParameterizedTest
    @MethodSource("bridgePathProvider")
    void staticIsBridgePath(String path, boolean expected) {
        assertEquals(expected, UrlVerifier.isBridgePath(path));
    }

    private static Stream<Arguments> bridgePathProvider() {
        return Stream.of(
                Arguments.of(null, false),
                Arguments.of("", false),
                Arguments.of("/artifactory/repo", false),
                Arguments.of("/api/v1/foo", false),
                Arguments.of("/api/v1/bridge-xyz/artifactory/npm-remote/", false),
                Arguments.of("/artifactory/bridge-xxxx/", false),
                Arguments.of("/foo/bridge-bar/baz", false),
                Arguments.of("/prefix/bridge-x/artifactory", false),
                Arguments.of("/bridge-xxx/artifactory/repo/", true),
                Arguments.of("/bridge-x/artifactory", true),
                Arguments.of("/bridge-", true)
        );
    }

    @ParameterizedTest
    @MethodSource("bridgeUrlProvider")
    void staticIsBridgeUrl(String url, boolean expected) {
        assertEquals(expected, UrlVerifier.isBridgeUrl(url));
    }

    private static Stream<Arguments> bridgeUrlProvider() {
        return Stream.of(
                Arguments.of(null, false),
                Arguments.of("", false),
                Arguments.of("https://host/artifactory/repo", false),
                Arguments.of("https://acme.example/artifactory/bridge-xxxx/", false),
                Arguments.of("https://acme.example/bridge-t1/artifactory/libs-release/", true),
                Arguments.of("http://localhost:8046/bridge-tenant/artifactory", true),
                Arguments.of("http://localhost:8046/artifactory/bridge-xxx", false)
        );
    }

    @ParameterizedTest
    @NullAndEmptySource
    void isOutboundFetchBlocked_notBlocked_whenUrlIsBlank(String url) {
        // Blank URL short-circuits before any IP check — no AolUtils mock needed.
        assertFalse(urlVerifier.isOutboundFetchBlocked(url, "test-repo"));
    }

    @Test
    void isOutboundFetchBlocked_blocksLoopback_onOnPrem_withoutAolGate() {
        // CRITICAL: unlike isRemoteRepoBlocked, this method has NO !AolUtils.isAol() skip.
        // 127.0.0.1 must be blocked even when the server is on-prem (AOL=false, the default in tests).
        assertTrue(urlVerifier.isOutboundFetchBlocked("http://127.0.0.1:9999/data", "bower-remote"),
                "Loopback must be blocked on on-prem without requiring AOL mode");
    }

    @Test
    void isOutboundFetchBlocked_blocksLoopback_onSaas() {
        try (MockedStatic<AolUtils> mockedAolUtils = Mockito.mockStatic(AolUtils.class)) {
            mockedAolUtils.when(AolUtils::isAol).thenReturn(true);
            assertTrue(urlVerifier.isOutboundFetchBlocked("http://127.0.0.1:9999/data", "bower-remote"),
                    "Loopback must be blocked on SaaS as well");
        }
    }

    @Test
    void isOutboundFetchBlocked_blocksPrivateRange_whenStrictPolicy() {
        getBound().setProperty(ConstantValues.remoteRepoBlockUrlStrictPolicy, "true");
        assertTrue(urlVerifier.isOutboundFetchBlocked("http://10.0.0.1:8080/internal/secret", "bower-remote"),
                "Private (site-local) address must be blocked under strict policy");
    }

    @Test
    void isOutboundFetchBlocked_blocksAwsMetadataEndpoint_whenStrictPolicy() {
        getBound().setProperty(ConstantValues.remoteRepoBlockUrlStrictPolicy, "true");
        assertTrue(urlVerifier.isOutboundFetchBlocked("http://169.254.169.254/latest/meta-data/", "bower-remote"),
                "AWS metadata link-local endpoint must be blocked under strict policy");
    }

    @Test
    void isOutboundFetchBlocked_allowsPublicUrl() {
        // A normal public registry URL must not be blocked.
        assertFalse(urlVerifier.isOutboundFetchBlocked("https://registry.bower.io/some-pkg-1.0.0.tgz", "bower-remote"),
                "Public registry URL must not be blocked");
    }

    @Test
    void isOutboundFetchBlocked_allowsBridgeLoopbackUrl() {
        // JFrog Router bridge URLs on the router port must remain reachable even though they are loopback.
        assertFalse(urlVerifier.isOutboundFetchBlocked("http://localhost:8046/bridge-api/endpoint", "bower-remote"),
                "JFrog Bridge loopback URL must not be blocked");
    }

    @Test
    void isOutboundFetchBlocked_allowsWhitelistedLoopbackUrl() {
        // whiteListedRemoteRepositoryPrefixes is read once at construction time, so the property must be set
        // before creating the UrlVerifier instance — setting it on the shared spy (created in @BeforeEach)
        // would have no effect.
        getBound().setProperty(ConstantValues.whitelistRemoteRepoUrls, "http://127.0.0.1:9999");
        UrlVerifier verifierWithWhitelist = new UrlVerifier(new DenyListCacheHolderService(), configService);
        assertFalse(verifierWithWhitelist.isOutboundFetchBlocked("http://127.0.0.1:9999/trusted-internal",
                "bower-remote"),
                "Admin-whitelisted URL must not be blocked even if it is a loopback");
    }

    @Test
    void isOutboundFetchBlocked_blocksJFrogArgocdInternalHost() {
        // jfrog-argocd-internal.jfrog.io is an internal JFrog host confirmed in the H1 SSRF report.
        // We mock InetAddress to return a non-loopback, non-site-local address so the code reaches
        // the isJFrogInternalHost() check inside isRepoBlocked.
        InetAddress mockPublicAddress = mock(InetAddress.class);
        when(mockPublicAddress.isLoopbackAddress()).thenReturn(false);
        when(mockPublicAddress.isSiteLocalAddress()).thenReturn(false);
        when(mockPublicAddress.isLinkLocalAddress()).thenReturn(false);
        try (MockedStatic<InetAddress> mockedInetAddress = Mockito.mockStatic(InetAddress.class)) {
            mockedInetAddress.when(() -> InetAddress.getAllByName("jfrog-argocd-internal.jfrog.io"))
                    .thenReturn(new InetAddress[] {mockPublicAddress});
            assertTrue(urlVerifier.isOutboundFetchBlocked(
                    "http://jfrog-argocd-internal.jfrog.io/api/version", "bower-remote"),
                    "JFrog ArgoCD internal host must be blocked by the JFrog internal denylist");
        }
    }

    @Test
    void isOutboundFetchBlocked_blocksJFrogJpmsHost() {
        // jpms.jfrog.com is the JFrog Products Management System confirmed in the H1 SSRF report.
        InetAddress mockPublicAddress = mock(InetAddress.class);
        when(mockPublicAddress.isLoopbackAddress()).thenReturn(false);
        when(mockPublicAddress.isSiteLocalAddress()).thenReturn(false);
        when(mockPublicAddress.isLinkLocalAddress()).thenReturn(false);
        try (MockedStatic<InetAddress> mockedInetAddress = Mockito.mockStatic(InetAddress.class)) {
            mockedInetAddress.when(() -> InetAddress.getAllByName("jpms.jfrog.com"))
                    .thenReturn(new InetAddress[] {mockPublicAddress});
            assertTrue(urlVerifier.isOutboundFetchBlocked("https://jpms.jfrog.com/", "bower-remote"),
                    "JFrog JPMS host must be blocked by the JFrog internal denylist");
        }
    }

    @Test
    void isRedirectTargetBlocked_blocksLoopback() {
        assertTrue(urlVerifier.isRedirectTargetBlocked("http://127.0.0.1:9999/internal", "bower-remote"),
                "Redirect floor must block loopback targets");
    }

    @Test
    void isRedirectTargetBlocked_allowsPrivateRange_leftToRedirectDenylist() {
        // Floor deliberately does NOT block private/site-local ranges — those stay with the tunable
        // JFrogDefaultRedirectStrategy denylist so legitimate internal-mirror redirects keep working.
        getBound().setProperty(ConstantValues.remoteRepoBlockUrlStrictPolicy, "true");
        assertFalse(urlVerifier.isRedirectTargetBlocked("http://10.0.0.1:8080/internal/mirror", "bower-remote"),
                "Redirect floor must NOT block private-range targets");
    }

    @Test
    void isRedirectTargetBlocked_allowsPublicUrl() {
        assertFalse(urlVerifier.isRedirectTargetBlocked("https://registry.bower.io/pkg-1.0.0.tgz", "bower-remote"),
                "Redirect floor must not block public registry targets");
    }

    @Test
    void isRedirectTargetBlocked_blocksJFrogInternalHost() {
        InetAddress mockPublicAddress = mock(InetAddress.class);
        when(mockPublicAddress.isLoopbackAddress()).thenReturn(false);
        try (MockedStatic<InetAddress> mockedInetAddress = Mockito.mockStatic(InetAddress.class)) {
            mockedInetAddress.when(() -> InetAddress.getAllByName("jfrog-argocd-internal.jfrog.io"))
                    .thenReturn(new InetAddress[] {mockPublicAddress});
            assertTrue(urlVerifier.isRedirectTargetBlocked(
                    "http://jfrog-argocd-internal.jfrog.io/api/version", "bower-remote"),
                    "Redirect floor must block JFrog internal hosts");
        }
    }

    @Test
    void isRedirectTargetBlocked_allowsBridgeLoopbackUrl() {
        assertFalse(urlVerifier.isRedirectTargetBlocked("http://localhost:8046/bridge-api/endpoint", "bower-remote"),
                "Redirect floor must allow JFrog Bridge loopback URLs");
    }

    @Test
    void isOutboundTargetBlocked_blocksNonBridgeLoopback() {
        // RTDEV-90192: the internal router port is reachable via a non-bridge path; the outbound choke-point
        // floor must block it on every deployment.
        assertTrue(urlVerifier.isOutboundTargetBlocked("http://127.0.0.1:8046/router/api/v1/system/health",
                        "bower-remote"),
                "Outbound floor must block non-bridge loopback targets");
    }

    @Test
    void isOutboundTargetBlocked_allowsPrivateRange_leftToAdminPolicy() {
        // Floor deliberately does NOT block private/site-local ranges so legitimate on-prem mirrors keep working.
        getBound().setProperty(ConstantValues.remoteRepoBlockUrlStrictPolicy, "true");
        assertFalse(urlVerifier.isOutboundTargetBlocked("http://10.0.0.1:8080/internal/mirror", "bower-remote"),
                "Outbound floor must NOT block private-range targets");
    }

    @Test
    void isOutboundTargetBlocked_allowsBridgeLoopbackUrl() {
        assertFalse(urlVerifier.isOutboundTargetBlocked("http://localhost:8046/bridge-api/endpoint", "bower-remote"),
                "Outbound floor must allow JFrog Bridge loopback URLs");
    }

    @Test
    void performBlockingCheck_returnsBlockOnUnknownHost_whenFlagEnabled_viaOutboundFetch()
            throws UnknownHostException {
        getBound().setProperty(ConstantValues.remoteRepoBlockUrlOnUnknownHost, "true");
        doThrow(new UnknownHostException("no such host")).when(urlVerifier).isRepoBlocked(any(), any(), any());
        assertTrue(urlVerifier.isOutboundFetchBlocked("https://unresolvable-domain.example", "test-repo"),
                "UnknownHostException with remoteRepoBlockUrlOnUnknownHost=true must block the URL");
    }

    @Test
    void performBlockingCheck_returnsNotBlocked_onUnknownHost_whenFlagDisabled_viaOutboundFetch()
            throws UnknownHostException {
        getBound().setProperty(ConstantValues.remoteRepoBlockUrlOnUnknownHost, "false");
        doThrow(new UnknownHostException("no such host")).when(urlVerifier).isRepoBlocked(any(), any(), any());
        assertFalse(urlVerifier.isOutboundFetchBlocked("https://unresolvable-domain.example", "test-repo"),
                "UnknownHostException with remoteRepoBlockUrlOnUnknownHost=false must not block the URL");
    }

    @Test
    void performBlockingCheck_returnsStrictPolicy_whenUnexpectedException_andStrictPolicyEnabled()
            throws UnknownHostException {
        // useRemoteRepoStrictPolicy is captured at construction time; default is TRUE.
        doThrow(new RuntimeException("unexpected error")).when(urlVerifier).isRepoBlocked(any(), any(), any());
        assertTrue(urlVerifier.isOutboundFetchBlocked("https://unresolvable-domain.example", "test-repo"),
                "Unexpected exception with strictPolicy=true (default) must block the URL");
    }

    @Test
    void performBlockingCheck_returnsNotBlocked_whenUnexpectedException_andStrictPolicyDisabled()
            throws UnknownHostException {
        getBound().setProperty(ConstantValues.remoteRepoBlockUrlStrictPolicy, "false");
        UrlVerifier lenientVerifier = spy(new UrlVerifier(new DenyListCacheHolderService(), configService));
        doThrow(new RuntimeException("unexpected error")).when(lenientVerifier).isRepoBlocked(any(), any(), any());
        assertFalse(lenientVerifier.isOutboundFetchBlocked("https://unresolvable-domain.example", "test-repo"),
                "Unexpected exception with strictPolicy=false must not block the URL");
    }

    @Test
    void isRepoBlocked_blocksJFrogInternalHost_byStringMatch() throws UnknownHostException {
        // Verify isJFrogInternalHost fires after the IP checks, using a mock InetAddress that
        // is not loopback/site-local so only the JFrog-internal-host branch decides the result.
        InetAddress mockPublicAddress = mock(InetAddress.class);
        when(mockPublicAddress.isLoopbackAddress()).thenReturn(false);
        when(mockPublicAddress.isSiteLocalAddress()).thenReturn(false);
        when(mockPublicAddress.isLinkLocalAddress()).thenReturn(false);
        try (MockedStatic<InetAddress> mockedInetAddress = Mockito.mockStatic(InetAddress.class)) {
            mockedInetAddress.when(() -> InetAddress.getAllByName("jfrog-argocd-internal.jfrog.io"))
                    .thenReturn(new InetAddress[] {mockPublicAddress});
            mockedInetAddress.when(() -> InetAddress.getAllByName("registry.bower.io"))
                    .thenReturn(new InetAddress[] {mockPublicAddress});
            assertTrue(urlVerifier.isRepoBlocked("jfrog-argocd-internal.jfrog.io",
                    "http://jfrog-argocd-internal.jfrog.io/api/version", "bower-remote"),
                    "isRepoBlocked must block JFrog internal hostname");
            assertFalse(urlVerifier.isRepoBlocked("registry.bower.io",
                    "https://registry.bower.io/pkg", "bower-remote"),
                    "isRepoBlocked must not block a legitimate public registry");
        }
    }

    @Test
    void resolveAddress_doesNotCachePositiveDnsResult_toPreventDnsRebindingSsrfBypass() {
        // Security: a positive DNS cache shared across requests would let an attacker pass this check once
        // against a public IP, then rebind the domain to a loopback/private address - the outbound HTTP
        // client resolves independently and would connect to the rebound address while every request in the
        // cache window kept "passing" this now-stale check. Only negative (unresolvable) results may be
        // cached (see resolveAddress_cachesNegativeDnsResult_soUnknownHostIsResolvedOnce below); a resolved
        // domain must always be looked up fresh, no matter how many check entry points hit it.
        InetAddress mockPublicAddress = mock(InetAddress.class);
        when(mockPublicAddress.isLoopbackAddress()).thenReturn(false);
        try (MockedStatic<InetAddress> mockedInetAddress = Mockito.mockStatic(InetAddress.class)) {
            mockedInetAddress.when(() -> InetAddress.getAllByName("registry.bower.io"))
                    .thenReturn(new InetAddress[] {mockPublicAddress});

            assertFalse(urlVerifier.isOutboundTargetBlocked("https://registry.bower.io/a.tgz", "bower-remote"));
            assertFalse(urlVerifier.isRedirectTargetBlocked("https://registry.bower.io/b.tgz", "bower-remote"));

            mockedInetAddress.verify(() -> InetAddress.getAllByName("registry.bower.io"), atLeast(2));
        }
    }

    @Test
    void resolveAddress_alwaysResolvesFreshButDedupsUnresolvedHostLogLine() {
        // Security (code review r847057): remoteRepoBlockUrlOnUnknownHost defaults to fail-open (false), so
        // caching a negative DNS result would let a domain that later rebinds to a private/loopback address
        // keep being fail-open-allowed for the whole cache window, since a cached "unresolved" verdict would
        // never be re-resolved. Every call must still perform a live lookup; only the resulting log line is
        // deduplicated, which is what actually fixes the original log-volume regression (RTDEV-93356).
        ListAppender<ILoggingEvent> logAppender = attachLogAppender(UrlVerifier.class);
        try {
            try (MockedStatic<InetAddress> mockedInetAddress = Mockito.mockStatic(InetAddress.class)) {
                mockedInetAddress.when(() -> InetAddress.getAllByName("unresolvable.example"))
                        .thenThrow(new UnknownHostException("unresolvable.example: Name or service not known"));

                assertFalse(urlVerifier.isOutboundTargetBlocked("https://unresolvable.example/a", "bower-remote"));
                assertFalse(urlVerifier.isOutboundTargetBlocked("https://unresolvable.example/b", "bower-remote"));
                assertFalse(urlVerifier.isRedirectTargetBlocked("https://unresolvable.example/c", "bower-remote"));

                // Never short-circuited by a cached verdict - every call resolves live (each of the 3 calls
                // above triggers its own lookup(s), so strictly more than the 3 top-level calls themselves).
                mockedInetAddress.verify(() -> InetAddress.getAllByName("unresolvable.example"), atLeast(3));
            }
            long unresolvedWarnLogs = logAppender.list.stream()
                    .filter(event -> event.getLevel() == Level.WARN)
                    .filter(event -> event.getFormattedMessage().contains("unresolvable.example"))
                    .count();
            assertEquals(1, unresolvedWarnLogs, "The unresolved-host warning must be logged once per cache window");
        } finally {
            detachLogAppender(UrlVerifier.class, logAppender);
        }
    }

    /**
     * RTDEV-95692: every spelling below was accepted at remote-repo create/update while the outbound HTTP client
     * still connected to the local host, because the address check ran against a host derived differently from the
     * one the client connects to. They are asserted through both the create/update path and the per-request floor.
     */
    @ParameterizedTest
    @MethodSource("localHostBypassUrlProvider")
    void isRemoteRepoBlocked_shouldBlockNonCanonicalLocalHostForms(String url, String description) {
        try (MockedStatic<AolUtils> mockedAolUtils = Mockito.mockStatic(AolUtils.class)) {
            mockedAolUtils.when(AolUtils::isAol).thenReturn(true);
            assertTrue(urlVerifier.isRemoteRepoBlocked(url, "test-repo"), description);
        }
    }

    @ParameterizedTest
    @MethodSource("localHostBypassUrlProvider")
    void isOutboundTargetBlocked_shouldBlockNonCanonicalLocalHostForms(String url, String description) {
        assertTrue(urlVerifier.isOutboundTargetBlocked(url, "test-repo"), description);
    }

    private static Stream<Arguments> localHostBypassUrlProvider() {
        return Stream.of(
                Arguments.of("http://127.0.0.1:8046/", "Plain loopback must stay blocked"),
                Arguments.of("http://%31%32%37%2E%30%2E%30%2E%31:8046/", "Percent-encoded loopback must be blocked"),
                Arguments.of("http://127%2e0%2e0%2e1:8046/", "Partly percent-encoded loopback must be blocked"),
                Arguments.of("http://127.1:8046/", "Short-form loopback must be blocked"),
                Arguments.of("http://127.0.0.1.:8046/", "Trailing-dot loopback must be blocked"),
                Arguments.of("http://2130706433:8046/", "Decimal loopback must be blocked"),
                Arguments.of("http://[::1]:8046/", "IPv6 loopback must be blocked"),
                Arguments.of("http://[::ffff:127.0.0.1]:8046/", "IPv4-mapped loopback must be blocked"),
                Arguments.of("http://0.0.0.0:8046/", "The wildcard address reaches the local host, so it is blocked"),
                Arguments.of("http://0:8046/", "Short-form wildcard address must be blocked"),
                Arguments.of("http://registry.example@127.0.0.1:8046/", "Loopback behind userinfo must be blocked")
        );
    }

    /**
     * The create/update path validates a repository's own base url, so a url naming no host is invalid
     * configuration however it is spelled - whether it claims a scheme and yields no host, or names no destination
     * at all. The relative-reference skip that the fetch-time entry points apply must not reach here, or a
     * repository would save cleanly and then fail on every fetch with no host to connect to. The fetch-time
     * behaviour is covered by {@link #shouldNotEvaluateRelativeReference_whichNamesNoDestination(String)}.
     */
    @ParameterizedTest
    @ValueSource(strings = {"http://:8046/", "http://.:8046/", "/just/a/path", "registry.npmjs.org"})
    void isRemoteRepoBlocked_shouldBlock_whenNoHostCanBeDerivedFromUrl(String url) {
        assertBlockedAsRepositoryUrl(url);
    }

    /**
     * A repository url is the base every outbound request is appended to, so it has to state its own scheme. The
     * protocol-relative form is only meaningful where something else supplies one - a redirect Location, resolved
     * against the request url - so the host derivation that serves those cases must not let one through here as
     * valid configuration, however routable the host it names.
     */
    @Test
    void isRemoteRepoBlocked_shouldBlock_whenUrlStatesNoSchemeOfItsOwn() {
        // The host resolves to a routable address deliberately: every other reason to block is thereby removed, so
        // the assertion can only pass because a repository url is required to state its own scheme.
        InetAddress routableAddress = mock(InetAddress.class);
        try (MockedStatic<InetAddress> mockedInetAddress = Mockito.mockStatic(InetAddress.class);
                MockedStatic<AolUtils> mockedAolUtils = Mockito.mockStatic(AolUtils.class)) {
            mockedInetAddress.when(() -> InetAddress.getAllByName("cdn.example.com"))
                    .thenReturn(new InetAddress[] {routableAddress});
            mockedAolUtils.when(AolUtils::isAol).thenReturn(true);
            assertTrue(urlVerifier.isRemoteRepoBlocked("//cdn.example.com/pkg.tgz", "test-repo"),
                    "A repository url must state a scheme, however routable the host it names");
        }
    }

    /**
     * {@code isRemoteRepoBlocked} is not only the config-save path - {@code UrlVerifierRemoteInterceptor} and
     * {@code RemoteRepoBase#getAlternativeRemoteUrl} both call it on every remote download - so a repository
     * holding a scheme-less url would otherwise log once per download. The guard shares
     * {@code loggedUndeterminedUrlCache} with {@link #shouldWarnOncePerWindow_whenNoHostCanBeDeterminedFromUrl()},
     * which is what throttles it.
     */
    @Test
    void shouldWarnOncePerWindow_whenRepositoryUrlStatesNoSchemeOfItsOwn() {
        String url = "//cdn.example.com/pkg.tgz";
        ListAppender<ILoggingEvent> logAppender = attachLogAppender(UrlVerifier.class);
        try (MockedStatic<AolUtils> mockedAolUtils = Mockito.mockStatic(AolUtils.class)) {
            mockedAolUtils.when(AolUtils::isAol).thenReturn(true);
            assertTrue(urlVerifier.isRemoteRepoBlocked(url, "test-repo"));
            assertTrue(urlVerifier.isRemoteRepoBlocked(url, "test-repo"));
            long warnings = logAppender.list.stream()
                    .filter(event -> event.getLevel() == Level.WARN)
                    .filter(event -> event.getFormattedMessage().contains(url))
                    .count();
            assertEquals(1, warnings, "The scheme-less repository url warning must be logged once per cache window");
        } finally {
            detachLogAppender(UrlVerifier.class, logAppender);
        }
    }

    /**
     * The guard is checked after the same skip condition as the rest of {@code isRemoteRepoBlocked}, rather than
     * ahead of it: on-premise, where {@code !AolUtils.isAol()} already makes the remainder of the method a no-op,
     * a scheme-less repository url must neither be blocked nor logged either.
     */
    @Test
    void isRemoteRepoBlocked_shouldNotBlockOrWarn_onPremForUrlStatingNoSchemeOfItsOwn() {
        String url = "//cdn.example.com/pkg.tgz";
        ListAppender<ILoggingEvent> logAppender = attachLogAppender(UrlVerifier.class);
        try (MockedStatic<AolUtils> mockedAolUtils = Mockito.mockStatic(AolUtils.class)) {
            mockedAolUtils.when(AolUtils::isAol).thenReturn(false);
            assertFalse(urlVerifier.isRemoteRepoBlocked(url, "test-repo"),
                    "On-prem, the rest of isRemoteRepoBlocked is a no-op, so the scheme guard must not block either");
            boolean warned = logAppender.list.stream().anyMatch(event -> event.getLevel() == Level.WARN);
            assertFalse(warned, "On-prem, the scheme guard must not log either");
        } finally {
            detachLogAppender(UrlVerifier.class, logAppender);
        }
    }

    private void assertBlockedAsRepositoryUrl(String url) {
        try (MockedStatic<AolUtils> mockedAolUtils = Mockito.mockStatic(AolUtils.class)) {
            mockedAolUtils.when(AolUtils::isAol).thenReturn(true);
            assertTrue(urlVerifier.isRemoteRepoBlocked(url, "test-repo"),
                    "A URL with no derivable host must fail closed - we cannot evaluate where it would connect");
        }
    }

    /**
     * That fail-closed warning is deduplicated the same way, and for the same reason as
     * {@link #resolveAddress_alwaysResolvesFreshButDedupsUnresolvedHostLogLine()}: the floors run on every request,
     * so an upstream that keeps redirecting to a url naming no host would otherwise put a warning in the service log
     * once per request.
     */
    @Test
    void shouldWarnOncePerWindow_whenNoHostCanBeDeterminedFromUrl() {
        String url = "http://:9911/";
        ListAppender<ILoggingEvent> logAppender = attachLogAppender(UrlVerifier.class);
        try {
            assertTrue(urlVerifier.isRedirectTargetBlocked(url, "bower-remote"));
            assertTrue(urlVerifier.isRedirectTargetBlocked(url, "bower-remote"));
            long warnings = logAppender.list.stream()
                    .filter(event -> event.getLevel() == Level.WARN)
                    .filter(event -> event.getFormattedMessage().contains(url))
                    .count();
            assertEquals(1, warnings, "The fail-closed warning must be logged once per cache window");
        } finally {
            detachLogAppender(UrlVerifier.class, logAppender);
        }
    }

    @Test
    void isRemoteRepoBlocked_shouldBlockUndeterminableHost_regardlessOfTheFailOpenFlags() {
        // Both flags are read at construction time, hence the dedicated instance.
        getBound().setProperty(ConstantValues.remoteRepoBlockUrlStrictPolicy, "false");
        getBound().setProperty(ConstantValues.remoteRepoBlockUrlOnUnknownHost, "false");
        UrlVerifier lenientVerifier = new UrlVerifier(new DenyListCacheHolderService(), configService);
        try (MockedStatic<AolUtils> mockedAolUtils = Mockito.mockStatic(AolUtils.class)) {
            mockedAolUtils.when(AolUtils::isAol).thenReturn(true);
            assertTrue(lenientVerifier.isRemoteRepoBlocked("http://:8046/", "test-repo"),
                    "Failing closed on an undeterminable host must not be defeated by either fail-open flag");
        }
    }

    /**
     * RTDEV-96436: package handlers pass the outbound path of a remote fetch as the "url" - a bare npm package name,
     * a cargo crate path, a docker digest. None names a destination; each is appended
     * to the repository's base url, and the assembled url is what the per-request floor judges. Evaluating them here
     * instead blocked ordinary package traffic whenever the guard was enabled, and logged a warning per fetch while
     * it was not.
     * <p>
     * The last two hold a colon in their first segment, so they parse as a {@link java.net.URI} with a scheme even
     * though they name no host and are appended like any other path - a docker digest is the everyday example.
     * Requiring the scheme to be absent, rather than the authority, rejected them.
     */
    @ParameterizedTest
    @ValueSource(strings = {
            "express",
            "@scope%2fpkg",
            "some/nested/path.tgz",
            "/just/a/path",
            "downloadTagFile/org/repo/v1.0!/file.txt",
            "_relative/dep-1.0.0.tgz",
            "sha256:abc123def",
            "foo:bar/baz",
            "library/nginx/manifests/sha256:abc123def"
    })
    void shouldNotEvaluateRelativeReference_whichNamesNoDestination(String url) {
        ListAppender<ILoggingEvent> logAppender = attachLogAppender(UrlVerifier.class);
        try (MockedStatic<InetAddress> mockedInetAddress = Mockito.mockStatic(InetAddress.class)) {
            assertFalse(urlVerifier.isOutboundFetchBlocked(url, "npm-remote"),
                    "A path the assembler appends names no destination, so there is nothing to block");
            assertFalse(urlVerifier.isOutboundTargetBlocked(url, "npm-remote"),
                    "The same holds on the per-request floor");
            // The verdict alone would not notice the regression coming back: if a host were derived from the path
            // again, the lookup would fail and fail-open would report "not blocked" just the same. What has to be
            // asserted is that nothing was looked up and nothing was reported.
            mockedInetAddress.verifyNoInteractions();
            assertTrue(logAppender.list.stream().noneMatch(event -> event.getLevel() == Level.WARN),
                    "An ordinary package path must not be reported as a fault on every fetch");
        } finally {
            detachLogAppender(UrlVerifier.class, logAppender);
        }
    }

    /**
     * A path holding a character RFC 3986 forbids - a space, or a {@code %} that starts no escape - does not parse as
     * a {@link java.net.URI} at all. It is still a path: nothing can connect to it, and the assembler appends it like
     * any other, since the predicate deciding verbatim use reports the same parse failure as "not absolute". Letting
     * it through to the checks instead produced a WARN with a stack trace on every uncached fetch - louder than the
     * log line RTDEV-96436 exists to remove - and then a verdict that depends on the strict-policy flag.
     */
    @ParameterizedTest
    @ValueSource(strings = {
            "downloadTagFile/org/repo/master!my file.md",
            "pkg/100%.tgz",
            "a b/c d.tgz"
    })
    void shouldNotEvaluateOrWarnAboutPathThatDoesNotParseAsAUri(String url) {
        ListAppender<ILoggingEvent> logAppender = attachLogAppender(UrlVerifier.class);
        try (MockedStatic<InetAddress> mockedInetAddress = Mockito.mockStatic(InetAddress.class)) {
            assertFalse(urlVerifier.isOutboundFetchBlocked(url, "npm-remote"),
                    "A path that cannot be parsed is still a path, and is appended rather than connected to");
            assertFalse(urlVerifier.isOutboundTargetBlocked(url, "npm-remote"),
                    "The same holds on the per-request floor");
            mockedInetAddress.verifyNoInteractions();
            assertTrue(logAppender.list.stream().noneMatch(event -> event.getLevel() == Level.WARN),
                    "An ordinary package path must not be reported as a fault on every fetch");
        } finally {
            detachLogAppender(UrlVerifier.class, logAppender);
        }
    }

    /**
     * The Bower external-dependency guard ({@code NpmRemoteFetcher#isBlockedBySsrfGuard}) checks its dependency url
     * through the create/update method, which does not skip - so a relative dependency would be blocked there rather
     * than let through, the opposite of the fetch-time verdict above. That asymmetry is invisible today, because
     * every {@code NpmRemoteFetcher} subclass yields an absolute url or null and {@code NpmDependencyFetcherFactory}
     * only selects the URL fetcher for a string {@code new URL(..)} accepts. It is pinned here so a change on that
     * side surfaces as a decision rather than as a blocked fetch on SaaS.
     */
    @Test
    void isRemoteRepoBlocked_shouldStillBlockRelativeReference_asTheBowerGuardCallsIt() {
        try (MockedStatic<AolUtils> mockedAolUtils = Mockito.mockStatic(AolUtils.class)) {
            mockedAolUtils.when(AolUtils::isAol).thenReturn(true);
            assertTrue(urlVerifier.isRemoteRepoBlocked("_relative/dep-1.0.0.tgz", "bower-virtual"),
                    "The create/update method does not skip a path, whichever caller hands it one");
        }
    }

    /**
     * The redirect floor skips a relative {@code Location} deliberately, not incidentally: the client resolves it
     * against the request url, whose host was cleared before that request went out. {@code
     * UrlVerifierRedirectStrategy} filters these out before the floor sees them today, so asserting the verdict here
     * keeps the redirect floor's own behaviour pinned rather than resting on that filter staying as it is.
     */
    @ParameterizedTest
    @ValueSource(strings = {"/internal/admin", "file.tgz", "../sibling/file.tgz"})
    void isRedirectTargetBlocked_shouldAllowLocationStatingNoAuthority(String location) {
        assertFalse(urlVerifier.isRedirectTargetBlocked(location, "bower-remote"),
                "A relative Location resolves to the host this request was already cleared for");
    }

    /**
     * The create/update path has no base url to append to, so a url it cannot parse is one it cannot place: that is
     * the same position as a url naming no host, and it must reach the same verdict rather than fall through to the
     * strict-policy default.
     */
    @Test
    void isRemoteRepoBlocked_shouldBlock_whenUrlDoesNotParseAsAUri() {
        // A lenient instance, so the verdict cannot come from the strict policy: both flags are read at construction.
        getBound().setProperty(ConstantValues.remoteRepoBlockUrlStrictPolicy, "false");
        getBound().setProperty(ConstantValues.remoteRepoBlockUrlOnUnknownHost, "false");
        UrlVerifier lenientVerifier = new UrlVerifier(new DenyListCacheHolderService(), configService);
        try (MockedStatic<AolUtils> mockedAolUtils = Mockito.mockStatic(AolUtils.class)) {
            mockedAolUtils.when(AolUtils::isAol).thenReturn(true);
            assertTrue(lenientVerifier.isRemoteRepoBlocked("http://evil host/x", "test-repo"),
                    "A repository url that does not parse must fail closed, whatever the strict policy says");
        }
    }

    /**
     * A url the assembler uses verbatim is a destination even when it states an empty authority, which
     * {@link java.net.URI} reports as none at all: {@code file:///x} and {@code http:///x} have a null authority
     * while {@link org.artifactory.util.HttpUtils#isAbsolute(String)} - the predicate deciding verbatim use - calls
     * them absolute. Keying the skip on the authority alone therefore took the check off strings that are connected
     * to rather than appended, which is why the predicate needs both clauses.
     */
    @ParameterizedTest
    @ValueSource(strings = {"file:///etc/passwd", "http:///x"})
    void shouldStillEvaluateAbsoluteUrlStatingAnEmptyAuthority(String url) {
        assertTrue(urlVerifier.isOutboundTargetBlocked(url, "test-repo"),
                "A URL used verbatim must be evaluated, and one naming no host fails closed");
    }

    /**
     * Borrowing a scheme to derive a host must not turn a malformed authority into a usable one: {@code //:8046/}
     * states an authority that yields no host, so it keeps failing closed instead of slipping through the
     * protocol-relative path. It is blocked without a lookup, which is why it is not among the resolved spellings in
     * {@link #shouldStillEvaluateUrlStatingAnAuthority_whichDoesNameADestination(String)}.
     */
    @Test
    void shouldBlockProtocolRelativeUrl_whoseAuthorityYieldsNoHost() {
        assertTrue(urlVerifier.isRedirectTargetBlocked("//:8046/", "bower-remote"),
                "An authority with no host in it must fail closed, scheme or no scheme");
    }

    /**
     * A url stating an authority names a destination and must be evaluated, whether or not it also states a scheme
     * and whether or not a host can be derived from it. The bracketed IPv6 forms are here because deriving this from
     * {@link org.artifactory.util.HttpUtils#isAbsolute(String)} - which the assembler uses to decide verbatim use -
     * reports them as not absolute, its colon escaping having mangled {@code ::} into {@code %3A%3A}; borrowing that
     * predicate would have skipped the check for every IPv6 loopback upstream.
     */
    @ParameterizedTest
    @ValueSource(strings = {
            "http://127.0.0.1:8046/router/api",
            "http://127.0.0.1:8046/sha256:abc123def",
            "http://[::1]:8046/",
            "http://[::ffff:127.0.0.1]:8046/",
            // No scheme, but an authority: as a redirect Location the client resolves this against the request url,
            // so the host has to be derived from the authority - in every spelling the absolute forms above cover,
            // since a discrepancy with how the client parses one of these is an SSRF path.
            "//127.0.0.1:8046/router/api",
            "//[::1]:8046/",
            "//[::ffff:127.0.0.1]:8046/",
            "//%31%32%37%2E%30%2E%30%2E%31:8046/",
            "//127.1:8046/",
            "//127.0.0.1.:8046/",
            "//0.0.0.0:8046/",
            "//registry.example@127.0.0.1:8046/"
    })
    void shouldStillEvaluateUrlStatingAnAuthority_whichDoesNameADestination(String url) {
        assertResolvedAndBlocked(url);
    }

    /**
     * Blocking is only the right answer here if the host was looked up: a url whose host cannot be derived is also
     * reported blocked, by failing closed, which is how the protocol-relative case above passed while every such
     * reference was in fact being refused for lacking a scheme. Asserting the lookup happened separates the two.
     */
    private void assertResolvedAndBlocked(String url) {
        try (MockedStatic<InetAddress> mockedInetAddress = Mockito.mockStatic(InetAddress.class,
                Mockito.CALLS_REAL_METHODS)) {
            assertTrue(urlVerifier.isOutboundTargetBlocked(url, "test-repo"),
                    "A URL stating an authority denotes a destination, so the loopback check must still run on it");
            mockedInetAddress.verify(() -> InetAddress.getAllByName(anyString()), atLeastOnce());
        }
    }

    /**
     * Evaluating a protocol-relative reference has to mean judging the host it states, not refusing it. A CDN or
     * mirror redirect arrives as {@code Location: //cdn.example.com/...} and must be followed; failing closed on the
     * absent scheme rejected every one of them, which the loopback cases above could not reveal because they assert
     * the same verdict either way.
     */
    @Test
    void shouldNotBlockProtocolRelativeUrl_whoseHostIsRoutable() {
        InetAddress routableAddress = mock(InetAddress.class);
        try (MockedStatic<InetAddress> mockedInetAddress = Mockito.mockStatic(InetAddress.class)) {
            mockedInetAddress.when(() -> InetAddress.getAllByName("cdn.example.com"))
                    .thenReturn(new InetAddress[] {routableAddress});
            assertFalse(urlVerifier.isRedirectTargetBlocked("//cdn.example.com/pkg-1.0.0.tgz", "bower-remote"),
                    "A protocol-relative redirect to a routable host must be followed");
            assertFalse(urlVerifier.isOutboundTargetBlocked("//cdn.example.com/pkg-1.0.0.tgz", "bower-remote"),
                    "The same holds on the per-request floor");
        }
    }

    @Test
    void isOutboundTargetBlocked_shouldBlockJFrogInternalHost_whateverTheCasing() {
        // The JFrog-internal host list is an exact string match, so the derived host has to be case-folded first.
        InetAddress mockPublicAddress = mock(InetAddress.class);
        try (MockedStatic<InetAddress> mockedInetAddress = Mockito.mockStatic(InetAddress.class)) {
            mockedInetAddress.when(() -> InetAddress.getAllByName("jpms.jfrog.com"))
                    .thenReturn(new InetAddress[] {mockPublicAddress});
            assertTrue(urlVerifier.isOutboundTargetBlocked("https://JPMS.JFROG.COM/", "test-repo"),
                    "An upper-cased JFrog internal host must be blocked just like its lower-cased spelling");
        }
    }

    /**
     * The textbook DNS-based SSRF: an ordinary-looking hostname whose A record points at the local host. Nothing
     * in the URL hints at loopback, so only the address check can catch it. Asserted through the create/update
     * path and the per-request floor, which differ in whether they consult the per-domain verdict cache.
     */
    @Test
    void shouldBlockPublicHostname_resolvingSolelyToLoopback() {
        String url = "https://rebinder.example/pkg-1.0.0.tgz";
        InetAddress loopbackAddress = mock(InetAddress.class);
        when(loopbackAddress.isLoopbackAddress()).thenReturn(true);
        try (MockedStatic<InetAddress> mockedInetAddress = Mockito.mockStatic(InetAddress.class);
                MockedStatic<AolUtils> mockedAolUtils = Mockito.mockStatic(AolUtils.class)) {
            mockedInetAddress.when(() -> InetAddress.getAllByName("rebinder.example"))
                    .thenReturn(new InetAddress[] {loopbackAddress});
            mockedAolUtils.when(AolUtils::isAol).thenReturn(true);
            assertTrue(urlVerifier.isOutboundTargetBlocked(url, "test-repo"),
                    "The outbound floor must block a hostname that resolves to the local host");
            assertTrue(urlVerifier.isRemoteRepoBlocked(url, "test-repo"),
                    "A remote repo must not be created against a hostname that resolves to the local host");
            // The counterpart to shouldNotEvaluateRelativeReference_whichNamesNoDestination(String): a url naming a
            // host is resolved, so that test's "nothing was looked up" means the skip and not a dead check.
            mockedInetAddress.verify(() -> InetAddress.getAllByName("rebinder.example"), atLeastOnce());
        }
    }

    /**
     * Stripping a leading {@code www.} before resolving sent the address check to a different name than the one
     * the outbound client connects to, which is the desync RTDEV-95692 closes. Here the bare domain answers a
     * routable address, so anything resolving it clears the URL, while the host in the URL is loopback.
     */
    @Test
    void shouldBlockWwwHostResolvingToLoopback_whenBareDomainResolvesElsewhere() {
        String url = "http://www.attacker.test:8046/";
        InetAddress loopbackAddress = mock(InetAddress.class);
        when(loopbackAddress.isLoopbackAddress()).thenReturn(true);
        InetAddress routableAddress = mock(InetAddress.class);
        try (MockedStatic<InetAddress> mockedInetAddress = Mockito.mockStatic(InetAddress.class);
                MockedStatic<AolUtils> mockedAolUtils = Mockito.mockStatic(AolUtils.class)) {
            mockedInetAddress.when(() -> InetAddress.getAllByName("www.attacker.test"))
                    .thenReturn(new InetAddress[] {loopbackAddress});
            mockedInetAddress.when(() -> InetAddress.getAllByName("attacker.test"))
                    .thenReturn(new InetAddress[] {routableAddress});
            mockedAolUtils.when(AolUtils::isAol).thenReturn(true);
            assertTrue(urlVerifier.isOutboundTargetBlocked(url, "test-repo"),
                    "The host in the URL must be the host that gets resolved, prefix and all");
            assertTrue(urlVerifier.isRemoteRepoBlocked(url, "test-repo"),
                    "A remote repo must not be cleared by resolving a different name than it points at");
            mockedInetAddress.verify(() -> InetAddress.getAllByName("attacker.test"), never());
        }
    }

    @Test
    void isOutboundFetchBlocked_shouldStillApplyDenylistEntry_namingTheBareDomainOfAWwwHost() {
        DenylistValidator denylistValidator = mock(DenylistValidator.class);
        when(denylistValidator.isBlocked("blocked.example")).thenReturn(true);
        UrlVerifier verifier = verifierWithDenylistValidator(denylistValidator);
        InetAddress routableAddress = mock(InetAddress.class);
        try (MockedStatic<InetAddress> mockedInetAddress = Mockito.mockStatic(InetAddress.class)) {
            mockedInetAddress.when(() -> InetAddress.getAllByName("www.blocked.example"))
                    .thenReturn(new InetAddress[] {routableAddress});
            assertTrue(verifier.isOutboundFetchBlocked("https://www.blocked.example/pkg", "test-repo"),
                    "Keeping the www. prefix must not narrow what a denylist entry naming the bare domain covers");
        }
    }

    @Test
    void isOutboundTargetBlocked_shouldBlockDomain_resolvingToBothRoutableAndLoopbackAddresses() {
        // getByName only ever returned the first address, so a record answering a routable address first cleared
        // the check while the client remained free to connect to the loopback one.
        InetAddress routableAddress = mock(InetAddress.class);
        InetAddress loopbackAddress = mock(InetAddress.class);
        when(loopbackAddress.isLoopbackAddress()).thenReturn(true);
        try (MockedStatic<InetAddress> mockedInetAddress = Mockito.mockStatic(InetAddress.class)) {
            mockedInetAddress.when(() -> InetAddress.getAllByName("multi-homed.example"))
                    .thenReturn(new InetAddress[] {routableAddress, loopbackAddress});
            assertTrue(urlVerifier.isOutboundTargetBlocked("https://multi-homed.example/pkg", "test-repo"),
                    "Every address the domain resolves to must be checked, not only the first");
        }
    }

    /**
     * The Bridge check now takes the port and path from the same derived identity as every other check, so a URL
     * denoting the router's Bridge path earns the exemption however its host is spelled. Previously the second
     * parse read {@code URI.getPort()} as -1 for these spellings and denied it, which was safe but inconsistent -
     * the plain spelling of the very same destination has always been exempt.
     */
    @ParameterizedTest
    @ValueSource(strings = {
            "http://127.0.0.1:8046/bridge-api",
            "http://%31%32%37%2E%30%2E%30%2E%31:8046/bridge-api",
            "http://127.0.0.1.:8046/bridge-api"
    })
    void isLoopbackBridgeUrl_shouldExemptBridgeUrl_whateverTheHostSpelling(String url) {
        assertTrue(urlVerifier.isLoopbackBridgeUrl(url),
                "Equivalent spellings of the same Bridge destination must reach the same verdict");
    }

    @Test
    void isLoopbackBridgeUrl_shouldDenyExemption_whenDomainAlsoResolvesToRoutableAddress() {
        InetAddress routableAddress = mock(InetAddress.class);
        InetAddress loopbackAddress = mock(InetAddress.class);
        when(loopbackAddress.isLoopbackAddress()).thenReturn(true);
        try (MockedStatic<InetAddress> mockedInetAddress = Mockito.mockStatic(InetAddress.class)) {
            mockedInetAddress.when(() -> InetAddress.getAllByName("dual-homed.example"))
                    .thenReturn(new InetAddress[] {loopbackAddress, routableAddress});
            assertFalse(urlVerifier.isLoopbackBridgeUrl("http://dual-homed.example:8046/bridge-api"),
                    "The Bridge exemption requires every resolved address to be loopback");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://localhost:8046/bridge-x/../router/api/v1/system/health",
            "http://localhost:8046/bridge-x/..%2frouter/api/v1/system/health",
            // Percent-encoded dot segments: URI.normalize() leaves these alone, so only the decoded path shows them.
            "http://localhost:8046/bridge-x/%2e%2e/router/api/v1/system/health",
            "http://localhost:8046/bridge-x/%2e%2e%2frouter/api/v1/system/health",
            "http://localhost:8046/bridge-x/%2E%2E/router/api/v1/system/health",
            // Double-encoded: one decode leaves the literal text "%2e%2e", so the segment is invisible to a plain
            // ".." comparison while a hop that decodes once more resolves it into a traversal.
            "http://localhost:8046/bridge-x/%252e%252e/router/api/v1/system/health",
            "http://localhost:8046/bridge-x/%252e%252e%252frouter/api/v1/system/health"
    })
    void isOutboundTargetBlocked_shouldBlockTraversalOutOfBridgePath(String url) {
        assertTrue(urlVerifier.isOutboundTargetBlocked(url, "test-repo"),
                "A traversal out of the Bridge route must not inherit the Bridge loopback exemption");
    }

    /**
     * A string naming no host is simply not a Bridge url, rather than a failure of the Bridge check, so it must not
     * put a warning and a stack trace in the service log. The values below are relative alternative-download paths
     * (RTDEV-96436), which the public entry points now skip before the Bridge check ever sees them - see
     * {@link #shouldNotEvaluateRelativeReference_whichNamesNoDestination(String)} - but this method is reachable on
     * its own, and reaches the same catch for any url whose host cannot be derived.
     */
    @ParameterizedTest
    @ValueSource(strings = {"express", "http-proxy", "some/nested/path.tgz"})
    void isLoopbackBridgeUrl_shouldNotWarn_whenUrlNamesNoHost(String url) {
        ListAppender<ILoggingEvent> logAppender = attachLogAppender(UrlVerifier.class);
        try {
            assertFalse(urlVerifier.isLoopbackBridgeUrl(url), "A path naming no host is not a Bridge url");
            assertTrue(logAppender.list.stream().noneMatch(event -> event.getLevel() == Level.WARN),
                    "Naming no host is an ordinary negative answer here, not a fault worth warning about");
        } finally {
            detachLogAppender(UrlVerifier.class, logAppender);
        }
    }

    /**
     * A traversal that lands on another Bridge route keeps the exemption, and is meant to. The exemption is not
     * scoped to one Bridge instance - {@code bridgeUrlPathPattern} covers every route on the router port - so
     * arriving at {@code /bridge-y/foo} by way of {@code /bridge-x/..} reaches a destination that the plain
     * spelling is exempt for anyway. Normalizing first is what points the decision at that destination rather than
     * at how the URL is spelled, which is the property this class exists to hold; the traversals that actually
     * leave the Bridge namespace are covered by
     * {@link #isOutboundTargetBlocked_shouldBlockTraversalOutOfBridgePath(String)}.
     */
    @ParameterizedTest
    @ValueSource(strings = {
            "http://localhost:8046/bridge-x/../bridge-y/foo",
            "http://localhost:8046/bridge-a/foo/../../bridge-b/x"
    })
    void isLoopbackBridgeUrl_shouldStillExemptTraversalLandingOnAnotherBridgeRoute(String url) {
        assertTrue(urlVerifier.isLoopbackBridgeUrl(url),
                "Every Bridge route on the router port is exempt, so landing on one of them grants nothing new");
    }

    /**
     * The traversal rejection looks for dot segments, not for the escapes that can spell them: a path may
     * legitimately hold a percent sign once decoded (a file named {@code 100%} arrives as {@code 100%25}), and a
     * Bridge route carrying one must keep its exemption rather than be denied for the way it is spelled.
     */
    @ParameterizedTest
    @ValueSource(strings = {
            "http://localhost:8046/bridge-x/artifactory/repo/100%25.jar",
            "http://localhost:8046/bridge-x/artifactory/repo/a.b.c/file.jar"
    })
    void isLoopbackBridgeUrl_shouldStillExemptRouteWhoseDecodedPathHoldsAPercentOrDots(String url) {
        assertTrue(urlVerifier.isLoopbackBridgeUrl(url),
                "Rejecting encoded traversal must not deny a genuine Bridge route for an unrelated escape");
    }

    @Test
    void isRemoteRepoBlocked_whitelistPrefix_shouldNotExemptLoopbackBehindUserinfo() {
        getBound().setProperty(ConstantValues.whitelistRemoteRepoUrls, "https://acme.example");
        UrlVerifier verifierWithWhitelist = new UrlVerifier(new DenyListCacheHolderService(), configService);
        try (MockedStatic<AolUtils> mockedAolUtils = Mockito.mockStatic(AolUtils.class)) {
            mockedAolUtils.when(AolUtils::isAol).thenReturn(true);
            assertTrue(verifierWithWhitelist.isRemoteRepoBlocked("https://acme.example@127.0.0.1:8046/", "test-repo"),
                    "A whitelisted prefix must not exempt a URL whose real host is the part after the '@'");
        }
    }

    @Test
    void isOutboundTargetBlocked_whitelistPrefix_shouldNotExemptDomainThatMerelyStartsWithIt() {
        getBound().setProperty(ConstantValues.whitelistRemoteRepoUrls, "https://acme.example");
        UrlVerifier verifierWithWhitelist = new UrlVerifier(new DenyListCacheHolderService(), configService);
        InetAddress loopbackAddress = mock(InetAddress.class);
        when(loopbackAddress.isLoopbackAddress()).thenReturn(true);
        try (MockedStatic<InetAddress> mockedInetAddress = Mockito.mockStatic(InetAddress.class)) {
            mockedInetAddress.when(() -> InetAddress.getAllByName("acme.example.attacker.test"))
                    .thenReturn(new InetAddress[] {loopbackAddress});
            assertTrue(verifierWithWhitelist.isOutboundTargetBlocked("https://acme.example.attacker.test/pkg",
                    "test-repo"),
                    "A whitelisted prefix must not exempt a different domain that only starts with the same text");
        }
    }

    /**
     * A path-scoped whitelist entry must confine the exemption to that path. Traversing out of it reaches another
     * endpoint on a host that is only trusted for the configured prefix - on loopback, another local endpoint.
     */
    @ParameterizedTest
    @ValueSource(strings = {
            "http://127.0.0.1:9999/trusted/../admin",
            "http://127.0.0.1:9999/trusted/..%2fadmin",
            "http://127.0.0.1:9999/trusted/./../admin",
            "http://127.0.0.1:9999/trusted/sub/../../admin",
            // Percent-encoded dot segments: URI.normalize() leaves these alone, so only the decoded path shows them.
            "http://127.0.0.1:9999/trusted/%2e%2e/admin",
            "http://127.0.0.1:9999/trusted/%2e%2e%2fadmin",
            "http://127.0.0.1:9999/trusted/%2E%2E/admin",
            // Double-encoded: one decode leaves the literal text "%2e%2e", which only a deeper decode resolves.
            "http://127.0.0.1:9999/trusted/%252e%252e/admin"
    })
    void isOutboundTargetBlocked_whitelistedPath_shouldNotExemptTraversalOutOfIt(String url) {
        getBound().setProperty(ConstantValues.whitelistRemoteRepoUrls, "http://127.0.0.1:9999/trusted");
        UrlVerifier verifierWithWhitelist = new UrlVerifier(new DenyListCacheHolderService(), configService);
        assertTrue(verifierWithWhitelist.isOutboundTargetBlocked(url, "test-repo"),
                "A path-scoped whitelist entry must not exempt a URL that traverses out of that path");
    }

    @Test
    void isOutboundTargetBlocked_whitelistedPath_shouldStillExemptPathsBeneathIt() {
        getBound().setProperty(ConstantValues.whitelistRemoteRepoUrls, "http://127.0.0.1:9999/trusted");
        UrlVerifier verifierWithWhitelist = new UrlVerifier(new DenyListCacheHolderService(), configService);
        assertFalse(verifierWithWhitelist.isOutboundTargetBlocked("http://127.0.0.1:9999/trusted/sub/a.tgz",
                "test-repo"),
                "Rejecting traversal must not stop the entry from covering what it is meant to cover");
        assertFalse(verifierWithWhitelist.isOutboundTargetBlocked("http://127.0.0.1:9999/trusted/./sub/a.tgz",
                "test-repo"),
                "A '.' segment resolves inside the whitelisted path, so it stays exempt");
    }

    /**
     * A path-scoped entry must end on a segment boundary. Every path below is a sibling of the whitelisted one, not
     * a descendant of it, so it is a separate endpoint on a host trusted only for the configured path.
     */
    @ParameterizedTest
    @ValueSource(strings = {
            "http://127.0.0.1:9999/trusted-evil",
            "http://127.0.0.1:9999/trustedextra/a.tgz",
            "http://127.0.0.1:9999/trusted.bak"
    })
    void isOutboundTargetBlocked_whitelistedPath_shouldNotExemptASiblingPathSharingItsPrefix(String url) {
        getBound().setProperty(ConstantValues.whitelistRemoteRepoUrls, "http://127.0.0.1:9999/trusted");
        UrlVerifier verifierWithWhitelist = new UrlVerifier(new DenyListCacheHolderService(), configService);
        assertTrue(verifierWithWhitelist.isOutboundTargetBlocked(url, "test-repo"),
                "A whitelisted path must not carry the exemption over to a sibling that merely starts with it");
    }

    @Test
    void isOutboundTargetBlocked_whitelistedPath_shouldStillExemptThePathItself() {
        getBound().setProperty(ConstantValues.whitelistRemoteRepoUrls, "http://127.0.0.1:9999/trusted");
        UrlVerifier verifierWithWhitelist = new UrlVerifier(new DenyListCacheHolderService(), configService);
        assertFalse(verifierWithWhitelist.isOutboundTargetBlocked("http://127.0.0.1:9999/trusted", "test-repo"),
                "Requiring a segment boundary must not stop the entry from covering the exact path it names");
        assertFalse(verifierWithWhitelist.isOutboundTargetBlocked("http://127.0.0.1:9999/trusted/", "test-repo"),
                "The same path with a trailing slash denotes the same endpoint, so it stays exempt");
    }

    /**
     * The entry's own trailing slash must not change what it covers, or requiring a boundary would deny the
     * descendants of every entry configured as a directory.
     */
    @Test
    void isOutboundTargetBlocked_whitelistEntryWithTrailingSlash_shouldStillCoverPathsBeneathIt() {
        getBound().setProperty(ConstantValues.whitelistRemoteRepoUrls, "http://127.0.0.1:9999/trusted/");
        UrlVerifier verifierWithWhitelist = new UrlVerifier(new DenyListCacheHolderService(), configService);
        assertFalse(verifierWithWhitelist.isOutboundTargetBlocked("http://127.0.0.1:9999/trusted/sub/a.tgz",
                "test-repo"),
                "An entry naming a directory must keep covering what is below it");
        assertTrue(verifierWithWhitelist.isOutboundTargetBlocked("http://127.0.0.1:9999/trusted-evil", "test-repo"),
                "The boundary must hold for a directory entry too");
    }

    @Test
    void isOutboundTargetBlocked_whitelistEntryWithoutPort_shouldStillCoverEveryPortOnThatHost() {
        getBound().setProperty(ConstantValues.whitelistRemoteRepoUrls, "http://127.0.0.1");
        UrlVerifier verifierWithWhitelist = new UrlVerifier(new DenyListCacheHolderService(), configService);
        assertFalse(verifierWithWhitelist.isOutboundTargetBlocked("http://127.0.0.1:9999/trusted", "test-repo"),
                "An entry that pins no port must keep exempting every port on that host, as it does today");
        assertTrue(verifierWithWhitelist.isOutboundTargetBlocked("https://127.0.0.1:9999/trusted", "test-repo"),
                "The entry's scheme must still have to match");
    }

    @Test
    void isOutboundFetchBlocked_shouldStillConsultTheDenylist_whenTheHostDoesNotResolve() {
        DenylistValidator denylistValidator = mock(DenylistValidator.class);
        when(denylistValidator.isBlocked("denylisted.example")).thenReturn(true);
        UrlVerifier verifier = verifierWithDenylistValidator(denylistValidator);
        try (MockedStatic<InetAddress> mockedInetAddress = Mockito.mockStatic(InetAddress.class)) {
            mockedInetAddress.when(() -> InetAddress.getAllByName("denylisted.example"))
                    .thenThrow(new UnknownHostException("denylisted.example"));
            assertTrue(verifier.isOutboundFetchBlocked("https://denylisted.example/pkg", "test-repo"),
                    "A denylisted host must be blocked even when its address lookup fails");
        }
    }

    @Test
    void isOutboundFetchBlocked_shouldStillCheckTheAddresses_whenTheDenylistLookupFails() {
        DenylistValidator denylistValidator = mock(DenylistValidator.class);
        when(denylistValidator.isBlocked(any())).thenThrow(new IllegalStateException("denylist lookup failed"));
        UrlVerifier verifier = verifierWithDenylistValidator(denylistValidator);
        assertTrue(verifier.isOutboundFetchBlocked("http://127.0.0.1:8046/", "test-repo"),
                "A failing denylist lookup must not stop the loopback address check from running");
    }

    /**
     * The real validator's content comes from the platform-wide {@code shared.security.denylist.addresses} key,
     * which lives in {@code SysConfig} rather than in {@link ConstantValues} and so cannot be seeded through the
     * property stub. The field is only read while a URL is being checked, never during construction, so swapping
     * it afterwards is equivalent to having built the verifier with it.
     */
    private UrlVerifier verifierWithDenylistValidator(DenylistValidator denylistValidator) {
        UrlVerifier verifier = new UrlVerifier(new DenyListCacheHolderService(), configService);
        ReflectionTestUtils.setField(verifier, "validator", denylistValidator);
        return verifier;
    }

    @SuppressWarnings("unchecked")
    private static ListAppender<ILoggingEvent> attachLogAppender(Class<?> loggerOwner) {
        LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.setContext(loggerContext);
        appender.start();
        loggerContext.getLogger(loggerOwner).addAppender(appender);
        return appender;
    }

    private static void detachLogAppender(Class<?> loggerOwner, ListAppender<ILoggingEvent> appender) {
        LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        loggerContext.getLogger(loggerOwner).detachAppender(appender);
        appender.stop();
    }
}