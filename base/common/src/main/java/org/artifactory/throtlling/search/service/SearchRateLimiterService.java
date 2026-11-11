package org.artifactory.throtlling.search.service;

import jakarta.annotation.Nonnull;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;

import java.io.IOException;

public interface SearchRateLimiterService {

    /**
     * Returns true when the request should be subject to search rate limiting (pattern matches, enabled, non-system
     * user).
     */
    boolean isSearchLimitingNeeded(@Nonnull HttpServletRequest request);

    /**
     * Wraps the request with search rate limiting inside RequestFilter; runs the runnable only if a permit is acquired.
     */
    void wrapRequestWithSearchLimit(@Nonnull HttpServletRequest request, @Nonnull SearchLimitRunnable runnable)
            throws ServletException, IOException;
}
