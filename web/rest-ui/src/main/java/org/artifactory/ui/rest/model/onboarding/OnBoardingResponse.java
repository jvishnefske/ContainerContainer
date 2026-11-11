package org.artifactory.ui.rest.model.onboarding;

import lombok.Builder;
import lombok.Data;

@Builder
@Data
public class OnBoardingResponse {

    private Boolean isAuthenticated;
    private Boolean isPublished;
    private Boolean isDownloaded;
}
