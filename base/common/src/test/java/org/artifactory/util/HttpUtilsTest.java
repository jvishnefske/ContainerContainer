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
package org.artifactory.util;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.core.Response;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.utils.DateUtils;
import org.apache.http.message.BasicHeader;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.descriptor.config.CentralConfigDescriptor;
import org.artifactory.repo.RepoPath;
import org.artifactory.request.ArtifactoryRequest;
import org.artifactory.request.RequestThreadLocal;
import org.artifactory.test.ArtifactoryHomeBoundTest;
import org.artifactory.version.ArtifactoryServiceVersion;
import org.artifactory.version.ArtifactoryServiceVersionProvider;
import org.jfrog.build.api.Build;
import org.jfrog.build.api.builder.BuildInfoBuilder;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockServletContext;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.artifactory.api.context.ArtifactoryContext.CONTEXT_ID_PROP;
import static org.artifactory.util.HttpUtils.escapeColonSymbol;
import static org.artifactory.util.HttpUtils.isAbsolute;
import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.reset;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertThrows;
import static org.testng.Assert.assertTrue;

/**
 * Tests the helper {@link HttpUtils} class.
 *
 * @author Yossi Shaul
 */
@Test
public class HttpUtilsTest extends ArtifactoryHomeBoundTest {
    private final static String REPO_KEY = "repoKey";

    @BeforeMethod
    private void setupMock() {
        ArtifactoryContext context = createMock(ArtifactoryContext.class);
        ArtifactoryContextThreadBinder.bind(context);
        CentralConfigService cc = createMock(CentralConfigService.class);
        expect(context.getCentralConfig()).andReturn(cc).anyTimes();
        CentralConfigDescriptor ccd = createMock(CentralConfigDescriptor.class);
        expect(cc.getDescriptor()).andReturn(ccd).anyTimes();
        expect(ccd.getUrlBase()).andReturn(null).anyTimes();
        expect(ccd.getServerUrlForEmail()).andReturn("http://localhost:8082/").anyTimes();
        expect(context.beanForType(CentralConfigService.class)).andReturn(cc).anyTimes();
        expect(context.getContextPath()).andReturn("").andReturn("test");
        replay(context, cc, ccd);
    }

    public void encodeUrlWithParameters() {
        String unescaped = "http://domain:8089/context/path?x=a/b/c&b=c";
        String escaped = BasicHttpUtils.encodeQuery(unescaped);
        assertEquals(escaped, unescaped);
    }

    public void encodeUrlWithSpaces() {
        String unescaped = "http://domain:8089/context/file path.txt";
        String escaped = BasicHttpUtils.encodeQuery(unescaped);
        assertEquals(escaped, "http://domain:8089/context/file%20path.txt");
    }

    public void encodeBuildUrl() {
        String unescaped = "http://127.0.0.1:53123/artifactory/api/build/moo :: moo/999";
        String escaped = BasicHttpUtils.encodeQuery(unescaped);
        assertEquals(escaped, "http://127.0.0.1:53123/artifactory/api/build/moo%20::%20moo/999");
    }

    public void getServletContextUrl() {
        String requestUrl = "http://lala.land.com";
        MockHttpServletRequest mockRequest = new MockHttpServletRequest("GET", requestUrl);
        mockRequest.setServerName("lala.land.com");
        String servletContextUrl = HttpUtils.getServletContextUrl(mockRequest);
        assertEquals(servletContextUrl, requestUrl);
    }

    public void getServletForwardedContextUrl() {
        String requestUrl = "https://bla.bla.com";
        MockHttpServletRequest mockRequest = new MockHttpServletRequest("GET", requestUrl);
        mockRequest.setServerName("lala.land.com");
        mockRequest.addHeader("x-forwarded-host", "bla.bla.com");
        mockRequest.addHeader("x-forwarded-proto", "https");
        String servletContextUrl = HttpUtils.getServletContextUrl(mockRequest);
        assertEquals(servletContextUrl, requestUrl);
    }

    public void getServletPartiallyForwardedContextUrl() {
        String requestUrl = "http://lala.land.com";
        MockHttpServletRequest mockRequest = new MockHttpServletRequest("GET", requestUrl);
        mockRequest.setServerName("lala.land.com");
        mockRequest.addHeader("x-forwarded-host", "bla.bla.com");
        String servletContextUrl = HttpUtils.getServletContextUrl(mockRequest);
        assertEquals(servletContextUrl, requestUrl);
    }

    public void getServletContextUrlHttps() {
        String requestUrl = "https://lala.land.com";
        MockHttpServletRequest mockRequest = new MockHttpServletRequest("GET", requestUrl);
        mockRequest.setServerName("lala.land.com");
        mockRequest.setScheme("https");
        mockRequest.setServerPort(443);
        String servletContextUrl = HttpUtils.getServletContextUrl(mockRequest);
        assertEquals(servletContextUrl, requestUrl);
    }

    public void getServletContextUrlWithUrlBase() {
        String requestUrl = "http://lala.land.com";
        setBaseUrl("https://custombaseurl.net");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", requestUrl);
        request.setServerName("lala.land.com");
        String servletContextUrl = HttpUtils.getServletContextUrl(request);
        assertEquals(servletContextUrl, "https://custombaseurl.net/artifactory");
    }

    public void getServletContextUrlWithUrlBaseTrailingSlash() {
        String requestUrl = "http://lala.land.com";
        setBaseUrl("https://custombaseurl.net/");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", requestUrl);
        request.setServerName("lala.land.com");
        String servletContextUrl = HttpUtils.getServletContextUrl(request);
        assertEquals(servletContextUrl, "https://custombaseurl.net/artifactory");
    }

    public void getServletContextUrlWithUrlBaseoldCustomerContextPath() {
        String requestUrl = "http://lala.land.com";
        setBaseUrl("https://custombaseurl.net/artifactory");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", requestUrl);
        request.setServerName("lala.land.com");
        String servletContextUrl = HttpUtils.getServletContextUrl(request);
        assertEquals(servletContextUrl, "https://custombaseurl.net/artifactory");
    }

    public void getServletContextUrlWithUrlBaseoldCustomerContextPathTrailingSlash() {
        String requestUrl = "http://lala.land.com";
        setBaseUrl("https://custombaseurl.net/artifactory/");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", requestUrl);
        request.setServerName("lala.land.com");
        String servletContextUrl = HttpUtils.getServletContextUrl(request);
        assertEquals(servletContextUrl, "https://custombaseurl.net/artifactory");
    }

