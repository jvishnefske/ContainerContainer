package org.artifactory.remote.stats;

import java.util.Set;

/**
 * Sort field constants for the remote repository statistics API.
 *
 * @author Hussein Alshqerat
 */
public final class RemoteRepoStatsSortFields {

    public static final String TOTAL_REQUESTS = "totalRequests";
    public static final String ERROR_RATE = "errorRate";
    public static final String AVG_RESPONSE_TIME = "avgResponseTime";
    public static final String AVG_TTFB = "avgTtfb";
    public static final String TOTAL_RESPONSE_SIZE = "totalResponseSize";

    public static final Set<String> ALL = Set.of(
            TOTAL_REQUESTS, ERROR_RATE, AVG_RESPONSE_TIME,
            AVG_TTFB, TOTAL_RESPONSE_SIZE
    );

    private RemoteRepoStatsSortFields() {
    }
}
