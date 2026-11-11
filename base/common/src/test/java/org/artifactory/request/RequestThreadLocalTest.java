package org.artifactory.request;

import jakarta.servlet.http.HttpServletRequest;
import org.artifactory.common.ConstantValues;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.lang.reflect.Field;
import java.net.URISyntaxException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Eli Fishman
 */
class RequestThreadLocalTest extends ArtifactoryHomeBoundJUnitTest {

    @AfterEach
    void tearDown() {
        RequestThreadLocal.unbind();
    }

    @ParameterizedTest
    @CsvSource({
            "artifactory/1.0,true",
            "artifactory-federation-service,true",
            "Mozilla/5.0,false"
    })
    void testIsInternalUserWithArtifactoryUserAgent(String userAgent, boolean expected) {
        HttpServletRequest requestMock = mock(HttpServletRequest.class);
        when(requestMock.getHeader("User-Agent")).thenReturn(userAgent);
        RequestThreadLocalContext context = createContextMock(requestMock);
        setContext(context);
        assertEquals(expected, RequestThreadLocal.isInternalUser());
    }

    @Test
    void testIsInternalUserWithNullContext() {
        setContext(null);
        assertFalse(RequestThreadLocal.isInternalUser());
    }

    // RTDEV-87329: a request captured on one thread and rebound on another (e.g. a mirror-event
    // executor thread) can already be recycled by Tomcat by the time it's read here, throwing
    // IllegalStateException from the request facade. Must degrade to a safe default, not propagate.
    @Test
    void testIsInternalUser_whenRequestIsRecycled_returnsFalse() {
        HttpServletRequest requestMock = mock(HttpServletRequest.class);
        when(requestMock.getHeader("User-Agent"))
                .thenThrow(new IllegalStateException("The request object has been recycled "
                        + "and is no longer associated with this facade"));
        RequestThreadLocalContext context = createContextMock(requestMock);
        setContext(context);
        assertFalse(RequestThreadLocal.isInternalUser());
    }

    @Test
    void testIsUnlimitedUserAgent() {
        assertTrue(RequestThreadLocal.isUnlimitedUserAgent("artifactory/7.0.0"));
        assertTrue(RequestThreadLocal.isUnlimitedUserAgent("xray/3.0.0"));
        assertTrue(RequestThreadLocal.isUnlimitedUserAgent("jfrog-pipelines:build"));
        assertTrue(RequestThreadLocal.isUnlimitedUserAgent("pipelines api"));
        assertTrue(RequestThreadLocal.isUnlimitedUserAgent("artifactory-federation-service"));
        assertTrue(RequestThreadLocal.isUnlimitedUserAgent("artifactory-client-java/2.0.0"));
        assertFalse(RequestThreadLocal.isUnlimitedUserAgent("Mozilla/5.0"));
        assertFalse(RequestThreadLocal.isUnlimitedUserAgent("curl/7.68.0"));
    }

    /**
     * Verifies that {@link RequestThreadLocal#generateLimitExclusionUserAgentPatterns()} expands
     * the symbolic names configured in {@link ConstantValues#limitAllQueriesExemptUserAgents} into
     * the same anchored patterns produced by {@link RequestThreadLocal#generateUserAgentPatterns()},
     * matched case-insensitively. Mirrors {@link #testIsUnlimitedUserAgent()} for the exemption
     * property. The generator is called directly because the cached field
     * {@code universalLimitExclusionUserAgentPatterns} is lazy-initialised once (defaulting to
     * empty), so property changes after class-load must be exercised through the generator itself.
     */
    @Test
    void testGenerateLimitExclusionUserAgentPatterns_anchoredPatternMatch() {
        homeStub.setProperty(ConstantValues.limitAllQueriesExemptUserAgents,
                "pipeline, xray, artifactory, distribution, federation");
        java.util.List<java.util.regex.Pattern> patterns =
                RequestThreadLocal.generateLimitExclusionUserAgentPatterns();
        java.util.function.Predicate<String> matches = ua -> {
            String lower = ua.toLowerCase();
            return patterns.stream().anyMatch(p -> p.matcher(lower).matches());
        };
        // Genuine internal-client user agents match
        assertTrue(matches.test("xray/3.0.0"));
        assertFalse(matches.test("Mozillaxray/3.0.0"));
        assertTrue(matches.test("artifactory/7.0.0"));
        assertTrue(matches.test("artifactory-client-java/2.0.0"));
        assertTrue(matches.test("artifactory-federation-service"));
        assertTrue(matches.test("jfrog-pipelines:build"));
        assertTrue(matches.test("pipelines api"));
        // Case-insensitive match
        assertTrue(matches.test("XRAY/5.0"), "matching must be case-insensitive");
        // An unrelated agent that merely contains a symbolic name as a substring is NOT matched
        assertFalse(matches.test("MozillaXray/1.0"), "substring of a symbolic name must not match");
        assertFalse(matches.test("xray-scanner/2.0"), "anchored pattern must not match a prefixed variant");
        // Agents that match none of the patterns are not exempt
        assertFalse(matches.test("gradle/7.0"));
        assertFalse(matches.test("curl/7.68.0"));
        homeStub.setProperty(ConstantValues.limitAllQueriesExemptUserAgents, "");
    }

