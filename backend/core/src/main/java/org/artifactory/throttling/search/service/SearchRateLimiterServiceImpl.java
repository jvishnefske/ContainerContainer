package org.artifactory.throttling.search.service;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.api.security.SecurityService;
import org.artifactory.exception.SearchTooManyRequestsException;
import org.artifactory.storage.common.semaphore.UserGlobalSemaphoresManager;
import org.artifactory.throtlling.search.service.SearchLimitRunnable;
import org.artifactory.throtlling.search.service.SearchRateLimiterService;
import org.artifactory.webapp.servlet.RequestUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import static org.artifactory.common.ConstantValues.searchLimit;
import static org.artifactory.common.ConstantValues.searchLimitEnabled;
import static org.artifactory.common.ConstantValues.searchLimitPatternAdditionalList;
import static org.artifactory.common.ConstantValues.searchLimitPatternDefaultList;
import static org.artifactory.common.ConstantValues.searchPendingTimeoutMillis;
import static org.artifactory.common.ConstantValues.searchUserLimitPercent;

@Slf4j
@Service
public class SearchRateLimiterServiceImpl implements SearchRateLimiterService {

    private final Pattern searchLimitPattern = getSearchLimitPattern();
    private final UserGlobalSemaphoresManager searchSemaphoreManager = new UserGlobalSemaphoresManager(
            searchLimit.getInt(),
            searchUserLimitPercent.getInt(),
            searchPendingTimeoutMillis.getLong(),
            "Search API limiter");
    private final SecurityService securityService;

    @Autowired
    public SearchRateLimiterServiceImpl(SecurityService securityService) {
        this.securityService = securityService;
    }

    @Override
    public boolean isSearchLimitingNeeded(@Nonnull HttpServletRequest request) {
        if (!searchLimitEnabled.getBoolean() || searchLimitPattern == null) {
            return false;
        }
        String path = RequestUtils.getServletPathFromRequest(request);
        if (!searchLimitPattern.matcher(path).matches()) {
            return false;
        }
        return !SecurityService.USER_SYSTEM.equals(securityService.currentUsername());
    }

    @Override
    public void wrapRequestWithSearchLimit(@Nonnull HttpServletRequest request, @Nonnull SearchLimitRunnable runnable)
            throws ServletException, IOException {
        try (UserGlobalSemaphoresManager.PermitsHolder permits = tryAcquireSearchPermits(request)) {
            runnable.run();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ServletException("Search execution interrupted while acquiring semaphore", e);
        }
    }

    @Nullable
    private UserGlobalSemaphoresManager.PermitsHolder tryAcquireSearchPermits(@Nonnull HttpServletRequest request)
            throws InterruptedException {
        if (!isSearchLimitingNeeded(request)) {
            return null;
        }
        String username = securityService.currentUsername();
        String path = RequestUtils.getServletPathFromRequest(request);
        log.debug("Search rate limit applied for path: {} user: {}", path, username);

        UserGlobalSemaphoresManager.PermitsHolder permits = searchSemaphoreManager.tryAcquire(username);

        if (permits == null) {
            log.debug("Didn't acquire semaphore. Too-Many-Requests response will be sent. path: {}", path);
            throw new SearchTooManyRequestsException();
        }
        return permits;
    }

    @Nullable
    private Pattern getSearchLimitPattern() {
        try {
            String defaultList = searchLimitPatternDefaultList.getString();
            if (defaultList.isBlank()) {
                return null;
            }
            String additionalList = searchLimitPatternAdditionalList.getString();
            return Pattern.compile(defaultList + (additionalList.isBlank() ? "" : "|" + additionalList));
        } catch (PatternSyntaxException e) {
            log.error("Search rate limiter regex compilation failed: {}", e.getMessage());
            return null;
        }
    }
}
