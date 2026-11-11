package org.artifactory.ui.rest.model.onboarding;


import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@Getter
@Setter
public class OnBoardingParams {

    private String repoKey;
    private String downloadPackageName;
    private String downloadVersion;
    private String uploadPackageName;
    private String uploadVersion;
}
