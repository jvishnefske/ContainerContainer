package org.artifactory.throttling.search.service;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import org.artifactory.api.security.SecurityService;
import org.artifactory.exception.SearchTooManyRequestsException;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.artifactory.throtlling.search.service.SearchLimitRunnable;
import org.artifactory.webapp.servlet.RequestUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.artifactory.common.ConstantValues.searchLimit;
import static org.artifactory.common.ConstantValues.searchLimitEnabled;
import static org.artifactory.common.ConstantValues.searchLimitPatternAdditionalList;
import static org.artifactory.common.ConstantValues.searchLimitPatternDefaultList;
import static org.artifactory.common.ConstantValues.searchPendingTimeoutMillis;
import static org.artifactory.common.ConstantValues.searchUserLimitPercent;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.openMocks;

class SearchRateLimiterServiceImplTest extends ArtifactoryHomeBoundJUnitTest {

    @Mock
    private SecurityService securityService;
    private SearchRateLimiterServiceImpl searchRateLimiterService;
    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = openMocks(this);
        getBound().setProperty(searchLimitEnabled.getPropertyName(), "true");
        getBound().setProperty(searchLimit.getPropertyName(), "2");
        getBound().setProperty(searchUserLimitPercent.getPropertyName(), "100");
        getBound().setProperty(searchPendingTimeoutMillis.getPropertyName(), "1000");
        getBound().setProperty(searchLimitPatternDefaultList.getPropertyName(),
                "(/api/search/artifact.*)|(/api/search/versions.*)");
        getBound().setProperty(searchLimitPatternAdditionalList.getPropertyName(), "");
        when(securityService.currentUsername()).thenReturn("testUser");
        searchRateLimiterService = new SearchRateLimiterServiceImpl(securityService);
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    @ParameterizedTest
    @CsvSource({
            "/api/search/artifact,true",
            "/api/storage/something,false"
    })
    void isSearchLimitingNeeded_shouldReturnExpected_whenPathMatchesOrDoesNotMatchSearchPattern(String path,
            boolean expected) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(RequestUtils.getServletPathFromRequest(request)).thenReturn(path);

