package org.artifactory.ui.rest.service.artifacts.search.syntax;

import lombok.RequiredArgsConstructor;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.rest.common.service.RestService;
import org.artifactory.ui.rest.model.artifacts.search.syntax.Search;
import org.springframework.stereotype.Component;

/**
 * @author Igor Usenko
 */
@Component
@RequiredArgsConstructor
public class SyntaxSearchRestService implements RestService<Search> {

    private final SyntaxSearchOperations syntaxSearchService;


    @Override
    public void execute(ArtifactoryRestRequest<Search> request, RestResponse response) {
        syntaxSearchService.execute(request, response);
    }
}
