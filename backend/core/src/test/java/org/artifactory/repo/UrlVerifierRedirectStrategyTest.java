/*
 *
 * Artifactory is a binaries repository manager.
 * Copyright (C) 2018 JFrog Ltd.
 *
 * Artifactory is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 * Artifactory is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Artifactory.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package org.artifactory.repo;

import org.apache.http.HttpHeaders;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.ProtocolException;
import org.apache.http.client.RedirectStrategy;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.message.BasicHeader;
import org.apache.http.protocol.HttpContext;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.common.ConstantValues;
import org.artifactory.repo.http.DenyListCacheHolderService;
import org.artifactory.request.UrlVerifier;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.net.InetAddress;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UrlVerifierRedirectStrategyTest extends ArtifactoryHomeBoundJUnitTest {

    private static final String REPO_KEY = "bower-remote";

    private RedirectStrategy delegate;
    private UrlVerifier urlVerifier;
    private HttpRequest request;
    private HttpResponse response;
    private HttpContext context;
    private UrlVerifierRedirectStrategy strategy;

    @BeforeEach
    void setup() {
        delegate = mock(RedirectStrategy.class);
        urlVerifier = mock(UrlVerifier.class);
        request = mock(HttpRequest.class);
        response = mock(HttpResponse.class);
        context = mock(HttpContext.class);
        strategy = new UrlVerifierRedirectStrategy(delegate, urlVerifier, REPO_KEY);
    }

    private void setLocation(String location) {
        when(response.getFirstHeader(HttpHeaders.LOCATION))
                .thenReturn(new BasicHeader(HttpHeaders.LOCATION, location));
    }

    @Test
    void isRedirected_returnsFalse_whenDelegateSaysNoRedirect() throws ProtocolException {
        when(delegate.isRedirected(request, response, context)).thenReturn(false);
        assertFalse(strategy.isRedirected(request, response, context),
                "Must not redirect when the delegate strategy says it is not a redirect");
        verify(urlVerifier, never()).isRedirectTargetBlocked(any(), any());
    }

    @Test
    void isRedirected_skipsCheck_whenBothFlagsDisabled() throws ProtocolException {
        getBound().setProperty(ConstantValues.httpRepoRedirectSsrfGuardEnabled, "false");
        getBound().setProperty(ConstantValues.httpRepoRedirectSsrfGuardShadowMode, "false");
        when(delegate.isRedirected(request, response, context)).thenReturn(true);
        assertTrue(strategy.isRedirected(request, response, context),
                "Must follow redirect and skip the guard entirely when both flags are disabled");
        verify(urlVerifier, never()).isRedirectTargetBlocked(any(), any());
    }

    @Test
    void isRedirected_blocksRedirect_whenGuardEnabledAndLocationBlocked() throws ProtocolException {
        getBound().setProperty(ConstantValues.httpRepoRedirectSsrfGuardEnabled, "true");
        when(delegate.isRedirected(request, response, context)).thenReturn(true);
        setLocation("http://jfrog-argocd-internal.jfrog.io/api/version");
        when(urlVerifier.isRedirectTargetBlocked("http://jfrog-argocd-internal.jfrog.io/api/version", REPO_KEY))
                .thenReturn(true);
        assertFalse(strategy.isRedirected(request, response, context),
                "Guard enabled + blocked Location must fail closed (do not follow)");
        verify(urlVerifier).isRedirectTargetBlocked("http://jfrog-argocd-internal.jfrog.io/api/version", REPO_KEY);
    }

    @Test
    void isRedirected_followsRedirect_whenGuardEnabledAndLocationAllowed() throws ProtocolException {
        getBound().setProperty(ConstantValues.httpRepoRedirectSsrfGuardEnabled, "true");
        when(delegate.isRedirected(request, response, context)).thenReturn(true);
        setLocation("https://registry.bower.io/pkg-1.0.0.tgz");
        when(urlVerifier.isRedirectTargetBlocked("https://registry.bower.io/pkg-1.0.0.tgz", REPO_KEY))
                .thenReturn(false);
        assertTrue(strategy.isRedirected(request, response, context),
                "Guard enabled + allowed Location must follow the redirect");
    }

    @Test
    void isRedirected_followsButLogs_whenShadowModeAndLocationBlocked() throws ProtocolException {
        getBound().setProperty(ConstantValues.httpRepoRedirectSsrfGuardEnabled, "false");
        getBound().setProperty(ConstantValues.httpRepoRedirectSsrfGuardShadowMode, "true");
        when(delegate.isRedirected(request, response, context)).thenReturn(true);
        setLocation("http://127.0.0.1:9999/internal");
        when(urlVerifier.isRedirectTargetBlocked("http://127.0.0.1:9999/internal", REPO_KEY)).thenReturn(true);
        assertTrue(strategy.isRedirected(request, response, context),
                "Shadow mode must log but still follow the redirect");
        verify(urlVerifier).isRedirectTargetBlocked("http://127.0.0.1:9999/internal", REPO_KEY);
    }

    /**
     * A Location stating no authority resolves to the host the request was already validated against, so it is not
     * re-checked. This is the line between it and a protocol-relative Location, which states one and is checked -
     * see the {@code //} cases in {@link #isRedirected_blocksLocationDenotingLocalHost(String)}.
     */
    @ParameterizedTest
    @ValueSource(strings = {"/relative/path/file.tgz", "file.tgz", "../sibling/file.tgz"})
    void isRedirected_skipsCheck_forLocationStatingNoAuthority(String location) throws ProtocolException {
        getBound().setProperty(ConstantValues.httpRepoRedirectSsrfGuardEnabled, "true");
        when(delegate.isRedirected(request, response, context)).thenReturn(true);
        setLocation(location);
        assertTrue(strategy.isRedirected(request, response, context),
                "Relative redirects resolve to the already-validated host and must not be re-checked");
        verify(urlVerifier, never()).isRedirectTargetBlocked(any(), any());
    }

    /**
     * The counterpart to the {@code //} cases in {@link #isRedirected_blocksLocationDenotingLocalHost(String)}:
     * checking a protocol-relative Location has to mean judging the host it states, so a mirror or CDN redirect
     * sent that way is followed. Those cases assert the opposite verdict, so on their own they cannot tell a real
     * loopback decision from one refused for stating no scheme.
     */
    @Test
    void isRedirected_followsProtocolRelativeLocation_whoseHostIsRoutable() throws ProtocolException {
        getBound().setProperty(ConstantValues.httpRepoRedirectSsrfGuardEnabled, "true");
        RedirectStrategy realVerifierStrategy = strategyWithRealVerifier();
        when(delegate.isRedirected(request, response, context)).thenReturn(true);
        setLocation("//cdn.example.com/pkg-1.0.0.tgz");
        InetAddress routableAddress = mock(InetAddress.class);
        try (MockedStatic<InetAddress> mockedInetAddress = Mockito.mockStatic(InetAddress.class)) {
            mockedInetAddress.when(() -> InetAddress.getAllByName("cdn.example.com"))
                    .thenReturn(new InetAddress[] {routableAddress});
            assertTrue(realVerifierStrategy.isRedirected(request, response, context),
                    "A protocol-relative redirect to a routable host must be followed, not refused for its spelling");
        }
    }

    @Test
    void isRedirected_skipsCheck_whenLocationHeaderMissing() throws ProtocolException {
        getBound().setProperty(ConstantValues.httpRepoRedirectSsrfGuardEnabled, "true");
        when(delegate.isRedirected(request, response, context)).thenReturn(true);
        when(response.getFirstHeader(HttpHeaders.LOCATION)).thenReturn(null);
        assertTrue(strategy.isRedirected(request, response, context),
                "Missing Location header must not block the redirect");
        verify(urlVerifier, never()).isRedirectTargetBlocked(any(), any());
    }

    /**
     * RTDEV-95692: the Location values below all denote the local host, and each one used to be followed - the
     * encoded and short forms because no host could be derived from them, and the wildcard address because it was
     * never checked for. A real {@link UrlVerifier} is wired in deliberately: with a mocked one this would only
     * assert that the string is forwarded, not that it is recognised as loopback.
     */
    @ParameterizedTest
    @ValueSource(strings = {
            "http://127.0.0.1:8049/",
            "http://%31%32%37%2E%30%2E%30%2E%31:8049/",
            "http://127.1:8049/",
            "http://127.0.0.1.:8049/",
            "http://2130706433:8049/",
            "http://0.0.0.0:8049/",
            "http://0:8049/",
            "http://registry.example@127.0.0.1:8049/",
            // No scheme, but an authority: the client resolves this against the request url and connects to the
            // host stated here, so treating "not absolute" as "not worth checking" followed it unchecked.
            "//127.0.0.1:8049/",
            "//localhost:8049/internal",
            // Every spelling the absolute cases cover, since the host is derived from the authority for these and a
            // discrepancy with how the client parses one of them is an SSRF path.
            "//[::1]:8049/",
            "//%31%32%37%2E%30%2E%30%2E%31:8049/",
            "//127.1:8049/",
            "//127.0.0.1.:8049/",
            "//0.0.0.0:8049/",
            "//registry.example@127.0.0.1:8049/",
            // Bracketed IPv6 literals: the host is derived from the brackets, so the redirect path has to keep
            // handling them as literals rather than as names to look up.
            "http://[::1]:8049/",
            "http://[::ffff:127.0.0.1]:8049/"
    })
    void isRedirected_blocksLocationDenotingLocalHost(String location) throws ProtocolException {
        getBound().setProperty(ConstantValues.httpRepoRedirectSsrfGuardEnabled, "true");
        RedirectStrategy realVerifierStrategy = strategyWithRealVerifier();
        when(delegate.isRedirected(request, response, context)).thenReturn(true);
        setLocation(location);
        assertFalse(realVerifierStrategy.isRedirected(request, response, context),
                "A redirect to the local host must not be followed, however the Location spells it");
    }

    /**
     * The same guard, reached through what the host resolves to rather than how the Location spells it. A redirect
     * is the one hop where the target host is chosen by the remote server rather than by an admin, so nothing in
     * the string has to look local for it to be.
     */
    @Test
    void isRedirected_blocksLocation_whoseHostResolvesSolelyToLoopback() throws ProtocolException {
        getBound().setProperty(ConstantValues.httpRepoRedirectSsrfGuardEnabled, "true");
        RedirectStrategy realVerifierStrategy = strategyWithRealVerifier();
        when(delegate.isRedirected(request, response, context)).thenReturn(true);
        setLocation("https://rebinder.example/pkg-1.0.0.tgz");
        InetAddress loopbackAddress = mock(InetAddress.class);
        when(loopbackAddress.isLoopbackAddress()).thenReturn(true);
        try (MockedStatic<InetAddress> mockedInetAddress = Mockito.mockStatic(InetAddress.class)) {
            mockedInetAddress.when(() -> InetAddress.getAllByName("rebinder.example"))
                    .thenReturn(new InetAddress[] {loopbackAddress});
            assertFalse(realVerifierStrategy.isRedirected(request, response, context),
                    "A Location naming a public host that resolves to the local host must not be followed");
        }
    }

    /**
     * The routable address is returned first deliberately: reading only the first address, as the floor did before
     * RTDEV-95692, clears this Location while the client stays free to connect to the loopback one behind it.
     */
    @Test
    void isRedirected_blocksLocation_whoseHostAlsoAnswersALoopbackAddress() throws ProtocolException {
        getBound().setProperty(ConstantValues.httpRepoRedirectSsrfGuardEnabled, "true");
        RedirectStrategy realVerifierStrategy = strategyWithRealVerifier();
        when(delegate.isRedirected(request, response, context)).thenReturn(true);
        setLocation("https://multi-homed.example/pkg-1.0.0.tgz");
        InetAddress routableAddress = mock(InetAddress.class);
        InetAddress loopbackAddress = mock(InetAddress.class);
        when(loopbackAddress.isLoopbackAddress()).thenReturn(true);
        try (MockedStatic<InetAddress> mockedInetAddress = Mockito.mockStatic(InetAddress.class)) {
            mockedInetAddress.when(() -> InetAddress.getAllByName("multi-homed.example"))
                    .thenReturn(new InetAddress[] {routableAddress, loopbackAddress});
            assertFalse(realVerifierStrategy.isRedirected(request, response, context),
                    "A Location answering a loopback address as well as a routable one must not be followed");
        }
    }

    /**
     * A real verifier rather than the mocked one: with a mock this would only assert that the Location string is
     * forwarded, not that it is recognised as denoting the local host.
     */
    private RedirectStrategy strategyWithRealVerifier() {
        CentralConfigService configService = mock(CentralConfigService.class, RETURNS_DEEP_STUBS);
        when(configService.getSystemConfiguration().getSysConfig().helper().getRouterPort()).thenReturn(8046);
        return new UrlVerifierRedirectStrategy(delegate,
                new UrlVerifier(new DenyListCacheHolderService(), configService), REPO_KEY);
    }

    @Test
    void getRedirect_delegatesToWrappedStrategy() throws ProtocolException {
        HttpUriRequest expected = mock(HttpUriRequest.class);
        when(delegate.getRedirect(request, response, context)).thenReturn(expected);
        assertSame(expected, strategy.getRedirect(request, response, context),
                "getRedirect must delegate to the wrapped strategy");
        verify(delegate).getRedirect(eq(request), eq(response), eq(context));
    }
}
