package org.artifactory.request.audit;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Collects distinct HTTP header names observed on incoming requests and periodically logs them.
 * Controlled by {@code ConstantValues.httpHeadersAuditEnabled}.
 */
public interface HttpHeadersAuditService {

    /**
     * Record the header names present on the given request. No-op when the audit feature flag is disabled.
     */
    void recordHeaderNames(HttpServletRequest request);

    /**
     * Log the distinct header names observed since the last flush, then clear the collected set.
     * No-op when the audit feature flag is disabled.
     */
    void flush();
}
