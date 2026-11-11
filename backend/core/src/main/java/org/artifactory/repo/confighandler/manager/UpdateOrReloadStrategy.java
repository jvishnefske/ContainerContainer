package org.artifactory.repo.confighandler.manager;

import org.artifactory.repo.confighandler.ConfigChangeEventContext;

/**
 * @author Mor Merhav
 * Strategy for {@link org.artifactory.repo.confighandler.manager.ConfigChangeEventHandlerManagerImpl} for provoding
 * different ways of updating and reloading repositories.
 * Currently, this is needed due to system startup, during which we cannot use certain beans.
 * @see org.artifactory.repo.confighandler.manager.ConfigChangeEventHandlerManagerImpl
 * @see org.artifactory.repo.confighandler.ConfigChangeEventHandler
 */
public interface UpdateOrReloadStrategy {

    void updateOrReload(ConfigChangeEventContext context);

    void init();
}
