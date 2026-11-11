package org.artifactory.system.load.monitor;

/**
 * Self-diagnostic probe that measures actual request-processing latency by issuing a lightweight
 * HTTP request to the local readiness endpoint ({@code /artifactory/api/v1/system/readiness}).
 *
 * @author Amir Avidan
 * @see OutageSelfProbeImpl
 * @see DisabledOutageSelfProbe
 */
public interface OutageSelfProbe {

    ProbeResult getLatestResult();

    void start();

    void stop();
}
