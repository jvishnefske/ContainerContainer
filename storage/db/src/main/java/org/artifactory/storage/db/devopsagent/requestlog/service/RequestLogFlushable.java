package org.artifactory.storage.db.devopsagent.requestlog.service;

/**
 * Implemented by any request-log service that owns a write queue.
 * {@link org.artifactory.devopsagent.requestlog.job.RequestLogFlushJob} iterates all registered
 * beans of this type, so new directions (e.g. outbound) need only implement this interface —
 * the job requires no modification.
 */
public interface RequestLogFlushable {

    /** Drain the queue to the DB. Called periodically by the flush job and on shutdown. */
    void flush();

    /** Returns true when the underlying feature is active and this service should be flushed. */
    boolean isFeatureEnabled();
}
