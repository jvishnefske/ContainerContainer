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

package org.artifactory.repo.virtual;

import com.google.common.annotations.VisibleForTesting;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.descriptor.repo.RepoLayout;
import org.artifactory.descriptor.repo.VirtualRepoDescriptor;
import org.artifactory.descriptor.repo.VirtualResolverFilter;
import org.artifactory.model.VirtualRepoConfig;
import org.artifactory.repo.InternalRepoPathFactory;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.service.InternalRepositoryService;
import org.artifactory.request.RepoRequests;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Virtual resolver filter which filters virtual repo according to a request path and include/exclude pattern.
 *
 * @author Shay Yaakov
 */
public class VirtualResolverRequestFilter implements VirtualResolverFilter {
    private static final Logger log = LoggerFactory.getLogger(VirtualResolverRequestFilter.class);

    private final VirtualRepo virtualRepo;
    private final RepoPath repoPath;
    private final InternalRepositoryService repositoryService;
    private final CentralConfigService centralConfigService;
    private final PathTranslator pathTranslator;

    private final Map<String, Boolean> filterResultsForRepo = new HashMap<>();

    public VirtualResolverRequestFilter(VirtualRepo virtualRepo, RepoPath repoPath,
            InternalRepositoryService repositoryService, PathTranslator pathTranslator,
            CentralConfigService centralConfigService) {
        this.virtualRepo = virtualRepo;
        this.repoPath = repoPath;
        this.repositoryService = repositoryService;
        this.pathTranslator = pathTranslator;
        this.centralConfigService = centralConfigService;

    }

    @Override
    @Deprecated
    public boolean accepts(VirtualRepoDescriptor childDescriptor) {
        String childVirtualRepoKey = childDescriptor.getKey();
        Boolean filterResultForRepo = filterResultsForRepo.get(childVirtualRepoKey);
        if (filterResultForRepo != null) {
            return filterResultForRepo;
        }
        VirtualRepo childVirtualRepo = repositoryService.virtualRepositoryByKey(childVirtualRepoKey);
        if (childVirtualRepo == null) {
            log.error("Could not find nested virtual repository with key '{}'", childVirtualRepoKey);
            filterResultsForRepo.put(childVirtualRepoKey, false);
            return false;
        }

        String path = repoPath.getPath();
        String translatedPath = translateRepoPath(virtualRepo.getDescriptor(), childDescriptor, path);
        filterResultForRepo = comparePaths(childVirtualRepoKey, childVirtualRepo, path, translatedPath);
        filterResultsForRepo.put(childVirtualRepoKey, filterResultForRepo);
        return filterResultForRepo;
    }


    @Override
    public boolean accepts(VirtualRepoConfig childRepoConfig) {
        String childVirtualRepoKey = childRepoConfig.getKey();
        Boolean filterResultForRepo = filterResultsForRepo.get(childVirtualRepoKey);
        if (filterResultForRepo != null) {
            return filterResultForRepo;
        }
        VirtualRepo childVirtualRepo = repositoryService.virtualRepositoryByKey(childVirtualRepoKey);
        if (childVirtualRepo == null) {
            log.error("Could not find nested virtual repository with key '{}'", childVirtualRepoKey);
            filterResultsForRepo.put(childVirtualRepoKey, false);
            return false;
        }

        String path = repoPath.getPath();
        String translatedPath = translateRepoPath(virtualRepo.getRepoConfig(), childRepoConfig, path);
        filterResultForRepo = comparePaths(childVirtualRepoKey, childVirtualRepo, path, translatedPath);
        filterResultsForRepo.put(childVirtualRepoKey, filterResultForRepo);
        return filterResultForRepo;
    }

    @VisibleForTesting
    protected boolean comparePaths(String childVirtualRepoKey, VirtualRepo childVirtualRepo, String path,
            String translatedPath) {
        if (!translatedPath.equals(path)) {
            RepoRequests.logToContext("Resource was translated to '%s' in order to search within nested virtual '%s'",
                    translatedPath, childVirtualRepoKey);
        }
        RepoPath translatedRepoPath = InternalRepoPathFactory.create(childVirtualRepoKey, translatedPath,
                repoPath.isFolder());
        if (!childVirtualRepo.accepts(translatedRepoPath)) {
            // includes/excludes should not affect system paths
            RepoRequests.logToContext("Adding no aggregated repositories - requested artifact is rejected by the " +
                    "include exclude patterns of nested virtual '%s'", childVirtualRepoKey);
            return false;
        }

        RepoRequests.logToContext("Appending the nested virtual repository '%s'", childVirtualRepo.getKey());
        return true;
    }

    private String translateRepoPath(VirtualRepoDescriptor source, VirtualRepoDescriptor target, String path) {
        RepoLayout sourceRepoLayout = source.getRepoLayout();
        RepoLayout targetRepoLayout = target.getRepoLayout();
        return translateRepoPath(sourceRepoLayout, targetRepoLayout, path);
    }

    @VisibleForTesting
    protected String translateRepoPath(VirtualRepoConfig source, VirtualRepoConfig target, String path) {
        String sourceLayoutRef = source.getBaseConfig().getRepoLayoutRef();
        String targetLayoutRef = target.getBaseConfig().getRepoLayoutRef();
        return pathTranslator.translateArtifactPath(centralConfigService.getDescriptor().getRepoLayout(sourceLayoutRef),
                centralConfigService.getDescriptor().getRepoLayout(targetLayoutRef), path);
    }

    private String translateRepoPath(RepoLayout sourceRepoLayout, RepoLayout targetRepoLayout, String path) {
        return pathTranslator.translateArtifactPath(sourceRepoLayout, targetRepoLayout, path);
    }

    @VisibleForTesting
    protected Map<String, Boolean> getFilterResultsForRepo() {
        return filterResultsForRepo;
    }
}
