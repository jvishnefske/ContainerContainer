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

package org.artifactory.rest.resource.repositories;

import org.apache.commons.lang3.SerializationUtils;
import org.apache.http.HttpStatus;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.api.rest.constant.RepositoriesRestConstants;
import org.artifactory.descriptor.repo.HttpRepoDescriptor;
import org.artifactory.descriptor.repo.RemoteRepoDescriptor;
import org.artifactory.model.RemoteRepoConfig;
import org.artifactory.repo.RepoDetailsType;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.io.IOException;
import java.util.List;

/**
 * A resource to manage the remote repository configuration actions
 *
 * @author Noam Tenne
 */
@Deprecated
public class RepoConfigurationResource {

    private final CentralConfigService centralConfigService;
    private final RepositoryService repositoryService;
    private final String remoteRepoKey;

    /**
     * Default constructor
     *
     * @param centralConfigService    Central config service
     * @param repositoryService Repo config service
     * @param remoteRepoKey           Key of selected remote repository
     */
    public RepoConfigurationResource(CentralConfigService centralConfigService,
            RepositoryService repositoryService, String remoteRepoKey) {
        this.centralConfigService = centralConfigService;
        this.repositoryService = repositoryService;
        this.remoteRepoKey = remoteRepoKey;
    }

    /**
     * Returns the remote repository descriptor of the given key, if shared
     *
     * @return Json generated descriptor
     * @throws IOException
     */
    @GET
    @Produces({RepositoriesRestConstants.MT_REMOTE_REPOSITORY_CONFIGURATION, MediaType.APPLICATION_JSON})
    public Response getRemoteDescriptor() {
        List<RemoteRepoConfig> remoteRepoConfigs =
                repositoryService.getRepoConfigsByType(RepoDetailsType.REMOTE).stream()
                        .map(repoConfig -> (RemoteRepoConfig) repoConfig)
                        .toList();
        for (RemoteRepoConfig remoteRepoConfig : remoteRepoConfigs) {
            String currentRepoKey = remoteRepoConfig.getKey();
            if (remoteRepoKey.equals(currentRepoKey) && remoteRepoConfig.getRepoTypeConfig().isShareConfiguration()) {
                // To keep supporting the deprecated REST endpoint api/repositories/<remote-repo-key>>/configuration
                // the {"@class" : "org.artifactory.descriptor.repo.HttpRepoDescriptor"} should be returned.
                // There is the test RepositoriesResourceOssTest.testRemoteRepoConfiguration that covers the flow.
                HttpRepoDescriptor httpRepoDescriptor = new HttpRepoDescriptor(remoteRepoConfig,
                        centralConfigService.getDescriptor().getRepoLayouts(),
                        centralConfigService.getDescriptor().getProxies(),
                        centralConfigService.getDescriptor().getPropertySets(),
                        centralConfigService.getDescriptor().getKeyPairs());
                return Response.ok().entity(maskData(httpRepoDescriptor)).build();
            }
        }
        return Response.status(HttpStatus.SC_NOT_FOUND).build();
    }

    /**
     * Mask different elements of the given remote repository descriptor
     *
     * @param remoteRepoDescriptor Descriptor to mask
     * @return Masked copy of given descriptor
     */
    private RemoteRepoDescriptor maskData(RemoteRepoDescriptor remoteRepoDescriptor) {
        if (remoteRepoDescriptor instanceof HttpRepoDescriptor httpRepoDescriptor) {
            HttpRepoDescriptor descriptorCopy = SerializationUtils.clone(httpRepoDescriptor);

            //Remote credentials
            descriptorCopy.setUsername(null);
            descriptorCopy.setPassword(null);

            //Remove proxy config
            descriptorCopy.setProxy(null);

            //Remove local address
            descriptorCopy.setLocalAddress(null);

            // remove property sets
            descriptorCopy.setPropertySets(null);

            return descriptorCopy;
        }
        return remoteRepoDescriptor;
    }
}