package org.artifactory.ui.rest.service.setmeup;

import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.config.RepositoryConfigService;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.repo.service.InternalRepositoryService;
import org.artifactory.rest.common.service.RestService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * @author Ron Malka
 */
@Component
public abstract class MvnGroupSettingGeneratorService<T> implements RestService<T> {
    protected AuthorizationService authorizationService;
    protected CentralConfigService centralConfigService;
    protected InternalRepositoryService repositoryService;
    protected RepositoryConfigService repositoryConfigService;

    @Autowired
    public void setAuthorizationService(AuthorizationService authorizationService) {
        this.authorizationService = authorizationService;
    }

    @Autowired
    public void setCentralConfigService(CentralConfigService centralConfigService) {
        this.centralConfigService = centralConfigService;
    }

    @Autowired
    public void setRepositoryService(InternalRepositoryService repositoryService) {
        this.repositoryService = repositoryService;
    }

    @Autowired
    public void setRepositoryConfigService(RepositoryConfigService repositoryConfigService) {
        this.repositoryConfigService = repositoryConfigService;
    }
}