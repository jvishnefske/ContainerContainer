package org.artifactory.repo;

import org.artifactory.api.config.RepositoryConfigService;
import org.artifactory.spring.BeforeContextCreationListener;
import org.artifactory.spring.ReloadableBean;

/**
 * Reloadable interface for the repository configuration service
 *
 * @author shayb
 */
public interface ReloadableRepositoryConfigService extends RepositoryConfigService, ReloadableBean,
        BeforeContextCreationListener {
}
