package org.artifactory.storage.db.fs.service;

import org.artifactory.spring.ReloadableBean;

/**
 * @author Mor Merhav
 */
public interface RepositoryPersistingService extends ReloadableBean {
    void persist();

    void markDirty();
}
