/*
 *
 * Artifactory is a binaries repository manager.
 * Copyright (C) 2018 JFrog Ltd.
 *
 * Artifactory is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 * Artifactory is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Artifactory.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

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
 * HttpRepoFactory for Ansible remote repositories. Builds {@link AnsibleHttpRepo} instances
 * that provide a dedicated token client (via HttpRepo#createHttpClient) for OAuth2 token fetches.
 */
@Component
public class AnsibleHttpRepoFactory implements HttpRepoFactory {

    private final InternalRepositoryService repositoryService;
    private final AddonsManager addonsManager;
    private final ResearchService researchService;
    private final OnlineMonitorService onlineMonitorService;
    private final CentralConfigService configService;
    private final IRequestRateLimiterService rateLimiterService;

    @Autowired
    public AnsibleHttpRepoFactory(InternalRepositoryService repositoryService,
            AddonsManager addonsManager,
            ResearchService researchService,
            OnlineMonitorService onlineMonitorService,
            CentralConfigService configService,
            IRequestRateLimiterService rateLimiterService) {
        this.repositoryService = repositoryService;
        this.addonsManager = addonsManager;
        this.researchService = researchService;
        this.onlineMonitorService = onlineMonitorService;
        this.configService = configService;
        this.rateLimiterService = rateLimiterService;
    }

    @Override
    public HttpRepo build(RemoteRepoConfig repoConfig, HttpRepoDescriptor repoDescriptor, RemoteRepo oldRemoteRepo) {
        return new AnsibleHttpRepo(repoDescriptor, repoConfig, repositoryService, addonsManager, researchService,
                oldRemoteRepo, onlineMonitorService, configService, rateLimiterService);
    }

    @Override
    public RepoType getRepoType() {
        return RepoType.Ansible;
    }
}
