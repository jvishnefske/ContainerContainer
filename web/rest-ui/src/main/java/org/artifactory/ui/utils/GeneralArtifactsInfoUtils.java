package org.artifactory.ui.utils;

import com.google.common.collect.Lists;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.descriptor.repo.RepoDescriptor;
import org.artifactory.descriptor.repo.VirtualRepoDescriptor;
import org.artifactory.request.RequestThreadLocal;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.tabs.general.includedrepositories.Repository;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.tabs.general.virtualrepositories.VirtualRepository;
import org.jfrog.client.util.PathUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.artifactory.security.ArtifactoryPermission.READ;

/**
 * @author Amir Avidan
 */
@Slf4j
@UtilityClass
public class GeneralArtifactsInfoUtils {

    public static final String USE_DEFAULT_MESSAGE =
            "Cannot retrieve {} repo config from the RepositoryConfigService, Using RepositoryService instead";

    public static List<VirtualRepository> populateVirtualRepositories(
            RepositoryService repositoryService, String repoKey) {
        RepoDescriptor descriptor = repositoryService.repoDescriptorByKey(repoKey);
        if (descriptor == null) {
            return Lists.newArrayList();
        }
        String baseURL = PathUtils.addTrailingSlash(RequestThreadLocal.getBaseUrl());
        return repositoryService.getVirtualReposContainingRepo(descriptor).stream()
                .map(virtualRepoDescriptor -> new VirtualRepository(virtualRepoDescriptor.getKey(),
                        baseURL + virtualRepoDescriptor.getKey()))
                .toList();
    }

    public static List<Repository> updateVirtualIncludedRepositories(VirtualRepoDescriptor virtualRepoDescriptor,
            AuthorizationService authorizationService) {
        Map<String, Boolean> canReadRepoCache = new HashMap<>();
        String baseURL = PathUtils.addTrailingSlash(RequestThreadLocal.getBaseUrl());
        return virtualRepoDescriptor.getRepositories().stream()
                .filter(descriptor -> authorizationService
                        .userHasPermissionsOnRepositoryRoot(descriptor.getKey(), Set.of(READ), canReadRepoCache))
                .map(descriptor -> new Repository(descriptor, baseURL + descriptor.getKey()))
                .toList();
    }
}
