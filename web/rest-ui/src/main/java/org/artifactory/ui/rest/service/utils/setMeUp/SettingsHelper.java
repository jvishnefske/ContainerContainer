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

package org.artifactory.ui.rest.service.utils.setMeUp;

import lombok.experimental.UtilityClass;
import org.artifactory.api.config.RepositoryConfigService;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.descriptor.repo.VirtualRepoDescriptor;
import org.artifactory.model.RepoConfig;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.repo.service.InternalRepositoryService;
import org.artifactory.repo.virtual.VirtualRepo;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.artifactory.repo.service.RepositoryConfigServiceHelper.isHiddenRepo;
import static org.artifactory.repo.service.RepositoryConfigServiceHelper.isInfoRepo;
import static org.artifactory.security.ArtifactoryPermission.READ;

/**
 * @author Lior Hasson
 */
@UtilityClass
public class SettingsHelper {

    /**
     * Returns a list of virtual repositories that a readable by the current user
     *
     * @return Readable virtual repository definitions
     */
    public static List<VirtualRepoDescriptor> getReadableVirtualRepoDescriptors(
            InternalRepositoryService repositoryService,
            AuthorizationService authorizationService) {
        Map<String, Boolean> permissionCache = new HashMap<>();
        return repositoryService.getVirtualRepositories()
                .stream()
                .filter(virtualRepo -> virtualRepo.getDescriptor().getType().isMavenGroup())
                .filter(virtualRepo -> authorizationService.userHasPermissionsOnRepositoryRoot(virtualRepo.getKey(),
                        Set.of(READ), permissionCache))
                .map(VirtualRepo::getDescriptor)
                .toList();
    }

    /**
     * Returns a list of repository configurations that are readable by the current user
     */
    public static List<RepoConfig> getReadableRepoConfigs(Set<RepoDetailsType> repoDetailsTypes,
            RepositoryConfigService repositoryConfigService, AuthorizationService authorizationService) {
        Map<String, Boolean> permissionCache = new HashMap<>();
        return repositoryConfigService.getRepoConfigsByTypes(repoDetailsTypes).stream()
                .filter(repoConfig -> repoConfig.getPackageType().isMavenGroup())
                .filter(repoConfig -> !isInfoRepo(repoConfig.getPackageType()))
                .filter(repoConfig -> !isHiddenRepo(repoConfig.getKey()))
                .filter(repoConfig -> authorizationService.userHasPermissionsOnRepositoryRoot(repoConfig.getKey(),
                        Set.of(READ), permissionCache))
                .toList();
    }
}
