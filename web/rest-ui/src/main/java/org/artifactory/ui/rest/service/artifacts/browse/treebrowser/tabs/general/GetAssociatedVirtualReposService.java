package org.artifactory.ui.rest.service.artifacts.browse.treebrowser.tabs.general;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.rest.common.service.RestService;
import org.artifactory.ui.utils.GeneralArtifactsInfoUtils;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * @author Hussein Alshqerat
 * @author Amir Avidan
 */
@Slf4j
@Component
@AllArgsConstructor
public class GetAssociatedVirtualReposService implements RestService<Void> {

    private final RepositoryService repositoryService;

    @Override
    public void execute(ArtifactoryRestRequest request, RestResponse response) {
        String repoKey = request.getPathParamByKey("repoKey");
        response.iModel(Map.of("virtualRepositories",
                GeneralArtifactsInfoUtils.populateVirtualRepositories(repositoryService, repoKey)));
    }
}