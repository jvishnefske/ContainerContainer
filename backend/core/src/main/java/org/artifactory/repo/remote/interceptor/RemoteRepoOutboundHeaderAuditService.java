package org.artifactory.repo.remote.interceptor;

import org.apache.http.HttpResponse;

import java.util.Set;

/**
 * Accumulates the distinct upstream response header names observed per remote repository and periodically logs
 * a summary. Controlled by {@code ConstantValues.remoteRepoOutboundHeadersAuditEnabled}.
 */
public interface RemoteRepoOutboundHeaderAuditService {

    /**
     * Record the response header names returned by the upstream for the given remote repo. No-op when
     * {@code repoKey} is blank or {@code response} is null. {@code configuredRepoUrl} is stored once per
     * {@code repoKey} for log context; values are never recorded. Callers gate this on
     * {@code ConstantValues.remoteRepoOutboundHeadersAuditEnabled}.
     */
    void record(String repoKey, String configuredRepoUrl, HttpResponse response);

    /**
     * Log the per-repo distinct header-name summaries observed since the last flush, then clear the accumulator.
     */
    void flush();

    /**
     * Return a snapshot of the distinct header names accumulated for the given {@code repoKey}, or an empty set
     * if no headers have been recorded for it.
     */
    Set<String> getHeaderNames(String repoKey);

    /**
     * Drop any accumulated header names and stored URL for the given {@code repoKey}.
     */
    void remove(String repoKey);
}
