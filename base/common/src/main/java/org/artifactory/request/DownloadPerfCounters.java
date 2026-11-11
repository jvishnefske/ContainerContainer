package org.artifactory.request;

import lombok.Getter;
import lombok.Setter;

import java.util.HashMap;
import java.util.Map;

/**
 * Holds details about a download request, such as the actual size of the
 * requested artifact, time taken to download the file from the remote repository,
 * results of security scanning, etc.
 * The download service will create an instance of this class, fill it with details and
 * attach to tHTTP request, so that Web filters will be able to report the
 * corresponding data to logs and metrics.
 */
@Getter
@Setter
public class DownloadPerfCounters {
    /// The size of the requested artifact in bytes
    private Long artifactSizeBytes;
    /// The total time (msec) it took to download artifact from the remote repository
    private Long remoteDownloadDurationMillis;
    // A map, holding the time (msec) spent waiting for external services, like Xray (keyed by service name).
    private Map<String, Long> externalWaitTimeMsec;

    public void addRemoteDownloadTime(long durationMsec) {
        if (remoteDownloadDurationMillis == null) {
            remoteDownloadDurationMillis = durationMsec;
        } else {
            remoteDownloadDurationMillis += durationMsec;
        }
    }

    public void addExternalWaitTime(String service, long durationMsec) {
        if (externalWaitTimeMsec == null) {
            externalWaitTimeMsec = new HashMap<>();
        }
        externalWaitTimeMsec.merge(service, durationMsec, Long::sum);
    }
}
