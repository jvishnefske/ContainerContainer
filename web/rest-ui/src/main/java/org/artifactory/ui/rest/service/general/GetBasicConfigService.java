package org.artifactory.ui.rest.service.general;

import org.artifactory.api.config.CentralConfigService;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.rest.common.service.RestService;
import org.artifactory.ui.rest.model.general.BaseConfigModel;
import org.artifactory.util.HttpUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * @author Omri Ziv
 */
@Component
public class GetBasicConfigService implements RestService {

    @Autowired
    private CentralConfigService centralConfigService;

    @Override
    public void execute(ArtifactoryRestRequest request, RestResponse response) {

        BaseConfigModel setMeUpModel = getBaseConfigModel(request);
        response.iModel(setMeUpModel);
    }

    private BaseConfigModel getBaseConfigModel(ArtifactoryRestRequest artifactoryRequest) {
        BaseConfigModel baseConfigModel = new BaseConfigModel();
        String servletContextUrl = HttpUtils.getServletContextUrl(artifactoryRequest.getServletRequest());
        baseConfigModel.setBaseUrl(servletContextUrl);
        return baseConfigModel;
    }

}