        assertEquals(expected, searchRateLimiterService.isSearchLimitingNeeded(request));
    }

    @Test
    void isSearchLimitingNeeded_shouldReturnFalse_whenSystemUser() {
        when(securityService.currentUsername()).thenReturn(SecurityService.USER_SYSTEM);
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(RequestUtils.getServletPathFromRequest(request))
                .thenReturn("/api/search/artifact");

        assertFalse(searchRateLimiterService.isSearchLimitingNeeded(request));
    }

    @Test
    void isSearchLimitingNeeded_shouldReturnFalse_whenSearchLimitDisabled() {
        getBound().setProperty(searchLimitEnabled.getPropertyName(), "false");

        HttpServletRequest request = mock(HttpServletRequest.class);
        when(RequestUtils.getServletPathFromRequest(request))
                .thenReturn("/api/search/artifact");

        assertFalse(searchRateLimiterService.isSearchLimitingNeeded(request));
    }

    @Test
    void wrapRequestWithSearchLimit_shouldExecuteRunnable_whenNotSearchRequest() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(RequestUtils.getServletPathFromRequest(request))
                .thenReturn("/api/storage/something");

        SearchLimitRunnable runnable = mock(SearchLimitRunnable.class);

        searchRateLimiterService.wrapRequestWithSearchLimit(request, runnable);

        verify(runnable, times(1)).run();
        verify(securityService, times(0)).currentUsername();
    }

    @Test
    void wrapRequestWithSearchLimit_shouldApplyRateLimiting_whenSystemUser() throws Exception {
        when(securityService.currentUsername()).thenReturn(SecurityService.USER_SYSTEM);
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(RequestUtils.getServletPathFromRequest(request))
                .thenReturn("/api/search/artifact");

        SearchLimitRunnable runnable = mock(SearchLimitRunnable.class);

        searchRateLimiterService.wrapRequestWithSearchLimit(request, runnable);

        verify(runnable, times(1)).run();
        verify(securityService, times(1)).currentUsername();
    }

    @Test
    void wrapRequestWithSearchLimit_shouldExecuteRunnable_whenPermitAcquired() throws Exception {
        when(securityService.currentUsername()).thenReturn("testUser");
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(RequestUtils.getServletPathFromRequest(request))
                .thenReturn("/api/search/artifact");

        SearchLimitRunnable runnable = mock(SearchLimitRunnable.class);

        searchRateLimiterService.wrapRequestWithSearchLimit(request, runnable);

        verify(runnable, times(1)).run();
    }

    @Test
    void wrapRequestWithSearchLimit_shouldThrowLoadHealerException_whenPermitNotAcquired() throws Exception {
        getBound().setProperty(searchLimit.getPropertyName(), "1");
        getBound().setProperty(searchPendingTimeoutMillis.getPropertyName(), "50");
        searchRateLimiterService = new SearchRateLimiterServiceImpl(securityService);

        when(securityService.currentUsername()).thenReturn("testUser");
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(RequestUtils.getServletPathFromRequest(request))
                .thenReturn("/api/search/artifact");

        CountDownLatch firstHoldingPermit = new CountDownLatch(1);
        Thread first = new Thread(() -> {
            try {
                searchRateLimiterService.wrapRequestWithSearchLimit(request, () -> {
                    firstHoldingPermit.countDown();
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });
            } catch (Exception e) {
            }
        });
        first.start();
        assertTrue(firstHoldingPermit.await(5, TimeUnit.SECONDS));

        assertThrows(SearchTooManyRequestsException.class,
                () -> searchRateLimiterService.wrapRequestWithSearchLimit(request, () -> {}));
    }

    @Test
    void wrapRequestWithSearchLimit_shouldNotCallSemaphore_whenSearchLimitDisabled() throws Exception {
        getBound().setProperty(searchLimitEnabled.getPropertyName(), "false");
        searchRateLimiterService = new SearchRateLimiterServiceImpl(securityService);

        when(securityService.currentUsername()).thenReturn("testUser");
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(RequestUtils.getServletPathFromRequest(request))
                .thenReturn("/api/search/artifact");

        SearchLimitRunnable runnable = mock(SearchLimitRunnable.class);

        searchRateLimiterService.wrapRequestWithSearchLimit(request, runnable);

        verify(runnable, times(1)).run();
    }

    @Test
    void isSearchLimitingNeeded_shouldReturnFalse_whenPatternIsInvalid() {
        getBound().setProperty(searchLimitPatternDefaultList.getPropertyName(), "[invalid regex");
        searchRateLimiterService = new SearchRateLimiterServiceImpl(securityService);

        HttpServletRequest request = mock(HttpServletRequest.class);
        when(RequestUtils.getServletPathFromRequest(request))
                .thenReturn("/api/search/artifact");

        assertFalse(searchRateLimiterService.isSearchLimitingNeeded(request));
    }

    @Test
    void isSearchLimitingNeeded_shouldReturnTrue_whenPathMatchesAdditionalPattern() {
        getBound().setProperty(searchLimitPatternAdditionalList.getPropertyName(), "(/api/custom/search.*)");
        searchRateLimiterService = new SearchRateLimiterServiceImpl(securityService);

        HttpServletRequest request = mock(HttpServletRequest.class);
        when(RequestUtils.getServletPathFromRequest(request))
                .thenReturn("/api/custom/search/foo");

        assertTrue(searchRateLimiterService.isSearchLimitingNeeded(request));
    }

    @ParameterizedTest
    @CsvSource({
            "true, testUser, enabled",
            "false, testUser, disabled"
    })
    void wrapRequestWithSearchLimit_shouldExecuteRunnable_whenLimitDisabledOrPermitAcquired(
            boolean limitEnabled, String username, String description) throws Exception {
        getBound().setProperty(searchLimitEnabled.getPropertyName(), String.valueOf(limitEnabled));
        searchRateLimiterService = new SearchRateLimiterServiceImpl(securityService);

        when(securityService.currentUsername()).thenReturn(username);
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(RequestUtils.getServletPathFromRequest(request))
                .thenReturn("/api/search/artifact");

        SearchLimitRunnable runnable = mock(SearchLimitRunnable.class);

        searchRateLimiterService.wrapRequestWithSearchLimit(request, runnable);

        verify(runnable, times(1)).run();
    }

    @Test
    void wrapRequestWithSearchLimit_shouldPropagateServletException_fromRunnable() {
        when(securityService.currentUsername()).thenReturn("testUser");
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(RequestUtils.getServletPathFromRequest(request))
                .thenReturn("/api/search/artifact");

        SearchLimitRunnable runnable = () -> {
            throw new ServletException("test");
        };

        assertThrows(ServletException.class,
                () -> searchRateLimiterService.wrapRequestWithSearchLimit(request, runnable));
    }

    @Test
    void wrapRequestWithSearchLimit_shouldPropagateIOException_fromRunnable() {
        when(securityService.currentUsername()).thenReturn("testUser");
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(RequestUtils.getServletPathFromRequest(request))
                .thenReturn("/api/search/artifact");

        SearchLimitRunnable runnable = () -> {
            throw new IOException("test");
        };

        assertThrows(IOException.class, () -> searchRateLimiterService.wrapRequestWithSearchLimit(request, runnable));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/api/search/artifact",
            "/api/search/gavc",
            "/api/search/prop",
            "/api/search/creation",
            "/api/search/dates",
            "/api/search/versions",
            "/api/search/latestVersion",
            "/api/search/checksum",
            "/api/search/pattern",
            "/api/search/buildArtifacts",
            "/api/search/usage",
            "/ui/artifactsearch/quick",
            "/ui/artifactsearch/pkg",
            "/ui/syntax-search"
    })
    void isSearchLimitingNeeded_shouldReturnTrue_forAllDefaultSearchApiPaths(String path) {
        String fullPattern = "(/api/search/artifact.*)|(/api/search/gavc.*)|(/api/search/prop.*)|"
                + "(/api/search/creation.*)|(/api/search/dates.*)|(/api/search/versions.*)|"
                + "(/api/search/latestVersion.*)|(/api/search/checksum.*)|(/api/search/pattern.*)|"
                + "(/api/search/buildArtifacts.*)|(/api/search/usage.*)|"
                + "(/ui/artifactsearch/quick.*)|(/ui/artifactsearch/pkg.*)|(/ui/syntax-search.*)";
        getBound().setProperty(searchLimitPatternDefaultList.getPropertyName(), fullPattern);
        SearchRateLimiterServiceImpl service = new SearchRateLimiterServiceImpl(securityService);

        when(securityService.currentUsername()).thenReturn("testUser");
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(RequestUtils.getServletPathFromRequest(request)).thenReturn(path);

        assertTrue(service.isSearchLimitingNeeded(request),
                "Path should be rate limited: " + path);
    }
}
