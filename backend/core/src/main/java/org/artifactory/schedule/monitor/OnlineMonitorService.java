package org.artifactory.schedule.monitor;

import org.apache.http.StatusLine;
import org.artifactory.repo.RemoteRepo;
import org.artifactory.spring.ReloadableBean;

/**
 * @author Alexei Vainshtein
 */
public interface OnlineMonitorService extends ReloadableBean {

    /**
     * creates a monitor for the given repo
     * @param repo - The repo needed to monitor
     */
    void createOfflineRepoMonitor(RemoteRepo repo);

    /**
     * Checks whether response code falls into ResourceUnavailable marked status codes
     *
     * @param statusLine {@link org.apache.http.StatusLine}
     * @return true if response.status is one of HttpRepo#offlineStatusCodes
     * members or false
     */
    boolean isResourceUnavailable(StatusLine statusLine);

    /**
     * Stop the monitoring for the given repository
     *
     * @param remoteRepoKey - The repository to stop monitor for the state.
     */
    boolean stopMonitorForRepo(String remoteRepoKey);

    boolean isRemoteRepoMonitored(String remoteRepoKey);
}
