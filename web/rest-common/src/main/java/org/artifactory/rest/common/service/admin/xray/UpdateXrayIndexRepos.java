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

package org.artifactory.rest.common.service.admin.xray;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.xray.XrayAddon;
import org.artifactory.addon.xray.XrayRepo;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.descriptor.repo.RemoteRepoDescriptor;
import org.artifactory.descriptor.repo.RepoDescriptor;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.projects.ProjectsService;
import org.artifactory.projects.ProjectsUtils;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.rest.common.service.RestService;
import org.artifactory.rest.common.util.RequestUtils;
import org.artifactory.exception.http.BadRequestException;
import org.artifactory.exception.http.ForbiddenException;
import org.artifactory.rest.exception.ForbiddenWebAppException;
import org.artifactory.security.AuthenticationHelper;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Get list of repositories to index, mark these repositories for 'xrayIndex' and remove the old 'xrayIndex' if any.
 *
 * @author Shay Bagants
 */
@Slf4j
@Component
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
@RequiredArgsConstructor
public class UpdateXrayIndexRepos implements RestService {

    private final ProjectsService projectsService;
    private final RepositoryService repoService;
    private final AddonsManager addonsManager;
    private final SecurityService securityService;

    @Override
    public void execute(ArtifactoryRestRequest request, RestResponse response) {
        final String projectKey = RequestUtils.getNormalizedProjectKey(request);
        validateProjectAdminPermissions(projectKey);

        XrayAddon xrayAddon = addonsManager.addonByType(XrayAddon.class);
        if (!xrayAddon.isXrayEnabled()) {
            throw new ForbiddenWebAppException("Failed to update repositories. Xray configuration is not enabled.");
        }

        List<XrayRepo> repos = Optional.ofNullable(((ArtifactoryRestRequest<XrayRepo>) request).getModels())
                .orElse(Collections.emptyList());

        Set<String> reposToIndex = repos.stream().map(XrayRepo::getName).collect(Collectors.toSet());
        log.debug("Requested repos: {} under project: {}", reposToIndex, projectKey);

        assertNoVirtualOrDistRepos(repos);
        validateAllReposAreRelatedToTheRequiredProject(projectKey, repos);

        String remoteAddress = AuthenticationHelper.getRemoteAddress(AuthenticationHelper.getAuthentication());
        log.info("Adding repos to Xray index, request received from instance at: {}", remoteAddress);
        xrayAddon.updateXraySelectedIndexedRepos(reposToIndex, projectKey);
    }

    private void validateAllReposAreRelatedToTheRequiredProject(@NonNull String projectKey,
            @NonNull List<XrayRepo> repos) {
        if (ProjectsUtils.isDefaultProject(projectKey)) {
            return;
        }

        final Set<String> projectRepos = projectsService.getProjectRepositoriesKeys(projectKey, false);
        final Set<String> requestRepos = repos.stream().map(XrayRepo::getName).collect(Collectors.toSet());
        if (!projectRepos.containsAll(requestRepos)) {
            throw new IllegalArgumentException("Some of the repositories do not belong to the provided project");
        }
    }

    private void assertNoVirtualOrDistRepos(List<XrayRepo> repos) {
        repos.forEach(repo -> {
            RepoDescriptor descriptor = repoService.repoDescriptorByKey(repo.getName());
            if (descriptor == null) {
                throw new BadRequestException(
                        "Request could not be completed. Repository key:'" + repo.getName() + "' does not exists.");
            }
            if (!descriptor.isReal() || descriptor.getType().equals(RepoType.Distribution) ||
                    (descriptor instanceof RemoteRepoDescriptor &&
                            !((RemoteRepoDescriptor) descriptor).isStoreArtifactsLocally())) {
                throw new BadRequestException("Request could not be completed. Repository key:'" + repo.getName() +
                        "' is not supported for xray indexing.");
            }
        });
    }

    private void validateProjectAdminPermissions(@NonNull String projectKey) {
        if (!securityService.isProjectAdmin(projectKey)) {
            throw new ForbiddenException("Forbidden for non admin users");
        }
    }
}