    @Test
    void testIsLimitExclusionUserAgent_nullAndBlankGuard() {
        assertFalse(RequestThreadLocal.isLimitExclusionUserAgent(null));
        assertFalse(RequestThreadLocal.isLimitExclusionUserAgent("   "), "blank user-agent must not be exempt");
    }

    @Test
    void testIsExcludedFromUniversalLimit_returnsFalseForExternalAgent() {
        HttpServletRequest requestMock = mock(HttpServletRequest.class);
        when(requestMock.getHeader("User-Agent")).thenReturn("Mozilla/5.0 (compatible; nativeBrowser)");
        RequestThreadLocalContext contextMock = createContextMock(requestMock);
        setContext(contextMock);
        assertFalse(RequestThreadLocal.isExcludedFromUniversalLimit());
    }

    @Test
    void testIsExcludedFromUniversalLimit_returnsFalseWhenNoContext() {
        setContext(null);
        assertFalse(RequestThreadLocal.isExcludedFromUniversalLimit());
    }

    /**
     * Happy-path "true" case, exercised end-to-end through {@link RequestThreadLocal#isExcludedFromUniversalLimit()}
     * rather than the {@code generateLimitExclusionUserAgentPatterns()} generator directly (as
     * {@link #testGenerateLimitExclusionUserAgentPatterns_anchoredPatternMatch()} does). The
     * {@code universalLimitExclusionUserAgentPatterns} field is a Lombok {@code @Getter(lazy = true)} cache
     * computed once for the life of the JVM, so the property must be set and the cache invalidated
     * <i>before</i> the first read, and both must be restored afterwards regardless of test order/parallelism.
     */
    @Test
    void testIsExcludedFromUniversalLimit_returnsTrueForExemptAgent() {
        homeStub.setProperty(ConstantValues.limitAllQueriesExemptUserAgents, "xray");
        resetLimitExclusionPatternsCache();
        try {
            HttpServletRequest requestMock = mock(HttpServletRequest.class);
            when(requestMock.getHeader("User-Agent")).thenReturn("xray/3.0.0");
            RequestThreadLocalContext contextMock = createContextMock(requestMock);
            setContext(contextMock);
            assertTrue(RequestThreadLocal.isExcludedFromUniversalLimit());
        } finally {
            homeStub.setProperty(ConstantValues.limitAllQueriesExemptUserAgents, "");
            resetLimitExclusionPatternsCache();
        }
    }

