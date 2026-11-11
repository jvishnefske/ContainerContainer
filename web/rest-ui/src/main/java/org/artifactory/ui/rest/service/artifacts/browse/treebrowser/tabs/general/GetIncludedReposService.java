package org.artifactory.ui.rest.service.artifacts.browse.treebrowser.tabs.general;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpStatus;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.descriptor.repo.RepoDescriptor;
import org.artifactory.descriptor.repo.VirtualRepoDescriptor;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.rest.common.service.RestService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

import static org.artifactory.ui.utils.GeneralArtifactsInfoUtils.updateVirtualIncludedRepositories;

/**
 * @author Amir Avidan
 */
@Slf4j
@Component
@AllArgsConstructor
public class GetIncludedReposService implements RestService<Void> {

    private final RepositoryService repositoryService;
    private final AuthorizationService authorizationService;

    @Override
    public void execute(ArtifactoryRestRequest request, RestResponse response) {
        String repoKey = request.getPathParamByKey("repoKey");
        RepoDescriptor repoDescriptor = repositoryService.repoDescriptorByKey(repoKey);
        if (repoDescriptor == null) {
            log.debug("Unable to find repository descriptor for repo key '{}'", repoKey);
            response.responseCode(HttpStatus.SC_NOT_FOUND)
                    .error("Unable to find repository descriptor for repo key '" + repoKey + "'");
            return;
        }
        if (repoDescriptor instanceof VirtualRepoDescriptor virtualRepoDescriptor) {
            response.iModel(Map.of("includedRepositories",
                    updateVirtualIncludedRepositories(virtualRepoDescriptor, authorizationService)));
        } else {
            response.iModel(Map.of("includedRepositories", List.of()));
        }
    }
}