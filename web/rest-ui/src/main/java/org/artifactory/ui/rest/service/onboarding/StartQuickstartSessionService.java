package org.artifactory.ui.rest.service.onboarding;

import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpStatus;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.onboarding.OnBoardingAddon;
import org.artifactory.addon.onboarding.OnBoardingException;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.rest.common.service.RestService;
import org.artifactory.ui.rest.model.onboarding.OnBoardingParams;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Service for starting a new onboarding quickstart session.
 *
 * @author BarakH
 */
@Slf4j
@Component
public class StartQuickstartSessionService implements RestService<OnBoardingParams> {

    private AddonsManager addonsManager;

    @Autowired
    public void setAddonsManager(AddonsManager addonsManager) {
        this.addonsManager = addonsManager;
    }

    @Override
    public void execute(ArtifactoryRestRequest<OnBoardingParams> request, RestResponse response) {
        OnBoardingParams params = request.getImodel();

        if (params == null) {
            response.responseCode(HttpStatus.SC_BAD_REQUEST);
            return;
        }
        try {
            if (addonsManager.addonByType(OnBoardingAddon.class)
                    .startOnBoardingTutorial(params.getRepoKey(), params.getDownloadPackageName(),
                            params.getDownloadVersion(), params.getUploadPackageName(), params.getUploadVersion())) {
                response.responseCode(HttpStatus.SC_OK);
                return;
            }
        } catch (OnBoardingException e) {
            response.responseCode(e.getStatus()).error(e.getMessage());
            return;
        }
        response.responseCode(HttpStatus.SC_BAD_REQUEST);
    }
}
