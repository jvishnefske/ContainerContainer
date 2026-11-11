package org.artifactory.ui.rest.service.utils.repositories.repodata;

import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;

public interface GetRepositoriesDataHandler<T> {

    void handle(ArtifactoryRestRequest<T> request, RestResponse response);

    String getHandledQueryParam();
}