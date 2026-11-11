package org.artifactory.repo.cleanup;

import org.artifactory.spring.ReloadableBean;

/**
 * Service interface for systematic empty folder scanning.
 *
 * @author annakv
 */
public interface EmptyFolderScanService extends ReloadableBean {
    /**
     * Scans for empty folders and queues them for pruning.
     */
    void scanEmptyFolders();
}


