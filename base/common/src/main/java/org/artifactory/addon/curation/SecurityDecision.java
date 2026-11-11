package org.artifactory.addon.curation;

/**
 * RT-side enum representing the source of a Curation download decision.
 * <p>
 * The first three values ({@link #SECURITY_SCANNED_FINAL}, {@link #SECURITY_UNSCANNED_PENDING},
 * {@link #SECURITY_UNSCANNED_FINAL}) mirror the shared {@code DownloadDecision} enum from xray-client
 * and represent decisions originating from Xray/Curation.
 * <p>
 * The remaining values are RT-specific and represent decisions made locally by Artifactory
 * (e.g., Curation unavailable, rate limiting, pre-check optimizations).
 *
 * @author Dvir Twina
 */
public enum SecurityDecision {

    SECURITY_SCANNED_FINAL,
    SECURITY_UNSCANNED_PENDING,
    SECURITY_UNSCANNED_FINAL,

    SECURITY_UNSCANNED_PENDING_TIMEOUT,
    SECURITY_NOT_AVAILABLE,
    CURATION_REQUEST_NOT_CACHED,
    CURATION_NOT_ENABLED,
    CURATION_REPO_NOT_CURATED,
    CURATION_PROTOCOL_ERROR,
    CURATION_NO_LOCAL_CACHE_HEAD_ONLY
}
