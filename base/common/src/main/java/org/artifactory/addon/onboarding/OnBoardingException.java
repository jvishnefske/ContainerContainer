package org.artifactory.addon.onboarding;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public class OnBoardingException extends Exception {

    private String message;
    private int Status;
}
