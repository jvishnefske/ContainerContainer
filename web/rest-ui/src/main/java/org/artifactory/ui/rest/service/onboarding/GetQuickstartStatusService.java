package org.artifactory.ui.rest.service.onboarding;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.onboarding.OnBoardingAddon;
import org.artifactory.addon.onboarding.OnBoardingException;
import org.artifactory.addon.onboarding.model.OnBoardingRecord;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.rest.common.service.RestService;
import org.artifactory.ui.rest.model.onboarding.OnBoardingResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Service for getting ongoing onboarding quickstart session status.
 *
 * @author BarakH
 */
@Slf4j
@Component
public class GetQuickstartStatusService implements RestService<String> {

    private AddonsManager addonsManager;

    @Autowired
    public void setAddonsManager(AddonsManager addonsManager) {
        this.addonsManager = addonsManager;
    }

    @Override
    public void execute(ArtifactoryRestRequest<String> request, RestResponse response) {
        String repoKey = request.getImodel();

        if (StringUtils.isEmpty(repoKey)) {
            response.responseCode(HttpStatus.SC_BAD_REQUEST);
            return;
        }
        try {
            OnBoardingRecord onBoardingRecord = addonsManager.addonByType(OnBoardingAddon.class).getStatus(repoKey);
            if (onBoardingRecord != null) {
                OnBoardingResponse onBoardingResponse = OnBoardingResponse.builder()
                        .isAuthenticated(onBoardingRecord.isAuthenticated())
                        .isDownloaded(onBoardingRecord.isDownloaded())
                        .isPublished(onBoardingRecord.isUploaded()).build();
                response.iModel(onBoardingResponse);
                return;
            }
        } catch (OnBoardingException e) {
            response.responseCode(e.getStatus()).error(e.getMessage());
            return;
        }
        response.responseCode(HttpStatus.SC_BAD_REQUEST);
    }
}
