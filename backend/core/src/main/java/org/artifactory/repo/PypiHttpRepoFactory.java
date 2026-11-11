package org.artifactory.repo;

import org.artifactory.addon.AddonsManager;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.repo.ResearchService;
import org.artifactory.descriptor.repo.HttpRepoDescriptor;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.model.RemoteRepoConfig;
import org.artifactory.repo.service.InternalRepositoryService;
import org.artifactory.schedule.monitor.OnlineMonitorService;
import org.artifactory.throtlling.generic.rrl.service.IRequestRateLimiterService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Factory that creates {@link PypiHttpRepo} instances for
 * PyPI remote repositories.
 *
 * @author Shahaf Golan
 */
@Component
public class PypiHttpRepoFactory implements HttpRepoFactory {

    private final InternalRepositoryService repositoryService;
    private final AddonsManager addonsManager;
    private final ResearchService researchService;
    private final OnlineMonitorService onlineMonitorService;
    private final CentralConfigService configService;
    private final IRequestRateLimiterService rateLimiterService;

    @Autowired
    public PypiHttpRepoFactory(InternalRepositoryService repositoryService, AddonsManager addonsManager,
            ResearchService researchService, OnlineMonitorService onlineMonitorService,
            CentralConfigService configService, IRequestRateLimiterService rateLimiterService) {
        this.repositoryService = repositoryService;
        this.addonsManager = addonsManager;
        this.researchService = researchService;
        this.onlineMonitorService = onlineMonitorService;
        this.configService = configService;
        this.rateLimiterService = rateLimiterService;
    }

    @Override
    public HttpRepo build(RemoteRepoConfig repoConfig, HttpRepoDescriptor repoDescriptor, RemoteRepo oldRemoteRepo) {
        return new PypiHttpRepo(repoDescriptor, repoConfig, repositoryService, addonsManager,
                researchService, oldRemoteRepo, onlineMonitorService, configService, rateLimiterService);
    }

    @Override
    public RepoType getRepoType() {
        return RepoType.Pypi;
    }
}
