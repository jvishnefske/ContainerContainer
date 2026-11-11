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

package org.artifactory.ui.rest.service.admin.configuration.repositories.util;

import org.apache.http.HttpStatus;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.CoreAddons;
import org.artifactory.api.config.RepositoryConfigService;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.model.VirtualRepoConfig;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.rest.common.service.RestService;
import org.artifactory.ui.rest.model.admin.configuration.docker.repo.DockerRepo;
import org.artifactory.util.AolUtils;
import org.artifactory.util.HttpUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;
import java.net.MalformedURLException;
import java.net.URL;

/**
 * @author Shay Yaakov
 */
@Component
public class GetDockerRepoService implements RestService {
    private static final Logger log = LoggerFactory.getLogger(GetDockerRepoService.class);

    @Autowired
    private RepositoryService repoService;

    @Autowired
    private RepositoryConfigService repositoryConfigService;

    @Override
    public void execute(ArtifactoryRestRequest request, RestResponse response) {
        String repoKey = request.getQueryParamByKey("repoKey");
        DockerRepo dockerRepo = new DockerRepo();
        CoreAddons coreAddons = ContextHelper.get().beanForType(AddonsManager.class).addonByType(CoreAddons.class);
        String hostName = coreAddons.getArtifactoryServerName();
        dockerRepo.setHostname(hostName);
        if (repoService.localOrFederatedRepoDescriptorByKey(repoKey) != null) {
            boolean deployToLocal = repositoryConfigService.getRepoConfigsByType(RepoDetailsType.VIRTUAL).stream()
                    .map(VirtualRepoConfig.class::cast)
                    .filter(virtualRepoConfig -> RepoType.Docker.equals(virtualRepoConfig.getPackageType()))
                    .anyMatch(virtualRepoConfig -> {
                        String defaultDeploymentRepoRef =
                                virtualRepoConfig.getRepoTypeConfig().getDefaultDeploymentRepoRef();
                        return repoKey.equals(defaultDeploymentRepoRef);
                    });
            dockerRepo.setDeployToLocal(deployToLocal);
        }
        if (AolUtils.isAol()) {
            try {
                String servletContextUrl =
                        HttpUtils.getServletContextUrlArtifactoryPreferred(request.getServletRequest());
                dockerRepo.setBaseUrl(servletContextUrl);
                dockerRepo.setDockerPath(createDockerPath(hostName, repoKey, request.getServletRequest()));
                dockerRepo.setSkipDockerDns(AolUtils.skipDockerDns());
            } catch (MalformedURLException e) {
                log.error("Exception occurred: {}", e.getMessage());
                log.debug("Exception occurred: {}", e.getMessage(), e);
                response.responseCode(HttpStatus.SC_BAD_REQUEST);
                response.iModel("An error occurred: " + e);
                return;
            }
        }
        response.responseCode(HttpStatus.SC_OK);
        response.iModel(dockerRepo);
    }

    private String createDockerPath(String hostName, String repoKey,
            HttpServletRequest servletRequest) throws MalformedURLException {
        if (AolUtils.skipDockerDns()) {
            String urlBase = HttpUtils.getServletContextUrl(servletRequest);
            URL baseUrl = new URL(urlBase);
            return baseUrl.getHost() + "/" + repoKey;
        }
        return hostName + "-" + repoKey + ".jfrog.io";
    }
}