    public void getServletContextUrlWithUrlBaseoldCustomerContextPathCustom() {
        String requestUrl = "http://lala.land.com";
        setBaseUrl("https://custombaseurl.net/atlasian");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", requestUrl);
        request.setServerName("lala.land.com");
        String servletContextUrl = HttpUtils.getServletContextUrl(request);
        assertEquals(servletContextUrl, "https://custombaseurl.net/atlasian");
    }

    public void getServletContextUrlWithUrlBaseHttp() {
        String requestUrl = "http://lala.land.com";
        setBaseUrl("http://custombaseurl.net");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", requestUrl);
        request.setServerName("lala.land.com");
        String servletContextUrl = HttpUtils.getServletContextUrl(request);
        assertEquals(servletContextUrl, "http://custombaseurl.net/artifactory");
    }

    public void getServletContextUrlWithUrlBaseoldCustomerContextPathHttp() {
        String requestUrl = "http://lala.land.com";
        setBaseUrl("http://custombaseurl.net/artifactory");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", requestUrl);
        request.setServerName("lala.land.com");
        String servletContextUrl = HttpUtils.getServletContextUrl(request);
        assertEquals(servletContextUrl, "http://custombaseurl.net/artifactory");
    }

    public void getServletContextUrlWithUrlBaseoldCustomerContextPathCustomHttp() {
        String requestUrl = "http://lala.land.com";
        setBaseUrl("http://custombaseurl.net/atlasian");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", requestUrl);
        request.setServerName("lala.land.com");
        String servletContextUrl = HttpUtils.getServletContextUrl(request);
        assertEquals(servletContextUrl, "http://custombaseurl.net/atlasian");
    }

    public void getServletContextUrlWithBaseUrlAndOverridingHeader() {
        String requestUrl = "http://lala.land.com";
        setBaseUrl("https://custombaseurl.net");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", requestUrl);
        request.setServerName("lala.land.com");
        request.addHeader(ArtifactoryRequest.ARTIFACTORY_OVERRIDE_BASE_URL, "http://originartifactory.net");
        String servletContextUrl = HttpUtils.getServletContextUrl(request);
        assertEquals(servletContextUrl, "http://originartifactory.net");
    }

    public void getServletContextUrlWithJFrogOverridingHeader() {
        String requestUrl = "http://lala.land.com";
        String overrideUrl = "http://mothership.net/";
        setBaseUrl("https://custombaseurl.net");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", requestUrl);
        request.setServerName("lala.land.com");
        request.addHeader(ArtifactoryRequest.JFROG_OVERRIDE_BASE_URL, overrideUrl);
        String servletContextUrl = HttpUtils.getServletContextUrl(request);
        assertEquals(servletContextUrl, overrideUrl);

        // On second call context path is test
        servletContextUrl = HttpUtils.getServletContextUrl(request);
        assertEquals(servletContextUrl, overrideUrl + "test");
    }

    public void getServletMothershipContextUrlWithJFrogOverridingHeader() {
        String requestUrl = "http://lala.land.com";
        String overrideUrl = "http://mothership.net";
        setBaseUrl("https://custombaseurl.net");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", requestUrl);
        request.setServerName("lala.land.com");
        request.addHeader(ArtifactoryRequest.JFROG_OVERRIDE_BASE_URL, overrideUrl);
        String servletContextUrl = HttpUtils.getPlatformServletContextUrl(request);
        assertEquals(servletContextUrl, overrideUrl);

        // On second call context path is test
        servletContextUrl = HttpUtils.getPlatformServletContextUrl(request);
        assertEquals(servletContextUrl, overrideUrl);
    }

    public void getServletContextUrlJfrogAndArtifactoryOverrideHeaders() {
        String requestUrl = "http://lala.land.com";
        String overrideUrl = "http://mothership.net/";
        setBaseUrl("https://custombaseurl.net");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", requestUrl);
        request.setServerName("lala.land.com");
        request.addHeader(ArtifactoryRequest.JFROG_OVERRIDE_BASE_URL, overrideUrl);
        request.addHeader(ArtifactoryRequest.ARTIFACTORY_OVERRIDE_BASE_URL, "http://art.com");
        String servletContextUrl = HttpUtils.getServletContextUrl(request);
        assertEquals(servletContextUrl, overrideUrl);
    }

    @Test
    public void getServletContextUrlIgnoresUntrustedJFrogOverrideHeader() {
        String requestUrl = "http://lala.land.com";
        setBaseUrl("https://custombaseurl.net");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", requestUrl);
        request.setServerName("lala.land.com");
        request.addHeader(ArtifactoryRequest.JFROG_OVERRIDE_BASE_URL, "https://evil.com#://host:8082");
        String servletContextUrl = HttpUtils.getServletContextUrl(request);
        assertEquals(servletContextUrl, "https://custombaseurl.net/artifactory");
    }

    @Test
    public void getServletContextUrlIgnoresUntrustedArtifactoryOverrideHeader() {
        String requestUrl = "http://lala.land.com";
        setBaseUrl("https://custombaseurl.net");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", requestUrl);
        request.setServerName("lala.land.com");
        request.addHeader(ArtifactoryRequest.ARTIFACTORY_OVERRIDE_BASE_URL, "https://evil.com/serve?://host:8082");
        String servletContextUrl = HttpUtils.getServletContextUrl(request);
        assertEquals(servletContextUrl, "https://custombaseurl.net/artifactory");
    }

