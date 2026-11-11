package org.artifactory.ui.rest.service.admin.configuration.repositories.util;

import org.apache.commons.lang3.StringUtils;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.repo.config.RepoConfigDefaultValues;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.rest.common.service.RestService;
import org.artifactory.exception.http.BadRequestException;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Return to the UI all the remote registries URLs with their names.
 *
 * @author BarakH
 */
@Component
public class GetRemoteUrlsByType implements RestService<Map<String, String>> {

    @Override
    public void execute(ArtifactoryRestRequest<Map<String, String>> request, RestResponse response) {
        String type = request.getPathParamByKey("repoType");
        if (StringUtils.isEmpty(type)) {
            throw new BadRequestException("RepoType must be provided");
        }
        RepoType repoType = RepoType.fromType(type);
        response.iModel(RepoConfigDefaultValues.DefaultUrl.urlListByType(repoType));
    }
}
