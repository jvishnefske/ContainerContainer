package org.artifactory.ui.rest.service.onboarding;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.http.HttpStatus;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.onboarding.OnBoardingAddon;
import org.artifactory.addon.onboarding.OnBoardingException;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.rest.common.service.RestService;
import org.artifactory.ui.rest.model.onboarding.SupportedRequestModel;
import org.artifactory.ui.rest.model.onboarding.SupportedResponseModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;


/**
 * Service for validating if a set of repositories are supporting onboarding quickstart.
 */
@Slf4j
@Component
public class QuickstartSupportedBatchService implements RestService<SupportedRequestModel> {
    private AddonsManager addonsManager;

    @Autowired
    public void setAddonsManager(AddonsManager addonsManager) {
        this.addonsManager = addonsManager;
    }

    @Override
    public void execute(ArtifactoryRestRequest<SupportedRequestModel> request, RestResponse response) {
        SupportedRequestModel imodel = request.getImodel();
        if (imodel == null || CollectionUtils.isEmpty(imodel.getRepoKeys())) {
            response.responseCode(HttpStatus.SC_BAD_REQUEST);
            return;
        }
        try {
            var model = addonsManager.addonByType(OnBoardingAddon.class).assertRepoIsSupported(imodel.getRepoKeys());
            response.responseCode(HttpStatus.SC_OK);
            response.iModel(new SupportedResponseModel(model));
        } catch (OnBoardingException e) {
            response.responseCode(e.getStatus()).error(e.getMessage());
        }
    }
}