    /**
     * Guards getForwardedHostUrl(): malicious x-forwarded-proto must not win over the request host.
     * Covers H1 (forwarded host+proto) when override-base-url headers are absent.
     */
    @Test(dataProvider = "untrustedForwardedProtoValues")
    public void getServletContextUrlIgnoresUntrustedForwardedProto(String hostileProto) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "http://lala.land.com");
        request.setServerName("lala.land.com");
        request.setScheme("http");
        request.setServerPort(80);
        request.addHeader("x-forwarded-host", "bla.bla.com");
        request.addHeader("x-forwarded-proto", hostileProto);

        String servletContextUrl = HttpUtils.getServletContextUrl(request);

        assertEquals(servletContextUrl, "http://lala.land.com");
        assertFalse(servletContextUrl.contains("evil.com"));
        assertFalse(servletContextUrl.startsWith("javascript:"));
    }

    @Test(dataProvider = "trustedOverrideBaseUrls")
    public void isTrustedOverrideBaseUrlAcceptsValidUrls(String url) {
        assertTrue(HttpUtils.isTrustedOverrideBaseUrl(url));
    }

    @Test(dataProvider = "untrustedOverrideBaseUrls")
    public void isTrustedOverrideBaseUrlRejectsInvalidUrls(String url) {
        assertFalse(HttpUtils.isTrustedOverrideBaseUrl(url));
    }

    @DataProvider
    public Object[][] trustedOverrideBaseUrls() {
        return new Object[][] {
                {"http://mothership.net"},
                {"https://mothership.net/"},
                {"http://originartifactory.net/artifactory"},
                {"https://host.example.com:8443"}
        };
    }

    @DataProvider
    public Object[][] untrustedOverrideBaseUrls() {
        return new Object[][] {
                {"https://evil.com#"},
                {"https://evil.com#://host:8082"},
                {"https://evil.com/serve?://host:8082/artifactory/repo/lib.jar"},
                {"javascript:alert(1)"},
                {"ftp://example.com"},
                {""},
                {null}
        };
    }

    @DataProvider
    public Object[][] untrustedForwardedProtoValues() {
        return new Object[][] {
                {"https://evil.com#"},
                {"https://evil.com?"},
                {"javascript:alert(1)"},
                {"ftp"}
        };
    }

    public void contextIdExtractedFromContextPath() {
        MockServletContext mockContext = new MockServletContext();
        mockContext.setContextPath("dandan");
        String contextId = HttpUtils.getContextId(mockContext);
        assertEquals(contextId, "dandan");
    }

    public void contextIdExtractedFromServletInitParam() {
        MockServletContext mockContext = new MockServletContext();
        mockContext.setContextPath("not-this");
        mockContext.setAttribute(CONTEXT_ID_PROP, "yossis");
        String contextId = HttpUtils.getContextId(mockContext);
        assertEquals(contextId, "yossis");
    }

    public void encodeFullUrlWithParams() {
        String unescaped = "http://domain:8089/context/path?x=a/b/c&b=c";
        String escaped = HttpUtils.encodeUrl(unescaped);
        assertEquals(escaped, unescaped);
    }

    public void encodeHostnameWithUnderscore() {
        // not a valid dns name but who cares
        String unescaped = "http://_domain:8089/context/path?x=a/b/c&b=c";
        String escaped = HttpUtils.encodeUrl(unescaped);
        assertEquals(escaped, unescaped);
    }

    public void encodeHostnameWithoutPort() {
        // not a valid dns name but who cares
        String unescaped = "http://localhost/context/path?x=a/b/c&b=c";
        String escaped = HttpUtils.encodeUrl(unescaped);
        assertEquals(escaped, unescaped);
    }

    public void encodeNakedUrl() {
        String unescaped = "http://domain:8089/";
        String escaped = HttpUtils.encodeUrl(unescaped);
        assertEquals(escaped, unescaped);
    }

    public void encodeUrlWithMatrixParams() {
        String unescaped = "http://domain:8089/;foo=bar";
        String escaped = HttpUtils.encodeUrl(unescaped);
        assertEquals(escaped, unescaped);
    }

    public void encodeNakedUrlWithMatrixDelimiter() {
        String unescaped = "http://domain:8089/;";
        String escaped = HttpUtils.encodeUrl(unescaped);
        assertEquals(escaped, unescaped);
    }

    public void encodeNakedUrlWithNoTrailingSlash() {
        String unescaped = "http://domain:8089";
        String escaped = HttpUtils.encodeUrl(unescaped);
        assertEquals(escaped, unescaped);
    }

    public void encodeArchiveEntryUrl() {
        String unescaped = "http://domain:8089/foo.zip!bar.ext;";
        String escaped = HttpUtils.encodeUrl(unescaped);
        assertEquals(escaped, unescaped);
    }

    public void encodeNakedUrlWithQueryDelimiter() {
        String unescaped = "http://domain:8089/?";
        String escaped = HttpUtils.encodeUrl(unescaped);
        assertEquals(escaped, unescaped);
    }

    public void encodeFullUrlWithQueryAndMatrixParams() {
        String unescaped = "http://domain:8089/context/path?x=a/b/c&b=c;foo=bar,bar=baz;doodle=moodle";
        String escaped = HttpUtils.encodeUrl(unescaped);
        assertEquals(escaped, unescaped);
    }

    public void encodeFullUrlWithSpecialChars() {
        String unescaped = "http://domain:8089/context/path?x=a/b/c&b=c;foo=bar :: baz,bar+=baz;doodle=#$moodle";
        String escaped = HttpUtils.encodeUrl(unescaped);
        assertEquals(escaped,
                "http://domain:8089/context/path?x=a/b/c&b=c;foo=bar%20::%20baz,bar+=baz;doodle=%23$moodle");
    }

    public void encodeSchemeLessUrlWithDoubleSlash() {
        String unescaped = "domain:8089//context/path?x=a/b/c&b=c;foo=bar,bar=baz;doodle=moodle";
        String escaped = HttpUtils.encodeUrl(unescaped);
        assertEquals(escaped, unescaped);
    }

    public void encodeFullIPv6Address() {
        String unescaped = "http://[2a05:d014:82c:c112:ecc8:404a:7917:dbc2]:8089/context/path?x=a/b/c&b=c";
        String escaped = HttpUtils.encodeUrl(unescaped);
        assertEquals(escaped, unescaped);
    }

    public void encodeFullIPv6AddressWithFourElements() {
        String unescaped = "http://[2a05::d014:82c:c112]:8089/context/path?x=a/b/c&b=c";
        String escaped = HttpUtils.encodeUrl(unescaped);
        assertEquals(escaped, unescaped);
    }

    public void encodeCompressedIPv6Address() {
        String unescaped = "http://[::1]:8089/context/path?x=a/b/c&b=c";
        String escaped = HttpUtils.encodeUrl(unescaped);
        assertEquals(escaped, unescaped);
    }

    public void encodeIPv6AddressWithZone() {
        String unescaped = "http://[fe80::1ff:fe23:4567:890a%25eth0]/foo";
        String escaped = HttpUtils.encodeUrl(unescaped);
        assertEquals(escaped, unescaped);
    }

    public void encodeAddressWithNoHost() {
        String unescaped = "http://:8080/context/path?x=a/b/c&b=c";
        String escaped = HttpUtils.encodeUrl(unescaped);
        assertEquals(escaped, unescaped);
    }

    public void encodeIPv4Address() {
        String unescaped = "http://127.0.0.1:8080/context/path?x=a/b/c&b=c";
        String escaped = HttpUtils.encodeUrl(unescaped);
        assertEquals(escaped, unescaped);
    }

    public void encodeSshAddress() {
        String unescaped = "ssh://:8080/context/path?x=a/b/c&b=c";
        String escaped = HttpUtils.encodeUrl(unescaped);
        assertEquals(escaped, unescaped);
    }

    public void encodeUrlWhenPathPrefixesHost() {
        assertEquals(HttpUtils.encodeUrl("https://code.jquery.com/co"),
                "https://code.jquery.com/co");
        assertEquals(HttpUtils.encodeUrl("https://code.jquery.com/cod"),
                "https://code.jquery.com/cod");
        assertEquals(HttpUtils.encodeUrl("https://code.jquery.com/code"),
                "https://code.jquery.com/code");
        assertEquals(HttpUtils.encodeUrl("https://registry.npmjs.org/re"),
                "https://registry.npmjs.org/re");
        assertEquals(HttpUtils.encodeUrl("https://registry.npmjs.org/reg"),
                "https://registry.npmjs.org/reg");
    }

    public void encodeUrlWhenPathPrefixesHostWithPort() {
        assertEquals(HttpUtils.encodeUrl("https://code.jquery.com:8443/co"),
                "https://code.jquery.com:8443/co");
        assertEquals(HttpUtils.encodeUrl("http://code.jquery.com:8080/code"),
                "http://code.jquery.com:8080/code");
        assertEquals(HttpUtils.encodeUrl("https://user:pass@code.jquery.com:8443/co"),
                "https://user:pass@code.jquery.com:8443/co");
        assertEquals(HttpUtils.encodeUrl("https://[::1]:8080/co"),
                "https://[::1]:8080/co");
    }

    public void encodeUrlWhenPathRepeatsInsideQuery() {
        String unescaped = "https://example.com/foo?u=https://x/foo";
        assertEquals(HttpUtils.encodeUrl(unescaped), unescaped);
    }

    public void encodeUrlWhenPathSegmentMatchesHost() {
        String unescaped = "https://example.com/example.com";
        assertEquals(HttpUtils.encodeUrl(unescaped), unescaped);
    }

    public void testIsResponseNotEmpty() {
        Response response = spy(Response.class);
        boolean notEmpty;

        for (int allowedStatus : List.of(200, 302, 304)) {
            when(response.getStatus()).thenReturn(allowedStatus);
            assertTrue(HttpUtils.isResponseOkOrRedirectedMovedTemporarilyCode(response));
        }

        for (int nonAllowedStatus : List.of(404, 500)) {
            when(response.getStatus()).thenReturn(nonAllowedStatus);
            assertFalse(HttpUtils.isResponseOkOrRedirectedMovedTemporarilyCode(response));
        }

        // null - please do not remove!
        notEmpty = HttpUtils.isResponseOkOrRedirectedMovedTemporarilyCode(null);
        assertFalse(notEmpty);
    }

    public void testIsIPv6AddressTrue() {
        String address = "[::1]:8081";
        boolean isIPv6Address = HttpUtils.isIPv6Address(address);
        assertTrue(isIPv6Address);
    }

    public void testIsIPv6AddressFalse() {
        String address = "localhost:8081";
        boolean isIPv6Address = HttpUtils.isIPv6Address(address);
        assertFalse(isIPv6Address);
    }

    public void testIsIPv6AddressFalseWithSquareBrackets() {
        String address = "[localhost:8081]";
        boolean isIPv6Address = HttpUtils.isIPv6Address(address);
        assertFalse(isIPv6Address);
    }

    public void testIsIPv6AddressFalseWithAuthInfo() {
        String address = "admin:password@localhost:8081";
        boolean isIPv6Address = HttpUtils.isIPv6Address(address);
        assertFalse(isIPv6Address);
    }

    public void testIsIPv6AddressTrueWithAuthInfo() {
        String address = "admin:password@[::1]:8081";
        boolean isIPv6Address = HttpUtils.isIPv6Address(address);
        assertTrue(isIPv6Address);
    }

    public void testIsIPv6AddressTrueWithNakedAddress() {
        String address = "[2a05:d014:82c:c112:ecc8:404a:7917:dbc2]";
        boolean isIPv6Address = HttpUtils.isIPv6Address(address);
        assertTrue(isIPv6Address);
    }

    public void encodeUrlWithBasicAuth() {
        String unescaped = "http://admin:password@domain:8089/context/file path.txt";
        String escaped = BasicHttpUtils.encodeQuery(unescaped);
        assertEquals(escaped, "http://admin:password@domain:8089/context/file%20path.txt");
    }

    public void testGetRequestPathWithHostHasLeadingSlash() {
        String path = HttpUtils.getRequestPath("http://localhost:8080/artifactory/api/system/ping");
        assertEquals(path, "/artifactory/api/system/ping");
    }

    public void testGetRequestPathWithoutHostIsBlank() {
        String path = HttpUtils.getRequestPath("/artifactory/api/system/ping");
        assertEquals(path, "");
    }

    public void testGetRequestPathWithoutHostIsNotBlank() {
        String path = HttpUtils.getRequestPath("localhost/artifactory/api/system/ping");
        assertEquals(path, "/artifactory/api/system/ping");
    }

    public void testTrimEnclosingSquareBrackets() {
        String address = HttpUtils.trimSquareBracketsIfNeeded("[::1]");
        assertEquals(address, "::1");
    }

    public void testTrimEnclosingSquareBracketsNoOp() {
        String address = HttpUtils.trimSquareBracketsIfNeeded("[::1]:8080");
        assertEquals(address, "[::1]:8080");
    }

    public void testGetOverrideContextPath() {
        final ArtifactoryHome artifactoryHome = ArtifactoryHome.get();
        assertEquals(HttpUtils.getOverrideContextPath(), "/artifactory");
        artifactoryHome.getArtifactoryProperties()
                .setProperty(ConstantValues.overrideContextPath.getPropertyName(), "customerName");
        assertEquals(HttpUtils.getOverrideContextPath(), "/customerName");
        artifactoryHome.getArtifactoryProperties()
                .setProperty(ConstantValues.overrideContextPath.getPropertyName(), "/customerName");
        assertEquals(HttpUtils.getOverrideContextPath(), "/customerName");
        artifactoryHome.getArtifactoryProperties()
                .setProperty(ConstantValues.overrideContextPath.getPropertyName(), "//customerName///");
        assertEquals(HttpUtils.getOverrideContextPath(), "/customerName");
        artifactoryHome.getArtifactoryProperties()
                .setProperty(ConstantValues.overrideContextPath.getPropertyName(), "empty");
        assertEquals(HttpUtils.getOverrideContextPath(), "");
        artifactoryHome.getArtifactoryProperties()
                .setProperty(ConstantValues.overrideContextPath.getPropertyName(), "EMPTY");
        assertEquals(HttpUtils.getOverrideContextPath(), "");
        artifactoryHome.getArtifactoryProperties().setProperty(ConstantValues.overrideContextPath.getPropertyName(),
                ConstantValues.overrideContextPath.getDefValue());
    }

    public void testGetBaseUrlWithOverrideContextPath() {
        final ArtifactoryHome artifactoryHome = ArtifactoryHome.get();
        this.setBaseUrl("http://127.0.0.1:8080");
        assertEquals(HttpUtils.getBaseUrlWithOverrideContextPathAndRepoKey(REPO_KEY),
                "http://127.0.0.1:8080/artifactory/repoKey/");
        artifactoryHome.getArtifactoryProperties()
                .setProperty(ConstantValues.overrideContextPath.getPropertyName(), "customerName");
        assertEquals(HttpUtils.getBaseUrlWithOverrideContextPathAndRepoKey(REPO_KEY),
                "http://127.0.0.1:8080/customerName/repoKey/");
        this.setBaseUrl("http://127.0.0.1:8080/");
        artifactoryHome.getArtifactoryProperties()
                .setProperty(ConstantValues.overrideContextPath.getPropertyName(), "customerNameNew");
        assertEquals(HttpUtils.getBaseUrlWithOverrideContextPathAndRepoKey(REPO_KEY),
                "http://127.0.0.1:8080/customerNameNew/repoKey/");
        assertNull(HttpUtils.getBaseUrlWithOverrideContextPathAndRepoKey(null),
                "http://127.0.0.1:8080/customerNameNew/repoKey/");
        this.setBaseUrl(null);
        assertNull(HttpUtils.getBaseUrlWithOverrideContextPathAndRepoKey(REPO_KEY), null);
        this.setBaseUrl("");
        assertNull(HttpUtils.getBaseUrlWithOverrideContextPathAndRepoKey(REPO_KEY), null);
        this.setBaseUrl("http://artifactory.something.com/something");
        artifactoryHome.getArtifactoryProperties()
                .setProperty(ConstantValues.overrideContextPath.getPropertyName(), "something");
        assertEquals(HttpUtils.getBaseUrlWithOverrideContextPathAndRepoKey(REPO_KEY),
                "http://artifactory.something.com/something/repoKey/");
        artifactoryHome.getArtifactoryProperties()
                .setProperty(ConstantValues.overrideContextPath.getPropertyName(), "artifactory");
        assertEquals(HttpUtils.getBaseUrlWithOverrideContextPathAndRepoKey(REPO_KEY),
                "http://artifactory.something.com/something/artifactory/repoKey/");
        this.setBaseUrl("http://127.0.0.1:8080/artifactory");
        artifactoryHome.getArtifactoryProperties()
                .setProperty(ConstantValues.overrideContextPath.getPropertyName(), "artifactory");
        assertEquals(HttpUtils.getBaseUrlWithOverrideContextPathAndRepoKey(REPO_KEY),
                "http://127.0.0.1:8080/artifactory/repoKey/");
        this.setBaseUrl("http://artifactory.com/artifactory/");
        artifactoryHome.getArtifactoryProperties()
                .setProperty(ConstantValues.overrideContextPath.getPropertyName(), "artifactory");
        assertEquals(HttpUtils.getBaseUrlWithOverrideContextPathAndRepoKey(REPO_KEY),
                "http://artifactory.com/artifactory/repoKey/");
        artifactoryHome.getArtifactoryProperties()
                .setProperty(ConstantValues.overrideContextPath.getPropertyName(), "something");
        assertEquals(HttpUtils.getBaseUrlWithOverrideContextPathAndRepoKey(REPO_KEY),
                "http://artifactory.com/artifactory/something/repoKey/");
        this.setBaseUrl("http://artifactory.com/artifactory");
        artifactoryHome.getArtifactoryProperties()
                .setProperty(ConstantValues.overrideContextPath.getPropertyName(), "/artifactory");
        assertEquals(HttpUtils.getBaseUrlWithOverrideContextPathAndRepoKey(REPO_KEY),
                "http://artifactory.com/artifactory/repoKey/");
        artifactoryHome.getArtifactoryProperties()
                .setProperty(ConstantValues.overrideContextPath.getPropertyName(), "/artifactory/");
        assertEquals(HttpUtils.getBaseUrlWithOverrideContextPathAndRepoKey(REPO_KEY),
                "http://artifactory.com/artifactory/repoKey/");
        artifactoryHome.getArtifactoryProperties()
                .setProperty(ConstantValues.overrideContextPath.getPropertyName(), null);
        assertEquals(HttpUtils.getBaseUrlWithOverrideContextPathAndRepoKey(REPO_KEY),
                "http://artifactory.com/artifactory/repoKey/");
        this.setBaseUrl("http://artifactory.com/something");
        artifactoryHome.getArtifactoryProperties()
                .setProperty(ConstantValues.overrideContextPath.getPropertyName(), "/something");
        assertEquals(HttpUtils.getBaseUrlWithOverrideContextPathAndRepoKey(REPO_KEY),
                "http://artifactory.com/something/repoKey/");
        artifactoryHome.getArtifactoryProperties()
                .setProperty(ConstantValues.overrideContextPath.getPropertyName(), "/something/");
        assertEquals(HttpUtils.getBaseUrlWithOverrideContextPathAndRepoKey(REPO_KEY),
                "http://artifactory.com/something/repoKey/");
        artifactoryHome.getArtifactoryProperties()
                .setProperty(ConstantValues.overrideContextPath.getPropertyName(), null);
        assertEquals(HttpUtils.getBaseUrlWithOverrideContextPathAndRepoKey(REPO_KEY),
                "http://artifactory.com/something/artifactory/repoKey/");
    }

    private void setBaseUrl(String baseUrl) {
        CentralConfigDescriptor ccd = ContextHelper.get().getCentralConfig().getDescriptor();
        reset(ccd);
        expect(ccd.getUrlBase()).andReturn(baseUrl).anyTimes();
        replay(ccd);
    }

    /**
     * RTFACT-9804 - Allow deployment of files with colon,
     * when using native browsing the href content should be with %3A instead of ':' char
     */
    public void encodeFileNameWithColonChar() {
        assertEquals(HttpUtils.encodeWithinPath("file:file"), "file%3Afile");
    }

    /**
     * RTFACT-15708
     * when Ivi resolve dependencies he dont encode special chars,
     * if package name contain '+' the href tag should contain '+' and not escaped char
     */
    public void encodeFileNameWithPlusChar() {
        assertEquals(HttpUtils.encodeWithinPath("file+file"), "file+file");
    }

    public void encodeFileNameWithSpaceChar() {
        assertEquals(HttpUtils.encodeWithinPath("file file"), "file%20file");
    }

    public void decodeFileNameWithColonChar() {
        assertEquals(HttpUtils.decodeUri("file%3Afile"), "file:file");
    }

    public void decodeFileNameWithSpaceChar() {
        assertEquals(HttpUtils.decodeUri("file%20file"), "file file");
    }

    public void versionHeaderWithRevision() {
        String userAgent = "Artifactory/7.9.1 70901900";
        Optional<ArtifactoryServiceVersion> result = HttpUtils.extractVersionFromHeader(userAgent);
        Optional<ArtifactoryServiceVersion> expected =
                Optional.of(ArtifactoryServiceVersionProvider.get("7.9.1", 70901900));
        Assert.assertEquals(result, expected);
    }

    public void versionHeaderWithRevisionAndJfrogUserAgent() {
        String userAgent = "Artifactory/7.9.1 70901900 (+https://jfrog.com/artifactory/)";
        Optional<ArtifactoryServiceVersion> result = HttpUtils.extractVersionFromHeader(userAgent);
        Optional<ArtifactoryServiceVersion> expected =
                Optional.of(ArtifactoryServiceVersionProvider.get("7.9.1", 70901900));
        Assert.assertEquals(result, expected);
    }

    public void versionHeaderWithoutRevision() {
        String userAgent = "Artifactory/6.23.1";
        Optional<ArtifactoryServiceVersion> result = HttpUtils.extractVersionFromHeader(userAgent);
        Optional<ArtifactoryServiceVersion> expected =
                Optional.of(ArtifactoryServiceVersionProvider.get("6.23.1", 62301900));
        Assert.assertEquals(result, expected);
    }

    public void versionHeaderOfDevelopment() {
        String userAgent = "Artifactory/development 2147483647";
        Optional<ArtifactoryServiceVersion> result = HttpUtils.extractVersionFromHeader(userAgent);
        Optional<ArtifactoryServiceVersion> expected = Optional.of(ArtifactoryServiceVersion.getCurrent());
        Assert.assertEquals(result, expected);
    }

    public void versionHeaderNotInFormat1() {
        String userAgent = "kuku";
        Optional<ArtifactoryServiceVersion> result = HttpUtils.extractVersionFromHeader(userAgent);
        Assert.assertTrue(result.isEmpty());
    }

    public void versionHeaderNotInFormat2() {
        String userAgent = "Artifactory/kuku";
        Optional<ArtifactoryServiceVersion> result = HttpUtils.extractVersionFromHeader(userAgent);
        Assert.assertTrue(result.isEmpty());
    }

    public void versionHeaderEmpty() {
        String userAgent = "";
        Optional<ArtifactoryServiceVersion> result = HttpUtils.extractVersionFromHeader(userAgent);
        Assert.assertTrue(result.isEmpty());
    }

    public void versionHeaderNull() {
        String userAgent = null;
        Optional<ArtifactoryServiceVersion> result = HttpUtils.extractVersionFromHeader(userAgent);
        Assert.assertTrue(result.isEmpty());
    }

    public void createLinkToBrowsableArtifact() {
        RepoPath repoPath = mock(RepoPath.class);
        String path = "repo/item.log";
        when(repoPath.toPath()).thenReturn(path);
        String expected =
                "<a href=http://localhost:8082/ui/repos/tree/General/repo/item.log target=\"blank\">repo/item.log</a>";
        Assert.assertEquals(HttpUtils.createLinkToBrowsableArtifact(repoPath, path), expected);
    }

    public void createBuildInfoLink() {
        Build build = new BuildInfoBuilder("buildName").number("1").startedDate(new Date()).build();
        String expected =
                "<a href=\"http://localhost:8082/ui/builds/buildName/1\" target=\"blank\">buildName:1</a>";
        Assert.assertEquals(expected, HttpUtils.createBuildInfoLink(build));
    }

    public void addMatrixParamToUrl() {
        String url = "http://localhost:8082/artifactory/repo1/file1.xml";
        String expectedUrl = "http://localhost:8082/artifactory/repo1/file1.xml;prop1=value1;prop2=value2";
        String urlWithParams = HttpUtils.addMatrixParamToUrl(url, "prop1", new HashSet<>(
                Collections.singletonList("value1")));
        urlWithParams = HttpUtils.addMatrixParamToUrl(urlWithParams, "prop2", new HashSet<>(
                Collections.singletonList("value2")));
        assertEquals(urlWithParams, expectedUrl);
    }

    public void addMatrixParamWithMultiValueToUrl() {
        String propertyKeyValueString1 = "prop1=value1";
        String propertyKeyValueString2 = "prop1=value2";
        String url = "http://localhost:8082/artifactory/repo1/file1.xml";
        String urlWithParams =
                HttpUtils.addMatrixParamToUrl(url, "prop1", new HashSet<>(Arrays.asList("value1", "value2")));
        assertTrue(urlWithParams.contains(propertyKeyValueString1) && urlWithParams.contains(propertyKeyValueString2));
    }

    public void addMatrixParamWithSpecialChars() {
        String url = "http://localhost:8082/artifactory/repo1/file1.xml";
        String expectedUrl = "http://localhost:8082/artifactory/repo1/file1.xml;" +
                "special%21%3F%23%5B%5D%40%21%24%26%28%29*%2B=special%21%3F%23%5B%5D%40%21%24%26%28%29*%2B";
        String urlWithParams = HttpUtils.addMatrixParamToUrl(url, "special!?#[]@!$&()*+", new HashSet<>(
                Collections.singletonList("special!?#[]@!$&()*+")));
        assertEquals(urlWithParams, expectedUrl);
    }

    @Test(expectedExceptions = IllegalArgumentException.class, expectedExceptionsMessageRegExp = ".*Property name " +
            "should not be null.*")
    public void addMatrixParamWithNullName() {
        String url = "http://localhost:8082/artifactory/repo1/file1.xml";
        HttpUtils.addMatrixParamToUrl(url, null, new HashSet<>(Collections.singletonList("value1")));
    }

    @Test(expectedExceptions = IllegalArgumentException.class, expectedExceptionsMessageRegExp = ".*Property value " +
            "should not be null.*")
    public void addMatrixParamWithNullValue() {
        String url = "http://localhost:8082/artifactory/repo1/file1.xml";
        HttpUtils.addMatrixParamToUrl(url, "prop1", null);
    }

    public void addMatrixParamWithEmptyValues() {
        String url = "http://localhost:8082/artifactory/repo1/file1.xml";
        Set<String> values = new HashSet<>();
        String processedUrl = HttpUtils.addMatrixParamToUrl(url, "prop1", new HashSet<>());
        assertEquals(processedUrl, url);
    }

    public void testIsAbsolute() {
        //RTDEV-5944
        assertFalse(isAbsolute("someFolder:withColon/someFile.test"));
        assertTrue(isAbsolute("http://localhost:8080/artifactory/someFolder:withColon/someFile.test"));
        assertFalse(isAbsolute("devel:/kubic:/libcontainers:/stable/CentOS_8/devel:kubic:libcontainers:stable.repo"));
        assertTrue(isAbsolute("http://localhost:8080/artifactory/devel:/kubic:/libcontainers:/stable/" +
                "CentOS_8/devel:kubic:libcontainers:stable.repo"));
    }

    public void testEscapeColonSymbol() {
        assertEquals(escapeColonSymbol("someFolder:withColon/someFile.test"), "someFolder%3AwithColon/someFile.test");
        assertEquals(escapeColonSymbol("http://localhost:8080/artifactory/someFolder:withColon/someFile.test"),
                "http://localhost%3A8080/artifactory/someFolder%3AwithColon/someFile.test");
        assertEquals(
                escapeColonSymbol("devel:/kubic:/libcontainers:/stable/CentOS_8/devel:kubic:libcontainers:stable.repo"),
                "devel%3A/kubic%3A/libcontainers%3A/stable/CentOS_8/devel%3Akubic%3Alibcontainers%3Astable.repo");
    }

    @DataProvider(name = "validAddresses")
    public static Object[][] validAddresses() {
        return new Object[][] {
                {""},
                {null},
                {"2001:db8:85a3:8d3:1319:8a2e:370:7348"},
                {"[2001:db8:85a3:8d3:1319:8a2e:370:7348]:8080"},
                {"[2001:db8:85a3:8d3:1319:8a2e:370:7348]"},
                {"127.0.0.1"},
                {"127.0.0.1:8080"},
                {"::1234:5678"}
        };
    }

    @Test(dataProvider = "validAddresses")
    public void testValidAddressesValidation(String address) throws URISyntaxException {
        String validated = HttpUtils.validateRemoteClientAddress(address);
        assertEquals(address, validated);
    }

    @DataProvider(name = "invalidAddresses")
    public static Object[][] invalidAddresses() {
        return new Object[][] {
                {"127.a.0.1"}, // invalid symbol "a" in IP
                {"127.0.0.1:808a"}, // invalid symbol "a" in port
                {"127.0.0.1<br><!--"},
                {"2001:db8:85_3:8d3:1319:8a2e:370:7348"}, // invalid symbol "_" in IP
                {"[2001:db8:85a3:8d3:1319:8a2e:370:7348]:808a"}, // invalid symbol "a" in port
                {"[2001:db8:85a3:8d3:1319:8a2e:370:7348"}, // missing ]
                {"2001:db8:85a3:8d3:1319:8a2e:370:7348]"}, // missing [
                {"localhost"},
                {"localhost:8081"},
                {"http://httpd.apache.org/docs/2.2/mod/mod_proxy"},
                {"::ffff:808:808%"},
                {"::1234:5678</p>Reset your password by following this link:"}
        };
    }

    @Test(dataProvider = "invalidAddresses")
    public void testInvalidAddressesValidation(String address) {
        assertThrows(URISyntaxException.class, () -> HttpUtils.validateRemoteClientAddress(address));
    }

    @Test
    public void testGetContentLength() {
        String contentLengthHeader = HttpHeaders.CONTENT_LENGTH;
        String contentRangeHeader = HttpHeaders.CONTENT_RANGE;
        CloseableHttpResponse response = mock(CloseableHttpResponse.class);
        when(response.getFirstHeader(contentLengthHeader)).thenReturn(new BasicHeader(contentLengthHeader, "10000"));
        when(response.getFirstHeader(contentRangeHeader)).thenReturn(
                new BasicHeader(contentRangeHeader, "bytes 0-1/60000"));

        long actual = HttpUtils.getContentLength(response);

        // Should get length from CONTENT_LENGTH header (use.range.for.replaced.get flag is disabled).
        assertEquals(actual, 10000);
    }

    @Test
    public void testGetContentLengthWithRangeEnabled() {
        String contentLengthHeader = HttpHeaders.CONTENT_LENGTH;
        String contentRangeHeader = HttpHeaders.CONTENT_RANGE;
        setStringSystemProperty(ConstantValues.useRangeForReplacedGet, "true");
        CloseableHttpResponse response = mock(CloseableHttpResponse.class);
        when(response.containsHeader(contentRangeHeader)).thenReturn(true);
        when(response.getFirstHeader(contentLengthHeader)).thenReturn(new BasicHeader(contentLengthHeader, "10000"));

        when(response.getFirstHeader(contentRangeHeader)).thenReturn(
                new BasicHeader(contentRangeHeader, "bytes 0-1/60000"));
        long actual = HttpUtils.getContentLength(response);

        // Should get content length from CONTENT_RANGE header.
        assertEquals(actual, 60000);
    }

    @Test
    public void testGetContentLengthForRangeRequest() {
        String contentRangeHeader = HttpHeaders.CONTENT_RANGE;
        CloseableHttpResponse response = mock(CloseableHttpResponse.class);
        when(response.containsHeader(contentRangeHeader)).thenReturn(true);
        when(response.getFirstHeader(contentRangeHeader)).thenReturn(
                new BasicHeader(contentRangeHeader, "bytes 0-1/60000"));
        long actual = HttpUtils.getContentLengthForRangeRequest(response);

        assertEquals(actual, 60000);
    }

    @Test(dataProvider = "rangeContentLengthError")
    public void testGetContentLengthForRangeRequestError(String length) {
        String contentRangeHeader = HttpHeaders.CONTENT_RANGE;
        CloseableHttpResponse response = mock(CloseableHttpResponse.class);
        when(response.containsHeader(contentRangeHeader)).thenReturn(true);
        when(response.getFirstHeader(contentRangeHeader)).thenReturn(
                new BasicHeader(contentRangeHeader, length));
        long actual = HttpUtils.getContentLengthForRangeRequest(response);

        assertEquals(actual, -1);
    }

    @Test
    public void testGetContentLengthForRangeNullHeader() {
        String contentRangeHeader = HttpHeaders.CONTENT_RANGE;
        CloseableHttpResponse response = mock(CloseableHttpResponse.class);
        when(response.containsHeader(contentRangeHeader)).thenReturn(true);
        when(response.getFirstHeader(contentRangeHeader)).thenReturn(null);
        long actual = HttpUtils.getContentLengthForRangeRequest(response);

        assertEquals(actual, -1);
    }

    @Test(dataProvider = "artifactoryServletContextUrlTestDataProvider")
    void testGetArtifactoryServerNameIfPresent(String url, String expectedValue) {
        try (MockedStatic<RequestThreadLocal> requestMockedStatic = Mockito.mockStatic(RequestThreadLocal.class)) {
            var requestMock = mock(HttpServletRequest.class);
            requestMockedStatic.when(RequestThreadLocal::getRequest).thenReturn(requestMock);
            try (MockedStatic<HttpUtils> httpUtilsMockedStatic = Mockito.mockStatic(HttpUtils.class)) {
                httpUtilsMockedStatic.when(() -> HttpUtils.getServletContextUrl(requestMock)).thenReturn(url);
                httpUtilsMockedStatic.when(HttpUtils::getArtifactoryUrlFromRequestIfPresent).thenCallRealMethod();
                assertEquals(HttpUtils.getArtifactoryUrlFromRequestIfPresent(), expectedValue);
            }
        }
    }

    @Test
    void testGetArtifactoryServerNameIfPresentWhenNoRequest() {
        try (MockedStatic<RequestThreadLocal> requestMockedStatic = Mockito.mockStatic(RequestThreadLocal.class)) {
            requestMockedStatic.when(RequestThreadLocal::getRequest).thenReturn(null);
            assertEquals(HttpUtils.getArtifactoryUrlFromRequestIfPresent(), "");
        }
    }

    @Test
    public void testGetSubdomainWithValidUrl() {
        MockHttpServletRequest mockHttpServletRequest = new MockHttpServletRequest("GET", "/path/to/resource");
        mockHttpServletRequest.setServerName("subdomain.example.com");
        assertEquals(HttpUtils.getSubdomain(mockHttpServletRequest), "subdomain");
    }

    @Test
    public void testGetSubdomainWithValidWwwUrl() {
        MockHttpServletRequest mockHttpServletRequest = new MockHttpServletRequest("GET", "/path/to/resource");
        mockHttpServletRequest.setServerName("www.subdomain.example.com");
        assertEquals(HttpUtils.getSubdomain(mockHttpServletRequest), "subdomain");
        mockHttpServletRequest.setServerName("www2.subdomain.example.com");
        assertEquals(HttpUtils.getSubdomain(mockHttpServletRequest), "subdomain");
    }

    @Test
    public void testGetSubdomainWithInvalidUrl() {
        MockHttpServletRequest mockHttpServletRequest = new MockHttpServletRequest("GET", "/path/to/resource");
        mockHttpServletRequest.setServerName("");
        assertEquals(HttpUtils.getSubdomain(mockHttpServletRequest), StringUtils.EMPTY);
    }

    @Test
    public void testGetSubdomainWithForwardedHostUrl() {
        MockHttpServletRequest mockHttpServletRequest = new MockHttpServletRequest("GET", "/path/to/resource");
        mockHttpServletRequest.addHeader("x-forwarded-host", "subdomain.example.com");
        mockHttpServletRequest.addHeader("x-forwarded-proto", "https");
        assertEquals(HttpUtils.getSubdomain(mockHttpServletRequest), "subdomain");
    }

    @Test
    public void testLastModified() {
        HttpResponse response = mock(HttpResponse.class);
        assertEquals(HttpUtils.getLastModified(response), -1);

        long testTimeMillis = 1759237301000L;
        Date lastModified = new Date(testTimeMillis);
        when(response.getFirstHeader(HttpHeaders.LAST_MODIFIED))
                .thenReturn(new BasicHeader(HttpHeaders.LAST_MODIFIED, DateUtils.formatDate(lastModified)));
        assertEquals(HttpUtils.getLastModified(response), testTimeMillis);

        when(response.getFirstHeader(HttpHeaders.LAST_MODIFIED))
                .thenReturn(new BasicHeader(HttpHeaders.LAST_MODIFIED,
                        " \"" + DateUtils.formatDate(lastModified) + "\" "));
        assertEquals(HttpUtils.getLastModified(response), testTimeMillis);

        when(response.getFirstHeader(HttpHeaders.LAST_MODIFIED))
                .thenReturn(new BasicHeader(HttpHeaders.LAST_MODIFIED, "This morning"));
        assertTrue(HttpUtils.getLastModified(response) > testTimeMillis); // should be "now"
    }

    @DataProvider(name = "rangeContentLengthError")
    public static Object[][] rangeContentLengthError() {
        return new Object[][] {
                {"1"},
                {"1/*"},
                {""},
                {null}
        };
    }

    @DataProvider(name = "artifactoryServletContextUrlTestDataProvider")
    public static Object[][] provideArtifactoryServletContextUrlTestData() {
        return new Object[][] {
                {"", ""},
                {null, ""},
                {"someUrl", "someUrl"}
        };
    }

    @AfterMethod
    private void shutdownMock() {
        ArtifactoryContextThreadBinder.unbind();
    }
}