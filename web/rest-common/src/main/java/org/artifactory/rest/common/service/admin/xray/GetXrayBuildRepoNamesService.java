package org.artifactory.rest.common.service.admin.xray;

import com.google.common.collect.Sets;
import com.jfrog.build.acl.bridge.BuildFacade;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.projects.ProjectsService;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.rest.common.service.RestService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * @author Mor Merhav
 */
@Component
public class GetXrayBuildRepoNamesService implements RestService<String> {

    private final BuildFacade buildFacade;

    @Autowired
    public GetXrayBuildRepoNamesService(BuildFacade buildFacade) {
        this.buildFacade = buildFacade;
    }

    @Override
    public void execute(ArtifactoryRestRequest<String> request, RestResponse response) {
        String projectKey = request.getQueryParamByKey(ProjectsService.PROJECT_KEY);
        if (StringUtils.isBlank(projectKey)) {
            response.iModel(buildFacade.getAllBuildInfoRepos());
            return;
        }
        response.iModel(Sets.newHashSet(buildFacade.getBuildInfoRepoKeyByProject(projectKey, true)));
    }
}