    /** Forces {@link RequestThreadLocal#generateLimitExclusionUserAgentPatterns()} to be recomputed on next read. */
    private static void resetLimitExclusionPatternsCache() {
        try {
            Field patternsField = RequestThreadLocal.class.getDeclaredField("universalLimitExclusionUserAgentPatterns");
            patternsField.setAccessible(true);
            ((AtomicReference<?>) patternsField.get(null)).set(null);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // RTDEV-87329: this is the exact call in the reported "chain of disaster" -
    // UniversalLimitHandlerUtility.getConfiguredLimit()/isToBlock() call this before getRequestPath(),
    // on a background thread that rebound an already-completing HttpServletRequest.
    @Test
    void testIsExcludedFromUniversalLimit_whenRequestIsRecycled_returnsFalse() {
        HttpServletRequest requestMock = mock(HttpServletRequest.class);
        when(requestMock.getHeader("User-Agent"))
                .thenThrow(new IllegalStateException("The request object has been recycled "
                        + "and is no longer associated with this facade"));
        RequestThreadLocalContext contextMock = createContextMock(requestMock);
        setContext(contextMock);
        assertFalse(RequestThreadLocal.isExcludedFromUniversalLimit());
    }

    @Test
    void testGetRequestPath_returnsPathInfo() {
        HttpServletRequest requestMock = mock(HttpServletRequest.class);
        when(requestMock.getPathInfo()).thenReturn("search/aql");
        RequestThreadLocalContext context = createContextMock(requestMock);
        setContext(context);
        assertEquals("search/aql", RequestThreadLocal.getRequestPath());
    }

    @Test
    void testGetRequestPath_returnsEmptyStringWhenNoContext() {
        setContext(null);
        assertEquals("", RequestThreadLocal.getRequestPath());
    }

    // RTDEV-87329: reproduces the reported stack trace - RequestThreadLocal.getRequestPath ->
    // request.getPathInfo() -> IllegalStateException on a recycled Tomcat request facade. Must
    // log a warning and fall back to an empty path instead of letting the exception bubble up
    // through UniversalLimitHandlerUtility and break the caller's DB read.
    @Test
    void testGetRequestPath_whenRequestIsRecycled_returnsEmptyString() {
        HttpServletRequest requestMock = mock(HttpServletRequest.class);
        when(requestMock.getPathInfo())
                .thenThrow(new IllegalStateException("The request object has been recycled "
                        + "and is no longer associated with this facade"));
        RequestThreadLocalContext context = createContextMock(requestMock);
        setContext(context);
        assertEquals("", RequestThreadLocal.getRequestPath());
    }

    @Test
    void testGetUserAgent_returnsHeaderValue() {
        HttpServletRequest requestMock = mock(HttpServletRequest.class);
        when(requestMock.getHeader("User-Agent")).thenReturn("artifactory/7.0.0");
        RequestThreadLocalContext context = createContextMock(requestMock);
        setContext(context);
        assertEquals("artifactory/7.0.0", RequestThreadLocal.getUserAgent());
    }

    @Test
    void testGetUserAgent_whenRequestIsRecycled_returnsEmptyString() {
        HttpServletRequest requestMock = mock(HttpServletRequest.class);
        when(requestMock.getHeader("User-Agent"))
                .thenThrow(new IllegalStateException("The request object has been recycled "
                        + "and is no longer associated with this facade"));
        RequestThreadLocalContext context = createContextMock(requestMock);
        setContext(context);
        assertEquals("", RequestThreadLocal.getUserAgent());
    }

    @Test
    void testShouldStream_returnsTrueWhenHeaderIsTrue() {
        HttpServletRequest requestMock = mock(HttpServletRequest.class);
        when(requestMock.getHeader("stream")).thenReturn("true");
        setContext(createContextMock(requestMock));
        assertTrue(RequestThreadLocal.shouldStream());
    }

    @Test
    void testShouldStream_returnsFalseWhenHeaderIsAbsent() {
        HttpServletRequest requestMock = mock(HttpServletRequest.class);
        when(requestMock.getHeader("stream")).thenReturn(null);
        setContext(createContextMock(requestMock));
        assertFalse(RequestThreadLocal.shouldStream());
    }

    @Test
    void testShouldStream_returnsFalseWhenNoContext() {
        setContext(null);
        assertFalse(RequestThreadLocal.shouldStream());
    }

    @Test
    void testShouldStream_whenRequestIsRecycled_returnsFalse() {
        HttpServletRequest requestMock = mock(HttpServletRequest.class);
        when(requestMock.getHeader("stream"))
                .thenThrow(new IllegalStateException("The request object has been recycled "
                        + "and is no longer associated with this facade"));
        RequestThreadLocalContext context = createContextMock(requestMock);
        setContext(context);
        assertFalse(RequestThreadLocal.shouldStream());
    }

    @Test
    void testGetClientAddress_returnsAddressFromRequestWrapper() throws Exception {
        RequestWrapper requestWrapperMock = mock(RequestWrapper.class);
        when(requestWrapperMock.getClientAddress()).thenReturn("10.0.0.1");
        setContext(createContextMock(requestWrapperMock));
        assertEquals("10.0.0.1", RequestThreadLocal.getClientAddress());
    }

    @Test
    void testGetClientAddress_returnsEmptyStringWhenNoContext() {
        setContext(null);
        assertEquals("", RequestThreadLocal.getClientAddress());
    }

    // Pre-existing behavior (not related to RTDEV-87329): HttpUtils.getRemoteClientAddress() can throw
    // URISyntaxException for a malformed address; this was already caught before the hardening.
    @Test
    void testGetClientAddress_whenRequestWrapperThrowsUriSyntaxException_returnsEmptyString() throws Exception {
        RequestWrapper requestWrapperMock = mock(RequestWrapper.class);
        when(requestWrapperMock.getClientAddress()).thenThrow(new URISyntaxException("bad::uri", "malformed"));
        setContext(createContextMock(requestWrapperMock));
        assertEquals("", RequestThreadLocal.getClientAddress());
    }

    @Test
    void testGetClientAddress_whenRequestWrapperThrowsIllegalStateException_returnsEmptyString() throws Exception {
        RequestWrapper requestWrapperMock = mock(RequestWrapper.class);
        when(requestWrapperMock.getClientAddress())
                .thenThrow(new IllegalStateException("The request object has been recycled "
                        + "and is no longer associated with this facade"));
        setContext(createContextMock(requestWrapperMock));
        assertEquals("", RequestThreadLocal.getClientAddress());
    }

    @Test
    void testGetBaseUrl_returnsBaseUrlFromRequestWrapper() {
        RequestWrapper requestWrapperMock = mock(RequestWrapper.class);
        when(requestWrapperMock.getBaseUrl()).thenReturn("http://localhost:8081/artifactory");
        setContext(createContextMock(requestWrapperMock));
        assertEquals("http://localhost:8081/artifactory", RequestThreadLocal.getBaseUrl());
    }

    @Test
    void testGetBaseUrl_returnsEmptyStringWhenNoContext() {
        setContext(null);
        assertEquals("", RequestThreadLocal.getBaseUrl());
    }

    @Test
    void testGetBaseUrl_whenRequestWrapperThrowsIllegalStateException_returnsEmptyString() {
        RequestWrapper requestWrapperMock = mock(RequestWrapper.class);
        when(requestWrapperMock.getBaseUrl())
                .thenThrow(new IllegalStateException("The request object has been recycled "
                        + "and is no longer associated with this facade"));
        setContext(createContextMock(requestWrapperMock));
        assertEquals("", RequestThreadLocal.getBaseUrl());
    }

    @Test
    void testGetDownloadPerfCounters_returnsNullWhenNoContext() {
        setContext(null);
        assertNull(RequestThreadLocal.getDownloadPerfCounters(true));
    }

    @Test
    void testGetDownloadPerfCounters_returnsExistingCountersWithoutCreatingNewOne() {
        DownloadPerfCounters existing = mock(DownloadPerfCounters.class);
        HttpServletRequest requestMock = mock(HttpServletRequest.class);
        when(requestMock.getAttribute(DownloadPerfCounters.class.getName())).thenReturn(existing);
        setContext(createContextMock(requestMock));

        assertSame(existing, RequestThreadLocal.getDownloadPerfCounters(true));
        verify(requestMock, never()).setAttribute(anyString(), any());
    }

    @Test
    void testGetDownloadPerfCounters_whenAbsentAndNotCreating_returnsNull() {
        HttpServletRequest requestMock = mock(HttpServletRequest.class);
        when(requestMock.getAttribute(DownloadPerfCounters.class.getName())).thenReturn(null);
        setContext(createContextMock(requestMock));

        assertNull(RequestThreadLocal.getDownloadPerfCounters(false));
        verify(requestMock, never()).setAttribute(anyString(), any());
    }

    @Test
    void testGetDownloadPerfCounters_createsAndStoresNewInstanceWhenAbsent() {
        HttpServletRequest requestMock = mock(HttpServletRequest.class);
        when(requestMock.getAttribute(DownloadPerfCounters.class.getName())).thenReturn(null);
        setContext(createContextMock(requestMock));

        DownloadPerfCounters result = RequestThreadLocal.getDownloadPerfCounters(true);

        assertNotNull(result);
        verify(requestMock).setAttribute(DownloadPerfCounters.class.getName(), result);
    }

    @Test
    void testGetDownloadPerfCounters_noArgOverload_doesNotCreateWhenAbsent() {
        HttpServletRequest requestMock = mock(HttpServletRequest.class);
        when(requestMock.getAttribute(DownloadPerfCounters.class.getName())).thenReturn(null);
        setContext(createContextMock(requestMock));

        assertNull(RequestThreadLocal.getDownloadPerfCounters());
        verify(requestMock, never()).setAttribute(anyString(), any());
    }

    @Test
    void testGetDownloadPerfCounters_whenRequestIsRecycled_returnsNull() {
        HttpServletRequest requestMock = mock(HttpServletRequest.class);
        when(requestMock.getAttribute(DownloadPerfCounters.class.getName()))
                .thenThrow(new IllegalStateException("The request object has been recycled "
                        + "and is no longer associated with this facade"));
        RequestThreadLocalContext context = createContextMock(requestMock);
        setContext(context);
        assertNull(RequestThreadLocal.getDownloadPerfCounters(true));
    }

    @Test
    void testGetRedirectInfoWhenNotInitialized() {
        HttpServletRequest requestMock = mock(HttpServletRequest.class);
        when(requestMock.getAttribute(RedirectInfo.class.getName())).thenReturn(null);
        RequestThreadLocalContext context = createContextMock(requestMock);
        setContext(context);
        RedirectInfo redirectInfo = RequestThreadLocal.getRedirectInfo();
        assertNotNull(redirectInfo);
        verify(requestMock).setAttribute(RedirectInfo.class.getName(), redirectInfo);
    }

    @Test
    void testGetRedirectInfoWhenInitialized() {
        RedirectInfo redirectInfoMock = mock(RedirectInfo.class);
        HttpServletRequest requestMock = mock(HttpServletRequest.class);
        when(requestMock.getAttribute(RedirectInfo.class.getName())).thenReturn(redirectInfoMock);
        RequestThreadLocalContext context = createContextMock(requestMock);
        setContext(context);
        RedirectInfo redirectInfo = RequestThreadLocal.getRedirectInfo();
        assertEquals(redirectInfoMock, redirectInfo);
    }

    @Test
    void testGetRedirectInfo_whenRequestIsRecycled_returnsNull() {
        HttpServletRequest requestMock = mock(HttpServletRequest.class);
        when(requestMock.getAttribute(RedirectInfo.class.getName()))
                .thenThrow(new IllegalStateException("The request object has been recycled "
                        + "and is no longer associated with this facade"));
        RequestThreadLocalContext context = createContextMock(requestMock);
        setContext(context);
        assertNull(RequestThreadLocal.getRedirectInfo());
    }

    @ParameterizedTest
    @MethodSource("scanFactorTestData")
    void testScanFactor(String headerValue, Integer expectedScanFactor) {
        HttpServletRequest requestMock = mock(HttpServletRequest.class);
        when(requestMock.getHeader("scan-factor")).thenReturn(headerValue);
        RequestThreadLocalContext context = createContextMock(requestMock);
        setContext(context);
        int result = RequestThreadLocal.scanFactor();
        assertEquals(expectedScanFactor.intValue(), result);
    }

    @Test
    void testScanFactorWithNullContext() {
        setContext(null);
        int result = RequestThreadLocal.scanFactor();
        assertEquals(1, result);
    }

    @Test
    void testScanFactor_whenRequestIsRecycled_returnsDefault() {
        HttpServletRequest requestMock = mock(HttpServletRequest.class);
        when(requestMock.getHeader("scan-factor"))
                .thenThrow(new IllegalStateException("The request object has been recycled "
                        + "and is no longer associated with this facade"));
        RequestThreadLocalContext context = createContextMock(requestMock);
        setContext(context);
        assertEquals(1, RequestThreadLocal.scanFactor());
    }

    private static Stream<Arguments> scanFactorTestData() {
        return Stream.of(
                Arguments.of(null, 1),
                Arguments.of("1", 1),
                Arguments.of("10 ", 10),
                Arguments.of("0", 1),
                Arguments.of("invalid", 1),
                Arguments.of("", 1),
                Arguments.of("-1", 1)
        );
    }

    private void setContext(RequestThreadLocalContext contextValue) {
        try {
            Field contextField = RequestThreadLocal.class.getDeclaredField("context");
            contextField.setAccessible(true);
            ThreadLocal<RequestThreadLocalContext> threadLocal =
                    (ThreadLocal<RequestThreadLocalContext>) contextField.get(null);
            threadLocal.set(contextValue);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void testGetOrCreateRequestAttribute_whenRequestIsNull() {
        // Arrange
        setContext(null);
        Supplier<String> supplier = mock(Supplier.class);
        
        // Act
        String result = RequestThreadLocal.getOrCreateRequestAttribute(String.class, supplier);
        
        // Assert - Request is null - should return null
        assertNull(result);
        verify(supplier, never()).get();
    }

    @Test
    void testGetOrCreateRequestAttribute_whenAttributeDoesNotExist() {
        // Arrange
        HttpServletRequest requestMock = mock(HttpServletRequest.class);
        when(requestMock.getAttribute(String.class.getName())).thenReturn(null);
        RequestThreadLocalContext context = createContextMock(requestMock);
        setContext(context);

        Supplier<String> supplier = mock(Supplier.class);
        when(supplier.get()).thenReturn("new-value");
        
        // Act
        String result = RequestThreadLocal.getOrCreateRequestAttribute(String.class, supplier);
        
        // Assert - Attribute doesn't exist - should create new instance
        assertNotNull(result);
        assertEquals("new-value", result);
        verify(requestMock).getAttribute(String.class.getName());
        verify(requestMock).setAttribute(String.class.getName(), "new-value");
        verify(supplier).get();
    }

    @Test
    void testGetOrCreateRequestAttribute_whenAttributeExists() {
        // Arrange
        HttpServletRequest requestMock = mock(HttpServletRequest.class);
        String existingAttribute = "existing-value";
        when(requestMock.getAttribute(String.class.getName())).thenReturn(existingAttribute);
        RequestThreadLocalContext context = createContextMock(requestMock);
        setContext(context);
        
        Supplier<String> supplier = mock(Supplier.class);
        
        // Act
        String result = RequestThreadLocal.getOrCreateRequestAttribute(String.class, supplier);
        
        // Assert - Attribute exists - should return existing without calling supplier
        assertNotNull(result);
        assertSame(existingAttribute, result);
        assertEquals("existing-value", result);
        verify(requestMock).getAttribute(String.class.getName());
        verify(supplier, never()).get();
    }

    @Test
    void testGetOrCreateRequestAttribute_whenSupplierCreatesNewInstance() {
        // Arrange
        HttpServletRequest requestMock = mock(HttpServletRequest.class);
        when(requestMock.getAttribute(String.class.getName())).thenReturn(null);
        RequestThreadLocalContext context = createContextMock(requestMock);
        setContext(context);

        // Act - first call creates new instance
        String firstResult = RequestThreadLocal.getOrCreateRequestAttribute(String.class,
                () -> "test-value");

        // Assert - first call returns new instance
        assertNotNull(firstResult);
        assertEquals("test-value", firstResult);
        verify(requestMock).setAttribute(String.class.getName(), firstResult);

        // Act - second call should return same instance
        when(requestMock.getAttribute(String.class.getName())).thenReturn(firstResult);
        String secondResult = RequestThreadLocal.getOrCreateRequestAttribute(String.class,
                () -> "different-value");

        // Assert - second call returns same instance, supplier not called
        assertSame(firstResult, secondResult);
        assertEquals("test-value", secondResult);
    }

    // RTDEV-87329: request.getAttribute() throws IllegalStateException on a recycled request facade.
    // The caller-supplied Supplier must not be invoked once the attribute read itself has failed.
    @Test
    void testGetOrCreateRequestAttribute_whenRequestIsRecycled_returnsNullAndSkipsSupplier() {
        HttpServletRequest requestMock = mock(HttpServletRequest.class);
        when(requestMock.getAttribute(String.class.getName()))
                .thenThrow(new IllegalStateException("The request object has been recycled "
                        + "and is no longer associated with this facade"));
        RequestThreadLocalContext context = createContextMock(requestMock);
        setContext(context);

        Supplier<String> supplier = mock(Supplier.class);
        String result = RequestThreadLocal.getOrCreateRequestAttribute(String.class, supplier);

        assertNull(result);
        verify(supplier, never()).get();
    }

    @Test
    void testGetOrCreateRequestAttribute_whenSupplierThrowsIllegalStateException_propagates() {
        HttpServletRequest requestMock = mock(HttpServletRequest.class);
        when(requestMock.getAttribute(String.class.getName())).thenReturn(null);
        setContext(createContextMock(requestMock));

        IllegalStateException expected = new IllegalStateException("supplier failure");
        Supplier<String> supplier = () -> {
            throw expected;
        };

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> RequestThreadLocal.getOrCreateRequestAttribute(String.class, supplier));
        assertSame(expected, thrown);
        verify(requestMock, never()).setAttribute(anyString(), any());
    }

    @Test
    void testSetRequestAttribute_whenRequestIsRecycled_doesNotThrow() {
        HttpServletRequest requestMock = mock(HttpServletRequest.class);
        doThrow(new IllegalStateException("The request object has been recycled "
                        + "and is no longer associated with this facade"))
                .when(requestMock).setAttribute(anyString(), any());
        RequestThreadLocalContext context = createContextMock(requestMock);
        setContext(context);

        RequestThreadLocal.setRequestAttribute("key", "value");
    }

    @Test
    void testGetRequestAttribute_returnsTypedValue() {
        HttpServletRequest requestMock = mock(HttpServletRequest.class);
        when(requestMock.getAttribute("key")).thenReturn("value");
        RequestThreadLocalContext context = createContextMock(requestMock);
        setContext(context);

        assertEquals("value", RequestThreadLocal.getRequestAttribute("key", String.class));
    }

    @Test
    void testGetRequestAttribute_whenRequestIsRecycled_returnsNull() {
        HttpServletRequest requestMock = mock(HttpServletRequest.class);
        when(requestMock.getAttribute("key"))
                .thenThrow(new IllegalStateException("The request object has been recycled "
                        + "and is no longer associated with this facade"));
        RequestThreadLocalContext context = createContextMock(requestMock);
        setContext(context);

        assertNull(RequestThreadLocal.getRequestAttribute("key", String.class));
    }

    private RequestThreadLocalContext createContextMock(HttpServletRequest requestMock) {
        RequestThreadLocalContext contextMock = mock(RequestThreadLocalContext.class);
        when(contextMock.getRequestThreadLocal()).thenReturn(mock(RequestWrapper.class));
        when(contextMock.getRequestThreadLocal().getRequest()).thenReturn(requestMock);
        return contextMock;
    }

    /** For tests that need to stub {@link RequestWrapper} methods directly (e.g. getClientAddress, getBaseUrl). */
    private RequestThreadLocalContext createContextMock(RequestWrapper requestWrapperMock) {
        RequestThreadLocalContext contextMock = mock(RequestThreadLocalContext.class);
        when(contextMock.getRequestThreadLocal()).thenReturn(requestWrapperMock);
        return contextMock;
    }

}
